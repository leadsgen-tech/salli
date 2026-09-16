package lk.salli.app.features.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import lk.salli.data.db.entities.RecurringSeriesEntity
import lk.salli.data.planning.RecurringService
import lk.salli.domain.Currency
import lk.salli.domain.Money
import lk.salli.domain.planning.SafeToSpend
import lk.salli.domain.recurring.RecurringStatus

data class RecurringRow(
    val id: Long,
    val name: String,
    val isIncome: Boolean,
    val cadenceLabel: String,
    val amount: Money,
    val isFixed: Boolean,
    val nextAt: Long?,
    val lastAt: Long,
    val status: RecurringStatus,
    val userState: String,
    val declinedAttempts: Int,
    val isDetected: Boolean,
)

data class RecurringUiState(
    val failing: List<RecurringRow> = emptyList(),
    val dueSoon: List<RecurringRow> = emptyList(),
    val active: List<RecurringRow> = emptyList(),
    val missed: List<RecurringRow> = emptyList(),
    val dismissed: List<RecurringRow> = emptyList(),
    /** Expected repeating expenses in the next 30 days; null when there are none. */
    val next30Days: Money? = null,
    val loading: Boolean = true,
) {
    val isEmpty: Boolean
        get() = failing.isEmpty() && dueSoon.isEmpty() && active.isEmpty() && missed.isEmpty() && dismissed.isEmpty()
}

@HiltViewModel
class RecurringViewModel @Inject constructor(
    private val service: RecurringService,
) : ViewModel() {

    val state: StateFlow<RecurringUiState> = service.observe()
        .map { rows -> build(rows, System.currentTimeMillis()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecurringUiState())

    /** Re-detect when the screen opens, so SMS that arrived since the last pass are included. */
    fun refresh() {
        viewModelScope.launch { runCatching { service.recompute() } }
    }

    fun confirm(id: Long) = setUserState(id, RecurringSeriesEntity.CONFIRMED)
    fun dismiss(id: Long) = setUserState(id, RecurringSeriesEntity.DISMISSED)
    fun restore(id: Long) = setUserState(id, RecurringSeriesEntity.AUTO)

    private fun setUserState(id: Long, state: String) {
        viewModelScope.launch { service.setUserState(id, state) }
    }

    private fun build(rows: List<RecurringSeriesEntity>, now: Long): RecurringUiState {
        val live = rows.filter { it.userState != RecurringSeriesEntity.DISMISSED }.map { it.toRow() }
        val sections = live.groupBy { row ->
            when {
                // A series the detector no longer sees has stopped, even if its last known state
                // was "keeps getting declined"; a stale FAILING label would never clear.
                !row.isDetected -> RecurringStatus.MISSED
                row.status == RecurringStatus.FAILING -> RecurringStatus.FAILING
                else -> row.status
            }
        }
        val upcoming = rows.filter {
            it.flowId == 0 && it.userState != RecurringSeriesEntity.DISMISSED && it.isDetected &&
                it.nextAt != null && it.intervalDays != null &&
                (it.status == RecurringStatus.ACTIVE.name || it.status == RecurringStatus.DUE_SOON.name)
        }
        val currency = upcoming.groupBy { it.currency }.maxByOrNull { it.value.size }?.key ?: Currency.LKR
        val horizon = now + 30L * 24 * 60 * 60 * 1000
        val total = upcoming.filter { it.currency == currency }
            .sumOf { it.typicalAmountMinor * SafeToSpend.occurrencesBefore(it.nextAt!!, it.intervalDays!!, horizon) }
        return RecurringUiState(
            failing = sections[RecurringStatus.FAILING].orEmpty(),
            dueSoon = sections[RecurringStatus.DUE_SOON].orEmpty(),
            active = sections[RecurringStatus.ACTIVE].orEmpty(),
            missed = sections[RecurringStatus.MISSED].orEmpty(),
            dismissed = rows.filter { it.userState == RecurringSeriesEntity.DISMISSED }.map { it.toRow() },
            next30Days = if (total > 0L) Money(total, currency) else null,
            loading = false,
        )
    }

    private fun RecurringSeriesEntity.toRow() = RecurringRow(
        id = id,
        name = displayName.ifBlank { seriesKey },
        isIncome = flowId == 1,
        cadenceLabel = when (cadence) {
            "WEEKLY" -> "Weekly"
            "FORTNIGHTLY" -> "Every 2 weeks"
            "MONTHLY" -> "Monthly"
            "QUARTERLY" -> "Every 3 months"
            "YEARLY" -> "Yearly"
            else -> "Card subscription"
        },
        amount = Money(typicalAmountMinor, currency),
        isFixed = isFixed,
        nextAt = nextAt,
        lastAt = lastAt,
        status = runCatching { RecurringStatus.valueOf(status) }.getOrDefault(RecurringStatus.ACTIVE),
        userState = userState,
        declinedAttempts = declinedAttempts,
        isDetected = isDetected,
    )
}
