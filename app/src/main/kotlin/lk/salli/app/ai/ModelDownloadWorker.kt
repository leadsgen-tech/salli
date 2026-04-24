package lk.salli.app.ai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lk.salli.data.ai.LocalModel
import lk.salli.data.ai.ModelManager
import lk.salli.data.ai.ModelProgressStore

/**
 * Downloads the on-device model as a WorkManager job. Why not use system [android.app.DownloadManager]?
 * It silently parks large downloads in `STATUS_WAITING_TO_RETRY` with `total_bytes=-1` when the
 * server returns a 302 to a signed CloudFront URL (which is exactly what HuggingFace does). We
 * hit that black hole and never recover. Google's own AI Edge Gallery reference app uses the
 * same CoroutineWorker+HttpURLConnection pattern we have here, so this is the officially-blessed
 * approach.
 *
 * Foreground service so the download survives the user backgrounding the app. Progress is
 * streamed via [setProgress] and mirrored into [ModelProgressStore] so plain Flow observers can
 * see it without binding to WorkManager directly.
 */
@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val modelManager: ModelManager,
    private val progressStore: ModelProgressStore,
) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo = buildForegroundInfo(0, 0)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i(TAG, "doWork starting")
        try {
            setForeground(buildForegroundInfo(0, 0))
        } catch (t: Throwable) {
            Log.e(TAG, "setForeground failed: ${t.javaClass.simpleName}: ${t.message}", t)
            // Continue without foreground — the download can still run, just without a notification.
        }
        val accessToken = inputData.getString(KEY_ACCESS_TOKEN)

        val dest = modelManager.modelFile
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        val existing = if (tmp.exists()) tmp.length() else 0L

        progressStore.update(ModelProgressStore.Snapshot.Running(existing, LocalModel.SIZE_BYTES))

        val connection = try {
            openConnection(LocalModel.DOWNLOAD_URL, accessToken, rangeStart = existing)
        } catch (t: Throwable) {
            Log.e(TAG, "openConnection failed: ${t.javaClass.simpleName}: ${t.message}", t)
            progressStore.update(ModelProgressStore.Snapshot.Failed(t.message ?: "network error"))
            return@withContext Result.retry()
        }

        try {
            val code = connection.responseCode
            Log.i(TAG, "HTTP $code, total=${connection.getHeaderFieldLong("Content-Length", -1L)}")
            if (code !in ACCEPTABLE_CODES) {
                val msg = "HTTP $code ${connection.responseMessage.orEmpty()}"
                progressStore.update(ModelProgressStore.Snapshot.Failed(msg))
                return@withContext if (code in RETRYABLE_CODES) Result.retry() else Result.failure()
            }

            val contentLength = connection.getHeaderFieldLong("Content-Length", -1L)
            val total = if (existing > 0 && code == HttpURLConnection.HTTP_PARTIAL) {
                existing + contentLength
            } else {
                contentLength.takeIf { it > 0 } ?: LocalModel.SIZE_BYTES
            }

            var written = existing
            val buf = ByteArray(BUFFER_BYTES)
            var lastReported = 0L
            var lastNotifyMs = 0L
            connection.inputStream.use { input ->
                FileOutputStream(tmp, existing > 0).use { out ->
                    while (isStopped.not()) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        written += n
                        val now = System.currentTimeMillis()
                        if (now - lastNotifyMs > 250) {
                            setProgress(progressData(written, total))
                            setForeground(buildForegroundInfo(written, total))
                            progressStore.update(ModelProgressStore.Snapshot.Running(written, total))
                            lastNotifyMs = now
                            lastReported = written
                        }
                    }
                }
            }
            if (isStopped) {
                // User cancelled; leave the .tmp so a retry can resume.
                progressStore.update(ModelProgressStore.Snapshot.Idle)
                return@withContext Result.success()
            }
            if (written < MIN_VALID_BYTES) {
                tmp.delete()
                val msg = "Download ended at ${written / (1024 * 1024)} MB — expected ${total / (1024 * 1024)} MB"
                progressStore.update(ModelProgressStore.Snapshot.Failed(msg))
                return@withContext Result.retry()
            }
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
            progressStore.update(ModelProgressStore.Snapshot.Installed(dest.length()))
            Result.success()
        } catch (t: Throwable) {
            Log.e(TAG, "download body failed: ${t.javaClass.simpleName}: ${t.message}", t)
            progressStore.update(ModelProgressStore.Snapshot.Failed(t.message ?: "io error"))
            Result.retry()
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    private fun openConnection(url: String, token: String?, rangeStart: Long): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 30_000
        connection.readTimeout = 60_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Salli/1.0 (Android)")
        if (!token.isNullOrBlank()) {
            connection.setRequestProperty("Authorization", "Bearer ${token.trim()}")
        }
        if (rangeStart > 0) {
            connection.setRequestProperty("Range", "bytes=$rangeStart-")
        }
        connection.connect()
        return connection
    }

    private fun progressData(done: Long, total: Long): Data = Data.Builder()
        .putLong(PROGRESS_BYTES_DONE, done)
        .putLong(PROGRESS_BYTES_TOTAL, total)
        .build()

    private fun buildForegroundInfo(done: Long, total: Long): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Salli AI model",
                NotificationManager.IMPORTANCE_LOW,
            )
            manager.createNotificationChannel(channel)
        }
        val pct = if (total > 0) ((done * 100 / total).coerceIn(0, 100)).toInt() else 0
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Downloading AI model")
            .setContentText("${LocalModel.DISPLAY_NAME} · $pct%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, pct, total <= 0)
            .setOnlyAlertOnce(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val TAG = "SalliDl"
        const val UNIQUE_NAME = "model-download"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val PROGRESS_BYTES_DONE = "bytes_done"
        const val PROGRESS_BYTES_TOTAL = "bytes_total"

        private const val CHANNEL_ID = "salli-model-download"
        private const val NOTIFICATION_ID = 4201

        private const val BUFFER_BYTES = 256 * 1024
        private const val MIN_VALID_BYTES = 100L * 1024L * 1024L

        private val ACCEPTABLE_CODES = setOf(
            HttpURLConnection.HTTP_OK,
            HttpURLConnection.HTTP_PARTIAL,
        )

        private val RETRYABLE_CODES = setOf(
            HttpURLConnection.HTTP_CLIENT_TIMEOUT, // 408
            429, // too many requests
            HttpURLConnection.HTTP_INTERNAL_ERROR, // 500
            HttpURLConnection.HTTP_BAD_GATEWAY, // 502
            HttpURLConnection.HTTP_UNAVAILABLE, // 503
            HttpURLConnection.HTTP_GATEWAY_TIMEOUT, // 504
        )
    }
}
