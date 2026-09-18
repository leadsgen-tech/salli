package lk.salli.app.features.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import lk.salli.data.db.SalliDatabase
import lk.salli.data.db.entities.GoalContributionEntity
import lk.salli.data.db.entities.GoalEntity
import lk.salli.data.prefs.SalliPreferences
import lk.salli.domain.Currency
import lk.salli.domain.DateRange
import lk.salli.domain.Money
import lk.salli.domain.planning.GoalMath

data class ContributionRow(val id: Long, val at: Long, val amount: Money, val note: String?)

data class GoalRow(
    val id: Long,
    val name: String,
    val target: Money,
    val saved: Money,
    val percent: Int,
    val remaining: Money,
    val targetDate: Long?,
    val perCycle: Money?,
    val toSaveThisCycle: Money?,
    val isComplete: Boolean,
    val isOverdue: Boolean,
    val isArchived: Boolean,
    val linkedAccountId: Long?,
    val linkedAccountName: String?,
    val contributions: List<ContributionRow>,
)

data class AccountOption(val id: Long, val name: String)

data class GoalsUiState(
    val goals: List<GoalRow> = emptyList(),
    val accounts: List<AccountOption> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val db: SalliDatabase,
    prefs: SalliPreferences,
) : ViewModel() {

    val state: StateFlow<GoalsUiState> = combine(
        db.goals().observeGoals(),
        db.goals().observeContributions(),
        db.accounts().observeAll(),
        prefs.monthStartDay,
    ) { goals, contributions, accounts, startDay ->
        val now = System.currentTimeMillis()
        val cycle = DateRange.cycleFor(now, startDay)
        val byGoal = contributions.groupBy { it.goalId }
        val accountsById = accounts.associateBy { it.id }
        GoalsUiState(
            goals = goals.map { g ->
                val list = byGoal[g.id].orEmpty()
                val linked = g.linkedAccountId?.let { accountsById[it] }
                val saved = if (g.linkedAccountId != null) linked?.balanceMinor ?: 0L else list.sumOf { it.amountMinor }
                // A balance-tracked goal has no per-cycle contributions to split out.
                val before = if (g.linkedAccountId != null) saved else list.filter { it.at < cycle.fromMillis }.sumOf { it.amountMinor }
                val p = GoalMath.progress(g.targetMinor, saved, before, g.targetDate, now, startDay)
                GoalRow(
                    id = g.id,
                    name = g.name,
                    target = Money(g.targetMinor, g.currency),
                    saved = Money(saved, g.currency),
                    percent = p.percent,
                    remaining = Money(p.remainingMinor, g.currency),
                    targetDate = g.targetDate,
                    perCycle = p.perCycleMinor?.let { Money(it, g.currency) },
                    toSaveThisCycle = p.toSaveThisCycleMinor?.let { Money(it, g.currency) },
                    isComplete = p.isComplete,
                    isOverdue = p.isOverdue,
                    isArchived = g.isArchived,
                    linkedAccountId = g.linkedAccountId,
                    linkedAccountName = linked?.displayName,
                    contributions = list.map { ContributionRow(it.id, it.at, Money(it.amountMinor, g.currency), it.note) },
                )
            },
            accounts = accounts.filter { !it.isHidden }.map { AccountOption(it.id, it.displayName) },
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalsUiState())

    fun saveGoal(id: Long?, name: String, targetMinor: Long, targetDate: Long?, linkedAccountId: Long?) {
        viewModelScope.launch {
            if (id == null) {
                db.goals().insertGoal(
                    GoalEntity(
                        name = name.trim(),
                        targetMinor = targetMinor,
                        currency = Currency.LKR,
                        targetDate = targetDate,
                        linkedAccountId = linkedAccountId,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            } else {
                val existing = db.goals().goalById(id) ?: return@launch
                db.goals().updateGoal(
                    existing.copy(name = name.trim(), targetMinor = targetMinor, targetDate = targetDate, linkedAccountId = linkedAccountId),
                )
            }
        }
    }

    fun addContribution(goalId: Long, amountMinor: Long, note: String?) {
        viewModelScope.launch {
            db.goals().insertContribution(
                GoalContributionEntity(goalId = goalId, amountMinor = amountMinor, at = System.currentTimeMillis(), note = note?.trim()?.ifBlank { null }),
            )
        }
    }

    /** The last amount poured into a jar, kept so it can be taken back with one tap. */
    data class Pour(val contributionId: Long, val goalId: Long, val goalName: String, val amountMinor: Long)

    private val _lastPour = kotlinx.coroutines.flow.MutableStateFlow<Pour?>(null)
    val lastPour: StateFlow<Pour?> = _lastPour

    /** Pouring is a contribution with no note; unlike [addContribution] it remembers itself for undo. */
    fun pour(goalId: Long, goalName: String, amountMinor: Long) {
        if (amountMinor <= 0L) return
        viewModelScope.launch {
            val id = db.goals().insertContribution(
                GoalContributionEntity(goalId = goalId, amountMinor = amountMinor, at = System.currentTimeMillis()),
            )
            _lastPour.value = Pour(id, goalId, goalName, amountMinor)
        }
    }

    fun undoLastPour() {
        val pour = _lastPour.value ?: return
        _lastPour.value = null
        viewModelScope.launch { db.goals().deleteContribution(pour.contributionId) }
    }

    fun forgetLastPour() { _lastPour.value = null }

    fun setArchived(goalId: Long, archived: Boolean) {
        viewModelScope.launch { db.goals().setArchived(goalId, archived) }
    }

    fun deleteGoal(goalId: Long) {
        viewModelScope.launch { db.goals().deleteGoal(goalId) }
    }
}
