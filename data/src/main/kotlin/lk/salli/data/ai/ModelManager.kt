package lk.salli.data.ai

import android.content.Context
import android.net.Uri
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Owns the lifecycle of the on-device model file. Coordinates between the WorkManager-based
 * [lk.salli.app.ai.ModelDownloadWorker] (which actually streams bytes) and the settings UI
 * (which observes progress and file presence).
 *
 * Why WorkManager and not the system `DownloadManager`? DownloadManager parks large downloads
 * in `STATUS_WAITING_TO_RETRY` with `total_bytes=-1` when the server returns a 302 redirect to
 * a signed URL — which is exactly what HuggingFace does. That's a well-known dead-end. Google's
 * own AI Edge Gallery reference app uses a CoroutineWorker + HttpURLConnection, which is what
 * we do here.
 *
 * This class deliberately avoids any Worker-type imports so it can live in `:data` without
 * pulling the worker into the module graph. The worker class name is referenced by its unique
 * work name and the worker lives in `:app`.
 */
class ModelManager(
    private val context: Context,
    private val progressStore: ModelProgressStore,
) {

    val modelFile: File
        get() = File(
            context.getExternalFilesDir("models") ?: File(context.filesDir, "models"),
            LocalModel.FILENAME,
        )

    fun isInstalled(): Boolean = modelFile.exists() && modelFile.length() > MIN_VALID_BYTES

    /**
     * Enqueue the download worker. Caller passes a [workRequest] built elsewhere (the factory
     * lives in `:app` so it can construct the worker class — this module doesn't know about the
     * worker). If a download is already running or the file is installed, this is a no-op.
     */
    fun enqueueDownload(workRequest: OneTimeWorkRequest) {
        if (isInstalled()) return
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK,
            ExistingWorkPolicy.REPLACE,
            workRequest,
        )
    }

    fun cancelDownload() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
    }

    fun deleteModel(): Boolean {
        cancelDownload()
        val tmp = File(modelFile.parentFile, modelFile.name + ".tmp")
        if (tmp.exists()) tmp.delete()
        return modelFile.takeIf { it.exists() }?.delete() ?: true
    }

    /**
     * User picked the `.task` file themselves via SAF. Copy the bytes into our private dir and
     * return whether the result looks like a valid model. We don't trust the filename.
     */
    suspend fun importFromUri(uri: Uri): ImportResult {
        modelFile.parentFile?.mkdirs()
        if (modelFile.exists()) modelFile.delete()
        val input = context.contentResolver.openInputStream(uri)
            ?: return ImportResult.Failed("Couldn't open the selected file")
        var copied = 0L
        try {
            input.use { ins ->
                FileOutputStream(modelFile).use { out ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        val n = ins.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        copied += n
                    }
                }
            }
        } catch (t: Throwable) {
            modelFile.delete()
            return ImportResult.Failed(t.message ?: "copy error")
        }
        if (copied < MIN_VALID_BYTES) {
            modelFile.delete()
            return ImportResult.Failed(
                "File is only ${copied / (1024 * 1024)} MB — the real model is about " +
                    "${LocalModel.SIZE_BYTES / (1024 * 1024)} MB. Pick the correct .task file.",
            )
        }
        progressStore.update(ModelProgressStore.Snapshot.Installed(copied))
        return ImportResult.Success(copied)
    }

    /**
     * UI-facing status: combines the file-system truth (is the final file there?) with the
     * in-flight worker state (are we downloading, did it fail?). File presence wins — if the
     * real file exists on disk, we show Installed regardless of what the worker store says.
     */
    fun observeStatus(): Flow<ModelStatus> = combine(
        flowOf(Unit),
        progressStore.state,
    ) { _, snap ->
        toStatus(snap)
    }.map { it }

    fun currentStatus(): ModelStatus = toStatus(progressStore.state.value)

    private fun toStatus(snap: ModelProgressStore.Snapshot): ModelStatus {
        if (isInstalled()) return ModelStatus.Installed(modelFile.length())
        return when (snap) {
            is ModelProgressStore.Snapshot.Idle -> ModelStatus.NotDownloaded
            is ModelProgressStore.Snapshot.Running ->
                ModelStatus.Downloading(snap.bytesDownloaded, snap.totalBytes)
            is ModelProgressStore.Snapshot.Installed -> ModelStatus.Installed(snap.sizeBytes)
            is ModelProgressStore.Snapshot.Failed -> ModelStatus.Failed(snap.reason)
        }
    }

    companion object {
        const val UNIQUE_WORK = "model-download"
        private const val MIN_VALID_BYTES: Long = 100L * 1024L * 1024L

        /** Build the input bundle for the download worker (keeps the worker unaware of prefs). */
        fun buildInputData(accessToken: String? = null): Data {
            val builder = Data.Builder()
            if (!accessToken.isNullOrBlank()) builder.putString(KEY_ACCESS_TOKEN, accessToken)
            return builder.build()
        }

        const val KEY_ACCESS_TOKEN = "access_token"
    }
}

sealed interface ModelStatus {
    data object NotDownloaded : ModelStatus
    data class Queued(val bytesDownloaded: Long, val totalBytes: Long) : ModelStatus
    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long) : ModelStatus
    data class Installed(val sizeBytes: Long) : ModelStatus
    data class Failed(val reason: String) : ModelStatus
}

sealed interface ImportResult {
    data class Success(val bytes: Long) : ImportResult
    data class Failed(val reason: String) : ImportResult
}
