package lk.salli.app.sms

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
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
        val result = runCatching { ingestor.ingest(sender, body, receivedAt) }
            .getOrElse { return Result.retry() }
        // Fire the "who was this for?" prompt only for freshly-inserted transactions.
        // Paired/Merged/Duplicate/Queued paths either already know the counterpart or
        // aren't transactions at all. The notifier itself filters by freshness + type.
        when (result) {
            is IngestResult.Inserted -> promptNotifier.notifyIfNeeded(result.transactionId)
            is IngestResult.Paired -> {
                // An earlier prompt may have fired for the counterpart (e.g. "Who did you
                // pay Rs 5,000?") when the first leg arrived. Now that we know both legs
                // and it's an internal transfer, retract the stale prompt for both sides.
                promptNotifier.cancelFor(result.transactionId)
                promptNotifier.cancelFor(result.counterpartId)
            }
            else -> {}
        }
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
