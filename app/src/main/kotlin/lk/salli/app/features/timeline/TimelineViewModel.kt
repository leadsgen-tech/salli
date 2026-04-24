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
import lk.salli.app.ui.TimelineItem
import lk.salli.app.ui.toTimelineItem
import lk.salli.data.db.SalliDatabase
import lk.salli.data.db.entities.TransactionEntity
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
)

data class AccountSummary(
    val id: Long,
    val displayName: String,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val db: SalliDatabase,
    private val refresher: lk.salli.app.sms.SmsRefresher,
) : ViewModel() {

    val refreshing: StateFlow<Boolean> = refresher.refreshing
    fun refresh() = refresher.refresh()

    private val _range = MutableStateFlow(DateRange.currentMonth())
    val range: StateFlow<DateRange> = _range

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    /** Account IDs the user has toggled off in the chart legend. Empty = show all. */
    private val _hiddenAccounts = MutableStateFlow<Set<Long>>(emptySet())

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

    val state: StateFlow<TimelineUiState> = combine(
        kotlinx.coroutines.flow.combine(_range, _hiddenAccounts, _customRange) { r, h, c ->
            Triple(r, h, c)
        },
        _query,
        txns,
        db.categories().observeAll(),
        db.accounts().observeAll(),
    ) { rangeAndFilters, query, rows, categories, accounts ->
        val (range, hiddenAccountIds, customRange) = rangeAndFilters
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
        val filtered = queryFiltered.filter { it.accountId !in hiddenAccountIds }

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

        // For each transaction that's part of a transfer group, find its paired transaction's
        // account-display-name. One hop — powers the "Source → Destination" subtitle.
        val counterpartByTxId: Map<Long, String> = run {
            val byGroup = filtered
                .filter { it.transferGroupId != null }
                .groupBy { it.transferGroupId!! }
            filtered.mapNotNull { row ->
                val gid = row.transferGroupId ?: return@mapNotNull null
                val pair = byGroup[gid] ?: return@mapNotNull null
                val other = pair.firstOrNull { it.id != row.id } ?: return@mapNotNull null
                val name = byAcc[other.accountId]?.displayName ?: return@mapNotNull null
                row.id to name
            }.toMap()
        }

        val groups = filtered
            .groupBy { dayBucket(it.timestamp) }
            .toSortedMap(compareByDescending { it })
            .map { (bucketStart, list) -> buildGroup(bucketStart, list, byCat, byAcc, counterpartByTxId, dominantCurrency) }

        // `filtered` already excludes hidden accounts, so the chart's visible series comes
        // straight from it. For the legend we want every account that HAS activity in the
        // query-scoped range so the user can tap to re-enable a hidden one — compute that
        // from `queryFiltered` (pre-hide) instead.
        val visibleSeries = buildAccountSeries(range, filtered, byAcc, dominantCurrency)
        val accountsInView = buildAccountSeries(range, queryFiltered, byAcc, dominantCurrency)
            .map { AccountSummary(id = it.accountId, displayName = it.displayName) }

        TimelineUiState(
            range = range,
            query = query,
            grouped = groups,
            totalIncome = Money(income, dominantCurrency),
            totalExpense = Money(expense, dominantCurrency),
            transactionCount = filtered.size,
            isEmpty = groups.isEmpty(),
            monthOffset = if (customRange) null else monthOffsetFrom(range),
            series = visibleSeries,
            accountsInView = accountsInView,
            hiddenAccountIds = hiddenAccountIds,
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
        _range.value = DateRange.prev(_range.value)
        _customRange.value = false
    }
    fun onNextRange() {
        _range.value = DateRange.next(_range.value)
        _customRange.value = false
    }
    fun onPickRange(range: DateRange) {
        _range.value = range
        _customRange.value = true
    }
    fun onQueryChanged(query: String) { _query.value = query }
    fun clearQuery() { _query.value = "" }

    /** Jump directly to a calendar month relative to today (0 = current, −1 = last month). */
    fun onPickMonthOffset(offset: Int) {
        val c = Calendar.getInstance()
        c.add(Calendar.MONTH, offset)
        _range.value = DateRange.monthContaining(c.timeInMillis)
        _customRange.value = false
    }

    fun toggleAccount(accountId: Long) {
        _hiddenAccounts.value = _hiddenAccounts.value.toMutableSet().apply {
            if (!add(accountId)) remove(accountId)
        }
    }

    /** How far the active range's month is from the current calendar month (signed). */
    private fun monthOffsetFrom(range: DateRange): Int {
        val now = Calendar.getInstance()
        val rangeCal = Calendar.getInstance().apply { timeInMillis = range.fromMillis }
        val nowYm = now.get(Calendar.YEAR) * 12 + now.get(Calendar.MONTH)
        val rangeYm = rangeCal.get(Calendar.YEAR) * 12 + rangeCal.get(Calendar.MONTH)
        return rangeYm - nowYm
    }

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

    private fun buildGroup(
        bucketStart: Long,
        rows: List<TransactionEntity>,
        byCat: Map<Long, lk.salli.data.db.entities.CategoryEntity>,
        byAcc: Map<Long, lk.salli.data.db.entities.AccountEntity>,
        counterpartByTxId: Map<Long, String>,
        dominantCurrency: String,
    ): TimelineGroup {
        val net = rows
            .filter { !it.isDeclined && it.transferGroupId == null && it.amountCurrency == dominantCurrency }
            .sumOf { row ->
                when (row.flowId) {
                    TransactionFlow.INCOME.id -> row.amountMinor
                    TransactionFlow.EXPENSE.id -> -row.amountMinor
                    else -> 0L
                }
            }
        return TimelineGroup(
            label = labelFor(bucketStart),
            items = rows.map { row ->
                row.toTimelineItem(
                    category = row.categoryId?.let { byCat[it] },
                    accountDisplayName = byAcc[row.accountId]?.displayName,
                    counterpartAccountName = counterpartByTxId[row.id],
                )
            },
            netMinor = net,
            currency = dominantCurrency,
        )
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
