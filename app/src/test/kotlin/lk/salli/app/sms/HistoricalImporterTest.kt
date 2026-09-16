package lk.salli.app.sms

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import lk.salli.data.db.dao.TransactionPreviewRow
import lk.salli.data.ingest.IngestResult
import lk.salli.domain.TransactionFlow
import lk.salli.domain.TransactionType
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, manifest = Config.NONE, sdk = [34])
class HistoricalImporterTest {

    @Test
    fun `inbox query and ingest run away from the collector thread`() = runBlocking {
        val collector = Executors.newSingleThreadExecutor { task -> Thread(task, "import-test-collector") }
            .asCoroutineDispatcher()
        var readThread = ""
        var ingestThread = ""
        try {
            withContext(collector) {
                HistoricalImporter(
                    readInbox = {
                        readThread = Thread.currentThread().name
                        listOf(raw(1))
                    },
                    ingestMessage = { _, _, _ ->
                        ingestThread = Thread.currentThread().name
                        IngestResult.Inserted(1)
                    },
                ).import().toList()
            }
        } finally {
            collector.close()
        }

        assertThat(readThread).doesNotContain("import-test-collector")
        assertThat(ingestThread).doesNotContain("import-test-collector")
    }

    @Test
    fun `row exception fails the flow after safely committed earlier rows`() = runBlocking {
        val attempts = AtomicInteger()
        val importer = HistoricalImporter(
            readInbox = { listOf(raw(1), raw(2)) },
            ingestMessage = { _, _, _ ->
                if (attempts.incrementAndGet() == 1) IngestResult.Inserted(1)
                else throw IllegalStateException("database full")
            },
        )

        val error = runCatching { importer.import().toList() }.exceptionOrNull()

        assertThat(error).isInstanceOf(HistoricalImportException::class.java)
        val importError = error as HistoricalImportException
        assertThat(importError.processedBeforeFailure).isEqualTo(1)
        assertThat(importError.total).isEqualTo(2)
        assertThat(importError).hasCauseThat().hasMessageThat().isEqualTo("database full")
        assertThat(attempts.get()).isEqualTo(2)
    }

    @Test
    fun `cancellation propagates without being converted to an import failure`() = runBlocking {
        val importer = HistoricalImporter(
            readInbox = { listOf(raw(1)) },
            ingestMessage = { _, _, _ -> throw CancellationException("stop") },
        )

        val error = runCatching { importer.import().toList() }.exceptionOrNull()

        assertThat(error).isInstanceOf(CancellationException::class.java)
        assertThat(error).isNotInstanceOf(HistoricalImportException::class.java)
    }

    @Test
    fun `fatal errors remain fatal and retain the original cause`() = runBlocking {
        val fatal = AssertionError("fatal")
        val importer = HistoricalImporter(
            readInbox = { listOf(raw(1)) },
            ingestMessage = { _, _, _ -> throw fatal },
        )

        val error = runCatching { importer.import().toList() }.exceptionOrNull()

        // flowOn may recover the stack trace by cloning a throwable and chaining the original.
        // The contract is that a fatal Error stays fatal rather than becoming a row failure.
        assertThat(error).isInstanceOf(AssertionError::class.java)
        assertThat(error).hasMessageThat().isEqualTo("fatal")
        assertThat(error?.cause).isSameInstanceAs(fatal)
    }

    @Test
    fun `empty inbox completes with an honest zero progress snapshot`() = runBlocking {
        val progress = HistoricalImporter(
            readInbox = { emptyList() },
            ingestMessage = { _, _, _ -> error("must not ingest") },
        ).import().toList()

        assertThat(progress).containsExactly(
            HistoricalImporter.Progress(0, 0, 0, 0, 0, 0, 0, 0),
        )
        Unit
    }

    // ---------------------------------------------------------------- inbox summary

    @Test
    fun `summarize forwards the window and hands back what the reader found`() = runBlocking {
        var askedSince: Long? = -1L
        val importer = HistoricalImporter(
            readInbox = { error("summarize must not read bodies") },
            summarizeInbox = { since ->
                askedSince = since
                InboxSummary(2_096, listOf("BOC", "COMBANK"), 1_740_000_000_000L)
            },
            ingestMessage = { _, _, _ -> error("summarize must not ingest") },
        )

        val summary = importer.summarize(sinceMillis = 1_000L)

        assertThat(askedSince).isEqualTo(1_000L)
        assertThat(summary.totalMessages).isEqualTo(2_096)
        assertThat(summary.senders).containsExactly("BOC", "COMBANK").inOrder()
        assertThat(summary.earliestMillis).isEqualTo(1_740_000_000_000L)
    }

    // ---------------------------------------------------------------- previews

    @Test
    fun `previews are off unless asked for, and cost no queries when off`() = runBlocking {
        var lookups = 0
        val progress = importer(inserts = 3, onLookup = { lookups++ }).import().toList()

        assertThat(lookups).isEqualTo(0)
        assertThat(progress.mapNotNull { it.preview }).isEmpty()
    }

    @Test
    fun `every inserted transaction arrives as its own preview`() = runBlocking {
        val progress = importer(inserts = 3).import(emitPreviews = true).toList()

        val previews = progress.mapNotNull { it.preview }
        assertThat(previews.map { it.transactionId }).containsExactly(1L, 2L, 3L).inOrder()
        assertThat(previews.map { it.title }).containsExactly("Keells 1", "Keells 2", "Keells 3").inOrder()
        assertThat(previews.first().amountMinor).isEqualTo(100)
        assertThat(previews.first().currency).isEqualTo("LKR")
        assertThat(previews.first().sender).isEqualTo("COMBANK")
        assertThat(previews.first().categoryName).isEqualTo("Groceries")
        assertThat(previews.first().flow).isEqualTo(TransactionFlow.EXPENSE)
        assertThat(previews.first().type).isEqualTo(TransactionType.POS)
    }

    @Test
    fun `nothing inserted means nothing previewed and no lookup at all`() = runBlocking {
        var lookups = 0
        val importer = HistoricalImporter(
            readInbox = { List(30) { raw(it.toLong() + 1) } },
            loadPreviews = { lookups++; emptyList() },
            ingestMessage = { _, _, _ -> IngestResult.Duplicate(1L) },
        )

        val progress = importer.import(emitPreviews = true).toList()

        assertThat(lookups).isEqualTo(0)
        assertThat(progress.mapNotNull { it.preview }).isEmpty()
    }

    @Test
    fun `preview rows are fetched in batches, not one query per insert`() = runBlocking {
        val batches = mutableListOf<Int>()
        // 60 messages, all inserted: boundaries at 25, 50 and the last row.
        val importer = importer(inserts = 60, onBatch = { batches += it })

        val progress = importer.import(emitPreviews = true).toList()

        assertThat(batches).containsExactly(25, 25, 10).inOrder()
        assertThat(progress.mapNotNull { it.preview }).hasSize(60)
    }

    @Test
    fun `counts keep their meaning and the last emission is a plain snapshot`() = runBlocking {
        val progress = importer(inserts = 3).import(emitPreviews = true).toList()

        // Unchanged contract: an opening zero, then one snapshot at the last row.
        assertThat(progress.first()).isEqualTo(HistoricalImporter.Progress(0, 3, 0, 0, 0, 0, 0, 0))
        assertThat(progress.last()).isEqualTo(HistoricalImporter.Progress(3, 3, 3, 0, 0, 0, 0, 0))
        assertThat(progress.last().preview).isNull()
        // Each preview carries the same counts as the snapshot it travels with.
        progress.filter { it.preview != null }.forEach { assertThat(it.inserted).isEqualTo(3) }
    }

    @Test
    fun `a paired transfer leg is previewed too`() = runBlocking<Unit> {
        val importer = HistoricalImporter(
            readInbox = { listOf(raw(1)) },
            loadPreviews = { ids -> ids.map { row(it) } },
            ingestMessage = { _, _, _ -> IngestResult.Paired(transactionId = 7L, counterpartId = 8L, groupId = 1L) },
        )

        val previews = importer.import(emitPreviews = true).toList().mapNotNull { it.preview }

        assertThat(previews.map { it.transactionId }).containsExactly(7L)
    }

    @Test
    fun `a row deleted between insert and lookup is skipped rather than crashing the import`() = runBlocking<Unit> {
        val importer = HistoricalImporter(
            readInbox = { listOf(raw(1), raw(2)) },
            // Only the second id comes back.
            loadPreviews = { ids -> ids.drop(1).map { row(it) } },
            ingestMessage = { _, _, date -> IngestResult.Inserted(date) },
        )

        val previews = importer.import(emitPreviews = true).toList().mapNotNull { it.preview }

        assertThat(previews.map { it.transactionId }).containsExactly(2L)
    }

    @Test
    fun `a failing preview lookup costs the ticker, never the import`() = runBlocking<Unit> {
        val importer = HistoricalImporter(
            readInbox = { listOf(raw(1), raw(2)) },
            loadPreviews = { error("cursor window allocation failed") },
            ingestMessage = { _, _, date -> IngestResult.Inserted(date) },
        )

        val progress = importer.import(emitPreviews = true).toList()

        // The rows were still stored, and the flow completed so the one-shot completion pref
        // can be set instead of re-scanning the whole inbox next launch.
        assertThat(progress.last()).isEqualTo(HistoricalImporter.Progress(2, 2, 2, 0, 0, 0, 0, 0))
        assertThat(progress.mapNotNull { it.preview }).isEmpty()
    }

    @Test
    fun `cancellation during a preview lookup is not swallowed as a ticker hiccup`() = runBlocking<Unit> {
        val importer = HistoricalImporter(
            readInbox = { listOf(raw(1)) },
            loadPreviews = { throw CancellationException("stop") },
            ingestMessage = { _, _, date -> IngestResult.Inserted(date) },
        )

        val error = runCatching { importer.import(emitPreviews = true).toList() }.exceptionOrNull()

        assertThat(error).isInstanceOf(CancellationException::class.java)
    }

    @Test
    fun `the day a transaction lights up is its own local day`() = runBlocking {
        val zone = ZoneId.of("Asia/Colombo")
        // 23:30 in Colombo on 16 September 2026 is still the 16th, though it is the 17th in UTC+8.
        val at = LocalDate.of(2026, 9, 16).atTime(23, 30).atZone(zone).toInstant().toEpochMilli()
        val importer = HistoricalImporter(
            readInbox = { listOf(raw(1)) },
            loadPreviews = { ids -> ids.map { row(it, timestamp = at) } },
            zone = zone,
            ingestMessage = { _, _, _ -> IngestResult.Inserted(1L) },
        )

        val preview = importer.import(emitPreviews = true).toList().mapNotNull { it.preview }.single()

        assertThat(preview.dayEpoch).isEqualTo(LocalDate.of(2026, 9, 16).toEpochDay())
    }

    @Test
    fun `a note beats the parsed merchant, and neither leaves the title to the type`() = runBlocking {
        val importer = HistoricalImporter(
            readInbox = { listOf(raw(1), raw(2), raw(3)) },
            loadPreviews = { ids ->
                ids.map { id ->
                    when (id) {
                        1L -> row(id, note = "Rent", merchant = "IPG PAYMENT")
                        2L -> row(id, note = "   ", merchant = "Keells")
                        else -> row(id, note = null, merchant = null)
                    }
                }
            },
            ingestMessage = { _, _, date -> IngestResult.Inserted(date) },
        )

        val titles = importer.import(emitPreviews = true).toList().mapNotNull { it.preview }.map { it.title }

        assertThat(titles).containsExactly("Rent", "Keells", null).inOrder()
    }

    // ---------------------------------------------------------------- helpers

    /** An importer whose inbox is [inserts] messages, every one of which inserts a row. */
    private fun importer(
        inserts: Int,
        onLookup: () -> Unit = {},
        onBatch: (Int) -> Unit = {},
    ) = HistoricalImporter(
        readInbox = { List(inserts) { raw(it.toLong() + 1) } },
        loadPreviews = { ids ->
            onLookup()
            onBatch(ids.size)
            ids.map { row(it) }
        },
        ingestMessage = { _, _, date -> IngestResult.Inserted(date) },
    )

    private fun row(
        id: Long,
        note: String? = null,
        merchant: String? = "Keells $id",
        timestamp: Long = 1_757_000_000_000L,
    ) = TransactionPreviewRow(
        id = id,
        senderAddress = "COMBANK",
        note = note,
        merchantRaw = merchant,
        amountMinor = 100 * id,
        amountCurrency = "LKR",
        flowId = TransactionFlow.EXPENSE.id,
        typeId = TransactionType.POS.id,
        timestamp = timestamp,
        categoryName = "Groceries",
    )

    private fun raw(date: Long) = SmsInboxReader.RawSms(
        address = "COMBANK",
        body = "message-$date",
        dateMillis = date,
    )
}
