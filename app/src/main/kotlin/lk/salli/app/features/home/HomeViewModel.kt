package lk.salli.app.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import lk.salli.app.ui.TimelineItem
import lk.salli.app.ui.toTimelineItems
import lk.salli.data.db.SalliDatabase
import lk.salli.data.db.entities.TransactionEntity
import lk.salli.data.transactions.TransactionSpending
import lk.salli.data.prefs.SalliPreferences
import lk.salli.domain.Currency
import lk.salli.domain.DateRange
import lk.salli.domain.Money
import lk.salli.domain.TransactionFlow

data class AccountSummary(
    val id: Long,
    val displayName: String,
    val accountLabel: String,
    val balance: Money?,
    val senderAddress: String,
    /**
     * Net (income − expense) over all time for this account. Used as a fallback chip line
     * when [balance] is null — the bank's SMS never carry a balance for senders like ComBank,
     * but we can still show the user the cumulative flow we've tracked.
     */
    val activityNet: Money? = null,
    /** True when every tracked transaction on this account is an EXPENSE. Drives chip wording. */
    val activityExpenseOnly: Boolean = false,
)

data class TopSpender(
    val categoryId: Long?,
    val categoryName: String,
    val iconName: String,
    val colorSeed: Int,
    val total: Money,
    val count: Int,
)

/** Current-period vs previous-period delta for a headline metric. */
data class Trend(
    val currentMinor: Long,
    val previousMinor: Long,
    val currency: String,
    /**
     * Per-bucket values inside the *current* period — one entry per day for week/month
     * views. The ordering is chronological (oldest on the left, today on the right), which
     * matches how the Sparkline composable draws it.
     */
    val buckets: List<Long> = emptyList(),
) {
    /** Percentage change vs previous period. Null if previous was zero (can't %-diff from 0). */
    val percentDelta: Int?
        get() = if (previousMinor == 0L) null
        else (((currentMinor - previousMinor).toDouble() / previousMinor) * 100).toInt()

    /** True if spending is UP. For income we pass a boolean flag on read-time. */
    val isUp: Boolean get() = currentMinor > previousMinor
}

data class HomeUiState(
    val userName: String = "",
    val accounts: List<AccountSummary> = emptyList(),
    val recent: List<TimelineItem> = emptyList(),
    val monthIncome: Money = Money.zero(Currency.LKR),
    val monthExpense: Money = Money.zero(Currency.LKR),
    val todaySpend: Money = Money.zero(Currency.LKR),
    val weekTrend: Trend? = null,
    val monthTrend: Trend? = null,
    val topSpenders: List<TopSpender> = emptyList(),
    val isEmpty: Boolean = true,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val db: SalliDatabase,
    private val prefs: SalliPreferences,
    private val refresher: lk.salli.app.sms.SmsRefresher,
) : ViewModel() {

    val refreshing: StateFlow<Boolean> = refresher.refreshing
    fun refresh() = refresher.refresh()

    // Fold (accounts × per-account activity totals) into a single source so the main combine
    // stays at 5 args. Activity totals re-emit on every transaction insert, so chips refresh
    // without a manual refresh.
    private val accountsWithActivity = combine(
        db.accounts().observeAll(),
        db.transactions().observeActivityPerAccount(),
    ) { accounts, activity -> accounts to activity.groupBy { it.accountId } }

    /**
     * The five time windows Home needs — current and previous spending cycle, current and
     * previous week, today — re-derived whenever the user changes the month or week start
     * day in Settings. combine() caps at 5 sources, so the windows travel as one flow.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val windows = prefs.period.flatMapLatest { period ->
        val now = System.currentTimeMillis()
        val month = DateRange.cycleFor(now, period.monthStartDay)
        val prevMonth = DateRange.prevCycle(month, period.monthStartDay)
        val week = DateRange.weekContaining(now, period.weekStartDay)
        val prevWeek = DateRange.prevWeek(week, period.weekStartDay)
        val today = startOfToday()
        kotlinx.coroutines.flow.combine(
            db.transactions().observeInRange(month.fromMillis, month.untilMillis),
            db.transactions().observeInRange(prevMonth.fromMillis, prevMonth.untilMillis),
            db.transactions().observeInRange(week.fromMillis, week.untilMillis),
            db.transactions().observeInRange(prevWeek.fromMillis, prevWeek.untilMillis),
            db.transactions().observeInRange(today, today + DAY_MS),
        ) { m, pm, w, pw, t -> TimeWindows(m, pm, w, pw, t, monthRange = month, weekRange = week) }
    }

    private data class TimeWindows(
        val thisMonth: List<TransactionEntity>,
        val prevMonth: List<TransactionEntity>,
        val thisWeek: List<TransactionEntity>,
        val prevWeek: List<TransactionEntity>,
        val today: List<TransactionEntity>,
        val monthRange: DateRange,
        val weekRange: DateRange,
    )

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val state: StateFlow<HomeUiState> = combine(
        accountsWithActivity,
        db.transactions().observeTimeline(limit = 20),
        windows,
        db.categories().observeAll(),
        prefs.userName,
    ) { (allAccounts, activityByAccount), recentAll, windows, categories, userName ->
        val categoriesById = categories.associateBy { it.id }
        val accountsById = allAccounts.associateBy { it.id }
        // Accounts toggled off in Settings vanish from every number on this screen: the
        // strip, the hero balance, trends, top spenders and the recent list.
        val hiddenIds = allAccounts.filter { it.isHidden }.map { it.id }.toSet()
        val accounts = allAccounts.filter { !it.isHidden }
        val recent = recentAll.filter { it.accountId !in hiddenIds }

        val rawSummaries = accounts.map { a ->
            // Fold rows of the same account across currencies into one (rare — most LK
            // accounts only ever see LKR — but POS abroad lands in USD and would otherwise
            // appear as a second activity row).
            val rows = activityByAccount[a.id].orEmpty()
            val netMinor = rows.sumOf { it.netMinor }
            val expenseMinor = rows.sumOf { it.expenseMinor }
            val incomeMinor = rows.sumOf { it.incomeMinor }
            val activityCurrency = rows.maxByOrNull { kotlin.math.abs(it.netMinor) }?.currency
                ?: a.currency
            AccountSummary(
                id = a.id,
                displayName = a.displayName,
                accountLabel = a.senderAddress + if (a.accountSuffix != "—") " · ${a.accountSuffix}" else "",
                balance = a.balanceMinor?.let { Money(it, a.currency) },
                senderAddress = a.senderAddress,
                activityNet = if (rows.isEmpty()) null else Money(netMinor, activityCurrency),
                activityExpenseOnly = rows.isNotEmpty() && incomeMinor == 0L && expenseMinor > 0L,
            )
        }

        val accountSummaries = rawSummaries

        val items = recent.toTimelineItems(categoriesById, accountsById)

        val (thisMonthAll, prevMonthAll, thisWeekAll, prevWeekAll, todayAll) = windows
        val thisMonth = thisMonthAll.filter { it.accountId !in hiddenIds }
        val prevMonth = prevMonthAll.filter { it.accountId !in hiddenIds }
        val thisWeek = thisWeekAll.filter { it.accountId !in hiddenIds }
        val prevWeek = prevWeekAll.filter { it.accountId !in hiddenIds }
        val today = todayAll.filter { it.accountId !in hiddenIds }

        val realThisMonth = thisMonth.filter { !it.isDeclined && it.transferGroupId == null }
        val dominantCurrency = TransactionSpending.dominantCurrency(thisMonth)

        val expenseIn: (List<TransactionEntity>) -> Long = { list ->
            TransactionSpending.totalMinor(list, dominantCurrency)
        }
        val incomeIn: (List<TransactionEntity>) -> Long = { list ->
            list.filter {
                !it.isDeclined && it.transferGroupId == null &&
                    it.flowId == TransactionFlow.INCOME.id &&
                    it.amountCurrency == dominantCurrency
            }.sumOf { it.amountMinor }
        }

        val monthExpense = expenseIn(thisMonth)
        val monthIncome = incomeIn(thisMonth)
        val todayExpense = expenseIn(today)
        val weekTrend = Trend(
            currentMinor = expenseIn(thisWeek),
            previousMinor = expenseIn(prevWeek),
            currency = dominantCurrency,
            buckets = bucketByDay(thisWeek, dominantCurrency, windows.weekRange.fromMillis, days = 7),
        )
        val monthTrend = Trend(
            currentMinor = monthExpense,
            previousMinor = expenseIn(prevMonth),
            currency = dominantCurrency,
            // Always render exactly 30 days' worth of bars so the tile layout is
            // consistent regardless of where in the month we are. The bucket list
            // is trailing-30-days, not the current calendar month.
            buckets = bucketByDay(
                txns = (thisMonth + prevMonth).distinctBy { it.id },
                currency = dominantCurrency,
                startMs = startOfToday() - 29L * 24 * 60 * 60 * 1000,
                days = 30,
            ),
        )

        // Top 3 categories by month expense.
        val topSpenders = realThisMonth
            .filter { it.flowId == TransactionFlow.EXPENSE.id && it.amountCurrency == dominantCurrency }
            .groupBy { it.categoryId }
            .map { (catId, list) ->
                val cat = catId?.let { categoriesById[it] }
                TopSpender(
                    categoryId = catId,
                    categoryName = cat?.name ?: "Uncategorised",
                    iconName = cat?.iconName ?: "inbox",
                    colorSeed = cat?.colorSeed ?: 0xFF6B7280.toInt(),
                    total = Money(list.sumOf { it.amountMinor }, dominantCurrency),
                    count = list.size,
                )
            }
            .sortedWith(compareByDescending<TopSpender> { it.total.minorUnits }.thenByDescending { it.count })
            .take(3)

        HomeUiState(
            userName = userName,
            accounts = accountSummaries,
            recent = items,
            monthIncome = Money(monthIncome, dominantCurrency),
            monthExpense = Money(monthExpense, dominantCurrency),
            todaySpend = Money(todayExpense, dominantCurrency),
            weekTrend = weekTrend,
            monthTrend = monthTrend,
            topSpenders = topSpenders,
            isEmpty = accountSummaries.isEmpty() && items.isEmpty(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = HomeUiState(),
    )

    val darkTheme: StateFlow<Boolean> = prefs.darkTheme.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = false,
    )

    fun toggleTheme() {
        viewModelScope.launch {
            prefs.setDarkTheme(!darkTheme.value)
        }
    }

    /**
     * Sum [txns] expenses by day into a fixed-length bucket array starting at [startMs] and
     * running [days] days forward. Zero-fills days with no spend so sparklines keep a
     * consistent x-axis. Transfers and declines are excluded here the same way they are in
     * the aggregate totals.
     */
    private fun bucketByDay(
        txns: List<TransactionEntity>,
        currency: String,
        startMs: Long,
        days: Int,
    ): List<Long> {
        val dayMs = 24L * 60 * 60 * 1000
        val out = LongArray(days) { 0L }
        for (t in txns) {
            if (t.isDeclined || t.transferGroupId != null) continue
            if (t.flowId != TransactionFlow.EXPENSE.id) continue
            if (t.amountCurrency != currency) continue
            val idx = ((t.timestamp - startMs) / dayMs).toInt()
            if (idx in 0 until days) out[idx] += t.amountMinor
        }
        return out.toList()
    }


    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
