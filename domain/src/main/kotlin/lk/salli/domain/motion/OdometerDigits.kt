package lk.salli.domain.motion

/** Which way a digit wheel turns to reach its new value. */
enum class RollDirection { UP, DOWN, NONE }

/**
 * One character column of a rolling number.
 *
 * Non-digits — the "Rs", the thousands commas, the decimal point, a minus sign — are columns
 * too, so the layout stays put, but they never roll: [isDigit] is false and [distance] is 0.
 */
data class OdometerColumn(
    /** The character this column ends on. */
    val char: Char,
    val isDigit: Boolean,
    /** Digit this column starts from, or -1 when there is nothing to roll from. */
    val from: Int = NO_DIGIT,
    /** Digit this column ends on, or -1 for a non-digit. */
    val to: Int = NO_DIGIT,
    val direction: RollDirection = RollDirection.NONE,
    /** Wheel steps to travel in [direction], 0..9. */
    val distance: Int = 0,
    /** True when the column did not exist before — the number grew a digit. */
    val isNew: Boolean = false,
) {
    /** True when this column actually has to move. */
    val rolls: Boolean get() = isDigit && distance > 0

    companion object {
        const val NO_DIGIT = -1
    }
}

/**
 * Splits a formatted amount into per-character columns and works out how each digit wheel has
 * to turn to get from the old value to the new one. `SpringOdometer` in the design module draws
 * the result; this is the part worth testing, so it lives here.
 *
 * Columns are matched up from the **right**, the way a real odometer works: going from
 * `Rs 999.00` to `Rs 1,000.00`, the three nines roll to zeroes in place and a fresh `1,` appears
 * on the left rather than every digit shifting along by one.
 *
 * Every wheel in a number turns the same way — up when the amount grew, down when it shrank —
 * because a number where some digits count up while others count down reads as a glitch, not as
 * a total changing. [shortestSteps] is there for callers that would rather take the short way
 * round.
 */
object OdometerDigits {

    /**
     * Columns for rendering [new], carrying the roll needed from [old].
     *
     * The returned list always has one entry per character of [new], left to right.
     *
     * @param direction overrides the up/down choice; by default it comes from comparing the two
     *   numbers with [rollDirection].
     */
    fun columns(
        old: String,
        new: String,
        direction: RollDirection? = null,
    ): List<OdometerColumn> {
        val roll = direction ?: rollDirection(old, new)
        val offset = new.length - old.length

        return new.mapIndexed { i, ch ->
            if (!ch.isDigit()) {
                return@mapIndexed OdometerColumn(char = ch, isDigit = false)
            }
            val to = ch - '0'
            val oldIndex = i - offset
            val oldChar = old.getOrNull(oldIndex)
            val from = if (oldChar != null && oldChar.isDigit()) oldChar - '0' else OdometerColumn.NO_DIGIT

            if (from == OdometerColumn.NO_DIGIT) {
                // A column that has just appeared: nothing to roll from, so it fades in.
                OdometerColumn(char = ch, isDigit = true, to = to, isNew = true)
            } else {
                val steps = stepsIn(roll, from, to)
                OdometerColumn(
                    char = ch,
                    isDigit = true,
                    from = from,
                    to = to,
                    direction = if (steps == 0) RollDirection.NONE else roll,
                    distance = steps,
                )
            }
        }
    }

    /**
     * Up when [new] is the larger number, down when it is smaller, and [RollDirection.NONE]
     * when the digits are identical. Compares the digits only, so the grouping commas and the
     * currency prefix don't confuse it.
     */
    fun rollDirection(old: String, new: String): RollDirection {
        val a = old.filter { it.isDigit() }
        val b = new.filter { it.isDigit() }
        val negativeBefore = old.hasMinus()
        val negativeAfter = new.hasMinus()

        if (negativeBefore != negativeAfter) {
            return if (negativeAfter) RollDirection.DOWN else RollDirection.UP
        }
        val magnitude = when {
            a.length != b.length -> if (b.length > a.length) 1 else -1
            else -> b.compareTo(a).coerceIn(-1, 1)
        }
        if (magnitude == 0) return RollDirection.NONE
        // Below zero, a bigger magnitude is a smaller number.
        val signed = if (negativeAfter) -magnitude else magnitude
        return if (signed > 0) RollDirection.UP else RollDirection.DOWN
    }

    /**
     * Signed shortest move on a ten-digit wheel, -5..5. Negative means downward. Offered for
     * callers that want the fastest path rather than a consistent direction.
     */
    fun shortestSteps(from: Int, to: Int): Int {
        val up = wheelMod(to - from)
        return if (up <= 5) up else up - 10
    }

    /**
     * How many cells of a wheel are actually drawn for [column]. A wheel with nine digits to
     * travel does not need nine stacked views to be read as spinning, so long rolls are capped
     * at [maxSteps].
     */
    fun visibleSteps(column: OdometerColumn, maxSteps: Int = DEFAULT_MAX_VISIBLE_STEPS): Int =
        column.distance.coerceAtMost(maxSteps.coerceAtLeast(1))

    /**
     * The strip of digits a wheel shows on its way from [OdometerColumn.from] to
     * [OdometerColumn.to].
     *
     * Cell 0 is always where the wheel started and cell [visibleSteps] is always where it lands,
     * so there is no jump at either end; a roll longer than that skips the middle, which at
     * speed reads as spinning. One spare digit follows the target so a spring has somewhere to
     * overshoot into.
     */
    fun wheelDigits(column: OdometerColumn, maxSteps: Int = DEFAULT_MAX_VISIBLE_STEPS): List<Int> {
        val steps = visibleSteps(column, maxSteps)
        if (steps <= 0 || !column.isDigit) return listOf(column.to.coerceAtLeast(0))
        val direction = if (column.direction == RollDirection.DOWN) -1 else 1
        return (0 until steps).map { wheelMod(column.from + direction * it) } +
            column.to +
            wheelMod(column.to + direction)
    }

    /**
     * Per-column progress with a right-to-left stagger, so a number unspools instead of
     * flipping: the rightmost wheel starts at once and each one to its left waits a little
     * longer.
     *
     * Every column still reaches 1 exactly when [progress] does, so they all come to rest
     * on-register. Values above 1 are passed through — that is a spring's overshoot, and
     * [wheelDigits] leaves a spare digit for it.
     *
     * @param stagger how much of the roll the leftmost column waits out, 0 to just under 1.
     */
    fun staggeredProgress(
        progress: Float,
        index: Int,
        count: Int,
        stagger: Float = DEFAULT_STAGGER,
    ): Float {
        if (count <= 1) return progress.coerceAtLeast(0f)
        val clamped = stagger.coerceIn(0f, 0.9f)
        val lead = clamped * (count - 1 - index).coerceIn(0, count - 1) / (count - 1)
        return ((progress - lead) / (1f - lead)).coerceAtLeast(0f)
    }

    /** Long rolls past this many digits skip their middle. */
    const val DEFAULT_MAX_VISIBLE_STEPS = 3

    /** Default share of the roll the leftmost wheel waits out before it starts. */
    const val DEFAULT_STAGGER = 0.25f

    private fun stepsIn(direction: RollDirection, from: Int, to: Int): Int = when (direction) {
        RollDirection.UP -> wheelMod(to - from)
        RollDirection.DOWN -> wheelMod(from - to)
        RollDirection.NONE -> 0
    }

    /** `%` but never negative — hand-rolled so `:domain` keeps no JVM dependency. */
    private fun wheelMod(v: Int): Int = ((v % 10) + 10) % 10

    private fun String.hasMinus(): Boolean = any { it == '-' || it == '−' }
}
