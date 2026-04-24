package lk.salli.app.sms

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import lk.salli.data.ingest.IngestResult
import lk.salli.data.ingest.TransactionIngestor

/**
 * Bulk-processes messages from the SMS inbox at first run (or when the user explicitly
 * triggers a re-import). Emits [Progress] updates so the UI can show a live count.
 *
 * Pipelines through the same [TransactionIngestor] used at runtime, so duplicate handling and
 * transfer pairing behave identically whether the user installed Salli after their first BOC
 * SMS or before it.
 */
@Singleton
class HistoricalImporter @Inject constructor(
    private val reader: SmsInboxReader,
    private val ingestor: TransactionIngestor,
) {
    data class Progress(
        val processed: Int,
        val total: Int,
        val inserted: Int,
        val merged: Int,
        val paired: Int,
        val duplicates: Int,
        val queued: Int,
        val dropped: Int,
    )

    fun import(sinceMillis: Long? = null): Flow<Progress> = flow {
        val all = reader.readInbox(sinceMillis).sortedBy { it.dateMillis } // oldest first
        val total = all.size
        var inserted = 0
        var merged = 0
        var paired = 0
        var duplicates = 0
        var queued = 0
        var dropped = 0
        emit(Progress(0, total, 0, 0, 0, 0, 0, 0))

        all.forEachIndexed { index, raw ->
            when (ingestor.ingest(sender = raw.address, body = raw.body, receivedAt = raw.dateMillis)) {
                is IngestResult.Inserted -> inserted++
                is IngestResult.Paired -> { paired++; inserted++ }
                is IngestResult.Merged -> merged++
                is IngestResult.Duplicate -> duplicates++
                is IngestResult.Queued -> queued++
                is IngestResult.Dropped -> dropped++
            }

            // Emit every ~25 items to keep the UI responsive without flooding StateFlow.
            if ((index + 1) % 25 == 0 || index == all.lastIndex) {
                emit(
                    Progress(
                        processed = index + 1,
                        total = total,
                        inserted = inserted,
                        merged = merged,
                        paired = paired,
                        duplicates = duplicates,
                        queued = queued,
                        dropped = dropped,
                    ),
                )
            }
        }
    }
}
