package lk.salli.domain.motion

/** A cell in a heat grid. Column grows right, row grows down. */
data class GridCell(val column: Int, val row: Int)

/** A weekday (0 = Monday) and an hour of the day (0..23). */
data class WeekHour(val weekday: Int, val hour: Int)

/** Which days of the week carry most of the mass in a 7x24 grid. */
enum class DayGroup { WEEKDAYS, WEEKENDS, ALL_WEEK }

/** Which part of the day carries most of the mass in a 7x24 grid. */
enum class TimeBand { MORNING, AFTERNOON, EVENING, NIGHT, ALL_DAY }

/**
 * The one-line read on a 7x24 grid: "Mostly weekday evenings".
 *
 * [english] is the ready-made sentence for previews and for developers; screens that ship in
 * Sinhala and Tamil should switch on [dayGroup] and [timeBand] and pull the real copy out of
 * `strings.xml` instead.
 */
data class HeatCaption(
    val dayGroup: DayGroup,
    val timeBand: TimeBand,
    val english: String,
)

/**
 * Turns dates and timestamps into heat-grid cells, and reads a plain-English pattern back out
 * of a finished grid.
 *
 * Two grids, both drawn by `HeatGrid` in the design module:
 *
 * - **The year grid** (52 columns x 7 rows): onboarding Act 3's "your year lighting up", and
 *   Insights' "When you spend". One column per week, one row per weekday, the rightmost column
 *   being the week containing today.
 * - **The week-hour grid** (24 columns x 7 rows): Insights' "When you spend", counting
 *   transactions by weekday and hour.
 *
 * Pure Kotlin — no `java.time`, no Android — so `:domain` stays portable. Days are counted as
 * "day epoch": whole days since 1970-01-01, which is what Room already stores on a transaction.
 * Anything that needs a timezone takes an explicit offset in seconds rather than reaching for a
 * clock.
 *
 * Flat arrays are indexed row-major: `index = row * columns + column`.
 */
object GridBucketing {

    /** Weeks across the year grid. 52 x 7 = 364 dots, which is the design spec's grid. */
    const val YEAR_COLUMNS = 52

    /** Days down the year grid, Monday at the top. */
    const val YEAR_ROWS = 7

    /** Hours across the week-hour grid. */
    const val HOUR_COLUMNS = 24

    /** Days down the week-hour grid, Monday at the top. */
    const val WEEK_ROWS = 7

    private const val SECONDS_PER_DAY = 86_400L
    private const val SECONDS_PER_HOUR = 3_600L

    /**
     * Weekday of a day epoch: 0 = Monday through 6 = Sunday. 1970-01-01 was a Thursday, which
     * is where the +3 comes from.
     */
    fun weekdayIndex(dayEpoch: Long): Int = floorMod(dayEpoch + 3L, 7L).toInt()

    /** The Monday of the week containing [dayEpoch]. */
    fun mondayOf(dayEpoch: Long): Long = dayEpoch - weekdayIndex(dayEpoch)

    /** True for Saturday and Sunday. */
    fun isWeekend(weekday: Int): Boolean = weekday >= 5

    /**
     * Cell for [dayEpoch] in a 52x7 grid whose last column is the week containing [todayEpoch],
     * or null when the day falls outside that window — either in the future, or more than 51
     * whole weeks back.
     */
    fun yearCell(dayEpoch: Long, todayEpoch: Long): GridCell? {
        if (dayEpoch > todayEpoch) return null
        val weeksBack = (mondayOf(todayEpoch) - mondayOf(dayEpoch)) / 7L
        val column = (YEAR_COLUMNS - 1) - weeksBack
        if (column < 0 || column > YEAR_COLUMNS - 1) return null
        return GridCell(column.toInt(), weekdayIndex(dayEpoch))
    }

    /**
     * The inverse of [yearCell] — which day a grid cell stands for. Useful for tooltips, and
     * for filling the grid without a map lookup per cell. May return a future day for the cells
     * after today in the last column; those are the ones [yearCell] refuses.
     */
    fun yearDayEpoch(cell: GridCell, todayEpoch: Long): Long {
        val weeksBack = (YEAR_COLUMNS - 1) - cell.column
        return mondayOf(todayEpoch) - weeksBack * 7L + cell.row
    }

    /** First day the year grid covers, given today. */
    fun yearStartDayEpoch(todayEpoch: Long): Long = mondayOf(todayEpoch) - (YEAR_COLUMNS - 1) * 7L

    /** Row-major flat index, matching the `values` array `HeatGrid` draws. */
    fun flatIndex(cell: GridCell, columns: Int): Int = cell.row * columns + cell.column

    /**
     * Builds the 364-cell year grid from per-day counts. Days outside the window are ignored,
     * so the caller can hand over everything it has.
     */
    fun buildYearGrid(dayCounts: Map<Long, Int>, todayEpoch: Long): FloatArray {
        val grid = FloatArray(YEAR_COLUMNS * YEAR_ROWS)
        for ((day, count) in dayCounts) {
            val cell = yearCell(day, todayEpoch) ?: continue
            grid[flatIndex(cell, YEAR_COLUMNS)] += count.toFloat()
        }
        return grid
    }

    /** Day epoch of an instant, in the caller's timezone. */
    fun dayEpochOf(epochMillis: Long, zoneOffsetSeconds: Int): Long =
        floorDiv(localSeconds(epochMillis, zoneOffsetSeconds), SECONDS_PER_DAY)

    /** Weekday and hour of an instant, in the caller's timezone. */
    fun weekHourOf(epochMillis: Long, zoneOffsetSeconds: Int): WeekHour {
        val local = localSeconds(epochMillis, zoneOffsetSeconds)
        val day = floorDiv(local, SECONDS_PER_DAY)
        val secondOfDay = floorMod(local, SECONDS_PER_DAY)
        return WeekHour(weekdayIndex(day), (secondOfDay / SECONDS_PER_HOUR).toInt())
    }

    /** Cell for a weekday/hour pair in the 24x7 grid: hours across, days down. */
    fun weekHourCell(weekday: Int, hour: Int): GridCell = GridCell(hour, weekday)

    /** Counts occurrences into a 24x7 grid. */
    fun buildWeekHourGrid(occurrences: Iterable<WeekHour>): FloatArray {
        val grid = FloatArray(HOUR_COLUMNS * WEEK_ROWS)
        for (o in occurrences) {
            if (o.weekday !in 0 until WEEK_ROWS || o.hour !in 0 until HOUR_COLUMNS) continue
            grid[flatIndex(weekHourCell(o.weekday, o.hour), HOUR_COLUMNS)] += 1f
        }
        return grid
    }

    /** Which band of the day an hour belongs to. */
    fun timeBandOf(hour: Int): TimeBand = when (hour) {
        in 5..11 -> TimeBand.MORNING
        in 12..16 -> TimeBand.AFTERNOON
        in 17..21 -> TimeBand.EVENING
        else -> TimeBand.NIGHT
    }

    /**
     * Reads a 24x7 grid and says, in one sentence, where the mass sits.
     *
     * Deliberately a handful of thresholds rather than anything clever — no model, no AI, and
     * it has to give the same answer every time so the caption doesn't flicker as one
     * transaction lands.
     *
     * Two independent questions:
     *
     * - **Which days?** Weekdays are five sevenths of the week to begin with, so they only
     *   count as the pattern past [WEEKDAY_SHARE]; weekends only have two days, so they clear
     *   the bar at the much lower [WEEKEND_SHARE].
     * - **Which hours?** The busiest of the four bands wins if it holds at least
     *   [TIME_BAND_SHARE] of everything; four bands means that is a clear plurality.
     *
     * Neither question having an answer is itself an answer: "Spread across the week".
     */
    fun caption(grid: FloatArray, columns: Int = HOUR_COLUMNS): HeatCaption {
        var total = 0f
        var weekendMass = 0f
        val bandMass = FloatArray(4)

        for (i in grid.indices) {
            val v = grid[i]
            if (v <= 0f) continue
            val row = i / columns
            val hour = i % columns
            total += v
            if (isWeekend(row)) weekendMass += v
            bandMass[timeBandOf(hour).ordinal] += v
        }

        if (total <= 0f) {
            return HeatCaption(DayGroup.ALL_WEEK, TimeBand.ALL_DAY, "Not enough to see a pattern yet")
        }

        val weekendShare = weekendMass / total
        val dayGroup = when {
            weekendShare >= WEEKEND_SHARE -> DayGroup.WEEKENDS
            (1f - weekendShare) >= WEEKDAY_SHARE -> DayGroup.WEEKDAYS
            else -> DayGroup.ALL_WEEK
        }

        var bestBand = 0
        for (i in bandMass.indices) if (bandMass[i] > bandMass[bestBand]) bestBand = i
        val timeBand = if (bandMass[bestBand] / total >= TIME_BAND_SHARE) {
            TimeBand.entries[bestBand]
        } else {
            TimeBand.ALL_DAY
        }

        return HeatCaption(dayGroup, timeBand, sentenceFor(dayGroup, timeBand))
    }

    private fun sentenceFor(dayGroup: DayGroup, timeBand: TimeBand): String = when (dayGroup) {
        DayGroup.WEEKDAYS -> when (timeBand) {
            TimeBand.MORNING -> "Mostly weekday mornings"
            TimeBand.AFTERNOON -> "Mostly weekday afternoons"
            TimeBand.EVENING -> "Mostly weekday evenings"
            TimeBand.NIGHT -> "Mostly weekday nights"
            TimeBand.ALL_DAY -> "Weekdays, right through the day"
        }

        DayGroup.WEEKENDS -> when (timeBand) {
            TimeBand.MORNING -> "Mostly weekend mornings"
            TimeBand.AFTERNOON -> "Mostly weekend afternoons"
            TimeBand.EVENING -> "Mostly weekend evenings"
            TimeBand.NIGHT -> "Mostly weekend nights"
            TimeBand.ALL_DAY -> "Weekends, right through the day"
        }

        DayGroup.ALL_WEEK -> when (timeBand) {
            TimeBand.MORNING -> "Mornings, all week"
            TimeBand.AFTERNOON -> "Afternoons, all week"
            TimeBand.EVENING -> "Evenings, all week"
            TimeBand.NIGHT -> "Late nights, all week"
            TimeBand.ALL_DAY -> "Spread across the week"
        }
    }

    /** Weekdays are 5/7 of the week already, so they have to clear a high bar to be "the" pattern. */
    private const val WEEKDAY_SHARE = 0.80f

    /** Weekends are only 2/7, so a much smaller share still reads as a weekend habit. */
    private const val WEEKEND_SHARE = 0.45f

    /** One of four bands holding this much is a clear plurality. */
    private const val TIME_BAND_SHARE = 0.40f

    /**
     * Seconds since the epoch in the caller's timezone. `floorDiv`, not `/`: plain division
     * rounds *toward* zero, which would round a pre-1970 instant up into the wrong second and
     * quietly undo the negative-safe arithmetic everything else here takes care to get right.
     */
    private fun localSeconds(epochMillis: Long, zoneOffsetSeconds: Int): Long =
        floorDiv(epochMillis, 1000L) + zoneOffsetSeconds

    private fun floorDiv(a: Long, b: Long): Long {
        val q = a / b
        return if (a % b != 0L && (a xor b) < 0L) q - 1 else q
    }

    private fun floorMod(a: Long, b: Long): Long = a - floorDiv(a, b) * b
}
