package lk.salli.app.features.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import lk.salli.app.ui.TimelineItem
import lk.salli.app.nav.ActivityFilterArgs
import lk.salli.app.ui.toTimelineItems
import lk.salli.data.db.SalliDatabase
import lk.salli.data.db.entities.TransactionEntity
import lk.salli.data.db.entities.CategoryEntity
import lk.salli.domain.Currency
import lk.salli.domain.DateRange
import lk.salli.domain.Money
import lk.salli.domain.TransactionFlow

data class TimelineGroup(
    val label: String,
    val items: List<TimelineItem>,
    val netMinor: Long,
    val currency: String,
)

/** One line on the Timeline's per-account spend chart. */
data class AccountSeries(
    val accountId: Long,
    val displayName: String,
    /** Cumulative daily expense within the active range. Length = days-in-range. */
    val cumulative: List<Float>,
)

data class TimelineUiState(
    val range: DateRange,
    val query: String,
    val grouped: List<TimelineGroup> = emptyList(),
    val totalIncome: Money,
    val totalExpense: Money,
    val transactionCount: Int = 0,
    val isEmpty: Boolean = true,
    /** Signed month offset from the current month. Null when a custom range is active. */
    val monthOffset: Int? = 0,
    val series: List<AccountSeries> = emptyList(),
    /** Every account that has at least one transaction in range — for the filter menu. */
    val accountsInView: List<AccountSummary> = emptyList(),
    val hiddenAccountIds: Set<Long> = emptySet(),
    /** Start of the cycle containing today; the month pills label offsets from this. */
    val cycleStartMillis: Long = 0L,
    /** One absolute spending total per day in the selected period, for Activity's quiet bar row. */
    val dailySpend: List<Long> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val type: ActivityType = ActivityType.ALL,
    val showOwnTransfers: Boolean = true,
    val selectedAccountId: Long? = null,
    val selectedCategoryId: Long? = null,
)

enum class ActivityType { ALL, SPENDING, INCOME, TRANSFERS }

data class AccountSummary(
    val id: Long,
    val displayName: String,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val db: SalliDatabase,
    private val refresher: lk.salli.app.sms.SmsRefresher,
    prefs: lk.salli.data.prefs.SalliPreferences,
) : ViewModel() {

    val refreshing: StateFlow<Boolean> = refresher.refreshing
    val refreshStatus: StateFlow<lk.salli.app.sms.RefreshStatus> = refresher.status
    fun refresh() = refresher.refresh()
    fun consumeRefreshStatus() = refresher.consume()

    private val _range = MutableStateFlow(DateRange.currentMonth())
    val range: StateFlow<DateRange> = _range

    /** The user's month start day (1 = calendar month). Cycles, pills and stepping follow it. */
    private val monthStartDay: StateFlow<Int> = prefs.monthStartDay
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1)

    init {
        // A changed start day resets to the current cycle; a custom picked range is kept.
        viewModelScope.launch {
            prefs.monthStartDay.collect { day ->
                if (!_customRange.value) _range.value = DateRange.cycleFor(System.currentTimeMillis(), day)
            }
        }
    }

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query
    private val _filters = MutableStateFlow(ActivityFilterArgs.NONE)
    private val _type = MutableStateFlow(ActivityType.ALL)
    private val _showOwnTransfers = MutableStateFlow(true)

    fun applyFilters(filters: ActivityFilterArgs) {
        _filters.value = filters
        _query.value = filters.query.orEmpty()
    }

    fun updateFilters(accountId: Long?, categoryId: Long?, type: ActivityType, showOwnTransfers: Boolean) {
        _filters.value = ActivityFilterArgs(accountId = accountId, categoryId = categoryId, query = _query.value)
        _type.value = type
        _showOwnTransfers.value = showOwnTransfers
    }

    fun changeCategory(transactionId: Long, categoryId: Long) {
        viewModelScope.launch {
            db.transactions().setUserCategory(transactionId, categoryId, System.currentTimeMillis())
        }
    }

    /** True when the user has set a custom range that doesn't align to a calendar month. */
    private val _customRange = MutableStateFlow(false)

    // The source flow switches scope based on whether search is active:
    //  - query blank   → scoped to the active date range (Insights/Timeline/Budgets
    //                    all speak the same date-range language)
    //  - query present → widened to all-time so the user sees every hit regardless of
    //                    the currently-active period. This matches what the user
    //                    actually intends — "find this merchant everywhere".
    private val txns = kotlinx.coroutines.flow.combine(_range, _query) { r, q -> r to q }
        .flatMapLatest { (r, q) ->
            if (q.isBlank()) db.transactions().observeInRange(r.fromMillis, r.untilMillis)
            else db.transactions().observeInRange(0L, Long.MAX_VALUE)
        }

    private val searchAndFilters = combine(_query, _filters, _type, _showOwnTransfers) { query, filters, type, transfers ->
        FilterInputs(query, filters, type, transfers)
    }

    val state: StateFlow<TimelineUiState> = combine(
        kotlinx.coroutines.flow.combine(_range, _customRange, monthStartDay) { r, c, d -> Triple(r, c, d) },
        searchAndFilters,
        txns,
        db.categories().observeAll(),
        db.accounts().observeAll(),
    ) { rangeAndCustom, search, rows, categories, accounts ->
        val (range, customRange, startDay) = rangeAndCustom
        val (query, filters, type, showOwnTransfers) = search
        // Hidden accounts are a persisted Settings choice (accounts.is_hidden). The legend
        // chips on this screen flip the same flag, so the two places can never disagree.
        val hiddenAccountIds = accounts.filter { it.isHidden }.map { it.id }.toSet()
        val byCat = categories.associateBy { it.id }
        val byAcc = accounts.associateBy { it.id }

        val queryFiltered = if (query.isBlank()) rows else {
            val needle = query.trim().lowercase()
            rows.filter { row ->
                val merchant = row.merchantRaw.orEmpty().lowercase()
                val note = row.note.orEmpty().lowercase()
                val catName = row.categoryId?.let { byCat[it]?.name.orEmpty().lowercase() }.orEmpty()
                val accName = byAcc[row.accountId]?.displayName.orEmpty().lowercase()
                val sender = row.senderAddress.orEmpty().lowercase()
                val body = row.rawBody.orEmpty().lowercase()
                merchant.contains(needle) || note.contains(needle) ||
                    catName.contains(needle) || accName.contains(needle) ||
                    sender.contains(needle) || body.contains(needle)
            }
        }
        // Everything downstream (totals pills, day groups, chart series) reads from this
        // filtered list. The account toggle is a full filter, not just a chart-mask.
        val filtered = queryFiltered.filter {
            it.accountId !in hiddenAccountIds &&
                (filters.accountId == null || it.accountId == filters.accountId) &&
                (filters.categoryId == null || it.categoryId == filters.categoryId) &&
                (showOwnTransfers || it.transferGroupId == null) &&
                when (type) {
                    ActivityType.ALL -> true
                    ActivityType.SPENDING -> it.flowId == TransactionFlow.EXPENSE.id
                    ActivityType.INCOME -> it.flowId == TransactionFlow.INCOME.id
                    ActivityType.TRANSFERS -> it.transferGroupId != null
                }
        }

        // Pick the dominant currency for the pill totals — we don't sum across currencies.
        val dominantCurrency = filtered
            .groupBy { it.amountCurrency }
            .maxByOrNull { it.value.size }
            ?.key
            ?: Currency.LKR

        val realSpend = filtered.filter { !it.isDeclined }
        val income = realSpend.filter {
            it.flowId == TransactionFlow.INCOME.id && it.amountCurrency == dominantCurrency
        }.sumOf { it.amountMinor }
        val expense = realSpend.filter {
            it.flowId == TransactionFlow.EXPENSE.id && it.amountCurrency == dominantCurrency
        }.sumOf { it.amountMinor }

        // Fold internal-transfer pairs over the whole range first, then bucket by day: a
        // People's debit at 23:00 and the BOC credit next morning are one movement and must
        // become one row, which a per-day fold would have split back into two.
        val items = filtered.toTimelineItems(byCat, byAcc)
        val netByBucket = filtered.groupBy { dayBucket(it.timestamp) }
            .mapValues { (_, rows) -> netOf(rows, dominantCurrency) }
        val groups = items
            .groupBy { dayBucket(it.timestamp) }
            .toSortedMap(compareByDescending { it })
            .map { (bucketStart, list) ->
                TimelineGroup(
                    label = labelFor(bucketStart),
                    items = list,
                    netMinor = netByBucket[bucketStart] ?: 0L,
                    currency = dominantCurrency,
                )
            }

        // `filtered` already excludes hidden accounts, so the chart's visible series comes
        // straight from it. For the legend we want every account that HAS activity in the
        // query-scoped range so the user can tap to re-enable a hidden one — compute that
        // from `queryFiltered` (pre-hide) instead.
        val visibleSeries = buildAccountSeries(range, filtered, byAcc, dominantCurrency)
        val accountsInView = buildAccountSeries(range, queryFiltered.filter {
            filters.accountId == null || it.accountId == filters.accountId
        }, byAcc, dominantCurrency)
            .map { AccountSummary(id = it.accountId, displayName = it.displayName) }

        TimelineUiState(
            range = range,
            query = query,
            grouped = groups,
            totalIncome = Money(income, dominantCurrency),
            totalExpense = Money(expense, dominantCurrency),
            transactionCount = filtered.size,
            isEmpty = groups.isEmpty(),
            monthOffset = if (customRange) null else DateRange.cycleMonthOffset(range, startDay),
            cycleStartMillis = DateRange.cycleFor(System.currentTimeMillis(), startDay).fromMillis,
            series = visibleSeries,
            accountsInView = accountsInView,
            hiddenAccountIds = hiddenAccountIds,
            dailySpend = buildDailySpend(range, filtered, dominantCurrency),
            categories = categories,
            type = type,
            showOwnTransfers = showOwnTransfers,
            selectedAccountId = filters.accountId,
            selectedCategoryId = filters.categoryId,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TimelineUiState(
            range = _range.value,
            query = "",
            totalIncome = Money.zero(Currency.LKR),
            totalExpense = Money.zero(Currency.LKR),
        ),
    )

    fun onPrevRange() {
        _range.value = DateRange.prevCycle(_range.value, monthStartDay.value)
        _customRange.value = false
    }
    fun onNextRange() {
        _range.value = DateRange.nextCycle(_range.value, monthStartDay.value)
        _customRange.value = false
    }
    fun onPickRange(range: DateRange) {
        _range.value = range
        _customRange.value = true
    }
    fun onQueryChanged(query: String) { _query.value = query }
    fun clearQuery() { _query.value = "" }

    /** Jump to the cycle [offset] months from the current one (0 = current, −1 = last). */
    fun onPickMonthOffset(offset: Int) {
        _range.value = DateRange.cycleOffset(offset, monthStartDay.value)
        _customRange.value = false
    }

    /** Account visibility is a Settings preference. Activity filters only change this view. */

    /**
     * Builds one cumulative-expense series per account inside [range]. Each series has
     * `durationDays` data points aligned to day boundaries, so the chart can render all
     * accounts on the same x-axis. Transfers and declines are excluded (they'd double-count
     * or add noise).
     */
    private fun buildAccountSeries(
        range: DateRange,
        txns: List<TransactionEntity>,
        byAcc: Map<Long, lk.salli.data.db.entities.AccountEntity>,
        currency: String,
    ): List<AccountSeries> {
        val dayMs = 24L * 60 * 60 * 1000
        val days = range.durationDays.coerceAtLeast(1)
        val byAccountId = txns
            .filter {
                !it.isDeclined && it.transferGroupId == null &&
                    it.flowId == TransactionFlow.EXPENSE.id &&
                    it.amountCurrency == currency
            }
            .groupBy { it.accountId }
        return byAccountId
            .mapNotNull { (accountId, list) ->
                val account = byAcc[accountId] ?: return@mapNotNull null
                val perDay = FloatArray(days)
                for (t in list) {
                    val idx = ((t.timestamp - range.fromMillis) / dayMs).toInt()
                    if (idx in 0 until days) perDay[idx] += t.amountMinor.toFloat()
                }
                var running = 0f
                val cumulative = FloatArray(days) { i ->
                    running += perDay[i]
                    running
                }
                AccountSeries(
                    accountId = accountId,
                    displayName = account.displayName,
                    cumulative = cumulative.toList(),
                )
            }
            .sortedByDescending { it.cumulative.lastOrNull() ?: 0f }
    }

    private fun buildDailySpend(
        range: DateRange,
        txns: List<TransactionEntity>,
        currency: String,
    ): List<Long> {
        val days = range.durationDays.coerceAtLeast(1)
        val dayMs = 24L * 60 * 60 * 1000
        return LongArray(days).also { totals ->
            txns.filter {
                !it.isDeclined && it.transferGroupId == null &&
                    it.flowId == TransactionFlow.EXPENSE.id && it.amountCurrency == currency
            }.forEach { tx ->
                val index = ((tx.timestamp - range.fromMillis) / dayMs).toInt()
                if (index in totals.indices) totals[index] += tx.amountMinor
            }
        }.toList()
    }

    /** Signed day total in the dominant currency; declines and own-transfer legs are net-zero. */
    private fun netOf(rows: List<TransactionEntity>, dominantCurrency: String): Long =
        rows
            .filter { !it.isDeclined && it.transferGroupId == null && it.amountCurrency == dominantCurrency }
            .sumOf { row ->
                when (row.flowId) {
                    TransactionFlow.INCOME.id -> row.amountMinor
                    TransactionFlow.EXPENSE.id -> -row.amountMinor
                    else -> 0L
                }
            }

    private fun dayBucket(timestamp: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private val headerFormat = SimpleDateFormat("EEEE, d MMM", Locale.getDefault())

    private fun labelFor(bucketStart: Long): String {
        val todayCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val today = todayCal.timeInMillis
        val dayMillis = 24L * 60 * 60 * 1000
        return when (bucketStart) {
            today -> "Today"
            today - dayMillis -> "Yesterday"
            else -> headerFormat.format(Date(bucketStart))
        }
    }
}

private data class FilterInputs(
    val query: String,
    val filters: ActivityFilterArgs,
    val type: ActivityType,
    val showOwnTransfers: Boolean,
)
