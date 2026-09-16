package lk.salli.parser.utility

/**
 * Parses National Fuel Pass confirmations from sender `1919`:
 *
 * ```
 * National Fuel Pass: TRN confirmed.
 * 2026-09-08 06:02:23 BAM-0786          (older messages: 05.04.2026 14:14:35 BAM-0786)
 * Quota used: 8L
 * Weekly Balance: 0.000L
 * Station code: 102316
 * (Resets on - 2026-09-13)              (older messages: (Expires on - 2026-04-11))
 * ```
 *
 * OTP, registration and QR-link messages from the same sender return null.
 */
object FuelPassParser {

    private val confirmed = Regex("""TRN\s+confirmed""", RegexOption.IGNORE_CASE)
    private val stamp = Regex("""(\d{4}-\d{2}-\d{2}|\d{2}\.\d{2}\.\d{4})\s+(\d{2}:\d{2}:\d{2})\s+([A-Z0-9]{2,3}-\d{4})""")
    private val quota = Regex("""Quota\s+used\s*:\s*([\d.]+)\s*L""", RegexOption.IGNORE_CASE)
    private val balance = Regex("""Weekly\s+Balance\s*:\s*([\d.]+)\s*L""", RegexOption.IGNORE_CASE)
    private val station = Regex("""Station\s+code\s*:\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val resets = Regex("""\((?:Resets|Expires)\s+on\s*-\s*(\d{4}-\d{2}-\d{2})\)""", RegexOption.IGNORE_CASE)

    fun parse(sender: String, body: String, receivedAt: Long): ParsedFuelTransaction? {
        if (!UtilitySenders.isFuelPassSender(sender)) return null
        val text = body.trim()
        if (!confirmed.containsMatchIn(text)) return null
        val s = stamp.find(text) ?: return null
        val used = quota.find(text)?.groupValues?.get(1)?.let(::millilitres) ?: return null
        val left = balance.find(text)?.groupValues?.get(1)?.let(::millilitres) ?: 0L
        return ParsedFuelTransaction(
            vehicle = s.groupValues[3],
            litresMilli = used,
            weeklyBalanceMilli = left,
            stationCode = station.find(text)?.groupValues?.get(1),
            timestampMillis = UtilityDates.dateTimeMillis(s.groupValues[1], s.groupValues[2]) ?: receivedAt,
            resetsOnMillis = resets.find(text)?.let { UtilityDates.dayMillis(it.groupValues[1]) },
            rawBody = text,
        )
    }

    /** "2.51" → 2510, "8" → 8000, "4.390" → 4390. Integer millilitres avoid float drift in totals. */
    internal fun millilitres(litres: String): Long {
        val parts = litres.trim().split(".")
        val whole = parts[0].toLongOrNull() ?: return 0L
        val frac = parts.getOrNull(1).orEmpty().padEnd(3, '0').take(3).toLongOrNull() ?: 0L
        return whole * 1000 + frac
    }
}
