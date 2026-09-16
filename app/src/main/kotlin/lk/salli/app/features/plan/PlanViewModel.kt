package lk.salli.app.features.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import lk.salli.data.db.SalliDatabase
import lk.salli.data.db.entities.RecurringSeriesEntity
import lk.salli.data.upcoming.UpcomingItem
import lk.salli.data.upcoming.UpcomingService
import lk.salli.data.planning.PlanningService
import lk.salli.data.planning.PlanningSnapshot

/**
 * The live counts behind Plan's tracker rows.
 *
 * These moved here wholesale from `SettingsViewModel` when the rows were promoted out of the
 * Settings "Trackers" section: same queries, same derivations, same wording upstream. Settings
 * holds settings; a bill you owe on Thursday is not a setting.
 */
data class PlanUiState(
    val openBillCount: Int = 0,
    val fuelVehicleCount: Int = 0,
    val recurringCount: Int = 0,
    /** Recurring series that are failing or due within a week — the badge on the row. */
    val recurringNeedsAttention: Int = 0,
    val goalCount: Int = 0,
)

@HiltViewModel
class PlanViewModel @Inject constructor(
    db: SalliDatabase,
    planning: PlanningService,
    private val refresher: lk.salli.app.sms.SmsRefresher,
) : ViewModel() {

    val refreshing = refresher.refreshing
    val refreshStatus = refresher.status
    fun refresh() = refresher.refresh()
    fun consumeRefreshStatus() = refresher.consume()

    val upcoming: StateFlow<List<UpcomingItem>> = UpcomingService(db).observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val planningSnapshot: StateFlow<PlanningSnapshot?> = planning.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val state: StateFlow<PlanUiState> = combine(
        db.bills().observeOpenCount(),
        db.fuelPass().observeVehicles(),
        db.recurring().observeAll(),
        db.goals().observeGoals(),
    ) { openBills, vehicles, series, goals ->
        // A dismissed or merely suspected series is not something to count at the user: the
        // row would claim work that the user has already told us to ignore.
        val live = series.filter { it.userState != RecurringSeriesEntity.DISMISSED && it.isDetected }
        PlanUiState(
            openBillCount = openBills,
            fuelVehicleCount = vehicles.size,
            recurringCount = live.size,
            recurringNeedsAttention = live.count { it.status == "FAILING" || it.status == "DUE_SOON" },
            goalCount = goals.count { !it.isArchived },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanUiState())
}
