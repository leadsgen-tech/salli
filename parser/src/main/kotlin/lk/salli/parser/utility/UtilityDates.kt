package lk.salli.parser.utility

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

/** Date helpers shared by the utility parsers. All dates are Sri Lanka local. */
internal object UtilityDates {
    val colombo: ZoneId = ZoneId.of("Asia/Colombo")

    private val iso = DateTimeFormatter.ofPattern("yyyy-M-d")
    private val dotted = DateTimeFormatter.ofPattern("d.M.yyyy")
    private val dashed = DateTimeFormatter.ofPattern("d-M-yyyy")
    private val monthAbbrev = DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern("d-MMM-yy")
        .toFormatter(Locale.ENGLISH)

    /** Accepts `2026-09-22`, `09.09.2026`, `25-09-2026` and `20-Sep-26`. */
    fun dayMillis(text: String): Long? {
        val t = text.trim()
        val date = when {
            Regex("""^\d{4}-\d{1,2}-\d{1,2}$""").matches(t) -> runCatching { LocalDate.parse(t, iso) }.getOrNull()
            Regex("""^\d{1,2}\.\d{1,2}\.\d{4}$""").matches(t) -> runCatching { LocalDate.parse(t, dotted) }.getOrNull()
            Regex("""^\d{1,2}-\d{1,2}-\d{4}$""").matches(t) -> runCatching { LocalDate.parse(t, dashed) }.getOrNull()
            Regex("""^\d{1,2}-[A-Za-z]{3}-\d{2}$""").matches(t) -> runCatching { LocalDate.parse(t, monthAbbrev) }.getOrNull()
            else -> null
        }
        return date?.atStartOfDay(colombo)?.toInstant()?.toEpochMilli()
    }

    fun dateTimeMillis(date: String, time: String): Long? {
        val d = dayMillis(date) ?: return null
        val local = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(d), colombo)
        val parts = time.split(":").mapNotNull { it.toIntOrNull() }
        if (parts.size < 2) return null
        return runCatching {
            LocalDateTime.of(local.year, local.month, local.dayOfMonth, parts[0], parts[1], parts.getOrElse(2) { 0 })
                .atZone(colombo).toInstant().toEpochMilli()
        }.getOrNull()
    }
}
