package lk.salli.app.sms

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import lk.salli.data.prefs.SalliPreferences

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
 */
@Singleton
class SmsRefresher @Inject constructor(
    private val importer: HistoricalImporter,
    private val prefs: SalliPreferences,
    private val promptNotifier: TransactionPromptNotifier,
) {
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var ensureJob: Job? = null

    fun refresh() {
        scope.launch {
            if (!mutex.tryLock()) return@launch
            try {
                _refreshing.value = true
                val since = System.currentTimeMillis() - WINDOW_MS
                // If the broadcast receiver lost the race to this refresh (user pulled just as
                // an SMS landed) the refresh is what inserts the row — so it must also own the
                // "who was this for?" prompt, or the prompt silently never appears.
                try {
                    importer.import(sinceMillis = since, onIngested = promptNotifier::handle)
                        .collect { /* drain */ }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.e(TAG, "Recent SMS refresh failed", error)
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
                if (prefs.historicalImportCompleted.first()) return@withLock
                // The owner granted SMS access but explicitly chose not to scan history.
                if (prefs.historicalImportDeferred.first()) return@withLock
                _refreshing.value = true
                try {
                    importer.import(sinceMillis = null).collect { /* drain */ }
                    prefs.setHistoricalImportCompleted(true)
                    prefs.setHistoricalImportDeferred(false)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    // Leave the completion bit false: the next reconciliation may retry safely.
                    Log.e(TAG, "Historical SMS import failed", error)
                } finally {
                    _refreshing.value = false
                }
            }
        }
    }

    /** Lets deterministic app tests wait for the one deduplicated reconciliation attempt. */
    internal suspend fun awaitHistoricalImportIdle() {
        ensureJob?.join()
    }

    /**
     * User-initiated full-inbox resync. Unlike [ensureHistoricalImport] this bypasses the
     * completion pref — useful after a data wipe, or when the user suspects something in
     * the distant past didn't get picked up. Safe: duplicate detector makes re-ingesting a
     * no-op for anything already stored.
     */
    fun resyncAll() {
        scope.launch {
            if (!mutex.tryLock()) return@launch
            try {
                _refreshing.value = true
                try {
                    importer.import(sinceMillis = null).collect { /* drain */ }
                    prefs.setHistoricalImportCompleted(true)
                    prefs.setHistoricalImportDeferred(false)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.e(TAG, "Full SMS resync failed", error)
                }
            } finally {
                _refreshing.value = false
                mutex.unlock()
            }
        }
    }

    private companion object {
        const val TAG = "SmsRefresher"
        /** 3 days. Covers a long weekend of Doze; short enough to finish in seconds. */
        const val WINDOW_MS: Long = 3L * 24 * 60 * 60 * 1000
    }
}
