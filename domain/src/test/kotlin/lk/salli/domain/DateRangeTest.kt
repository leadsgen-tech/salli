package lk.salli.domain

import com.google.common.truth.Truth.assertThat
import java.util.Calendar
import org.junit.jupiter.api.Test

class DateRangeTest {

    private fun at(y: Int, m: Int, d: Int, h: Int = 0): Long =
        Calendar.getInstance().apply {
            clear()
            set(y, m - 1, d, h, 0, 0)
        }.timeInMillis

    @Test
    fun `start day 1 is the plain calendar month`() {
        val r = DateRange.cycleFor(at(2026, 9, 14), 1)
        assertThat(r.fromMillis).isEqualTo(at(2026, 9, 1))
        assertThat(r.untilMillis).isEqualTo(at(2026, 10, 1))
        assertThat(r.label).isEqualTo("September 2026")
    }

    @Test
    fun `start day 25 mid-month anchors to the previous 25th`() {
        val r = DateRange.cycleFor(at(2026, 9, 14), 25)
        assertThat(r.fromMillis).isEqualTo(at(2026, 8, 25))
        assertThat(r.untilMillis).isEqualTo(at(2026, 9, 25))
    }

    @Test
    fun `start day 28 survives February`() {
        val feb = DateRange.cycleFor(at(2026, 2, 15), 28)
        assertThat(feb.fromMillis).isEqualTo(at(2026, 1, 28))
        assertThat(feb.untilMillis).isEqualTo(at(2026, 2, 28))
        val next = DateRange.nextCycle(feb, 28)
        assertThat(next.fromMillis).isEqualTo(at(2026, 2, 28))
        assertThat(next.untilMillis).isEqualTo(at(2026, 3, 28))
        // Anchors above 28 are clamped to 28, never to a day February lacks.
        assertThat(DateRange.cycleFor(at(2026, 2, 15), 31).fromMillis).isEqualTo(at(2026, 1, 28))
    }

    @Test
    fun `cycles wrap the year in both directions`() {
        val jan = DateRange.cycleFor(at(2026, 1, 10), 25)
        assertThat(jan.fromMillis).isEqualTo(at(2025, 12, 25))
        assertThat(jan.untilMillis).isEqualTo(at(2026, 1, 25))
        val prev = DateRange.prevCycle(jan, 25)
        assertThat(prev.fromMillis).isEqualTo(at(2025, 11, 25))
        assertThat(DateRange.nextCycle(prev, 25)).isEqualTo(jan)
    }

    @Test
    fun `prev and next are inverse for calendar months too`() {
        val sep = DateRange.cycleFor(at(2026, 9, 14), 1)
        val aug = DateRange.prevCycle(sep, 1)
        assertThat(aug.label).isEqualTo("August 2026")
        assertThat(DateRange.nextCycle(aug, 1)).isEqualTo(sep)
    }

    @Test
    fun `cycle offsets step from the current cycle`() {
        val clock = { at(2026, 9, 14, 12) }
        val current = DateRange.cycleOffset(0, 25, clock)
        assertThat(current.fromMillis).isEqualTo(at(2026, 8, 25))
        val back = DateRange.cycleOffset(-2, 25, clock)
        assertThat(back.fromMillis).isEqualTo(at(2026, 6, 25))
        assertThat(DateRange.cycleMonthOffset(back, 25, clock)).isEqualTo(-2)
        assertThat(DateRange.cycleMonthOffset(current, 25, clock)).isEqualTo(0)
    }

    @Test
    fun `week ranges honour the chosen start day`() {
        // 16 Sep 2026 is a Wednesday.
        val wed = at(2026, 9, 16, 15)
        val monWeek = DateRange.weekContaining(wed, 1)
        assertThat(monWeek.fromMillis).isEqualTo(at(2026, 9, 14))
        assertThat(monWeek.untilMillis).isEqualTo(at(2026, 9, 21))
        val sunWeek = DateRange.weekContaining(wed, 7)
        assertThat(sunWeek.fromMillis).isEqualTo(at(2026, 9, 13))
        assertThat(sunWeek.untilMillis).isEqualTo(at(2026, 9, 20))
        assertThat(DateRange.prevWeek(sunWeek, 7).fromMillis).isEqualTo(at(2026, 9, 6))
        assertThat(DateRange.nextWeek(sunWeek, 7).fromMillis).isEqualTo(at(2026, 9, 20))
        assertThat(sunWeek.durationDays).isEqualTo(7)
    }
}
