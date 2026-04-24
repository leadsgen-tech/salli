package lk.salli.design.format

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Relative time formatting tuned for transaction lists. "Today" gets the time of day
 * (`2:47 PM`); yesterday gets `"Yesterday"`; anything older gets a short date (`22 Apr` if
 * same year, `22 Apr 2025` otherwise).
 */
object TimeFormat {

    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val sameYearDate = SimpleDateFormat("d MMM", Locale.getDefault())
    private val olderYearDate = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

    fun relative(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
        val then = Calendar.getInstance().apply { timeInMillis = epochMillis }
        val today = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val startOfToday = today.timeInMillis
        val startOfYesterday = startOfToday - 24L * 60 * 60 * 1000
        return when {
            epochMillis >= startOfToday -> timeFormat.format(Date(epochMillis))
            epochMillis >= startOfYesterday -> "Yesterday"
            then.get(Calendar.YEAR) == today.get(Calendar.YEAR) ->
                sameYearDate.format(Date(epochMillis))
            else -> olderYearDate.format(Date(epochMillis))
        }
    }
}
