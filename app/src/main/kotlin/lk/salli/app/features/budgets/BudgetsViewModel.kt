package lk.salli.app.features.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import lk.salli.data.db.SalliDatabase
import lk.salli.data.db.entities.AccountEntity
import lk.salli.data.db.entities.BudgetAccountEntity
import lk.salli.data.db.entities.BudgetEntity
import lk.salli.data.db.entities.BudgetLineEntity
import lk.salli.data.db.entities.CategoryEntity
import lk.salli.data.db.entities.TransactionEntity
import lk.salli.domain.DateRange
import lk.salli.domain.Money
import lk.salli.domain.TransactionFlow

/** 0 = per-category caps, 1 = single total cap. Mirrors [BudgetEntity.capModeId]. */
enum class BudgetCapMode(val id: Int) {
    PerCategory(0),
    Total(1),
    ;

    companion object {
        fun fromId(id: Int): BudgetCapMode = entries.firstOrNull { it.id == id } ?: PerCategory
    }
}

/**
 * A pace signal comparing elapsed time through the cycle against elapsed spend. Keeps the user
 * honest mid-cycle — a green "ON PACE" at day 10 with 33% spent is very different from 80%.
 */
enum class BudgetPace {
    Under,    // well below expected burn
    OnPace,   // within a tolerable band of expected burn
    Hot,      // running ahead of burn but not yet over
    Over,     // exceeded the cap outright
}

/** One category's cap within a budget, with its live spent + remaining. */
data class BudgetLineUi(
    val id: Long,
    val categoryId: Long,
    val categoryName: String,
    val iconName: String,
    val colorSeed: Int,
    val cap: Money,
    val spent: Money,
) {
    val remainingMinor: Long = cap.minorUnits - spent.minorUnits
    val progress: Float = if (cap.minorUnits > 0)
        (spent.minorUnits.toFloat() / cap.minorUnits).coerceAtLeast(0f) else 0f
    val overBudget: Boolean get() = remainingMinor < 0
    val approaching: Boolean get() = !overBudget && progress >= 0.8f
}

/** A budget composed of one-or-more category lines, or a single total cap. */
data class BudgetUi(
    val id: Long,
    val name: String,
    val currency: String,
    val capMode: BudgetCapMode,
    val lines: List<BudgetLineUi>,
    val totalCapMinor: Long,
    val totalSpentMinor: Long,
    val cycleLabel: String,
    val accountScope: AccountScope,
    val pace: BudgetPace,
    val paceExpectedFraction: Float,  // 0.0-1.0 — how far through the cycle we are
) {
    val progress: Float = if (totalCapMinor > 0)
        (totalSpentMinor.toFloat() / totalCapMinor).coerceAtLeast(0f) else 0f
    val overBudget: Boolean get() = pace == BudgetPace.Over
    val remainingMinor: Long = totalCapMinor - totalSpentMinor
}

/**
 * Which accounts are in scope for a budget. [specificAccounts] is empty when the budget covers
 * all accounts — we keep the semantic here so the UI can render "All accounts" vs "2 accounts".
 */
data class AccountScope(
    val allAccounts: Boolean,
    val specificAccounts: List<AccountEntity>,
) {
    fun containsAccountId(accountId: Long): Boolean =
        allAccounts || specificAccounts.any { it.id == accountId }
}

data class BudgetsUiState(
    val budgets: List<BudgetUi> = emptyList(),
    val availableCategories: List<CategoryEntity> = emptyList(),
    val availableAccounts: List<AccountEntity> = emptyList(),
    val loading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val db: SalliDatabase,
    private val refresher: lk.salli.app.sms.SmsRefresher,
) : ViewModel() {

    val refreshing: StateFlow<Boolean> = refresher.refreshing
    fun refresh() = refresher.refresh()

    // We fetch a wide slab of recent transactions and slice per-budget. The widest cycle is
    // 28 days + one-day boundary slack → 90 days covers "current cycle" plus room for history
    // features we haven't built yet without re-querying per budget.
    private val recentWindowMs: Long = TimeUnit.DAYS.toMillis(90)

    private val recentTxns = run {
        val from = System.currentTimeMillis() - recentWindowMs
        db.transactions().observeInRange(from, Long.MAX_VALUE)
    }

    // Six flows feed the state, so we combine them in two halves — kotlinx's typed `combine`
    // variants cap at five args, and the Array-callback version requires all flows to share a
    // type.
    private data class BudgetBundle(
        val budgets: List<BudgetEntity>,
        val lines: List<BudgetLineEntity>,
        val accountLinks: List<BudgetAccountEntity>,
    )

    private data class LookupBundle(
        val accounts: List<AccountEntity>,
        val categories: List<CategoryEntity>,
        val txns: List<TransactionEntity>,
    )

    val state: StateFlow<BudgetsUiState> = combine(
        combine(
            db.budgets().observeAll(),
            db.budgets().observeAllLines(),
            db.budgets().observeAllAccountLinks(),
        ) { budgets, lines, links -> BudgetBundle(budgets, lines, links) },
        combine(
            db.accounts().observeAll(),
            db.categories().observeAll(),
            recentTxns,
        ) { accounts, categories, txns -> LookupBundle(accounts, categories, txns) },
    ) { bundle, lookup ->
        val budgets = bundle.budgets
        val lines = bundle.lines
        val accountLinks = bundle.accountLinks
        val accounts = lookup.accounts
        val categories = lookup.categories
        val txns = lookup.txns

        val catLookup = categories.associateBy { it.id }
        val accountLookup = accounts.associateBy { it.id }
        val linksByBudget = accountLinks.groupBy { it.budgetId }
        val now = System.currentTimeMillis()

        val uiBudgets = budgets.map { b ->
            val cycle = DateRange.cycleFor(now, b.periodStartDay)
            val budgetLines = lines.filter { it.budgetId == b.id }
            val scopedAccountIds = linksByBudget[b.id].orEmpty().map { it.accountId }.toSet()
            val scope = AccountScope(
                allAccounts = scopedAccountIds.isEmpty(),
                specificAccounts = scopedAccountIds.mapNotNull { accountLookup[it] },
            )

            // Only real expense inside the cycle and inside the scoped accounts, in the
            // budget's currency. A USD COMBANK purchase can't be compared to an LKR cap
            // without an FX rate we don't carry; filtering by currency keeps the totals
            // honest and avoids silently treating USD cents as LKR cents.
            val cycleExpense = txns
                .filter { it.timestamp in cycle.fromMillis until cycle.untilMillis }
                .filter { !it.isDeclined && it.transferGroupId == null }
                .filter { it.flowId == TransactionFlow.EXPENSE.id }
                .filter { it.amountCurrency == b.currency }
                .filter { scope.allAccounts || it.accountId in scopedAccountIds }

            val expensePerCategory: Map<Long?, Long> = cycleExpense
                .groupBy { it.categoryId }
                .mapValues { (_, list) -> list.sumOf { it.amountMinor } }

            val capMode = BudgetCapMode.fromId(b.capModeId)

            val uiLines = budgetLines.map { line ->
                val cat = catLookup[line.categoryId]
                val spentMinor = expensePerCategory[line.categoryId] ?: 0L
                BudgetLineUi(
                    id = line.id,
                    categoryId = line.categoryId,
                    categoryName = cat?.name ?: "Category #${line.categoryId}",
                    iconName = cat?.iconName ?: "inbox",
                    colorSeed = cat?.colorSeed ?: 0xFF6B7280.toInt(),
                    cap = Money(line.amountMinor, b.currency),
                    spent = Money(spentMinor, b.currency),
                )
            }

            val totalCapMinor: Long = when (capMode) {
                BudgetCapMode.Total -> b.totalCapMinor ?: 0L
                BudgetCapMode.PerCategory -> uiLines.sumOf { it.cap.minorUnits }
            }
            val totalSpentMinor: Long = when (capMode) {
                // For total-cap budgets, every expense in scope counts — not only categorised ones.
                BudgetCapMode.Total -> cycleExpense.sumOf { it.amountMinor }
                BudgetCapMode.PerCategory -> uiLines.sumOf { it.spent.minorUnits }
            }

            val expectedFraction = cycleElapsedFraction(cycle, now)
            val pace = derivePace(
                spent = totalSpentMinor,
                cap = totalCapMinor,
                expectedFraction = expectedFraction,
            )

            BudgetUi(
                id = b.id,
                name = b.name,
                currency = b.currency,
                capMode = capMode,
                lines = uiLines,
                totalCapMinor = totalCapMinor,
                totalSpentMinor = totalSpentMinor,
                cycleLabel = cycle.label,
                accountScope = scope,
                pace = pace,
                paceExpectedFraction = expectedFraction,
            )
        }

        BudgetsUiState(
            budgets = uiBudgets,
            availableCategories = categories,
            availableAccounts = accounts.filter { !it.isArchived },
            loading = false,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        BudgetsUiState(loading = true),
    )

    /**
     * Persist a new budget. Supports both cap modes:
     *  - [BudgetCapMode.PerCategory]: [lines] carries category → cap pairs.
     *  - [BudgetCapMode.Total]: [totalCapMinor] is the single lump cap; [lines] is ignored.
     */
    fun create(
        name: String,
        currency: String,
        capMode: BudgetCapMode,
        lines: List<Pair<Long, Long>>,
        totalCapMinor: Long?,
        accountIds: List<Long>,
        periodStartDay: Int,
    ) {
        if (name.isBlank()) return
        if (capMode == BudgetCapMode.PerCategory && lines.isEmpty()) return
        if (capMode == BudgetCapMode.Total && (totalCapMinor == null || totalCapMinor <= 0)) return
        viewModelScope.launch {
            val id = db.budgets().insert(
                BudgetEntity(
                    name = name.trim(),
                    periodTypeId = 0,
                    currency = currency,
                    startDate = null,
                    createdAt = System.currentTimeMillis(),
                    capModeId = capMode.id,
                    totalCapMinor = if (capMode == BudgetCapMode.Total) totalCapMinor else null,
                    periodStartDay = periodStartDay.coerceIn(1, 28),
                ),
            )
            if (capMode == BudgetCapMode.PerCategory) {
                db.budgets().insertLines(
                    lines.map { (catId, capMinor) ->
                        BudgetLineEntity(budgetId = id, categoryId = catId, amountMinor = capMinor)
                    },
                )
            }
            db.budgets().replaceAccountLinks(id, accountIds)
        }
    }

    fun delete(budgetId: Long) {
        viewModelScope.launch {
            db.budgets().byId(budgetId)?.let { db.budgets().delete(it) }
        }
    }

    fun update(
        budgetId: Long,
        name: String,
        capMode: BudgetCapMode,
        lines: List<Pair<Long, Long>>,
        totalCapMinor: Long?,
        accountIds: List<Long>,
        periodStartDay: Int,
    ) {
        if (name.isBlank()) return
        if (capMode == BudgetCapMode.PerCategory && lines.isEmpty()) return
        if (capMode == BudgetCapMode.Total && (totalCapMinor == null || totalCapMinor <= 0)) return
        viewModelScope.launch {
            val existing = db.budgets().byId(budgetId) ?: return@launch
            db.budgets().update(
                existing.copy(
                    name = name.trim(),
                    capModeId = capMode.id,
                    totalCapMinor = if (capMode == BudgetCapMode.Total) totalCapMinor else null,
                    periodStartDay = periodStartDay.coerceIn(1, 28),
                ),
            )
            db.budgets().replaceLines(
                budgetId,
                if (capMode == BudgetCapMode.PerCategory) {
                    lines.map { (catId, capMinor) ->
                        BudgetLineEntity(budgetId = budgetId, categoryId = catId, amountMinor = capMinor)
                    }
                } else emptyList(),
            )
            db.budgets().replaceAccountLinks(budgetId, accountIds)
        }
    }

    /** Fraction of the cycle that has elapsed as of [now]. Clamped 0..1. */
    private fun cycleElapsedFraction(cycle: DateRange, now: Long): Float {
        val total = (cycle.untilMillis - cycle.fromMillis).coerceAtLeast(1L)
        val elapsed = (now - cycle.fromMillis).coerceIn(0L, total)
        return (elapsed.toDouble() / total).toFloat().coerceIn(0f, 1f)
    }

    /**
     * Compare actual spend to expected burn (a pro-rata line from Rs 0 on cycle start to
     * the full cap on cycle end). Bands are deliberately generous: real spending is lumpy, so
     * a strict "at 15% spent by day 15" pace would flag most normal weeks as abnormal.
     */
    private fun derivePace(spent: Long, cap: Long, expectedFraction: Float): BudgetPace {
        if (cap <= 0) return BudgetPace.OnPace
        if (spent > cap) return BudgetPace.Over
        val actualFraction = (spent.toDouble() / cap).toFloat()
        val upperBand = (expectedFraction + 0.10f).coerceAtMost(1f)
        val lowerBand = (expectedFraction - 0.15f).coerceAtLeast(0f)
        return when {
            actualFraction > upperBand -> BudgetPace.Hot
            actualFraction < lowerBand -> BudgetPace.Under
            else -> BudgetPace.OnPace
        }
    }
}
