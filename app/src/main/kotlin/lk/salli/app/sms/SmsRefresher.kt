package lk.salli.app.sms

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import lk.salli.data.prefs.SalliPreferences

/**
 * What the last (or current) pass over the inbox is doing. Drives the pull-to-refresh status
 * capsule, which has to say something truthful in every phase — including "already checking"
 * when a second pull lands while the first is still running.
 */
sealed interface RefreshStatus {

    /** Nothing running, nothing left to report. */
    data object Idle : RefreshStatus

    /** A refresh, a first-run import or a full resync is in flight. */
    data object Running : RefreshStatus

    /**
     * Finished. Counts are the final [HistoricalImporter.Progress] of the run, so
     * "3 new transactions" / "1 new, 2 to review" come straight from what was stored.
     * [inserted] already includes rows that paired into an own transfer.
     */
    data class Done(
        val inserted: Int,
        val merged: Int,
        val queued: Int,
        val finishedAt: Long,
    ) : RefreshStatus

    /**
     * The pass failed. [reason] is diagnostic text for logs and bug reports — the UI shows its
     * own translated copy, never this string.
     */
    data class Failed(val reason: String, val finishedAt: Long) : RefreshStatus
}

/**
 * Process-level "pull-to-refresh" for SMS ingest.
 *
 * Two modes:
 *
 *  - [refresh] re-scans the last 3 days. Catches the common case where Doze or OEM battery
 *    restrictions killed the BroadcastReceiver over a long weekend so a handful of real
 *    transactions never made it into the DB. Safe to call unconditionally — the ingester's
 *    duplicate detector makes re-processing a no-op.
 *  - [ensureHistoricalImport] performs the first-ever full-inbox backfill. Gated by a pref so
 *    it runs at most once per install. Called after the onboarding grant, and again if the
 *    user skipped SMS access during onboarding and grants it later via Settings.
 *
 * Shared as a singleton so the refresh indicator state is consistent across all screens — a
 * pull on Home reflects in Timeline etc.
 *
 * **Status lifecycle.** [status] is sticky on purpose: it settles on
 * [RefreshStatus.Done] or [RefreshStatus.Failed] and stays there until the UI calls
 * [consume], or until the next pass starts and moves it back to [RefreshStatus.Running].
 * A timeout was the alternative; an explicit hand-off was chosen because the capsule holds its
 * result for just over a second and the tests then need no virtual clock to prove it.
 *
 * The cost of sticky is a result nobody collected — a refresh that finished while the app was
 * backgrounded — still sitting there when a screen next appears. Both settled states carry a
 * `finishedAt` for exactly that: a capsule that wants to stay quiet about old news compares it
 * against the clock and skips straight to [consume]. Deciding *how* old is too old is the UI's
 * call, not this class's.
 */
@Singleton
class SmsRefresher internal constructor(
    private val importer: HistoricalImporter,
    private val prefs: SalliPreferences,
    private val promptNotifier: TransactionPromptNotifier,
    private val now: () -> Long,
) {
    @Inject
    constructor(
        importer: HistoricalImporter,
        prefs: SalliPreferences,
        promptNotifier: TransactionPromptNotifier,
    ) : this(importer, prefs, promptNotifier, { System.currentTimeMillis() })

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _status = MutableStateFlow<RefreshStatus>(RefreshStatus.Idle)
    val status: StateFlow<RefreshStatus> = _status.asStateFlow()

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var ensureJob: Job? = null

    /**
     * The most recent [refresh] / [resyncAll] coroutine, so deterministic tests can join it
     * rather than watching for a side effect to show up. Only ever read by
     * [awaitPassIdle]; nothing in production behaviour depends on it.
     */
    @Volatile
    private var lastPass: Job? = null

    /**
     * Clears a settled [RefreshStatus.Done] or [RefreshStatus.Failed] once the UI has shown it.
     * A no-op while a pass is running, so a late call can never hide a fresh result.
     */
    fun consume() {
        val settled = _status.value
        if (settled is RefreshStatus.Done || settled is RefreshStatus.Failed) {
            _status.compareAndSet(settled, RefreshStatus.Idle)
        }
    }

    fun refresh() {
        // Claim ownership at the call site. If this check runs only after dispatch to IO,
        // a pull made during an active pass can be scheduled after that pass unlocks and
        // unexpectedly start a second inbox scan.
        val ownsPass = mutex.tryLock()
        lastPass = scope.launch {
            if (!ownsPass) {
                reportSomeoneElseIsReading()
                return@launch
            }
            try {
                _refreshing.value = true
                _status.value = RefreshStatus.Running
                val since = now() - WINDOW_MS
                // If the broadcast receiver lost the race to this refresh (user pulled just as
                // an SMS landed) the refresh is what inserts the row — so it must also own the
                // "who was this for?" prompt, or the prompt silently never appears.
                runReportingStatus("Recent SMS refresh failed") {
                    importer.import(sinceMillis = since, onIngested = promptNotifier::handle)
                }
            } finally {
                _refreshing.value = false
                mutex.unlock()
            }
        }
    }

    @Synchronized
    fun ensureHistoricalImport() {
        if (ensureJob?.isActive == true) return
        ensureJob = scope.launch {
            mutex.withLock {
                // Another queued caller may have completed the import while this one waited.
                if (prefs.historicalImportCompleted.first()) return@withLock releaseQueuedStatus()
                // The owner granted SMS access but explicitly chose not to scan history.
                if (prefs.historicalImportDeferred.first()) return@withLock releaseQueuedStatus()
                _refreshing.value = true
                _status.value = RefreshStatus.Running
                try {
                    val completed = runReportingStatus("Historical SMS import failed") {
                        importer.import(sinceMillis = null)
                    }
                    // Leave the completion bit false on failure: the next reconciliation may
                    // retry safely and dedupe whatever already landed.
                    if (completed) {
                        prefs.setHistoricalImportCompleted(true)
                        prefs.setHistoricalImportDeferred(false)
                    }
                } finally {
                    _refreshing.value = false
                }
            }
        }
    }

    /**
     * What a caller does when it finds the pipeline busy: report that something *is* running
     * rather than returning silently and leaving the indicator describing a refresh that never
     * happened, then make sure that report doesn't outlive the pass it was made about.
     *
     * The promotion is only from [RefreshStatus.Idle] — the pass in flight owns `Running`, and
     * its `Done` must survive if it settles between our `tryLock` and the swap.
     *
     * Then we queue on the mutex rather than returning. Whoever held it is finished by the time
     * we get in, so a `Running` still standing at that point is provably behind nothing and is
     * handed back. That closes the case this exists for — [ensureHistoricalImport] holding the
     * lock across two DataStore reads and then deciding not to run at all — without depending on
     * *when* it decides: if it settles on `Done` we no-op, and if it no-ops we clean up after it.
     * Waiting costs a parked coroutine and starts no second pass over the inbox.
     */
    private suspend fun reportSomeoneElseIsReading() {
        _status.compareAndSet(RefreshStatus.Idle, RefreshStatus.Running)
        mutex.withLock { releaseQueuedStatus() }
    }

    /**
     * Drops a [RefreshStatus.Running] that no pass is behind any more.
     *
     * Scoped to `Running` so a settled [RefreshStatus.Done] the UI has not read yet is never
     * thrown away. Only correct while holding the mutex — that is what makes a `Running` here
     * provably stale.
     */
    private fun releaseQueuedStatus() {
        _status.compareAndSet(RefreshStatus.Running, RefreshStatus.Idle)
    }

    /** Lets deterministic app tests wait for the one deduplicated reconciliation attempt. */
    internal suspend fun awaitHistoricalImportIdle() {
        ensureJob?.join()
    }

    /**
     * Lets deterministic app tests wait for the most recent [refresh] or [resyncAll] to finish.
     *
     * Joining the coroutine is the only honest "it is over" signal: [status] settles a beat
     * before [refreshing] clears and the mutex unlocks, and the completion preferences are
     * written after that again. A test that watched any one of those would be asserting against
     * a pass still in flight.
     */
    internal suspend fun awaitPassIdle() {
        lastPass?.join()
    }

    /**
     * User-initiated full-inbox resync. Unlike [ensureHistoricalImport] this bypasses the
     * completion pref — useful after a data wipe, or when the user suspects something in
     * the distant past didn't get picked up. Safe: duplicate detector makes re-ingesting a
     * no-op for anything already stored.
     */
    fun resyncAll() {
        val ownsPass = mutex.tryLock()
        lastPass = scope.launch {
            if (!ownsPass) {
                reportSomeoneElseIsReading()
                return@launch
            }
            try {
                _refreshing.value = true
                _status.value = RefreshStatus.Running
                val completed = runReportingStatus("Full SMS resync failed") {
                    importer.import(sinceMillis = null)
                }
                if (completed) {
                    prefs.setHistoricalImportCompleted(true)
                    prefs.setHistoricalImportDeferred(false)
                }
            } finally {
                _refreshing.value = false
                mutex.unlock()
            }
        }
    }

    /**
     * Drains [start]'s flow keeping the last progress, then settles [status] on
     * [RefreshStatus.Done] with those counts or [RefreshStatus.Failed] with the error.
     *
     * Cancellation is never a result: it rewinds [status] to [RefreshStatus.Idle] and rethrows,
     * so a cancelled pass leaves no capsule stuck mid-spin and no false "up to date".
     *
     * @return true when the pass completed, false when it failed.
     */
    private suspend fun runReportingStatus(
        logMessage: String,
        start: () -> Flow<HistoricalImporter.Progress>,
    ): Boolean {
        var last: HistoricalImporter.Progress? = null
        return try {
            start().collect { last = it }
            _status.value = RefreshStatus.Done(
                inserted = last?.inserted ?: 0,
                merged = last?.merged ?: 0,
                queued = last?.queued ?: 0,
                finishedAt = now(),
            )
            true
        } catch (cancelled: CancellationException) {
            _status.value = RefreshStatus.Idle
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, logMessage, error)
            _status.value = RefreshStatus.Failed(
                reason = error.message ?: error::class.simpleName.orEmpty(),
                finishedAt = now(),
            )
            false
        }
    }

    private companion object {
        const val TAG = "SmsRefresher"
        /** 3 days. Covers a long weekend of Doze; short enough to finish in seconds. */
        const val WINDOW_MS: Long = 3L * 24 * 60 * 60 * 1000
    }
}
