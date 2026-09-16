package lk.salli.domain.planning

import com.google.common.truth.Truth.assertThat
import java.util.Calendar
import org.junit.jupiter.api.Test

class GoalMathTest {

    private fun local(y: Int, m: Int, d: Int) =
        Calendar.getInstance().apply { set(y, m, d, 10, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

    private val now = local(2026, Calendar.SEPTEMBER, 14)

    @Test
    fun `a goal without a date only reports progress`() {
        val p = GoalMath.progress(targetMinor = 400_000, savedMinor = 100_000, savedBeforeCycleMinor = 100_000, targetDate = null, now = now, monthStartDay = 1)
        assertThat(p.percent).isEqualTo(25)
        assertThat(p.remainingMinor).isEqualTo(300_000)
        assertThat(p.cyclesLeft).isNull()
        assertThat(p.toSaveThisCycleMinor).isNull()
    }

    @Test
    fun `a dated goal spreads what is left evenly over the cycles to go`() {
        val dec = local(2026, Calendar.DECEMBER, 20)
        val fresh = GoalMath.progress(400_000, savedMinor = 0, savedBeforeCycleMinor = 0, targetDate = dec, now = now, monthStartDay = 1)
        assertThat(fresh.cyclesLeft).isEqualTo(4)
        assertThat(fresh.perCycleMinor).isEqualTo(100_000)
        assertThat(fresh.toSaveThisCycleMinor).isEqualTo(100_000)

        // Saving this cycle lowers what is left to save now, not the share itself.
        val partway = GoalMath.progress(400_000, savedMinor = 30_000, savedBeforeCycleMinor = 0, targetDate = dec, now = now, monthStartDay = 1)
        assertThat(partway.perCycleMinor).isEqualTo(100_000)
        assertThat(partway.toSaveThisCycleMinor).isEqualTo(70_000)
    }

    @Test
    fun `an overdue goal asks for everything that is left now`() {
        val p = GoalMath.progress(500_000, savedMinor = 100_000, savedBeforeCycleMinor = 100_000, targetDate = local(2026, Calendar.AUGUST, 1), now = now, monthStartDay = 1)
        assertThat(p.isOverdue).isTrue()
        assertThat(p.cyclesLeft).isEqualTo(1)
        assertThat(p.toSaveThisCycleMinor).isEqualTo(400_000)
    }

    @Test
    fun `a reached goal needs nothing more`() {
        val p = GoalMath.progress(500_000, savedMinor = 600_000, savedBeforeCycleMinor = 0, targetDate = local(2026, Calendar.DECEMBER, 1), now = now, monthStartDay = 1)
        assertThat(p.isComplete).isTrue()
        assertThat(p.percent).isEqualTo(100)
        assertThat(p.toSaveThisCycleMinor).isEqualTo(0)
    }
}
