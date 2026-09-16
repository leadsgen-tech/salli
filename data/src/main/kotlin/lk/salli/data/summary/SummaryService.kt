package lk.salli.data.summary

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.util.Locale
import kotlinx.coroutines.flow.first
import lk.salli.data.db.SalliDatabase
import lk.salli.domain.DateRange
import lk.salli.data.prefs.SalliPreferences

/** Which of the three scheduled summaries; the preference key suffix doubles as the id. */
enum class SummaryCadence(val key: String) { DAILY("daily"), WEEKLY("weekly"), MONTHLY("monthly") }

/**
 * Decides which summaries are due right now and builds them. The hourly worker calls
 * [due] and posts whatever comes back; each period is posted once thanks to the
 * "last posted" markers in preferences.
 *
 *  - Daily: today so far, on every day.
 *  - Weekly: the week that just ended, on the user's week-start day.
 *  - Monthly: the cycle that just ended, on the user's month-start day.
 */
class SummaryService(
    private val db: SalliDatabase,
    private val prefs: SalliPreferences,
    private val zone: ZoneId = ZoneId.of("Asia/Colombo"),
) {

    data class Due(val cadence: SummaryCadence, val periodKey: String, val summary: SpendingSummary)

    suspend fun due(now: Long = System.currentTimeMillis()): List<Due> {
        val settings = prefs.summarySettings.first()
        if (!settings.daily && !settings.weekly && !settings.monthly) return emptyList()
        val local = Instant.ofEpochMilli(now).atZone(zone)
        if (local.hour < settings.hour) return emptyList()
        val period = prefs.period.first()
        val today = local.toLocalDate()

        val hidden = db.accounts().all().filter { it.isHidden }.map { it.id }.toSet()
        val categories = db.categories().all().associate { it.id to it.name }
        val out = ArrayList<Due>(3)

        if (settings.daily) {
            val key = today.toString()
            if (prefs.lastSummaryKey(SummaryCadence.DAILY.key) != key) {
                val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
                val rows = db.transactions().recentAll(start).filter { it.timestamp < start + DAY_MS }
                SummaryBuilder.build("Today", rows, categories, hidden)
                    ?.let { out += Due(SummaryCadence.DAILY, key, it) }
            }
        }
        if (settings.weekly && today.dayOfWeek.value == period.weekStartDay) {
            val thisWeek = DateRange.weekContaining(now, period.weekStartDay)
            val lastWeek = DateRange.prevWeek(thisWeek, period.weekStartDay)
            val key = "W" + Instant.ofEpochMilli(lastWeek.fromMillis).atZone(zone).toLocalDate().let {
                "${it.get(IsoFields.WEEK_BASED_YEAR)}-${it.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"
            }
            if (prefs.lastSummaryKey(SummaryCadence.WEEKLY.key) != key) {
                val rows = db.transactions().recentAll(lastWeek.fromMillis).filter { it.timestamp < lastWeek.untilMillis }
                SummaryBuilder.build("Last week · ${lastWeek.label}", rows, categories, hidden)
                    ?.let { out += Due(SummaryCadence.WEEKLY, key, it) }
            }
        }
        if (settings.monthly && today.dayOfMonth == period.monthStartDay) {
            val thisCycle = DateRange.cycleFor(now, period.monthStartDay)
            val lastCycle = DateRange.prevCycle(thisCycle, period.monthStartDay)
            val key = "M" + Instant.ofEpochMilli(lastCycle.fromMillis).atZone(zone).toLocalDate().format(monthKey)
            if (prefs.lastSummaryKey(SummaryCadence.MONTHLY.key) != key) {
                val rows = db.transactions().recentAll(lastCycle.fromMillis).filter { it.timestamp < lastCycle.untilMillis }
                SummaryBuilder.build("Last month · ${lastCycle.label}", rows, categories, hidden)
                    ?.let { out += Due(SummaryCadence.MONTHLY, key, it) }
            }
        }
        return out
    }

    suspend fun markPosted(due: Due) = prefs.setLastSummaryKey(due.cadence.key, due.periodKey)

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
        val monthKey: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM", Locale.US)
    }
}
