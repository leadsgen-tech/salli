package lk.salli.data.widget

import java.util.Calendar
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import lk.salli.data.db.SalliDatabase
import lk.salli.data.db.entities.TransactionEntity
import lk.salli.data.planning.PlanningService
import lk.salli.data.prefs.SalliPreferences
import lk.salli.data.transactions.TransactionSpending
import lk.salli.domain.Currency
import lk.salli.domain.DateRange
import lk.salli.domain.Money

/** Everything the home-screen widget shows, already summed. */
data class WidgetSummary(
    /** Dominant currency of this period, the same one Home uses for "spent today". */
    val currency: String,
    val spentTodayMinor: Long,
    val periodSpentMinor: Long,
    val periodLabel: String,
    /** Safe to spend today from [PlanningService]; null when there is no budget to work from. */
    val safeToSpendTodayMinor: Long?,
    val safeToSpendCurrency: String,
    val hideAmounts: Boolean,
    /** Makes the widget's period line actionable rather than an unexplained month label. */
    val daysRemaining: Int = 0,
) {
    fun spentTodayText(format: (Money) -> String): String = amountText(spentTodayMinor, currency, format)
    fun periodSpentText(format: (Money) -> String): String = amountText(periodSpentMinor, currency, format)
    fun safeToSpendTodayText(format: (Money) -> String): String = amountText(safeToSpendTodayMinor, safeToSpendCurrency, format)

    /** "Rs ••••" when amounts are hidden, "—" when there is no number, otherwise [format]. */
    fun amountText(minor: Long?, currency: String, format: (Money) -> String): String = when {
        hideAmounts -> "${symbol(currency)} $MASK"
        minor == null -> "—"
        else -> format(Money(minor, currency))
    }

    companion object {
        const val MASK = "••••"
        fun symbol(currency: String): String = if (currency == Currency.LKR) "Rs" else currency
    }
}

/**
 * Builds the widget's numbers with the same money rules as Home: declined attempts, own-transfer
 * legs and hidden accounts never count, and sums use the dominant currency of the current period.
 * "This period" follows the month start day from Settings; safe to spend comes from
 * [PlanningService] unchanged. The widget itself only formats and lays these out.
 */
class WidgetSummaryService(
    private val db: SalliDatabase,
    private val prefs: SalliPreferences,
    private val planning: PlanningService,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    suspend fun load(): WidgetSummary = observe().first()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<WidgetSummary> = combine(
        prefs.period,
        prefs.widgetHideAmounts,
        db.accounts().observeAll(),
        planning.observe(),
    ) { period, hide, accounts, plan -> Inputs(period.monthStartDay, hide, accounts.filter { it.isHidden }.mapTo(HashSet()) { it.id }, plan.safeToSpend.perDayMinor, plan.currency) }
        .distinctUntilChanged()
        .flatMapLatest { inputs ->
            val now = clock()
            val cycle = DateRange.cycleFor(now, inputs.monthStartDay)
            val today = startOfDay(now)
            combine(
                db.transactions().observeInRange(cycle.fromMillis, cycle.untilMillis),
                db.transactions().observeInRange(today, today + DAY_MS),
            ) { periodRows, todayRows -> build(inputs, cycle, periodRows, todayRows) }
        }

    private data class Inputs(
        val monthStartDay: Int,
        val hideAmounts: Boolean,
        val hiddenAccountIds: Set<Long>,
        val safePerDayMinor: Long?,
        val planningCurrency: String,
    )

    private fun build(
        inputs: Inputs,
        cycle: DateRange,
        periodRows: List<TransactionEntity>,
        todayRows: List<TransactionEntity>,
    ): WidgetSummary {
        val currency = TransactionSpending.dominantCurrency(periodRows, inputs.hiddenAccountIds)
        fun spent(rows: List<TransactionEntity>) =
            TransactionSpending.totalMinor(rows, currency, inputs.hiddenAccountIds)
        return WidgetSummary(
            currency = currency,
            spentTodayMinor = spent(todayRows),
            periodSpentMinor = spent(periodRows),
            periodLabel = cycle.label,
            safeToSpendTodayMinor = inputs.safePerDayMinor,
            safeToSpendCurrency = inputs.planningCurrency,
            hideAmounts = inputs.hideAmounts,
            daysRemaining = (((cycle.untilMillis - clock()).coerceAtLeast(0L) + DAY_MS - 1) / DAY_MS).toInt(),
        )
    }

    private fun startOfDay(now: Long): Long = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
