package lk.salli.app.sms

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import lk.salli.data.db.SalliDatabase
import lk.salli.data.ingest.IngestResult
import lk.salli.data.prefs.SalliPreferences
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [34])
class SmsRefresherTest {
    private lateinit var db: SalliDatabase
    private lateinit var prefs: SalliPreferences
    private lateinit var context: Application

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, SalliDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefs = SalliPreferences(context)
        prefs.setHistoricalImportCompleted(false)
        prefs.setHistoricalImportDeferred(false)
    }

    @After
    fun tearDown() = runBlocking {
        prefs.setHistoricalImportCompleted(false)
        prefs.setHistoricalImportDeferred(false)
        db.close()
    }

    @Test
    fun `deferred history suppresses automatic ensure`() = runBlocking {
        prefs.setHistoricalImportDeferred(true)
        val reads = AtomicInteger()
        val refresher = refresher(
            HistoricalImporter(
                readInbox = { reads.incrementAndGet(); emptyList() },
                ingestMessage = { _, _, _ -> error("must not ingest") },
            ),
        )

        refresher.ensureHistoricalImport()
        refresher.awaitHistoricalImportIdle()

        assertThat(reads.get()).isEqualTo(0)
        assertThat(prefs.historicalImportCompleted.first()).isFalse()
        assertThat(prefs.historicalImportDeferred.first()).isTrue()
    }

    @Test
    fun `concurrent ensure calls share one import and one completion write`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val reads = AtomicInteger()
        val refresher = refresher(
            HistoricalImporter(
                readInbox = {
                    reads.incrementAndGet()
                    listOf(SmsInboxReader.RawSms("COMBANK", "Synthetic", 1L))
                },
                ingestMessage = { _, _, _ ->
                    started.complete(Unit)
                    release.await()
                    IngestResult.Inserted(1)
                },
            ),
        )

        refresher.ensureHistoricalImport()
        withTimeout(10_000) { started.await() }
        repeat(4) { refresher.ensureHistoricalImport() }
        release.complete(Unit)
        withTimeout(10_000) { refresher.awaitHistoricalImportIdle() }

        assertThat(reads.get()).isEqualTo(1)
        assertThat(prefs.historicalImportCompleted.first()).isTrue()
        assertThat(prefs.historicalImportDeferred.first()).isFalse()
    }

    @Test
    fun `failed ensure remains incomplete and a later call retries`() = runBlocking {
        val attempts = AtomicInteger()
        val refresher = refresher(
            HistoricalImporter(
                readInbox = { listOf(SmsInboxReader.RawSms("COMBANK", "Synthetic", 1L)) },
                ingestMessage = { _, _, _ ->
                    if (attempts.incrementAndGet() == 1) error("database unavailable")
                    IngestResult.Inserted(1)
                },
            ),
        )

        refresher.ensureHistoricalImport()
        withTimeout(10_000) { refresher.awaitHistoricalImportIdle() }
        assertThat(prefs.historicalImportCompleted.first()).isFalse()

        refresher.ensureHistoricalImport()
        withTimeout(10_000) { refresher.awaitHistoricalImportIdle() }
        assertThat(attempts.get()).isEqualTo(2)
        assertThat(prefs.historicalImportCompleted.first()).isTrue()
    }

    @Test
    fun `explicit resync clears a deferred choice after success`() = runBlocking {
        prefs.setHistoricalImportDeferred(true)
        val refresher = refresher(
            HistoricalImporter(
                readInbox = { emptyList() },
                ingestMessage = { _, _, _ -> error("must not ingest") },
            ),
        )

        refresher.resyncAll()

        withTimeout(10_000) { prefs.historicalImportCompleted.first { it } }
        withTimeout(10_000) { prefs.historicalImportDeferred.first { !it } }
        Unit
    }

    private fun refresher(importer: HistoricalImporter) = SmsRefresher(
        importer = importer,
        prefs = prefs,
        promptNotifier = TransactionPromptNotifier(context, db),
    )
}
