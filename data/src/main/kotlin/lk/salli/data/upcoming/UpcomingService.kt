package lk.salli.data.upcoming

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import lk.salli.data.db.SalliDatabase
import lk.salli.data.db.entities.BillEntity
import lk.salli.data.db.entities.FuelPassRecordEntity
import lk.salli.data.db.entities.RecurringSeriesEntity
import lk.salli.domain.FuelPassRules
import lk.salli.domain.TransactionFlow

/** What an [UpcomingItem] is. The UI maps this to an icon and a string resource. */
enum class UpcomingKind {
    /** A utility bill with a due date, still open. */
    BILL,

    /** One expected charge of a repeating payment. */
    RECURRING,

    /** The next day this vehicle's plate is allowed to fill up. */
    FUEL_ELIGIBLE,

    /** The day the vehicle's weekly fuel quota resets, losing whatever is left. */
    FUEL_RESET,
}

/**
 * Colour intent, in the semantic vocabulary of the design tokens. Kept as an enum so `data/`
 * never reaches for a `Color`.
 */
enum class UpcomingTone { NEUTRAL, POSITIVE, WARNING, NEGATIVE }

/**
 * Routes an "Up next" row can open.
 *
 * These strings must stay equal to the corresponding constants in `lk.salli.app.nav.Route`;
 * `data/` cannot depend on `app/`, so they are duplicated here rather than imported.
 */
object UpcomingRoutes {
    const val BILLS = "bills"
    const val RECURRING = "recurring"
    const val FUEL_PASS = "fuel-pass"
}

/**
 * One thing that is going to happen. [title] is the user's own data (a biller, a merchant, a
 * number plate) and never chrome — the UI supplies the wording around it from [kind].
 */
data class UpcomingItem(
    val kind: UpcomingKind,
    val title: String,
    /** Null for items that are a date, not a payment (fuel pass days). */
    val amountMinor: Long?,
    val currency: String?,
    /** Days since the epoch, in [UpcomingService]'s timezone. */
    val dueEpochDay: Long,
    val tone: UpcomingTone,
    val deepLink: String,
)

/**
 * The single source for Home's "Up next" section, Plan's full list and Plan's 14-day ribbon.
 *
 * Merges three streams into one date-ordered list:
 *
 *  - **Bills** still open and carrying a due date. Overdue bills are kept and sort first — the
 *    thing you most need to see is the one you already missed.
 *  - **Repeating payments** the user confirmed, or that are fixed and confident enough to bet
 *    on, expanded into one row per expected charge inside the window.
 *  - **Fuel Pass** days: the next day each vehicle's plate is eligible, and the day its weekly
 *    quota resets.
 *
 * A repeating payment that is really this period's bill payment is dropped — paying SLT from
 * People's shows up as both a bill and a series, and listing it twice would double the number
 * the user reads. Same rule as `PlanningService`, kept in [UpcomingRules] so there is one place
 * to change it.
 *
 * **Hidden accounts.** None of the three sources carries a bank-account column: bills and fuel
 * records belong to a biller or a vehicle, and recurring series are already built from
 * transactions that `RecurringService.recompute` filtered by hidden account. So there is no
 * further account filtering to apply here, and inventing one would drop rows the user still owes.
 */
class UpcomingService(
    private val db: SalliDatabase,
    /** Sri Lanka, matching Bills, Fuel Pass and the reminder worker — never the UTC day. */
    private val zone: ZoneId = ZoneId.of("Asia/Colombo"),
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    fun observe(days: Int = DEFAULT_WINDOW_DAYS): Flow<List<UpcomingItem>> = combine(
        db.bills().observeOpenBills(),
        db.recurring().observeAll(),
        db.fuelPass().observeAll(),
    ) { bills, series, fuel ->
        build(bills = bills, series = series, fuel = fuel, days = days)
    }

    private fun build(
        bills: List<BillEntity>,
        series: List<RecurringSeriesEntity>,
        fuel: List<FuelPassRecordEntity>,
        days: Int,
    ): List<UpcomingItem> {
        val now = clock()
        val today = localDate(now)
        val todayDay = today.toEpochDay()
        // Half-open: `days = 14` means today plus the next 13 days.
        val lastDay = todayDay + days.coerceAtLeast(1) - 1
        val earliestDay = todayDay - OVERDUE_GRACE_DAYS

        val items = ArrayList<UpcomingItem>()
        val billWords = HashSet<String>()

        for (bill in bills) {
            val dueDay = bill.dueDate?.let { epochDay(it) } ?: continue
            if (dueDay > lastDay || dueDay < earliestDay) continue
            val owed = (bill.amountDueMinor - (bill.paidAmountMinor ?: 0L)).coerceAtLeast(0L)
            if (owed <= 0L) continue
            billWords += UpcomingRules.words(bill.biller)
            items += UpcomingItem(
                kind = UpcomingKind.BILL,
                title = bill.biller,
                amountMinor = owed,
                currency = bill.currency,
                dueEpochDay = dueDay,
                tone = billTone(dueDay - todayDay),
                deepLink = UpcomingRoutes.BILLS,
            )
        }

        for (s in series) {
            if (!UpcomingRules.isExpected(s)) continue
            if (UpcomingRules.words(s.displayName).any { it in billWords }) continue
            var at = s.nextAt ?: continue
            val stepMs = UpcomingRules.stepMillis(s.intervalDays ?: continue)
            // `nextAt` goes stale between recomputes. Roll it forward so at most one charge is
            // in the past — otherwise a monthly series last seen in April would stack four
            // identical rows on today.
            if (at + stepMs <= now) at += ((now - at) / stepMs) * stepMs
            var emitted = 0
            while (emitted < MAX_OCCURRENCES_PER_SERIES) {
                val day = epochDay(at)
                if (day > lastDay) break
                // A charge that slipped a few days is still coming; show it on today rather
                // than in the past, where the ribbon would never draw it.
                items += UpcomingItem(
                    kind = UpcomingKind.RECURRING,
                    title = s.displayName,
                    amountMinor = s.typicalAmountMinor,
                    currency = s.currency,
                    dueEpochDay = maxOf(day, todayDay),
                    tone = UpcomingTone.NEUTRAL,
                    deepLink = UpcomingRoutes.RECURRING,
                )
                emitted++
                at += stepMs
            }
        }

        // Newest record per canonical vehicle carries the live balance and reset date. A plate
        // can arrive with different casing or stray spacing across SMS vintages, so normalise
        // before grouping instead of rendering the same vehicle several times.
        val currentFuel = mutableMapOf<String, FuelPassRecordEntity>()
        for (record in fuel) {
            val vehicle = FuelPassRules.canonicalVehicle(record.vehicle)
            val current = currentFuel[vehicle]
            if (current == null || record.timestamp > current.timestamp) {
                currentFuel[vehicle] = record
            }
        }
        for ((vehicle, record) in currentFuel) {
            if (record.weeklyBalanceMilli <= 0L) continue
            val resetsOn = record.resetsOn?.let { localDate(it) }
            // A quota that has already rolled over tells us nothing until the next fill-up SMS.
            if (resetsOn != null && !today.isBefore(resetsOn)) continue

            val eligibleDay = UpcomingRules.nextEligibleDay(vehicle, today, resetsOn)
            if (eligibleDay != null) {
                val day = eligibleDay
                val epoch = day.toEpochDay()
                if (epoch <= lastDay) {
                    items += UpcomingItem(
                        kind = UpcomingKind.FUEL_ELIGIBLE,
                        title = vehicle,
                        amountMinor = null,
                        currency = null,
                        dueEpochDay = epoch,
                        tone = UpcomingTone.POSITIVE,
                        deepLink = UpcomingRoutes.FUEL_PASS,
                    )
                }
            } else {
                // The eligible day is the useful action. Only fall back to the reset deadline
                // when no eligible day exists before it, so Home and Plan never show two rows
                // with the same number plate.
                val resetDay = resetsOn?.toEpochDay() ?: continue
                if (resetDay > lastDay) continue
                items += UpcomingItem(
                    kind = UpcomingKind.FUEL_RESET,
                    title = vehicle,
                    amountMinor = null,
                    currency = null,
                    dueEpochDay = resetDay,
                    // A deadline, not an opportunity: whatever is left of the weekly quota is
                    // gone on this day. Sharing the eligible row's lime would paint two
                    // opposite meanings the same colour.
                    tone = UpcomingTone.WARNING,
                    deepLink = UpcomingRoutes.FUEL_PASS,
                )
            }
        }

        // Soonest first; within a day the money comes before the reminders, biggest first, and
        // the title settles any remaining tie so the list never reshuffles between emissions.
        return items.sortedWith(
            compareBy<UpcomingItem> { it.dueEpochDay }
                .thenBy { it.kind.ordinal }
                .thenByDescending { it.amountMinor ?: 0L }
                .thenBy { it.title },
        )
    }

    /** `LocalDate.ofInstant` is Java 9; API 26's java.time is Java 8, so go the long way. */
    private fun localDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    private fun epochDay(millis: Long): Long = localDate(millis).toEpochDay()

    private fun billTone(daysAway: Long): UpcomingTone = when {
        daysAway < 0 -> UpcomingTone.NEGATIVE
        daysAway <= BILL_WARNING_DAYS -> UpcomingTone.WARNING
        else -> UpcomingTone.NEUTRAL
    }

    companion object {
        /** Matches the Plan ribbon. Home shows the first three of the same list. */
        const val DEFAULT_WINDOW_DAYS = 14

        /** "Due soon" turns the row amber from here in. */
        const val BILL_WARNING_DAYS = 3L

        /**
         * How far back an unpaid bill still counts as "up next". A month past due it has stopped
         * being news and lives on the Bills screen instead — without this, a year of imported
         * history whose payments were made in cash would pin stale rows to the top forever.
         */
        const val OVERDUE_GRACE_DAYS = 30L

        /** A daily series over a long window would otherwise flood the list. */
        const val MAX_OCCURRENCES_PER_SERIES = 8
    }
}

/**
 * The rules deciding what counts as "coming up".
 *
 * These mirror `PlanningService.countsAsCommitment` and its biller/series de-duplication so the
 * Plan hero ("spoken for this period") and the list under it can never disagree. When
 * `PlanningService` is next touched it should delegate here rather than keep its own copy.
 */
object UpcomingRules {

    /** Same bar `PlanningService` uses before it will bet money on a detected series. */
    const val HIGH_CONFIDENCE = 0.7

    private const val DAY_MS = 24L * 60 * 60 * 1000

    private val genericWords = setOf("bill", "bank", "plc", "ltd", "the", "and", "pvt", "limited")

    private val ACTIVE_STATUSES = setOf("ACTIVE", "DUE_SOON")

    /**
     * True when a repeating expense is worth putting on the calendar: the user confirmed it, or
     * it is a fixed amount the detector is confident about. A dismissed series, one that stopped
     * showing up, or one the detector can't date is never expected.
     */
    fun isExpected(s: RecurringSeriesEntity): Boolean =
        s.flowId == TransactionFlow.EXPENSE.id &&
            s.isDetected &&
            s.userState != RecurringSeriesEntity.DISMISSED &&
            s.nextAt != null &&
            s.intervalDays != null &&
            s.status in ACTIVE_STATUSES &&
            (s.userState == RecurringSeriesEntity.CONFIRMED || (s.isFixed && s.confidence >= HIGH_CONFIDENCE))

    /**
     * Distinctive words in a name, used to spot a series that is really a bill payment.
     *
     * Known blunt edge, inherited from `PlanningService`: matching on *any* shared word means a
     * "Dialog" broadband bill also suppresses an unrelated "DIALOG AXIATA" mobile auto-debit.
     * Deliberately not tightened here — the Plan hero reuses `PlanningService`'s "spoken for"
     * total, and a stricter rule on this side alone would make the list and the number above it
     * disagree. Both should gain an amount/account check together.
     */
    fun words(text: String): Set<String> = text
        .lowercase()
        .split(Regex("""[^a-z]+"""))
        .filter { it.length >= 3 && it !in genericWords }
        .toSet()

    fun stepMillis(intervalDays: Double): Long = (intervalDays * DAY_MS).toLong().coerceAtLeast(DAY_MS)

    /**
     * The next day [vehicle] may fill up, counting today. Null when the plate has no digit (every
     * day is eligible, so there is nothing to announce) or when the quota resets first.
     */
    fun nextEligibleDay(vehicle: String, today: LocalDate, resetsOn: LocalDate?): LocalDate? {
        if (FuelPassRules.lastDigit(vehicle) == null) return null
        // Parity is on the day *of the month*, so it usually alternates daily — but a month can
        // end on the 31st and start again on the 1st, two odd days in a row (and 29 Feb → 1 Mar).
        // Three tries covers that; two would silently drop the row seven days a year.
        // `FuelPassRules.lastEligibleDayBefore` guards the same way for the same reason.
        var day = today
        repeat(3) {
            if (FuelPassRules.isEligible(vehicle, day)) {
                return if (resetsOn != null && !day.isBefore(resetsOn)) null else day
            }
            day = day.plusDays(1)
        }
        return null
    }
}
