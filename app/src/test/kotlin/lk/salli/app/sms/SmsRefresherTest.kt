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

    // ---------------------------------------------------------------- status

    @Test
    fun `a fresh refresher has nothing to report`() = runBlocking {
        val refresher = refresher(importerOf())

        assertThat(refresher.status.value).isEqualTo(RefreshStatus.Idle)
    }

    @Test
    fun `a finished refresh reports what it stored`() = runBlocking {
        val refresher = refresher(
            HistoricalImporter(
                readInbox = { listOf(rawSms(1), rawSms(2), rawSms(3)) },
                ingestMessage = { _, _, date ->
                    when (date) {
                        1L -> IngestResult.Inserted(1)
                        2L -> IngestResult.Merged(2)
                        else -> IngestResult.Queued(3)
                    }
                },
            ),
        )

        refresher.refresh()
        val done = refresher.awaitStatus<RefreshStatus.Done>()

        assertThat(done.inserted).isEqualTo(1)
        assertThat(done.merged).isEqualTo(1)
        assertThat(done.queued).isEqualTo(1)
        assertThat(done.finishedAt).isEqualTo(FIXED_NOW)
    }

    @Test
    fun `a paired transfer leg counts as inserted, as the importer counts it`() = runBlocking {
        val refresher = refresher(
            HistoricalImporter(
                readInbox = { listOf(rawSms(1)) },
                ingestMessage = { _, _, _ -> IngestResult.Paired(1, 2, 3) },
            ),
        )

        refresher.refresh()

        assertThat(refresher.awaitStatus<RefreshStatus.Done>().inserted).isEqualTo(1)
    }

    @Test
    fun `an empty window still settles on Done rather than spinning forever`() = runBlocking {
        val refresher = refresher(importerOf())

        refresher.refresh()

        assertThat(refresher.awaitStatus<RefreshStatus.Done>().inserted).isEqualTo(0)
    }

    @Test
    fun `a failed refresh reports the failure instead of only logging it`() = runBlocking {
        val refresher = refresher(
            HistoricalImporter(
                readInbox = { listOf(rawSms(1)) },
                ingestMessage = { _, _, _ -> error("database unavailable") },
            ),
        )

        refresher.refresh()
        val failed = refresher.awaitStatus<RefreshStatus.Failed>()

        assertThat(failed.reason).contains("database unavailable")
        assertThat(failed.finishedAt).isEqualTo(FIXED_NOW)
        // The spinner is cleared in the `finally`, a beat after the status settles.
        withTimeout(TIMEOUT) { refresher.refreshing.first { !it } }
        Unit
    }

    @Test
    fun `a second pull while one is running is told so instead of silently doing nothing`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val reads = AtomicInteger()
        val refresher = refresher(
            HistoricalImporter(
                readInbox = { reads.incrementAndGet(); listOf(rawSms(1)) },
                ingestMessage = { _, _, _ ->
                    started.complete(Unit)
                    release.await()
                    IngestResult.Inserted(1)
                },
            ),
        )

        refresher.refresh()
        withTimeout(TIMEOUT) { started.await() }

        // Every one of these finds the mutex held. None may start a second pass, and the status
        // must keep saying so instead of the indicator going quiet on a refresh that never ran.
        repeat(4) { refresher.refresh() }
        assertThat(refresher.status.value).isEqualTo(RefreshStatus.Running)
        assertThat(refresher.refreshing.value).isTrue()
        // Safe to assert here and nowhere else: while the first pass holds the lock a second
        // read is impossible. Once it releases, a queued pull running for real is correct —
        // the ingester dedupes it — so the count is only pinned for the overlapping window.
        assertThat(reads.get()).isEqualTo(1)

        release.complete(Unit)
        withTimeout(TIMEOUT) { refresher.status.first { it is RefreshStatus.Done } }
        Unit
    }

    @Test
    fun `the next pass replaces the previous result even if nobody consumed it`() = runBlocking {
        val inserts = AtomicInteger()
        val refresher = refresher(
            HistoricalImporter(
                readInbox = { List(inserts.get()) { rawSms(it.toLong() + 1) } },
                ingestMessage = { _, _, _ -> IngestResult.Inserted(1) },
            ),
        )

        inserts.set(1)
        // The first-run import is the one pass a test can join, so the mutex is provably free
        // before the second starts — `refresh()` settles its status just before it unlocks, so
        // chaining two of those would be a race, not a test.
        refresher.ensureHistoricalImport()
        withTimeout(TIMEOUT) { refresher.awaitHistoricalImportIdle() }
        assertThat(refresher.awaitStatus<RefreshStatus.Done>().inserted).isEqualTo(1)

        // No consume() in between: a stale Done must not survive the next pass.
        inserts.set(3)
        prefs.setHistoricalImportDeferred(true)
        refresher.resyncAll()

        withTimeout(TIMEOUT) { refresher.status.first { it is RefreshStatus.Done && it.inserted == 3 } }
        withTimeout(TIMEOUT) { prefs.historicalImportDeferred.first { !it } }
        Unit
    }

    @Test
    fun `consume clears a settled result exactly once`() = runBlocking {
        val refresher = refresher(importerOf())
        refresher.refresh()
        refresher.awaitStatus<RefreshStatus.Done>()

        refresher.consume()
        assertThat(refresher.status.value).isEqualTo(RefreshStatus.Idle)

        refresher.consume()
        assertThat(refresher.status.value).isEqualTo(RefreshStatus.Idle)
    }

    @Test
    fun `consume does nothing while a pass is still running`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val refresher = refresher(
            HistoricalImporter(
                readInbox = { listOf(rawSms(1)) },
                ingestMessage = { _, _, _ ->
                    started.complete(Unit)
                    release.await()
                    IngestResult.Inserted(1)
                },
            ),
        )

        refresher.refresh()
        withTimeout(TIMEOUT) { started.await() }

        refresher.consume()

        assertThat(refresher.status.value).isEqualTo(RefreshStatus.Running)
        release.complete(Unit)
        withTimeout(TIMEOUT) { refresher.status.first { it is RefreshStatus.Done } }
        Unit
    }

    @Test
    fun `the first-run import reports its result too`() = runBlocking {
        val refresher = refresher(
            HistoricalImporter(
                readInbox = { listOf(rawSms(1), rawSms(2)) },
                ingestMessage = { _, _, _ -> IngestResult.Inserted(1) },
            ),
        )

        refresher.ensureHistoricalImport()
        withTimeout(TIMEOUT) { refresher.awaitHistoricalImportIdle() }

        assertThat(refresher.status.value).isEqualTo(
            RefreshStatus.Done(inserted = 2, merged = 0, queued = 0, finishedAt = FIXED_NOW),
        )
    }

    @Test
    fun `a failed first-run import reports Failed and leaves the completion bit false`() = runBlocking {
        val refresher = refresher(
            HistoricalImporter(
                readInbox = { listOf(rawSms(1)) },
                ingestMessage = { _, _, _ -> error("provider went away") },
            ),
        )

        refresher.ensureHistoricalImport()
        withTimeout(TIMEOUT) { refresher.awaitHistoricalImportIdle() }

        assertThat(refresher.status.value).isInstanceOf(RefreshStatus.Failed::class.java)
        assertThat(prefs.historicalImportCompleted.first()).isFalse()
    }

    @Test
    fun `a skipped ensure leaves the status alone rather than claiming a refresh`() = runBlocking {
        prefs.setHistoricalImportDeferred(true)
        val refresher = refresher(importerOf())

        refresher.ensureHistoricalImport()
        withTimeout(TIMEOUT) { refresher.awaitHistoricalImportIdle() }

        assertThat(refresher.status.value).isEqualTo(RefreshStatus.Idle)
    }

    @Test
    fun `a pull that waited out a pass which did nothing takes its Running back`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val reads = AtomicInteger()
        val refresher = refresher(
            HistoricalImporter(
                readInbox = { reads.incrementAndGet(); listOf(rawSms(1)) },
                ingestMessage = { _, _, _ ->
                    started.complete(Unit)
                    release.await()
                    // Fails, so the pass settles on Failed rather than Done — the queued caller
                    // must still not leave a Running of its own standing behind it.
                    error("provider went away")
                },
            ),
        )

        refresher.refresh()
        withTimeout(TIMEOUT) { started.await() }
        refresher.refresh()
        release.complete(Unit)

        withTimeout(TIMEOUT) { refresher.status.first { it is RefreshStatus.Failed } }
        // The queued pull waits out the pass, then settles: it never starts a second read, and
        // the status it reported is either replaced by the real result or handed back.
        withTimeout(TIMEOUT) { refresher.refreshing.first { !it } }
        assertThat(refresher.status.value).isInstanceOf(RefreshStatus.Failed::class.java)
        assertThat(reads.get()).isEqualTo(1)
    }

    @Test
    fun `an ensure that finds the import already done reports nothing rather than Running`() = runBlocking {
        prefs.setHistoricalImportCompleted(true)
        val refresher = refresher(importerOf())

        refresher.ensureHistoricalImport()
        withTimeout(TIMEOUT) { refresher.awaitHistoricalImportIdle() }

        // A pull that landed while ensure held the mutex was told "Running" on its behalf; ensure
        // then decided not to run, so it must hand that back or the capsule spins forever.
        assertThat(refresher.status.value).isEqualTo(RefreshStatus.Idle)
    }

    @Test
    fun `a no-op ensure does not discard a result the UI has not read yet`() = runBlocking {
        val refresher = refresher(
            HistoricalImporter(
                readInbox = { listOf(rawSms(1)) },
                ingestMessage = { _, _, _ -> IngestResult.Inserted(1) },
            ),
        )

        refresher.refresh()
        refresher.awaitStatus<RefreshStatus.Done>()

        // Nothing consumed the Done. An ensure that finds nothing to do must leave it standing:
        // the hand-back only applies to a Running that no pass is behind.
        prefs.setHistoricalImportCompleted(true)
        refresher.ensureHistoricalImport()
        withTimeout(TIMEOUT) { refresher.awaitHistoricalImportIdle() }

        assertThat(refresher.status.value).isEqualTo(
            RefreshStatus.Done(inserted = 1, merged = 0, queued = 0, finishedAt = FIXED_NOW),
        )
    }

    @Test
    fun `an explicit resync reports its result`() = runBlocking {
        val refresher = refresher(
            HistoricalImporter(
                readInbox = { listOf(rawSms(1)) },
                ingestMessage = { _, _, _ -> IngestResult.Inserted(1) },
            ),
        )

        refresher.resyncAll()

        assertThat(refresher.awaitStatus<RefreshStatus.Done>().inserted).isEqualTo(1)
        // The completion write trails the status by a hair; wait for it so the pass is fully
        // finished before tearDown resets the prefs out from under it.
        withTimeout(TIMEOUT) { prefs.historicalImportCompleted.first { it } }
        Unit
    }

    // ---------------------------------------------------------------- helpers

    private fun importerOf() = HistoricalImporter(
        readInbox = { emptyList() },
        ingestMessage = { _, _, _ -> error("must not ingest") },
    )

    private fun rawSms(date: Long) = SmsInboxReader.RawSms("COMBANK", "Synthetic $date", date)

    private suspend inline fun <reified T : RefreshStatus> SmsRefresher.awaitStatus(): T =
        withTimeout(TIMEOUT) { status.first { it is T } } as T

    private fun refresher(importer: HistoricalImporter) = SmsRefresher(
        importer = importer,
        prefs = prefs,
        promptNotifier = TransactionPromptNotifier(context, db),
        now = { FIXED_NOW },
    )

    private companion object {
        // Generous on purpose: these wait on real IO-dispatcher coroutines, and a loaded CI box
        // should report a broken contract, not a slow one.
        const val TIMEOUT = 30_000L
        const val FIXED_NOW = 1_757_000_000_000L
    }
}
