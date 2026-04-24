package lk.salli.app.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import lk.salli.app.ui.TimelineItem
import lk.salli.app.ui.toTimelineItem
import lk.salli.data.db.SalliDatabase
import lk.salli.data.db.entities.TransactionEntity
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

    val state: StateFlow<HomeUiState> = combine(
        db.accounts().observeAll(),
        db.transactions().observeTimeline(limit = 20),
        observeAllThisAndPreviousMonth(),
        db.categories().observeAll(),
        prefs.userName,
    ) { accounts, recent, windows, categories, userName ->
        val categoriesById = categories.associateBy { it.id }
        val accountsById = accounts.associateBy { it.id }

        val accountSummaries = accounts.map { a ->
            AccountSummary(
                id = a.id,
                displayName = a.displayName,
                accountLabel = a.senderAddress + if (a.accountSuffix != "—") " · ${a.accountSuffix}" else "",
                balance = a.balanceMinor?.let { Money(it, a.currency) },
                senderAddress = a.senderAddress,
            )
        }

        val items = recent.map { tx ->
            tx.toTimelineItem(
                category = tx.categoryId?.let { categoriesById[it] },
                accountDisplayName = accountsById[tx.accountId]?.displayName,
            )
        }

        val (thisMonth, prevMonth, thisWeek, prevWeek, today) = windows

        val realThisMonth = thisMonth.filter { !it.isDeclined && it.transferGroupId == null }
        val dominantCurrency = realThisMonth
            .groupBy { it.amountCurrency }
            .maxByOrNull { it.value.size }
            ?.key
            ?: Currency.LKR

        val expenseIn: (List<TransactionEntity>) -> Long = { list ->
            list.filter {
                !it.isDeclined && it.transferGroupId == null &&
                    it.flowId == TransactionFlow.EXPENSE.id &&
                    it.amountCurrency == dominantCurrency
            }.sumOf { it.amountMinor }
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
            buckets = bucketByDay(thisWeek, dominantCurrency, startOfThisWeek(), days = 7),
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
     * Bundles the five time windows Home needs into a single reactive flow — this month,
     * previous month, this week (Mon–now), previous week, today. combine() caps at 5 sources
     * and we already use 4 elsewhere, so compressing these into one flow avoids arity pain.
     */
    private fun observeAllThisAndPreviousMonth() = kotlinx.coroutines.flow.combine(
        db.transactions().observeInRange(startOfThisMonth(), endOfThisMonth()),
        db.transactions().observeInRange(startOfPrevMonth(), endOfPrevMonth()),
        db.transactions().observeInRange(startOfThisWeek(), endOfThisWeek()),
        db.transactions().observeInRange(startOfPrevWeek(), endOfPrevWeek()),
        db.transactions().observeInRange(startOfToday(), endOfToday()),
    ) { month, prevMonth, week, prevWeek, today ->
        TimeWindows(month, prevMonth, week, prevWeek, today)
    }

    private data class TimeWindows(
        val thisMonth: List<TransactionEntity>,
        val prevMonth: List<TransactionEntity>,
        val thisWeek: List<TransactionEntity>,
        val prevWeek: List<TransactionEntity>,
        val today: List<TransactionEntity>,
    )

    private fun startOfThisMonth() = DateRange.currentMonth().fromMillis
    private fun endOfThisMonth() = DateRange.currentMonth().untilMillis
    private fun startOfPrevMonth() = DateRange.prev(DateRange.currentMonth()).fromMillis
    private fun endOfPrevMonth() = DateRange.prev(DateRange.currentMonth()).untilMillis

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    private fun endOfToday(): Long = startOfToday() + 24L * 60 * 60 * 1000

    private fun startOfThisWeek(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        // Monday as the first day of the week — matches Sri Lankan / most-of-world convention
        // and keeps our week-over-week deltas stable regardless of Android's locale default.
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    }.timeInMillis
    private fun endOfThisWeek(): Long = startOfThisWeek() + 7L * 24 * 60 * 60 * 1000

    private fun startOfPrevWeek(): Long = startOfThisWeek() - 7L * 24 * 60 * 60 * 1000
    private fun endOfPrevWeek(): Long = startOfThisWeek()

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
}
