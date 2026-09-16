package lk.salli.app

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import lk.salli.data.planning.RecurringService
import android.app.Application
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import lk.salli.app.reminders.UtilityReminderWorker
import lk.salli.app.security.AppLockController
import lk.salli.app.widget.WidgetUpdateCoordinator
import lk.salli.app.summaries.SummaryWorker
import lk.salli.data.db.SalliDatabase
import lk.salli.data.ingest.TransactionIngestor
import lk.salli.data.ingest.TransferGroupRepair
import lk.salli.data.ingest.UtilityIngestor
import lk.salli.data.seed.Seeder

/**
 * App process root. Implements [Configuration.Provider] so WorkManager resolves Hilt-injected
 * workers (SmsIngestWorker).
 *
 * The manifest declares no network permission at all, so no StrictMode network policy is
 * needed here — the platform itself refuses every socket the app might open.
 */
@HiltAndroidApp
class SalliApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var seeder: Seeder
    @Inject lateinit var db: SalliDatabase
    @Inject lateinit var ingestor: TransactionIngestor
    @Inject lateinit var utilityIngestor: UtilityIngestor
    @Inject lateinit var recurringService: RecurringService
    @Inject lateinit var appLock: AppLockController
    @Inject lateinit var widgetUpdates: WidgetUpdateCoordinator

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        // Reset any lingering StrictMode policy — earlier dev builds installed a cleartext
        // detector that netd tracks via kernel iptables rules on our UID, and those rules
        // outlive the process (even `pm clear` doesn't wipe them). An explicit empty policy
        // tells the platform to tear down the chains. Harmless no-op on fresh installs.
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
        // App lock watches the process lifecycle from the start, so a process an SMS woke up in
        // the background is still locked when the user opens it.
        appLock.start()
        // Idempotent seed — categories + keywords show up on first launch, and any new entries
        // we ship later trickle in automatically.
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            runCatching { seeder.run() }
            // Dissolve transfer pairings an older fee rule created wrongly (Rs 50 vs Rs 0.99).
            runCatching { TransferGroupRepair(db).run() }
            // Close bills that a later statement or payment already settled.
            runCatching { utilityIngestor.recomputeBillStatuses() }
            // Parser upgrades resolve old review-queue rows: notices vanish, transactions book.
            runCatching { ingestor.retriagePendingUnknown() }
            // Repeating payments: one pass now, then again once an SMS burst has settled
            // (30 s of quiet) rather than on every inserted row.
            runCatching { recurringService.recompute() }
            // Account changes count too: hiding an account must drop its repeating payments
            // from "spoken for" without waiting for the next SMS.
            merge(
                db.transactions().observeTimeline(limit = 1).drop(1).map { },
                db.accounts().observeAll().drop(1).map { },
            )
                .debounce(RECURRING_DEBOUNCE_MS)
                .collect { runCatching { recurringService.recompute() } }
        }
        // DB/DataStore invalidation observers are useful only while a widget is placed. The
        // receiver starts them on first placement; this restores them after process recreation.
        widgetUpdates.startIfWidgetsPresent()
        // Daily bill-due and Fuel Pass reminders. KEEP means a reinstall or update never
        // resets the schedule; the worker itself is idempotent per bill / per fuel week.
        UtilityReminderWorker.schedule(this)
        // Hourly check for opted-in daily/weekly/monthly spending summaries.
        SummaryWorker.schedule(this)
    }

    private companion object {
        const val RECURRING_DEBOUNCE_MS = 30_000L
    }
}
