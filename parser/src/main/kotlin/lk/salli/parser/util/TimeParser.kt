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

    // DD/MM/YY HH:MM:SS — HNB account debit/credit.
    private val hnbAccount = DateTimeFormatter.ofPattern("d/M/yy H:mm:ss")

    // DD.MM.YY HH:MM — HNB card SMS alerts + ATM receipts.
    private val hnbDot = DateTimeFormatter.ofPattern("d.M.yy H:mm")

    // DD/MM/YYYY hh:mm:ss AM|PM — Seylan card debit.
    private val seylan = DateTimeFormatter.ofPattern("d/M/yyyy h:mm:ss a", java.util.Locale.ENGLISH)

    // YYYY-MM-DD HH:MM:SS — ComBank fund-transfer-via-Combank-Online (24h).
    private val combankFundTransfer = DateTimeFormatter.ofPattern("yyyy-M-d H:mm:ss")

    // DD/MM/YYYY — ComBank "Bill Payment in the amount of … was received on …" (date only).
    private val combankBillPayment = DateTimeFormatter.ofPattern("d/M/yyyy")

    // Month-name stamps are matched case-insensitively: banks print "Sep", "SEP" and "sep"
    // and a case-sensitive formatter would silently fall back to the SMS receive time.
    private fun caseInsensitive(pattern: String): DateTimeFormatter =
        java.time.format.DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern(pattern)
            .toFormatter(java.util.Locale.ENGLISH)

    // DD/MMM/YYYY HH:MM — DFCC credit-card alert, e.g. "14/Sep/2026 18:42" (provisional).
    private val dfccCard = caseInsensitive("d/MMM/yyyy H:mm")

    // DD MMM YYYY — DFCC account alert, date only, e.g. "14 Sep 2026" (provisional).
    private val dayMonthNameYear = caseInsensitive("d MMM yyyy")

    // DD-MMM-YYYY hh:mm:ss AM|PM — HNB credit-card alert, e.g. "14-Sep-2026 06:42:10 PM" (provisional).
    private val hnbCard = caseInsensitive("d-MMM-yyyy h:mm:ss a")

    // DD/MM/YYYY HH:MM:SS — Sampath account alert (provisional).
    private val sampathAccount = DateTimeFormatter.ofPattern("d/M/yyyy H:mm:ss")

    fun parseDfccCard(datetime: String): Long? =
        runCatching {
            LocalDateTime.parse(datetime.trim(), dfccCard).atZone(colombo).toInstant().toEpochMilli()
        }.getOrNull()

    /** Date-only stamps resolve to local midnight; ordering within the day falls back to receivedAt. */
    fun parseDayMonthNameYear(date: String): Long? =
        runCatching {
            java.time.LocalDate.parse(date.trim(), dayMonthNameYear).atStartOfDay(colombo).toInstant().toEpochMilli()
        }.getOrNull()

    /** `yyyy-MM-dd HH:mm:ss` — same shape People's Bank confirms use; NDB card alerts too. */
    fun parseIsoDateTime(datetime: String): Long? = parsePeoplesConfirm(datetime.trim())

    fun parseHnbCard(datetime: String): Long? =
        runCatching {
            LocalDateTime.parse(datetime.trim(), hnbCard).atZone(colombo).toInstant().toEpochMilli()
        }.getOrNull()

    fun parseSampathAccount(date: String, time: String): Long? =
        runCatching {
            LocalDateTime.parse("$date $time", sampathAccount).atZone(colombo).toInstant().toEpochMilli()
        }.getOrNull()

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

    fun parseHnbAccount(date: String, time: String): Long? =
        runCatching {
            LocalDateTime.parse("$date $time", hnbAccount)
                .atZone(colombo).toInstant().toEpochMilli()
        }.getOrNull()

    fun parseHnbDot(date: String, time: String): Long? =
        runCatching {
            LocalDateTime.parse("$date $time", hnbDot)
                .atZone(colombo).toInstant().toEpochMilli()
        }.getOrNull()

    fun parseSeylan(date: String, time: String, ampm: String): Long? =
        runCatching {
            LocalDateTime.parse("$date $time ${ampm.uppercase()}", seylan)
                .atZone(colombo).toInstant().toEpochMilli()
        }.getOrNull()

    fun parseCombankFundTransfer(date: String, time: String): Long? =
        runCatching {
            LocalDateTime.parse("$date $time", combankFundTransfer)
                .atZone(colombo).toInstant().toEpochMilli()
        }.getOrNull()

    /** Date-only — Bill Payment confirmation falls back to midnight Colombo. */
    fun parseCombankBillPayment(date: String): Long? =
        runCatching {
            java.time.LocalDate.parse(date, combankBillPayment)
                .atStartOfDay(colombo).toInstant().toEpochMilli()
        }.getOrNull()
}
