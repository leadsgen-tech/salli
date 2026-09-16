package lk.salli.domain.recurring

import kotlin.math.sqrt

/**
 * One transaction as the detector sees it. [counterparty] is the user's note when set,
 * otherwise the merchant or payee text from the SMS.
 */
data class RecurringInput(
    val id: Long,
    val counterparty: String?,
    val flowId: Int,
    val currency: String,
    val amountMinor: Long,
    val timestamp: Long,
    val isDeclined: Boolean = false,
    val isOwnTransfer: Boolean = false,
    val categoryName: String? = null,
)

/** Allowed gap between charges, in days, for each cadence. */
enum class Cadence(val minDays: Double, val maxDays: Double) {
    WEEKLY(6.0, 8.0),
    FORTNIGHTLY(13.0, 16.0),
    MONTHLY(26.0, 35.0),
    QUARTERLY(84.0, 97.0),
    YEARLY(350.0, 380.0),
    ;

    val halfWidthDays: Double get() = (maxDays - minDays) / 2

    fun contains(days: Double): Boolean = days in minDays..maxDays
}

enum class RecurringStatus {
    /** The last two attempts were declined: a subscription the bank keeps refusing. */
    FAILING,

    /** The next charge is expected within a week. */
    DUE_SOON,

    ACTIVE,

    /** The expected charge is overdue by more than half the cadence's tolerance. */
    MISSED,
}

data class RecurringSeries(
    val key: String,
    val displayName: String,
    val flowId: Int,
    val currency: String,
    /** Null only for a failing card subscription that never went through. */
    val cadence: Cadence?,
    /** Median gap between charges, in days; null when [cadence] is null. */
    val intervalDays: Double?,
    val typicalAmountMinor: Long,
    val isFixed: Boolean,
    val occurrences: Int,
    val declinedAttempts: Int,
    val lastAt: Long,
    val nextAt: Long?,
    val status: RecurringStatus,
    /** 0..1 from occurrence count, cadence consistency and amount stability. */
    val confidence: Double,
)

/**
 * Finds payments and credits that repeat on a schedule. Pure and deterministic: same input,
 * same output, no clock except the [now] you pass.
 *
 * Tuned on a real Sri Lankan inbox where income is irregular and most "repeats" are noise:
 * dozens of transfers to the same bank at random intervals, and grocery runs. A series
 * qualifies only when at least two thirds of its gaps fall in one cadence window, it has three
 * or more charges (four when the amount varies), and a payee that is a bank name has a fixed
 * amount. Card subscriptions a bank keeps declining are surfaced as [RecurringStatus.FAILING].
 */
object RecurringDetector {

    /** Coefficient of variation at or below which an amount counts as fixed. */
    const val FIXED_CV = 0.15

    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val RESEND_WINDOW_MS = 2L * 60 * 60 * 1000
    private const val DUE_SOON_DAYS = 7
    private const val EXPENSE = 0

    /** A declined-only merchant must be retried across at least this many days to count. */
    private const val FAILING_MIN_SPAN_DAYS = 20

    /** …and must still be trying: the latest refusal within this many days of now. */
    private const val FAILING_RECENT_DAYS = 60

    private val digits = Regex("""[\d*#]+""")
    private val nonLetters = Regex("""[^a-z ]""")
    private val corporate = Regex("""\b(ltd|pvt|plc|inc|llc|com|www|lk|limited|private)\b""")
    private val spaces = Regex("""\s+""")

    private val bankNames = listOf(
        "bank of ceylon", "boc", "commercial bank", "combank", "people s bank", "peoples bank",
        "hatton national", "hnb", "sampath", "seylan", "nations trust", "ntb", "dfcc", "ndb",
        "nsb", "national savings", "pan asia", "amana", "cargills bank", "union bank", "hsbc",
        "standard chartered",
    ).map { Regex("""\b${Regex.escape(it)}\b""") }

    private val neverRecurringCategories = setOf("groceries", "food & dining")

    /**
     * Normalises payee text into a grouping key: lowercase, digits and card-mask symbols removed,
     * punctuation and corporate suffixes dropped, spaces collapsed, first 28 characters.
     * "APPLE.COM/BILL" and "Apple.com/bill 123" both become "apple bill".
     */
    fun counterpartyKey(text: String?): String? {
        if (text.isNullOrBlank()) return null
        var s = text.lowercase()
        s = digits.replace(s, " ")
        s = nonLetters.replace(s, " ")
        s = corporate.replace(s, " ")
        s = spaces.replace(s, " ").trim()
        return s.take(28).trim().ifEmpty { null }
    }

    fun isBankName(key: String): Boolean = bankNames.any { it.containsMatchIn(key) }

    fun detect(inputs: List<RecurringInput>, now: Long): List<RecurringSeries> {
        val keyed = inputs
            .filter { !it.isOwnTransfer }
            .mapNotNull { input -> counterpartyKey(input.counterparty)?.let { it to input } }

        val approved = keyed.filter { !it.second.isDeclined }
            .groupBy({ Triple(it.first, it.second.flowId, it.second.currency) }, { it.second })
        val declined = keyed.filter { it.second.isDeclined }
            .groupBy({ it.first to it.second.currency }, { it.second })

        val out = ArrayList<RecurringSeries>()
        val attached = HashSet<Pair<String, String>>()
        for ((group, rows) in approved) {
            val (key, flowId, currency) = group
            val refusals = if (flowId == EXPENSE) declined[key to currency].orEmpty() else emptyList()
            val series = seriesFrom(key, flowId, currency, rows, refusals, now) ?: continue
            if (refusals.isNotEmpty()) attached += key to currency
            out += series
        }
        for ((group, refusals) in declined) {
            if (group in attached) continue
            failingOnly(group.first, group.second, refusals, now)?.let(out::add)
        }
        return out.sortedWith(compareBy<RecurringSeries> { it.status.ordinal }.thenBy { it.nextAt ?: Long.MAX_VALUE })
    }

    private fun seriesFrom(
        key: String,
        flowId: Int,
        currency: String,
        rows: List<RecurringInput>,
        refusals: List<RecurringInput>,
        now: Long,
    ): RecurringSeries? {
        if (flowId == EXPENSE && mostlyNeverRecurring(rows)) return null
        val events = mergeResends(rows)
        if (events.size < 3) return null

        val gaps = events.zipWithNext { a, b -> (b.timestamp - a.timestamp).toDouble() / DAY_MS }
        val (cadence, inWindow) = Cadence.entries
            .map { c -> c to gaps.filter(c::contains) }
            .maxByOrNull { it.second.size } ?: return null
        if (inWindow.isEmpty() || inWindow.size * 3 < gaps.size * 2) return null

        val amounts = events.map { it.amountMinor }
        val cv = coefficientOfVariation(amounts)
        val fixed = cv <= FIXED_CV
        if (!fixed && events.size < 4) return null
        if (!fixed && isBankName(key)) return null

        val interval = median(inWindow)
        val lastAt = events.last().timestamp
        val nextAt = lastAt + (interval * DAY_MS).toLong()

        val attempts = (events.map { it.timestamp to false } + mergeResends(refusals).map { it.timestamp to true })
            .sortedBy { it.first }
        // Same freshness rule as the declines-only path: refusals from long ago describe a
        // subscription the user has since cancelled, not one that keeps failing today.
        val lastRefusalAt = attempts.lastOrNull { it.second }?.first
        val failing = attempts.size >= 2 && attempts.takeLast(2).all { it.second } &&
            lastRefusalAt != null && (now - lastRefusalAt) / DAY_MS <= FAILING_RECENT_DAYS
        val status = when {
            failing -> RecurringStatus.FAILING
            now > nextAt + (cadence.halfWidthDays * DAY_MS).toLong() -> RecurringStatus.MISSED
            nextAt - now <= DUE_SOON_DAYS * DAY_MS -> RecurringStatus.DUE_SOON
            else -> RecurringStatus.ACTIVE
        }

        val consistency = inWindow.size.toDouble() / gaps.size
        val occurrenceScore = ((events.size - 2) / 4.0).coerceIn(0.0, 1.0)
        val amountScore = (1.0 - cv).coerceIn(0.0, 1.0)
        return RecurringSeries(
            key = key,
            displayName = displayName(rows),
            flowId = flowId,
            currency = currency,
            cadence = cadence,
            intervalDays = interval,
            typicalAmountMinor = medianLong(amounts),
            isFixed = fixed,
            occurrences = events.size,
            declinedAttempts = refusals.size,
            lastAt = lastAt,
            nextAt = nextAt,
            status = status,
            confidence = 0.4 * consistency + 0.3 * occurrenceScore + 0.3 * amountScore,
        )
    }

    /** A merchant that only ever declines, retried across weeks and still trying. */
    private fun failingOnly(key: String, currency: String, refusals: List<RecurringInput>, now: Long): RecurringSeries? {
        if (isBankName(key) || mostlyNeverRecurring(refusals)) return null
        val events = mergeResends(refusals)
        if (events.size < 2) return null
        val first = events.first().timestamp
        val last = events.last().timestamp
        if ((last - first) / DAY_MS < FAILING_MIN_SPAN_DAYS) return null
        if ((now - last) / DAY_MS > FAILING_RECENT_DAYS) return null
        return RecurringSeries(
            key = key,
            displayName = displayName(refusals),
            flowId = EXPENSE,
            currency = currency,
            cadence = null,
            intervalDays = null,
            typicalAmountMinor = medianLong(events.map { it.amountMinor }),
            isFixed = coefficientOfVariation(events.map { it.amountMinor }) <= FIXED_CV,
            occurrences = 0,
            declinedAttempts = refusals.size,
            lastAt = last,
            nextAt = null,
            status = RecurringStatus.FAILING,
            confidence = (0.5 + 0.1 * events.size).coerceAtMost(0.9),
        )
    }

    private fun mostlyNeverRecurring(rows: List<RecurringInput>): Boolean {
        val hits = rows.count { it.categoryName?.lowercase() in neverRecurringCategories }
        return hits * 2 > rows.size
    }

    /** SMS re-sends and card retries within two hours are one event (the first one). */
    private fun mergeResends(rows: List<RecurringInput>): List<RecurringInput> {
        val sorted = rows.sortedBy { it.timestamp }
        val out = ArrayList<RecurringInput>(sorted.size)
        for (row in sorted) {
            val prev = out.lastOrNull()
            if (prev != null && row.timestamp - prev.timestamp < RESEND_WINDOW_MS) continue
            out += row
        }
        return out
    }

    private fun displayName(rows: List<RecurringInput>): String =
        rows.mapNotNull { it.counterparty?.trim()?.takeIf(String::isNotEmpty) }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: ""

    private fun coefficientOfVariation(values: List<Long>): Double {
        if (values.isEmpty()) return 0.0
        val mean = values.average()
        if (mean == 0.0) return 0.0
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        return sqrt(variance) / mean
    }

    private fun median(values: List<Double>): Double {
        val s = values.sorted()
        val mid = s.size / 2
        return if (s.size % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2
    }

    private fun medianLong(values: List<Long>): Long {
        val s = values.sorted()
        val mid = s.size / 2
        return if (s.size % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2
    }
}
