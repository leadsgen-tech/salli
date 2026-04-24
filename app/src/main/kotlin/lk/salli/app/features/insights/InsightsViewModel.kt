package lk.salli.app.features.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import lk.salli.data.db.SalliDatabase
import lk.salli.data.db.entities.CategoryEntity
import lk.salli.data.db.entities.TransactionEntity
import lk.salli.domain.Currency
import lk.salli.domain.DateRange
import lk.salli.domain.Money
import lk.salli.domain.TransactionFlow

/** One category's contribution to the visible range. */
data class InsightSlice(
    val categoryId: Long?,
    val categoryName: String,
    val iconName: String,
    val colorSeed: Int,
    val totalMinor: Long,
    val currency: String,
    val count: Int,
    val percent: Float,
)

/** One month's stacked expense, broken down by top-N categories. */
data class MonthlyBar(
    val label: String,                  // "Mar"
    val totalMinor: Long,
    /** Per-category contributions. The slice at index `i` shares the `i`-th colour tint. */
    val slices: List<BarSlice>,
    val isCurrent: Boolean,
)

data class BarSlice(
    val categoryId: Long?,
    val categoryName: String,
    val totalMinor: Long,
)

data class InsightsUiState(
    val range: DateRange,
    val totalSpend: Money,
    val totalIncome: Money,
    val slices: List<InsightSlice> = emptyList(),
    val monthlyBars: List<MonthlyBar> = emptyList(),
    val topCategory: InsightSlice? = null,
    val transactionCount: Int = 0,
    val loading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val db: SalliDatabase,
    private val refresher: lk.salli.app.sms.SmsRefresher,
) : ViewModel() {

    val refreshing: StateFlow<Boolean> = refresher.refreshing
    fun refresh() = refresher.refresh()

    private val _range = MutableStateFlow(DateRange.currentMonth())
    val range: StateFlow<DateRange> = _range

    // For the bar chart we always want the last 6 months regardless of `range` — the user's
    // active range drives the hero + category list, but the bars are a stable horizon.
    private val sixMonthFromMillis: Long = run {
        val c = Calendar.getInstance()
        c.add(Calendar.MONTH, -5)
        c.set(Calendar.DAY_OF_MONTH, 1)
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        c.timeInMillis
    }
    private val sixMonthTxns = db.transactions().observeInRange(sixMonthFromMillis, Long.MAX_VALUE)

    private val rangeTxns = _range.flatMapLatest { r ->
        db.transactions().observeInRange(r.fromMillis, r.untilMillis)
    }

    val state: StateFlow<InsightsUiState> = combine(
        _range,
        rangeTxns,
        sixMonthTxns,
        db.categories().observeAll(),
    ) { r, txns, sixMonth, categories -> aggregate(r, txns, sixMonth, categories) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            InsightsUiState(
                range = _range.value,
                totalSpend = Money.zero(Currency.LKR),
                totalIncome = Money.zero(Currency.LKR),
            ),
        )

    fun onPrevRange() { _range.value = DateRange.prev(_range.value) }
    fun onNextRange() { _range.value = DateRange.next(_range.value) }
    fun onPickRange(range: DateRange) { _range.value = range }

    private fun aggregate(
        range: DateRange,
        txns: List<TransactionEntity>,
        sixMonth: List<TransactionEntity>,
        categories: List<CategoryEntity>,
    ): InsightsUiState {
        val real = txns.filter { !it.isDeclined && it.transferGroupId == null }
        val expense = real.filter { it.flowId == TransactionFlow.EXPENSE.id }
        val income = real.filter { it.flowId == TransactionFlow.INCOME.id }

        val dominantCurrency = expense
            .groupBy { it.amountCurrency }
            .maxByOrNull { it.value.size }
            ?.key
            ?: Currency.LKR

        val expenseInCurrency = expense.filter { it.amountCurrency == dominantCurrency }
        val totalSpendMinor = expenseInCurrency.sumOf { it.amountMinor }
        val totalIncomeMinor = income.filter { it.amountCurrency == dominantCurrency }
            .sumOf { it.amountMinor }

        val catLookup = categories.associateBy { it.id }

        val slices = expenseInCurrency
            .groupBy { it.categoryId }
            .map { (catId, list) ->
                val total = list.sumOf { it.amountMinor }
                val pct = if (totalSpendMinor > 0) total.toFloat() / totalSpendMinor else 0f
                val cat = catId?.let { catLookup[it] }
                InsightSlice(
                    categoryId = catId,
                    categoryName = cat?.name ?: "Uncategorised",
                    iconName = cat?.iconName ?: "inbox",
                    colorSeed = cat?.colorSeed ?: 0xFF6B7280.toInt(),
                    totalMinor = total,
                    currency = dominantCurrency,
                    count = list.size,
                    percent = pct,
                )
            }
            .sortedByDescending { it.totalMinor }

        val monthlyBars = buildMonthlyBars(sixMonth, catLookup, dominantCurrency, range)

        return InsightsUiState(
            range = range,
            totalSpend = Money(totalSpendMinor, dominantCurrency),
            totalIncome = Money(totalIncomeMinor, dominantCurrency),
            slices = slices,
            monthlyBars = monthlyBars,
            topCategory = slices.firstOrNull(),
            transactionCount = real.size,
            loading = false,
        )
    }

    /**
     * Six stacked bars — one per calendar month, oldest on the left — with the top-3
     * categories of each month broken out as separate slices. Drives the vertical-bar
     * "Spending analysis" chart.
     */
    private fun buildMonthlyBars(
        sixMonth: List<TransactionEntity>,
        catLookup: Map<Long, CategoryEntity>,
        dominantCurrency: String,
        range: DateRange,
    ): List<MonthlyBar> {
        val fmt = SimpleDateFormat("MMM", Locale.getDefault())
        val monthMs = 30L * 24 * 60 * 60 * 1000  // approximate — only used for grouping key
        @Suppress("UNUSED_VARIABLE") val _m = monthMs

        val now = Calendar.getInstance()
        now.set(Calendar.DAY_OF_MONTH, 1)
        now.set(Calendar.HOUR_OF_DAY, 0); now.set(Calendar.MINUTE, 0)
        now.set(Calendar.SECOND, 0); now.set(Calendar.MILLISECOND, 0)

        val rangeStartMs = range.fromMillis

        return (0 until 6).map { back ->
            val start = Calendar.getInstance().apply {
                timeInMillis = now.timeInMillis
                add(Calendar.MONTH, -(5 - back))
            }
            val end = Calendar.getInstance().apply {
                timeInMillis = start.timeInMillis
                add(Calendar.MONTH, 1)
            }
            val label = fmt.format(start.time)
            val inMonth = sixMonth.filter {
                !it.isDeclined && it.transferGroupId == null &&
                    it.flowId == TransactionFlow.EXPENSE.id &&
                    it.amountCurrency == dominantCurrency &&
                    it.timestamp in start.timeInMillis until end.timeInMillis
            }
            val byCat = inMonth.groupBy { it.categoryId }
            val barSlices = byCat.map { (catId, list) ->
                BarSlice(
                    categoryId = catId,
                    categoryName = catId?.let { catLookup[it]?.name } ?: "Uncategorised",
                    totalMinor = list.sumOf { it.amountMinor },
                )
            }.sortedByDescending { it.totalMinor }.take(3)
            val total = inMonth.sumOf { it.amountMinor }
            MonthlyBar(
                label = label,
                totalMinor = total,
                slices = barSlices,
                isCurrent = start.timeInMillis == rangeStartMs,
            )
        }
    }
}
