package lk.salli.app.sms

import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import lk.salli.data.db.SalliDatabase
import lk.salli.data.db.dao.TransactionPreviewRow
import lk.salli.data.ingest.IngestResult
import lk.salli.data.ingest.TransactionIngestor
import lk.salli.domain.TransactionFlow
import lk.salli.domain.TransactionType

/**
 * What the inbox holds, known before a single message is parsed. See [SmsInboxReader.summarize].
 *
 * [senders] lists only banks — the IDs a template claims, plus the banks we recognise but have
 * no template for yet. Promos and OTPs are counted in [totalMessages] (the importer reads them
 * too) but never named.
 */
data class InboxSummary(
    val totalMessages: Int,
    val senders: List<String>,
    /** When the oldest message in the window arrived, or null on an empty inbox. */
    val earliestMillis: Long?,
)

/**
 * One transaction the import just created, flattened for a live ticker.
 *
 * Carries no entity and no formatting: a screen renders the logo from [sender], the amount from
 * [amountMinor] + [currency], and lights a heat-grid cell from [dayEpoch].
 */
data class TransactionPreview(
    val transactionId: Long,
    val sender: String?,
    /**
     * The row title as the timeline derives it: the user's note, else the parsed merchant. Null
     * when neither exists (an ATM withdrawal, a plain transfer) and the UI should label it from
     * [type] instead.
     */
    val title: String?,
    val amountMinor: Long,
    val currency: String,
    val flow: TransactionFlow,
    val type: TransactionType,
    val categoryName: String?,
    /** Days since the epoch, in the device's own timezone — one cell of the year grid. */
    val dayEpoch: Long,
)

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
    private val summarizeInbox: (Long?) -> InboxSummary = { InboxSummary(0, emptyList(), null) },
    private val loadPreviews: suspend (List<Long>) -> List<TransactionPreviewRow> = { emptyList() },
    private val zone: ZoneId = ZoneId.systemDefault(),
    // Last so tests can keep passing the ingest hook as a trailing lambda.
    private val ingestMessage: suspend (sender: String, body: String, receivedAt: Long) -> IngestResult,
) {
    @Inject
    constructor(reader: SmsInboxReader, ingestor: TransactionIngestor, db: SalliDatabase) : this(
        readInbox = reader::readInbox,
        ingestMessage = ingestor::ingest,
        summarizeInbox = reader::summarize,
        loadPreviews = db.transactions()::previewsByIds,
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
        /**
         * Set on exactly one emission per inserted transaction, and null on the periodic count
         * snapshots. Only populated when the caller asked for it — see [import].
         */
        val preview: TransactionPreview? = null,
    )

    /**
     * How big the job is, before it starts. Cheap enough to call on the way into onboarding;
     * runs off the caller's thread.
     */
    suspend fun summarize(sinceMillis: Long? = null): InboxSummary =
        withContext(Dispatchers.IO) { summarizeInbox(sinceMillis) }

    /**
     * @param onIngested optional hook per message, called after the ingest with the result and
     * the SMS's receive time. The 3-day refresh uses it to fire transaction prompts for
     * anything fresh it picked up; the first-run import and full resync pass nothing.
     * @param emitPreviews when true, every inserted transaction also arrives as its own
     * [Progress] carrying a [Progress.preview]. Off by default: the background refresh and
     * resync paths drain this flow and would only pay for emissions nobody reads. Onboarding
     * act 3 turns it on and throttles rendering itself.
     */
    fun import(
        sinceMillis: Long? = null,
        onIngested: (suspend (IngestResult, Long) -> Unit)? = null,
        emitPreviews: Boolean = false,
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
        // Ids awaiting a preview lookup. Resolved one batch per emission boundary rather than one
        // query per row, so the ticker costs a single extra round trip per 25 messages.
        val pendingPreviews = ArrayList<Long>()

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
                is IngestResult.Inserted -> {
                    inserted++
                    if (emitPreviews) pendingPreviews += result.transactionId
                }
                is IngestResult.Paired -> {
                    paired++
                    inserted++
                    if (emitPreviews) pendingPreviews += result.transactionId
                }
                is IngestResult.Merged -> merged++
                is IngestResult.Duplicate -> duplicates++
                is IngestResult.Queued -> queued++
                is IngestResult.Dropped -> dropped++
                // Bills and Fuel Pass records live in their own tables; for the progress
                // counter they are simply "not a transaction".
                is IngestResult.Utility -> dropped++
            }

            // Emit every ~25 items to keep the UI responsive without flooding StateFlow.
            if ((index + 1) % EMIT_EVERY == 0 || index == all.lastIndex) {
                val snapshot = Progress(
                    processed = index + 1,
                    total = total,
                    inserted = inserted,
                    merged = merged,
                    paired = paired,
                    duplicates = duplicates,
                    queued = queued,
                    dropped = dropped,
                )
                if (pendingPreviews.isNotEmpty()) {
                    // The ticker is cosmetic. A failed lookup must never fail the import, which
                    // would leave the one-shot completion pref false and re-scan the whole inbox
                    // next launch over a query nobody's data depends on.
                    val byId = try {
                        loadPreviews(pendingPreviews).associateBy { it.id }
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (error: Exception) {
                        android.util.Log.w("SalliImport", "preview lookup failed; ticker skipped", error)
                        emptyMap()
                    }
                    for (id in pendingPreviews) {
                        val row = byId[id] ?: continue
                        emit(snapshot.copy(preview = row.toPreview()))
                    }
                    pendingPreviews.clear()
                }
                // Previews first so the plain snapshot is always the freshest emission a
                // collector holding only the last value ends up with.
                emit(snapshot)
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun TransactionPreviewRow.toPreview() = TransactionPreview(
        transactionId = id,
        sender = senderAddress,
        title = note?.takeIf { it.isNotBlank() } ?: merchantRaw?.takeIf { it.isNotBlank() },
        amountMinor = amountMinor,
        currency = amountCurrency,
        flow = TransactionFlow.fromId(flowId),
        type = TransactionType.fromId(typeId),
        categoryName = categoryName,
        dayEpoch = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate().toEpochDay(),
    )

    private companion object {
        val DEBUG_SENDERS = setOf("COMBANK", "ComBank_Q+", "BOC", "PeoplesBank", "HNB", "SAMPATH", "SEYLAN")

        /** Count snapshots, and the preview batch boundary, land on the same cadence. */
        const val EMIT_EVERY = 25
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
