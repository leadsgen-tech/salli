package lk.salli.domain.planning

import com.google.common.truth.Truth.assertThat
import java.util.Calendar
import lk.salli.domain.DateRange
import org.junit.jupiter.api.Test

class SafeToSpendTest {

    private val day = 24L * 60 * 60 * 1000

    /** 14 Sep 2026, 10:00 local. The calendar-month cycle has 17 days left, today included. */
    private val now = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 14, 10, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    private val cycle = DateRange.cycleFor(now, 1)

    @Test
    fun `days left counts today through the last day of the cycle`() {
        assertThat(SafeToSpend.daysLeft(now, cycle)).isEqualTo(17)
        val lastDay = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 30, 23, 0, 0) }.timeInMillis
        assertThat(SafeToSpend.daysLeft(lastDay, cycle)).isEqualTo(1)
    }

    @Test
    fun `a user limit wins over history`() {
        val r = SafeToSpend.compute(now, cycle, userLimitMinor = 1_700_000, completedCycles = listOf(CycleSpend("Aug", 9_999_999)), spentMinor = 500_000, commitments = emptyList())
        assertThat(r.basis).isEqualTo(BudgetBasis.UserLimit(1_700_000))
        assertThat(r.leftMinor).isEqualTo(1_200_000)
        assertThat(r.perDayMinor).isEqualTo(1_200_000 / 17)
    }

    @Test
    fun `without a limit the budget is the median of the last three completed cycles`() {
        val three = listOf(CycleSpend("Aug", 300), CycleSpend("Jul", 100), CycleSpend("Jun", 200), CycleSpend("May", 99_999))
        val (budget, basis) = SafeToSpend.budget(null, three)
        assertThat(budget).isEqualTo(200)
        assertThat((basis as BudgetBasis.MedianOfCycles).cycles.map { it.label }).containsExactly("Aug", "Jul", "Jun").inOrder()
        assertThat(SafeToSpend.budget(null, listOf(CycleSpend("Aug", 100), CycleSpend("Jul", 300))).first).isEqualTo(200)
        assertThat(SafeToSpend.budget(0, three).first).isEqualTo(200)
    }

    @Test
    fun `no limit and no history gives no number`() {
        val r = SafeToSpend.compute(now, cycle, null, emptyList(), spentMinor = 10, commitments = emptyList())
        assertThat(r.basis).isEqualTo(BudgetBasis.NoHistory)
        assertThat(r.budgetMinor).isNull()
        assertThat(r.perDayMinor).isNull()
    }

    @Test
    fun `commitments reduce what is left and an overspent cycle floors at zero per day`() {
        val commitments = listOf(
            Commitment("SLT bill", 1_195_291, now + 8 * day, CommitmentKind.BILL),
            Commitment("LOLC", 4_502_500, now + 3 * day, CommitmentKind.RECURRING),
            Commitment("bogus", -50, null, CommitmentKind.GOAL),
        )
        val r = SafeToSpend.compute(now, cycle, userLimitMinor = 10_000_000, completedCycles = emptyList(), spentMinor = 2_000_000, commitments = commitments)
        assertThat(r.committedMinor).isEqualTo(5_697_791)
        assertThat(r.leftMinor).isEqualTo(10_000_000 - 2_000_000 - 5_697_791)

        val over = SafeToSpend.compute(now, cycle, userLimitMinor = 1_000_000, completedCycles = emptyList(), spentMinor = 2_000_000, commitments = emptyList())
        assertThat(over.leftMinor).isEqualTo(-1_000_000)
        assertThat(over.perDayMinor).isEqualTo(0)
    }

    @Test
    fun `repeating charges are counted for every expected date before the cycle ends`() {
        assertThat(SafeToSpend.occurrencesBefore(nextAt = now + 2 * day, intervalDays = 7.0, until = now + 20 * day)).isEqualTo(3)
        assertThat(SafeToSpend.occurrencesBefore(nextAt = now + 25 * day, intervalDays = 30.0, until = now + 20 * day)).isEqualTo(0)
        assertThat(SafeToSpend.occurrencesBefore(nextAt = now, intervalDays = 0.0, until = now + 20 * day)).isEqualTo(0)
    }
}
