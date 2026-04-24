package lk.salli.parser.util

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Helpers for turning a bank's in-body timestamp into UTC epoch millis.
 *
 * We *must* extract from the body rather than trust the phone's SMS delivery time — SMS often
 * arrive out of chronological order when the network hiccups, which makes a debit at 08:58
 * land after a credit at 08:56 on the device. Using delivery time meant balance-update
 * ordering was wrong.
 *
 * All times are interpreted in the Sri Lanka timezone (Asia/Colombo, UTC+5:30).
 */
object TimeParser {

    private val colombo: ZoneId = ZoneId.of("Asia/Colombo")

    // @HH:MM DD/MM/YYYY — People's Bank primary debit/credit template.
    private val peoplesPrimary = DateTimeFormatter.ofPattern("H:mm d/M/yyyy")

    // YYYY-MM-DD HH:MM:SS — People's Bank Mobile Payment / Fund Transfer confirm.
    private val peoplesConfirm = DateTimeFormatter.ofPattern("yyyy-M-d H:mm:ss")

    // DD/MM/YY HH:MM AM|PM — Commercial Bank purchase / decline.
    private val combank = DateTimeFormatter.ofPattern("d/M/yy h:mm a", java.util.Locale.ENGLISH)

    fun parsePeoplesPrimary(timeOfDay: String, date: String): Long? =
        runCatching {
            LocalDateTime.parse("$timeOfDay $date", peoplesPrimary)
                .atZone(colombo).toInstant().toEpochMilli()
        }.getOrNull()

    fun parsePeoplesConfirm(datetime: String): Long? =
        runCatching {
            LocalDateTime.parse(datetime, peoplesConfirm)
                .atZone(colombo).toInstant().toEpochMilli()
        }.getOrNull()

    fun parseCombank(date: String, time: String, ampm: String): Long? =
        runCatching {
            LocalDateTime.parse("$date $time ${ampm.uppercase()}", combank)
                .atZone(colombo).toInstant().toEpochMilli()
        }.getOrNull()
}
