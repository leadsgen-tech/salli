package lk.salli.app.sms

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
class HistoricalImporter internal constructor(
    private val readInbox: (Long?) -> List<SmsInboxReader.RawSms>,
    private val ingestMessage: suspend (sender: String, body: String, receivedAt: Long) -> IngestResult,
) {
    @Inject
    constructor(reader: SmsInboxReader, ingestor: TransactionIngestor) : this(
        readInbox = reader::readInbox,
        ingestMessage = ingestor::ingest,
    )

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

    /**
     * @param onIngested optional hook per message, called after the ingest with the result and
     * the SMS's receive time. The 3-day refresh uses it to fire transaction prompts for
     * anything fresh it picked up; the first-run import and full resync pass nothing.
     */
    fun import(
        sinceMillis: Long? = null,
        onIngested: (suspend (IngestResult, Long) -> Unit)? = null,
    ): Flow<Progress> = flow {
        val all = readInbox(sinceMillis).sortedBy { it.dateMillis } // oldest first
        val total = all.size
        var inserted = 0
        var merged = 0
        var paired = 0
        var duplicates = 0
        var queued = 0
        var dropped = 0
        emit(Progress(0, total, 0, 0, 0, 0, 0, 0))

        // Debug builds only: per-bank counts of what the reader returned and what ingest decided.
        val debugOutcomes = sortedMapOf<String, MutableMap<String, Int>>()
        all.forEachIndexed { index, raw ->
            val result = try {
                ingestMessage(raw.address, raw.body, raw.dateMillis)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                android.util.Log.e("SalliImport", "ingest failed for sender=${raw.address}", error)
                // Rows already committed before this one remain safe. Failing the flow keeps the
                // one-shot completion preference false, so a later retry can dedupe those rows and
                // resume instead of silently declaring a partial import successful.
                throw HistoricalImportException(
                    processedBeforeFailure = index,
                    total = total,
                    cause = error,
                )
            }
            onIngested?.invoke(result, raw.dateMillis)
            if (lk.salli.app.BuildConfig.DEBUG) {
                val id = lk.salli.parser.util.SenderId.normalize(raw.address)
                if (id in DEBUG_SENDERS) {
                    val label = (result as? IngestResult.Dropped)?.let { "Dropped:" + it.reason.take(22) }
                        ?: result::class.simpleName.orEmpty()
                    debugOutcomes.getOrPut(id) { mutableMapOf() }.merge(label, 1, Int::plus)
                }
                if (index == all.lastIndex) {
                    android.util.Log.d("SalliImport", "read ${all.size} messages; outcomes by bank: $debugOutcomes")
                }
            }
            when (result) {
                is IngestResult.Inserted -> inserted++
                is IngestResult.Paired -> { paired++; inserted++ }
                is IngestResult.Merged -> merged++
                is IngestResult.Duplicate -> duplicates++
                is IngestResult.Queued -> queued++
                is IngestResult.Dropped -> dropped++
                // Bills and Fuel Pass records live in their own tables; for the progress
                // counter they are simply "not a transaction".
                is IngestResult.Utility -> dropped++
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
    }.flowOn(Dispatchers.IO)

    private companion object {
        val DEBUG_SENDERS = setOf("COMBANK", "ComBank_Q+", "BOC", "PeoplesBank", "HNB", "SAMPATH", "SEYLAN")
    }
}

class HistoricalImportException(
    val processedBeforeFailure: Int,
    val total: Int,
    cause: Exception,
) : Exception(
    "Import stopped after $processedBeforeFailure of $total messages: " +
        (cause.message ?: cause::class.simpleName.orEmpty()),
    cause,
)
