package lk.salli.domain.motion

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class OdometerDigitsTest {

    // --- direction --------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource(
        "'Rs 84,200.00','Rs 84,300.00',UP",
        "'Rs 84,300.00','Rs 84,200.00',DOWN",
        "'Rs 84,200.00','Rs 84,200.00',NONE",
        "'Rs 999.00','Rs 1,000.00',UP",
        "'Rs 1,000.00','Rs 999.00',DOWN",
        "'','Rs 400.00',UP",
    )
    fun `direction follows the number, not the string length`(old: String, new: String, expected: RollDirection) {
        assertThat(OdometerDigits.rollDirection(old, new)).isEqualTo(expected)
    }

    @Test
    fun `crossing zero flips the direction`() {
        assertThat(OdometerDigits.rollDirection("Rs 100.00", "−Rs 100.00")).isEqualTo(RollDirection.DOWN)
        assertThat(OdometerDigits.rollDirection("−Rs 100.00", "Rs 100.00")).isEqualTo(RollDirection.UP)
    }

    @Test
    fun `below zero a bigger magnitude is a smaller number`() {
        assertThat(OdometerDigits.rollDirection("−Rs 100.00", "−Rs 900.00")).isEqualTo(RollDirection.DOWN)
        assertThat(OdometerDigits.rollDirection("−Rs 900.00", "−Rs 100.00")).isEqualTo(RollDirection.UP)
    }

    // --- columns ----------------------------------------------------------------------

    @Test
    fun `there is one column per character of the new value`() {
        val columns = OdometerDigits.columns("Rs 84,200.00", "Rs 84,300.00")
        assertThat(columns).hasSize("Rs 84,300.00".length)
        assertThat(columns.map { it.char }.joinToString("")).isEqualTo("Rs 84,300.00")
    }

    @Test
    fun `an unchanged value rolls nothing`() {
        val columns = OdometerDigits.columns("Rs 84,200.00", "Rs 84,200.00")
        assertThat(columns.none { it.rolls }).isTrue()
        assertThat(columns.all { it.direction == RollDirection.NONE }).isTrue()
    }

    @Test
    fun `currency, commas and the decimal point are columns but never roll`() {
        val columns = OdometerDigits.columns("Rs 84,200.00", "Rs 91,300.00")
        val stationary = columns.filterNot { it.isDigit }

        assertThat(stationary.map { it.char }).containsExactly('R', 's', ' ', ',', '.').inOrder()
        assertThat(stationary.all { it.distance == 0 && it.direction == RollDirection.NONE }).isTrue()
    }

    @Test
    fun `only the digits that changed actually move`() {
        val columns = OdometerDigits.columns("Rs 84,200.00", "Rs 84,300.00")
        val rolling = columns.withIndex().filter { it.value.rolls }

        // Just the hundreds digit: 2 -> 3.
        assertThat(rolling).hasSize(1)
        val (_, column) = rolling.single()
        assertThat(column.from).isEqualTo(2)
        assertThat(column.to).isEqualTo(3)
        assertThat(column.direction).isEqualTo(RollDirection.UP)
        assertThat(column.distance).isEqualTo(1)
    }

    @Test
    fun `columns are matched from the right so a growing number keeps its wheels in place`() {
        val columns = OdometerDigits.columns("999.00", "1,000.00")

        // The new leading "1," has nothing behind it and fades in.
        assertThat(columns[0].char).isEqualTo('1')
        assertThat(columns[0].isNew).isTrue()
        assertThat(columns[1].char).isEqualTo(',')
        assertThat(columns[1].isDigit).isFalse()

        // The three nines roll on to zero where they stand.
        val nines = columns.filter { it.from == 9 }
        assertThat(nines).hasSize(3)
        assertThat(nines.all { it.to == 0 && it.direction == RollDirection.UP && it.distance == 1 }).isTrue()

        // The cents were already zero and stay put.
        assertThat(columns.takeLast(2).none { it.rolls }).isTrue()
    }

    @Test
    fun `a shrinking number rolls every changed wheel downward`() {
        val columns = OdometerDigits.columns("1,000.00", "999.00")
        val rolling = columns.filter { it.rolls }

        assertThat(rolling).isNotEmpty()
        assertThat(rolling.all { it.direction == RollDirection.DOWN }).isTrue()
        assertThat(columns.filter { it.from == 0 && it.to == 9 }.all { it.distance == 1 }).isTrue()
    }

    @Test
    fun `the first value ever shown has no wheels to roll`() {
        val columns = OdometerDigits.columns("", "Rs 84,200.00")

        assertThat(columns.filter { it.isDigit }.all { it.isNew && it.distance == 0 }).isTrue()
        assertThat(columns.filter { it.isDigit }.all { it.from == OdometerColumn.NO_DIGIT }).isTrue()
    }

    @Test
    fun `a wheel takes the long way round when it has to keep the shared direction`() {
        // 9 -> 1 counting up is eight steps; the odometer does not shortcut backwards.
        val columns = OdometerDigits.columns("9", "1")
        assertThat(columns.single().direction).isEqualTo(RollDirection.DOWN)
        assertThat(columns.single().distance).isEqualTo(8)

        val up = OdometerDigits.columns("9", "1", direction = RollDirection.UP)
        assertThat(up.single().distance).isEqualTo(2)
    }

    @Test
    fun `an explicit direction overrides what the numbers say`() {
        val columns = OdometerDigits.columns("Rs 100.00", "Rs 200.00", direction = RollDirection.DOWN)
        val rolling = columns.single { it.rolls }
        assertThat(rolling.direction).isEqualTo(RollDirection.DOWN)
        assertThat(rolling.distance).isEqualTo(9)
    }

    @Test
    fun `a masked amount is all non-digits and survives intact`() {
        val columns = OdometerDigits.columns("Rs 84,200.00", "Rs ••••")
        assertThat(columns.none { it.isDigit }).isTrue()
        assertThat(columns.map { it.char }.joinToString("")).isEqualTo("Rs ••••")
    }

    // --- shortest path ----------------------------------------------------------------

    @ParameterizedTest
    @CsvSource("0,0,0", "1,2,1", "9,0,1", "0,9,-1", "0,5,5", "0,6,-4", "3,8,5")
    fun `shortest steps never travels more than five`(from: Int, to: Int, expected: Int) {
        assertThat(OdometerDigits.shortestSteps(from, to)).isEqualTo(expected)
    }

    // --- wheel strips -----------------------------------------------------------------

    private fun column(from: Int, to: Int, direction: RollDirection, distance: Int) =
        OdometerColumn(
            char = '0' + to,
            isDigit = true,
            from = from,
            to = to,
            direction = direction,
            distance = distance,
        )

    @Test
    fun `a short roll shows every digit it passes through`() {
        val strip = OdometerDigits.wheelDigits(column(7, 9, RollDirection.UP, 2))
        // Start, the digit in between, the target, then one spare for the overshoot.
        assertThat(strip).containsExactly(7, 8, 9, 0).inOrder()
    }

    @Test
    fun `a downward roll counts down and wraps`() {
        val strip = OdometerDigits.wheelDigits(column(1, 9, RollDirection.DOWN, 2))
        assertThat(strip).containsExactly(1, 0, 9, 8).inOrder()
    }

    @Test
    fun `a long roll keeps its endpoints and skips the middle`() {
        val long = column(1, 9, RollDirection.UP, 8)
        val strip = OdometerDigits.wheelDigits(long, maxSteps = 3)

        assertThat(OdometerDigits.visibleSteps(long, maxSteps = 3)).isEqualTo(3)
        assertThat(strip).hasSize(5)
        assertThat(strip.first()).isEqualTo(1)
        // Cell `visibleSteps` is the landing spot; the last cell is the overshoot spare.
        assertThat(strip[3]).isEqualTo(9)
    }

    @Test
    fun `a wheel with nowhere to go is a single digit`() {
        val still = column(4, 4, RollDirection.NONE, 0)
        assertThat(OdometerDigits.wheelDigits(still)).containsExactly(4)
    }

    // --- stagger ----------------------------------------------------------------------

    @Test
    fun `every wheel lands exactly on register when the roll finishes`() {
        for (index in 0 until 8) {
            assertThat(OdometerDigits.staggeredProgress(1f, index, count = 8))
                .isWithin(EPS).of(1f)
        }
    }

    @Test
    fun `the rightmost wheel starts first and the leftmost waits`() {
        val rightmost = OdometerDigits.staggeredProgress(0.1f, index = 7, count = 8)
        val leftmost = OdometerDigits.staggeredProgress(0.1f, index = 0, count = 8)

        assertThat(rightmost).isGreaterThan(0f)
        assertThat(leftmost).isEqualTo(0f)
    }

    @Test
    fun `progress is monotonic from right to left`() {
        val byIndex = (0 until 8).map { OdometerDigits.staggeredProgress(0.6f, it, count = 8) }
        assertThat(byIndex).isInOrder()
    }

    @Test
    fun `a spring overshoot is passed through so the wheel can bounce`() {
        assertThat(OdometerDigits.staggeredProgress(1.08f, index = 7, count = 8))
            .isGreaterThan(1f)
    }

    @Test
    fun `a single column has no one to stagger against`() {
        assertThat(OdometerDigits.staggeredProgress(0.4f, index = 0, count = 1))
            .isWithin(EPS).of(0.4f)
    }

    private companion object {
        const val EPS = 1e-5f
    }
}
