package lk.salli.domain.planning

import java.util.Calendar
import lk.salli.domain.DateRange

enum class CommitmentKind { BILL, RECURRING, GOAL }

/** Money already spoken for before the cycle ends. */
data class Commitment(
    val label: String,
    val amountMinor: Long,
    val dueAt: Long?,
    val kind: CommitmentKind,
)

/** Spending in one completed cycle, most recent first when passed as a list. */
data class CycleSpend(val label: String, val spentMinor: Long)

sealed interface BudgetBasis {
    /** The user set a monthly spending limit. */
    data class UserLimit(val limitMinor: Long) : BudgetBasis

    /** No limit set: the median of these completed cycles. */
    data class MedianOfCycles(val cycles: List<CycleSpend>) : BudgetBasis

    /** No limit and no completed cycle to learn from. */
    data object NoHistory : BudgetBasis
}

data class SafeToSpendResult(
    val budgetMinor: Long?,
    val basis: BudgetBasis,
    val spentMinor: Long,
    val commitments: List<Commitment>,
    val committedMinor: Long,
    /** Budget − spent − committed. Negative when the cycle is already over budget. */
    val leftMinor: Long?,
    /** Days remaining in the cycle, today included. Always at least 1. */
    val daysLeft: Int,
    /** max(0, left) ÷ days left; null without a budget. */
    val perDayMinor: Long?,
)

/**
 * "How much can I spend today and stay inside this cycle?" Deterministic and explainable: every
 * input is visible in [SafeToSpendResult], and nothing assumes a salary or a payday.
 */
object SafeToSpend {

    const val MEDIAN_CYCLES = 3
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** The user's limit when set; otherwise the median of up to three completed cycles. */
    fun budget(userLimitMinor: Long?, completedCycles: List<CycleSpend>): Pair<Long?, BudgetBasis> {
        if (userLimitMinor != null && userLimitMinor > 0L) {
            return userLimitMinor to BudgetBasis.UserLimit(userLimitMinor)
        }
        val recent = completedCycles.take(MEDIAN_CYCLES)
        if (recent.isEmpty()) return null to BudgetBasis.NoHistory
        return median(recent.map { it.spentMinor }) to BudgetBasis.MedianOfCycles(recent)
    }

    /** Calendar days from today through the last day of [cycle], inclusive. */
    fun daysLeft(now: Long, cycle: DateRange): Int {
        val today = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return ((cycle.untilMillis - today + DAY_MS - 1) / DAY_MS).toInt().coerceAtLeast(1)
    }

    fun compute(
        now: Long,
        cycle: DateRange,
        userLimitMinor: Long?,
        completedCycles: List<CycleSpend>,
        spentMinor: Long,
        commitments: List<Commitment>,
    ): SafeToSpendResult {
        val (budget, basis) = budget(userLimitMinor, completedCycles)
        val committed = commitments.sumOf { it.amountMinor.coerceAtLeast(0L) }
        val days = daysLeft(now, cycle)
        val left = budget?.let { it - spentMinor - committed }
        return SafeToSpendResult(
            budgetMinor = budget,
            basis = basis,
            spentMinor = spentMinor,
            commitments = commitments,
            committedMinor = committed,
            leftMinor = left,
            daysLeft = days,
            perDayMinor = left?.let { it.coerceAtLeast(0L) / days },
        )
    }

    /**
     * How many times a repeating charge lands before [until]: the expected [nextAt], then every
     * [intervalDays] after it. A charge a little late (still before [until]) counts once.
     */
    fun occurrencesBefore(nextAt: Long, intervalDays: Double, until: Long): Int {
        if (intervalDays <= 0.0) return 0
        val step = (intervalDays * DAY_MS).toLong().coerceAtLeast(DAY_MS)
        var t = nextAt
        var n = 0
        while (t < until && n < 31) {
            n++
            t += step
        }
        return n
    }

    private fun median(values: List<Long>): Long {
        val s = values.sorted()
        val mid = s.size / 2
        return if (s.size % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2
    }
}
