package lk.salli.app.sms

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import lk.salli.data.ingest.IngestResult
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

    private fun raw(date: Long) = SmsInboxReader.RawSms(
        address = "COMBANK",
        body = "message-$date",
        dateMillis = date,
    )
}
