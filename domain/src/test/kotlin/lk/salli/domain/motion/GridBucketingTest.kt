package lk.salli.domain.motion

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * `java.time` appears here but never in the production code — `:domain` stays portable, while
 * the test gets to say "16 September 2026" instead of a bare day number.
 */
class GridBucketingTest {

    private val today = LocalDate.of(2026, 9, 16).toEpochDay()

    // --- weekday arithmetic -----------------------------------------------------------

    @Test
    fun `weekday index agrees with the calendar for a year of dates`() {
        var date = LocalDate.of(2025, 1, 1)
        repeat(400) {
            assertThat(GridBucketing.weekdayIndex(date.toEpochDay()))
                .isEqualTo(date.dayOfWeek.value - 1)
            date = date.plusDays(1)
        }
    }

    @Test
    fun `weekday index is right before the epoch too`() {
        val christmas1969 = LocalDate.of(1969, 12, 25)
        assertThat(GridBucketing.weekdayIndex(christmas1969.toEpochDay()))
            .isEqualTo(christmas1969.dayOfWeek.value - 1)
    }

    @Test
    fun `monday of a week is a Monday, and its own Monday`() {
        val monday = GridBucketing.mondayOf(today)
        assertThat(GridBucketing.weekdayIndex(monday)).isEqualTo(0)
        assertThat(GridBucketing.mondayOf(monday)).isEqualTo(monday)
    }

    @ParameterizedTest
    @CsvSource("0,false", "4,false", "5,true", "6,true")
    fun `weekends are Saturday and Sunday`(weekday: Int, weekend: Boolean) {
        assertThat(GridBucketing.isWeekend(weekday)).isEqualTo(weekend)
    }

    // --- 52 x 7 year grid -------------------------------------------------------------

    @Test
    fun `today sits in the last column on its own weekday row`() {
        val cell = GridBucketing.yearCell(today, today)
        assertThat(cell).isEqualTo(GridCell(GridBucketing.YEAR_COLUMNS - 1, GridBucketing.weekdayIndex(today)))
    }

    @Test
    fun `a week ago is one column to the left on the same row`() {
        val cell = GridBucketing.yearCell(today - 7, today)
        assertThat(cell).isEqualTo(GridCell(GridBucketing.YEAR_COLUMNS - 2, GridBucketing.weekdayIndex(today)))
    }

    @Test
    fun `yesterday is in the same column unless today is a Monday`() {
        val cell = requireNotNull(GridBucketing.yearCell(today - 1, today))
        // 16 Sep 2026 is a Wednesday, so yesterday shares the column.
        assertThat(GridBucketing.weekdayIndex(today)).isEqualTo(2)
        assertThat(cell.column).isEqualTo(GridBucketing.YEAR_COLUMNS - 1)
        assertThat(cell.row).isEqualTo(1)
    }

    @Test
    fun `the future is not on the grid`() {
        assertThat(GridBucketing.yearCell(today + 1, today)).isNull()
        assertThat(GridBucketing.yearCell(today + 400, today)).isNull()
    }

    @Test
    fun `the window reaches back fifty-one whole weeks and no further`() {
        val oldest = GridBucketing.yearStartDayEpoch(today)
        assertThat(GridBucketing.yearCell(oldest, today)).isEqualTo(GridCell(0, 0))
        assertThat(GridBucketing.yearCell(oldest - 1, today)).isNull()
    }

    @Test
    fun `cell and day epoch are inverses of each other`() {
        for (back in 0 until GridBucketing.YEAR_COLUMNS * GridBucketing.YEAR_ROWS) {
            val day = today - back
            val cell = GridBucketing.yearCell(day, today) ?: continue
            assertThat(GridBucketing.yearDayEpoch(cell, today)).isEqualTo(day)
        }
    }

    @Test
    fun `the year grid is 364 cells and only counts days inside the window`() {
        val grid = GridBucketing.buildYearGrid(
            dayCounts = mapOf(
                today to 3,
                today - 7 to 1,
                today + 5 to 99, // future — ignored
                today - 400 to 99, // too far back — ignored
            ),
            todayEpoch = today,
        )

        assertThat(grid).hasLength(GridBucketing.YEAR_COLUMNS * GridBucketing.YEAR_ROWS)
        assertThat(grid.sum()).isEqualTo(4f)
        val todayCell = requireNotNull(GridBucketing.yearCell(today, today))
        assertThat(grid[GridBucketing.flatIndex(todayCell, GridBucketing.YEAR_COLUMNS)]).isEqualTo(3f)
    }

    @Test
    fun `flat index is row-major`() {
        assertThat(GridBucketing.flatIndex(GridCell(column = 3, row = 2), columns = 52)).isEqualTo(107)
    }

    // --- 7 x 24 week-hour grid --------------------------------------------------------

    @Test
    fun `an instant lands in the local hour, not UTC`() {
        // 2026-09-16T18:42+05:30 in Colombo is 13:12 UTC.
        val utcMillis = LocalDate.of(2026, 9, 16).atTime(13, 12).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()

        assertThat(GridBucketing.weekHourOf(utcMillis, COLOMBO_OFFSET)).isEqualTo(WeekHour(weekday = 2, hour = 18))
        assertThat(GridBucketing.weekHourOf(utcMillis, 0)).isEqualTo(WeekHour(weekday = 2, hour = 13))
    }

    @Test
    fun `local midnight rolls the day over`() {
        // 2026-09-16T00:15+05:30 is still 2026-09-15 in UTC.
        val utcMillis = LocalDate.of(2026, 9, 15).atTime(18, 45).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()

        assertThat(GridBucketing.weekHourOf(utcMillis, COLOMBO_OFFSET)).isEqualTo(WeekHour(weekday = 2, hour = 0))
        assertThat(GridBucketing.dayEpochOf(utcMillis, COLOMBO_OFFSET)).isEqualTo(today)
        assertThat(GridBucketing.dayEpochOf(utcMillis, 0)).isEqualTo(today - 1)
    }

    @Test
    fun `instants before the epoch floor rather than rounding toward zero`() {
        // Half a second before the epoch is still 1969-12-31, at 23:00 UTC+00.
        assertThat(GridBucketing.dayEpochOf(-500L, 0)).isEqualTo(-1L)
        assertThat(GridBucketing.weekHourOf(-500L, 0)).isEqualTo(WeekHour(weekday = 2, hour = 23))
    }

    @Test
    fun `the week-hour grid is 168 cells with hours across and days down`() {
        val grid = GridBucketing.buildWeekHourGrid(
            listOf(WeekHour(2, 18), WeekHour(2, 18), WeekHour(6, 3), WeekHour(9, 3), WeekHour(1, 99)),
        )

        assertThat(grid).hasLength(GridBucketing.HOUR_COLUMNS * GridBucketing.WEEK_ROWS)
        // Out-of-range weekday and hour are dropped rather than throwing.
        assertThat(grid.sum()).isEqualTo(3f)
        assertThat(grid[2 * GridBucketing.HOUR_COLUMNS + 18]).isEqualTo(2f)
        assertThat(grid[6 * GridBucketing.HOUR_COLUMNS + 3]).isEqualTo(1f)
    }

    @ParameterizedTest
    @CsvSource(
        "0,NIGHT", "4,NIGHT", "5,MORNING", "11,MORNING", "12,AFTERNOON",
        "16,AFTERNOON", "17,EVENING", "21,EVENING", "22,NIGHT", "23,NIGHT",
    )
    fun `hours map to the expected band`(hour: Int, band: TimeBand) {
        assertThat(GridBucketing.timeBandOf(hour)).isEqualTo(band)
    }

    // --- captions ---------------------------------------------------------------------

    private fun gridOf(days: IntRange, hours: List<Int>): FloatArray =
        GridBucketing.buildWeekHourGrid(days.flatMap { d -> hours.map { WeekHour(d, it) } })

    @Test
    fun `evenings on weekdays reads as weekday evenings`() {
        val caption = GridBucketing.caption(gridOf(0..4, listOf(18, 19, 20)))
        assertThat(caption.dayGroup).isEqualTo(DayGroup.WEEKDAYS)
        assertThat(caption.timeBand).isEqualTo(TimeBand.EVENING)
        assertThat(caption.english).isEqualTo("Mostly weekday evenings")
    }

    @Test
    fun `afternoons on Saturday and Sunday read as weekend afternoons`() {
        val caption = GridBucketing.caption(gridOf(5..6, listOf(13, 14)))
        assertThat(caption.dayGroup).isEqualTo(DayGroup.WEEKENDS)
        assertThat(caption.timeBand).isEqualTo(TimeBand.AFTERNOON)
        assertThat(caption.english).isEqualTo("Mostly weekend afternoons")
    }

    @Test
    fun `evenings every day reads as a time of day, not a set of days`() {
        val caption = GridBucketing.caption(gridOf(0..6, listOf(19, 20)))
        assertThat(caption.dayGroup).isEqualTo(DayGroup.ALL_WEEK)
        assertThat(caption.timeBand).isEqualTo(TimeBand.EVENING)
        assertThat(caption.english).isEqualTo("Evenings, all week")
    }

    @Test
    fun `late night spending gets its own caption`() {
        val caption = GridBucketing.caption(gridOf(0..6, listOf(23, 0, 1)))
        assertThat(caption.english).isEqualTo("Late nights, all week")
    }

    @Test
    fun `weekdays with no favourite hour reads as weekdays all day`() {
        val caption = GridBucketing.caption(gridOf(0..4, (0..23).toList()))
        assertThat(caption.dayGroup).isEqualTo(DayGroup.WEEKDAYS)
        assertThat(caption.timeBand).isEqualTo(TimeBand.ALL_DAY)
        assertThat(caption.english).isEqualTo("Weekdays, right through the day")
    }

    @Test
    fun `no pattern at all says so`() {
        val caption = GridBucketing.caption(gridOf(0..6, (0..23).toList()))
        assertThat(caption.dayGroup).isEqualTo(DayGroup.ALL_WEEK)
        assertThat(caption.timeBand).isEqualTo(TimeBand.ALL_DAY)
        assertThat(caption.english).isEqualTo("Spread across the week")
    }

    @Test
    fun `an empty grid does not pretend to see anything`() {
        val caption = GridBucketing.caption(FloatArray(GridBucketing.HOUR_COLUMNS * GridBucketing.WEEK_ROWS))
        assertThat(caption.english).isEqualTo("Not enough to see a pattern yet")
    }

    @Test
    fun `the rules can produce well over four distinct captions`() {
        val captions = listOf(
            gridOf(0..4, listOf(18, 19)),
            gridOf(0..4, listOf(8, 9)),
            gridOf(5..6, listOf(13, 14)),
            gridOf(5..6, listOf(19, 20)),
            gridOf(0..6, listOf(19, 20)),
            gridOf(0..6, listOf(23, 0)),
            gridOf(0..4, (0..23).toList()),
            gridOf(0..6, (0..23).toList()),
        ).map { GridBucketing.caption(it).english }

        assertThat(captions.toSet()).hasSize(8)
    }

    private companion object {
        /** Sri Lanka is UTC+5:30. */
        const val COLOMBO_OFFSET = 5 * 3600 + 30 * 60
    }
}
