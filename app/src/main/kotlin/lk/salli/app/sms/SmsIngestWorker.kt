package lk.salli.app.sms

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import lk.salli.data.ingest.IngestResult
import lk.salli.data.ingest.TransactionIngestor

/**
 * Runs a single SMS through the parser/ingestor pipeline. Invoked by [SmsReceiver] when a
 * broadcast arrives, and by [HistoricalImporter] during first-run bulk ingest. When a fresh
 * transaction is inserted that's missing a merchant (typical for BOC transfers), fires an
 * inline-reply notification so the user can tag the payee right from the shade.
 */
@HiltWorker
class SmsIngestWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val ingestor: TransactionIngestor,
    private val promptNotifier: TransactionPromptNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sender = inputData.getString(KEY_SENDER) ?: return Result.failure()
        val body = inputData.getString(KEY_BODY) ?: return Result.failure()
        val receivedAt = inputData.getLong(KEY_RECEIVED_AT, System.currentTimeMillis())
        val result = try {
            ingestor.ingest(sender, body, receivedAt)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            android.util.Log.w("SalliIngest", "ingest failed for sender=$sender, will retry", error)
            return Result.retry()
        }
        if (lk.salli.app.BuildConfig.DEBUG) android.util.Log.d("SalliIngest", "sender=$sender -> $result")
        // The notifier decides whether a prompt fires, is withdrawn, or nothing happens.
        promptNotifier.handle(result, receivedAt)
        return Result.success()
    }

    companion object {
        const val KEY_SENDER = "sender"
        const val KEY_BODY = "body"
        const val KEY_RECEIVED_AT = "received_at"

        fun inputOf(sender: String, body: String, receivedAt: Long): Data =
            Data.Builder()
                .putString(KEY_SENDER, sender)
                .putString(KEY_BODY, body)
                .putLong(KEY_RECEIVED_AT, receivedAt)
                .build()
    }
}
