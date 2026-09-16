package lk.salli.app.features.settings

import lk.salli.data.db.entities.RecurringSeriesEntity
import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import lk.salli.data.backup.BackupDocument
import lk.salli.data.backup.BackupManager
import lk.salli.data.prefs.PeriodSettings
import lk.salli.data.prefs.SummarySettings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lk.salli.app.sms.SmsRefresher
import lk.salli.data.db.SalliDatabase
import lk.salli.data.db.entities.AccountEntity
import lk.salli.data.export.DataWiper
import lk.salli.data.export.TransactionExporter
import lk.salli.data.prefs.SalliPreferences
import androidx.fragment.app.FragmentActivity
import lk.salli.app.security.AppLockController
import lk.salli.data.prefs.AppLockSettings

sealed interface SettingsEvent {
    data class ShareCsv(val intent: Intent) : SettingsEvent
    /** Any other file hand-off (JSON backup); [title] is the chooser caption. */
    data class ShareFile(val intent: Intent, val title: String) : SettingsEvent
    data class Message(val text: String) : SettingsEvent
}

/** A parsed backup waiting for the user's "replace everything" confirmation. */
data class PendingRestore(
    val document: BackupDocument,
    val exportedAt: Long,
    val transactions: Int,
    val rows: Int,
)

data class SettingsUiState(
    val exporting: Boolean = false,
    val backingUp: Boolean = false,
    val restoring: Boolean = false,
    val wiping: Boolean = false,
    val userName: String = "",
    val unknownSmsCount: Int = 0,
    val openBillCount: Int = 0,
    val fuelVehicleCount: Int = 0,
    val pendingRestore: PendingRestore? = null,
)

/** Counts behind the Trackers tiles. */
data class TrackerCounts(
    val recurring: Int = 0,
    /** Series that are failing or due within a week. */
    val needsAttention: Int = 0,
    val goals: Int = 0,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val exporter: TransactionExporter,
    private val wiper: DataWiper,
    private val prefs: SalliPreferences,
    private val db: SalliDatabase,
    private val refresher: SmsRefresher,
    private val backup: BackupManager,
    private val appLock: AppLockController,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    // ---- Widget + security ------------------------------------------------------------

    val widgetHideAmounts: StateFlow<Boolean> = prefs.widgetHideAmounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setWidgetHideAmounts(hide: Boolean) = viewModelScope.launch {
        prefs.setWidgetHideAmounts(hide)
    }

    val appLockSettings: StateFlow<AppLockSettings> = prefs.appLock
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppLockSettings(enabled = false, lockAfterSeconds = 0, hideInRecents = false))

    /** Whether the phone has a PIN, pattern or password; app lock cannot work without one. */
    fun isDeviceSecure(): Boolean = appLock.isDeviceSecure()

    /** Shows the system prompt first; the setting only changes after it succeeds. */
    fun requestAppLock(activity: FragmentActivity, enable: Boolean) {
        appLock.setEnabledAfterAuth(activity, enable) { message -> emit(SettingsEvent.Message(message)) }
    }

    fun requestAppLockAfter(activity: FragmentActivity, seconds: Int) {
        appLock.setLockAfter(activity, seconds) { message -> emit(SettingsEvent.Message(message)) }
    }

    fun requestHideInRecents(activity: FragmentActivity, hide: Boolean) {
        appLock.setHideInRecents(activity, hide) { message -> emit(SettingsEvent.Message(message)) }
    }

    // ---- Spending period + summaries ------------------------------------------------

    val period: StateFlow<PeriodSettings> = prefs.period
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeriodSettings(1, 1))

    val summaries: StateFlow<SummarySettings> = prefs.summarySettings
        .stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000),
            SummarySettings(daily = false, weekly = false, monthly = false, hour = SalliPreferences.DEFAULT_SUMMARY_HOUR),
        )

    /** Monthly spending limit in minor units; null means safe-to-spend picks a budget itself. */
    val spendingLimit: StateFlow<Long?> = prefs.monthlySpendingLimitMinor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setSpendingLimit(limitMinor: Long?) = viewModelScope.launch { prefs.setMonthlySpendingLimitMinor(limitMinor) }

    val trackers: StateFlow<TrackerCounts> = combine(
        db.recurring().observeAll(),
        db.goals().observeGoals(),
    ) { series, goals ->
        val live = series.filter { it.userState != RecurringSeriesEntity.DISMISSED && it.isDetected }
        TrackerCounts(
            recurring = live.size,
            needsAttention = live.count { it.status == "FAILING" || it.status == "DUE_SOON" },
            goals = goals.count { !it.isArchived },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrackerCounts())

    fun setMonthStartDay(day: Int) = viewModelScope.launch { prefs.setMonthStartDay(day) }
    fun setWeekStartDay(isoDay: Int) = viewModelScope.launch { prefs.setWeekStartDay(isoDay) }
    fun setSummaryDaily(on: Boolean) = viewModelScope.launch { prefs.setSummaryDaily(on) }
    fun setSummaryWeekly(on: Boolean) = viewModelScope.launch { prefs.setSummaryWeekly(on) }
    fun setSummaryMonthly(on: Boolean) = viewModelScope.launch { prefs.setSummaryMonthly(on) }
    fun setSummaryHour(hour: Int) = viewModelScope.launch { prefs.setSummaryHour(hour) }

    // ---- Backup / restore ----------------------------------------------------------

    fun backupJson() {
        if (actionState.value.backingUp) return
        actionState.value = actionState.value.copy(backingUp = true)
        viewModelScope.launch {
            try {
                val file = backup.exportToFile()
                emit(SettingsEvent.ShareFile(backup.shareIntent(file), "Share backup"))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                emit(SettingsEvent.Message("Backup failed: ${error.message}"))
            } finally {
                actionState.value = actionState.value.copy(backingUp = false)
            }
        }
    }

    /** Parses the picked file and parks it for confirmation; nothing is written yet. */
    fun inspectBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                val doc = withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openInputStream(uri)
                        ?: error("Could not open the file")
                    stream.use { backup.inspect(it) }
                }
                actionState.value = actionState.value.copy(
                    pendingRestore = PendingRestore(
                        document = doc,
                        exportedAt = doc.exportedAt,
                        transactions = doc.tables.transactions.size,
                        rows = doc.tables.rowCount,
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                emit(SettingsEvent.Message(error.message ?: "Not a Salli backup file"))
            }
        }
    }

    fun cancelRestore() {
        actionState.value = actionState.value.copy(pendingRestore = null)
    }

    fun confirmRestore() {
        val pending = actionState.value.pendingRestore ?: return
        if (actionState.value.restoring) return
        actionState.value = actionState.value.copy(restoring = true, pendingRestore = null)
        viewModelScope.launch {
            try {
                backup.restore(pending.document)
                emit(SettingsEvent.Message("Backup restored: ${pending.rows} rows"))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                emit(SettingsEvent.Message("Restore failed: ${error.message}"))
            } finally {
                actionState.value = actionState.value.copy(restoring = false)
            }
        }
    }

    /**
     * Kick off the one-shot historical import if it hasn't already run. Called when the user
     * grants SMS access from Settings — catches the onboarding-skipped path where the inbox
     * would otherwise only trickle in via the live receiver.
     */
    fun ensureHistoricalImport() {
        refresher.ensureHistoricalImport()
    }

    /** User-initiated full-inbox rescan — bypasses the already-imported pref. */
    fun resyncMessages() {
        refresher.resyncAll()
    }

    /** Whether a refresh/resync is currently running, for the tile's trailing spinner. */
    val syncing: StateFlow<Boolean> = refresher.refreshing

    /** Every account ever seen, for the visibility toggles. */
    val accounts: StateFlow<List<AccountEntity>> = db.accounts().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Hidden accounts keep ingesting SMS but disappear from every screen and total. */
    fun setAccountHidden(accountId: Long, hidden: Boolean) {
        viewModelScope.launch { db.accounts().setHidden(accountId, hidden) }
    }

    private val actionState = MutableStateFlow(SettingsUiState())

    val state: StateFlow<SettingsUiState> = combine(
        actionState,
        prefs.userName,
        db.unknownSms().observePendingCount(),
        db.bills().observeOpenCount(),
        db.fuelPass().observeVehicles(),
    ) { base, name, unknownCount, openBills, vehicles ->
        base.copy(
            userName = name,
            unknownSmsCount = unknownCount,
            openBillCount = openBills,
            fuelVehicleCount = vehicles.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    // Events are one-shot effects (open a share sheet, show a toast) — modelling them as
    // StateFlow would replay the last emission on every re-subscription (e.g. config change),
    // firing a "Deleted" toast a second time. A Channel guarantees exactly-once delivery.
    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    private fun emit(event: SettingsEvent) {
        _events.trySend(event)
    }

    fun exportCsv() {
        if (actionState.value.exporting) return
        actionState.value = actionState.value.copy(exporting = true)
        viewModelScope.launch {
            try {
                val file = exporter.exportToCsv()
                emit(SettingsEvent.ShareCsv(exporter.shareIntent(file)))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                emit(SettingsEvent.Message("Export failed: ${error.message}"))
            } finally {
                actionState.value = actionState.value.copy(exporting = false)
            }
        }
    }

    fun deleteAllData() {
        if (actionState.value.wiping) return
        actionState.value = actionState.value.copy(wiping = true)
        viewModelScope.launch {
            try {
                wiper.wipe()
                emit(SettingsEvent.Message("All data cleared"))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                emit(SettingsEvent.Message("Delete failed: ${error.message}"))
            } finally {
                actionState.value = actionState.value.copy(wiping = false)
            }
        }
    }

    fun setUserName(name: String) {
        viewModelScope.launch { prefs.setUserName(name) }
    }
}
