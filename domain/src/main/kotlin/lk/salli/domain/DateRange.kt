package lk.salli.domain

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * A half-open `[fromMillis, untilMillis)` window of time. Used for scoping the Insights,
 * Timeline, and Budgets views. Always carries a human-readable [label] so the UI doesn't
 * have to re-format it on every recomposition.
 *
 * Ranges are always aligned to day boundaries in the device's local timezone — callers that
 * build ranges from UTC-millis (e.g. the Material3 DateRangePicker, which returns UTC ms)
 * should use [ofUtc] which re-aligns to local.
 */
data class DateRange(
    val fromMillis: Long,
    val untilMillis: Long,
    val label: String,
) {
    val durationDays: Int
        get() = ((untilMillis - fromMillis) / MILLIS_PER_DAY).toInt()

    companion object {
        private const val MILLIS_PER_DAY: Long = 24L * 60 * 60 * 1000

        private val LOCAL_TZ: TimeZone get() = TimeZone.getDefault()

        /** The current calendar month, in local time. Label: "April 2026". */
        fun currentMonth(clock: () -> Long = { System.currentTimeMillis() }): DateRange =
            monthContaining(clock())

        /** The calendar month containing the given epoch ms, in local time. */
        fun monthContaining(epochMillis: Long): DateRange {
            val cal = Calendar.getInstance(LOCAL_TZ).apply {
                timeInMillis = epochMillis
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val from = cal.timeInMillis
            val labelFmt = SimpleDateFormat("MMMM yyyy", Locale.US).apply { timeZone = LOCAL_TZ }
            val label = labelFmt.format(cal.time)
            cal.add(Calendar.MONTH, 1)
            val until = cal.timeInMillis
            return DateRange(from, until, label)
        }

        /**
         * Given a range produced by the Material3 DateRangePicker (which hands us UTC-midnight
         * millis for the selected dates) re-align to local midnight and build the label. We
         * care about the user's wall clock, not UTC, so transactions at 2 am local on the
         * start day must be included.
         */
        fun ofUtc(fromUtcMs: Long, toUtcMs: Long): DateRange {
            val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            utc.timeInMillis = fromUtcMs
            val fromY = utc.get(Calendar.YEAR)
            val fromM = utc.get(Calendar.MONTH)
            val fromD = utc.get(Calendar.DAY_OF_MONTH)
            utc.timeInMillis = toUtcMs
            val toY = utc.get(Calendar.YEAR)
            val toM = utc.get(Calendar.MONTH)
            val toD = utc.get(Calendar.DAY_OF_MONTH)

            val local = Calendar.getInstance(LOCAL_TZ)
            local.set(fromY, fromM, fromD, 0, 0, 0); local.set(Calendar.MILLISECOND, 0)
            val fromLocal = local.timeInMillis
            local.set(toY, toM, toD, 0, 0, 0); local.set(Calendar.MILLISECOND, 0)
            local.add(Calendar.DAY_OF_MONTH, 1) // half-open — until *start* of the day after
            val untilLocal = local.timeInMillis

            val label = buildCustomLabel(fromLocal, untilLocal)
            return DateRange(fromLocal, untilLocal, label)
        }

        /**
         * Returns the custom-period cycle containing [anchorMillis] where each cycle runs from
         * [periodStartDay] of one month through day ([periodStartDay] − 1) of the next.
         *
         * If [periodStartDay] ≤ 1, falls back to [monthContaining] — the calendar-month behaviour.
         * [periodStartDay] is clamped to 1..28 to avoid month-length edge cases (Feb 30th etc).
         *
         * Example: for `periodStartDay = 25` and an anchor in mid-April → cycle runs
         * 25 March → 25 April (half-open). Label: `25 Mar – 24 Apr`.
         */
        fun cycleFor(anchorMillis: Long, periodStartDay: Int): DateRange {
            val day = periodStartDay.coerceIn(1, 28)
            if (day == 1) return monthContaining(anchorMillis)

            val cal = Calendar.getInstance(LOCAL_TZ).apply {
                timeInMillis = anchorMillis
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            // Cycle-start is the [day]-of-the-current-month if anchor is on/after it,
            // otherwise the [day]-of-the-previous-month.
            val anchorDay = cal.get(Calendar.DAY_OF_MONTH)
            if (anchorDay < day) cal.add(Calendar.MONTH, -1)
            cal.set(Calendar.DAY_OF_MONTH, day)
            val from = cal.timeInMillis
            cal.add(Calendar.MONTH, 1)
            val until = cal.timeInMillis

            val label = buildCustomLabel(from, until)
            return DateRange(from, until, label)
        }

        /** The previous calendar month relative to [range]. Label updates to the new month. */
        fun prev(range: DateRange): DateRange {
            val cal = Calendar.getInstance(LOCAL_TZ).apply { timeInMillis = range.fromMillis }
            cal.add(Calendar.MONTH, -1)
            return monthContaining(cal.timeInMillis)
        }

        /** The next calendar month relative to [range]. */
        fun next(range: DateRange): DateRange {
            val cal = Calendar.getInstance(LOCAL_TZ).apply { timeInMillis = range.fromMillis }
            cal.add(Calendar.MONTH, 1)
            return monthContaining(cal.timeInMillis)
        }

        private fun buildCustomLabel(fromMillis: Long, untilMillis: Long): String {
            val cal = Calendar.getInstance(LOCAL_TZ)
            cal.timeInMillis = untilMillis - 1 // last inclusive day
            val endDate = cal.time
            val startDate = Date(fromMillis)
            val sameMonth = SimpleDateFormat("yyyyMM", Locale.US).run {
                timeZone = LOCAL_TZ
                format(startDate) == format(endDate)
            }
            return if (sameMonth) {
                val s = SimpleDateFormat("d MMM", Locale.US).apply { timeZone = LOCAL_TZ }.format(startDate)
                val e = SimpleDateFormat("d MMM yyyy", Locale.US).apply { timeZone = LOCAL_TZ }.format(endDate)
                "$s – $e"
            } else {
                val f = SimpleDateFormat("d MMM yyyy", Locale.US).apply { timeZone = LOCAL_TZ }
                "${f.format(startDate)} – ${f.format(endDate)}"
            }
        }
    }
}
