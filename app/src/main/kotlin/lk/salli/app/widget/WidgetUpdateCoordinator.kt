package lk.salli.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import lk.salli.data.db.SalliDatabase
import lk.salli.data.prefs.SalliPreferences

/** Keeps widget invalidation observers alive only while at least one widget is placed. */
@Singleton
class WidgetUpdateCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: SalliDatabase,
    private val prefs: SalliPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var observerJob: Job? = null

    fun startIfWidgetsPresent() {
        val ids = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, SalliWidgetReceiver::class.java))
        if (ids.isNotEmpty()) {
            start()
            // KEEP restores a missing job after upgrade/process recreation without moving an
            // existing midnight schedule every time the app happens to start.
            WidgetRefreshWorker.schedule(context)
        }
    }

    @OptIn(FlowPreview::class)
    @Synchronized
    fun start() {
        if (observerJob?.isActive == true) return
        observerJob = scope.launch {
            while (isActive) {
                try {
                    merge(
                        db.transactions().observeTimeline(limit = 1).map { },
                        db.accounts().observeAll().map { },
                        db.bills().observeOpenBills().map { },
                        db.recurring().observeAll().map { },
                        db.goals().observeGoals().map { },
                        db.goals().observeContributions().map { },
                        prefs.widgetHideAmounts.map { },
                        prefs.period.map { },
                        prefs.monthlySpendingLimitMinor.map { },
                    )
                        .debounce(WIDGET_DEBOUNCE_MS)
                        .collect { refreshUntilSuccess() }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // A transient Room/DataStore failure must not permanently freeze the widget.
                    delay(RETRY_DELAY_MS)
                }
            }
        }
    }

    @Synchronized
    fun stop() {
        observerJob?.cancel()
        observerJob = null
    }

    private suspend fun refreshUntilSuccess() {
        while (scope.isActive) {
            try {
                SalliWidget.refresh(context)
                return
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Retry this exact invalidation instead of waiting for another database change.
                delay(RETRY_DELAY_MS)
            }
        }
    }

    private companion object {
        const val WIDGET_DEBOUNCE_MS = 2_000L
        const val RETRY_DELAY_MS = 5_000L
    }
}
