package lk.salli.data.upcoming

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import lk.salli.data.db.SalliDatabase
import lk.salli.data.db.entities.BillEntity
import lk.salli.data.db.entities.FuelPassRecordEntity
import lk.salli.data.db.entities.RecurringSeriesEntity
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpcomingServiceTest {

    private lateinit var db: SalliDatabase

    private val zone = ZoneId.of("Asia/Colombo")

    /** Monday 14 September 2026, mid-morning in Colombo. */
    private val today: LocalDate = LocalDate.of(2026, 9, 14)
    private val now: Long = today.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()

    private fun millisOn(date: LocalDate): Long =
        date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()

    private fun day(offset: Long): LocalDate = today.plusDays(offset)

    private var nextHash = 0

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SalliDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun service(days: Int = UpcomingService.DEFAULT_WINDOW_DAYS) =
        UpcomingService(db = db, zone = zone, clock = { now }) to days

    private suspend fun items(days: Int = UpcomingService.DEFAULT_WINDOW_DAYS): List<UpcomingItem> {
        val (svc, window) = service(days)
        return svc.observe(window).first()
    }

    // ---------------------------------------------------------------- fixtures

    private suspend fun bill(
        biller: String,
        due: LocalDate?,
        amountMinor: Long = 1_195_300,
        paidMinor: Long? = null,
        currency: String = "LKR",
        isPaid: Boolean = false,
    ) {
        db.bills().insert(
            BillEntity(
                biller = biller,
                accountRef = "ref-$biller",
                amountDueMinor = amountMinor,
                currency = currency,
                dueDate = due?.let { millisOn(it) },
                kindId = 0,
                isPaid = isPaid,
                paidAmountMinor = paidMinor,
                rawBodyHash = "hash-${nextHash++}",
                rawBody = "body",
                receivedAt = now,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private suspend fun series(
        name: String,
        next: LocalDate,
        intervalDays: Double = 30.0,
        amountMinor: Long = 420_000,
        userState: String = RecurringSeriesEntity.CONFIRMED,
        status: String = "ACTIVE",
        confidence: Double = 0.9,
        isFixed: Boolean = true,
        isDetected: Boolean = true,
        flowId: Int = 0,
    ) {
        db.recurring().insert(
            RecurringSeriesEntity(
                seriesKey = name.lowercase(),
                displayName = name,
                flowId = flowId,
                currency = "LKR",
                cadence = "MONTHLY",
                intervalDays = intervalDays,
                typicalAmountMinor = amountMinor,
                isFixed = isFixed,
                occurrences = 5,
                lastAt = millisOn(next.minusDays(intervalDays.toLong())),
                nextAt = millisOn(next),
                status = status,
                confidence = confidence,
                userState = userState,
                isDetected = isDetected,
                updatedAt = now,
            ),
        )
    }

    private suspend fun fuel(
        vehicle: String,
        at: LocalDate = today.minusDays(2),
        balanceMilli: Long = 12_000,
        resetsOn: LocalDate? = today.plusDays(4),
    ) {
        db.fuelPass().insert(
            FuelPassRecordEntity(
                vehicle = vehicle,
                litresMilli = 8_000,
                weeklyBalanceMilli = balanceMilli,
                timestamp = millisOn(at),
                resetsOn = resetsOn?.let { millisOn(it) },
                rawBody = "body",
                createdAt = now,
            ),
        )
    }

    // ---------------------------------------------------------------- empty

    @Test
    fun `nothing stored means nothing upcoming`() = runBlocking<Unit> {
        assertThat(items()).isEmpty()
    }

    // ---------------------------------------------------------------- bills

    @Test
    fun `an open bill inside the window shows what is still owed`() = runBlocking<Unit> {
        bill("SLT", due = day(3), amountMinor = 1_195_300, paidMinor = 195_300)

        val item = items().single()

        assertThat(item.kind).isEqualTo(UpcomingKind.BILL)
        assertThat(item.title).isEqualTo("SLT")
        assertThat(item.amountMinor).isEqualTo(1_000_000)
        assertThat(item.currency).isEqualTo("LKR")
        assertThat(item.dueEpochDay).isEqualTo(day(3).toEpochDay())
        assertThat(item.deepLink).isEqualTo(UpcomingRoutes.BILLS)
    }

    @Test
    fun `bill tone hardens as the due date approaches`() = runBlocking<Unit> {
        bill("Far", due = day(10))
        bill("Soon", due = day(3))
        bill("Today", due = day(0))
        bill("Late", due = day(-5))

        val byTitle = items().associateBy { it.title }

        assertThat(byTitle.getValue("Far").tone).isEqualTo(UpcomingTone.NEUTRAL)
        assertThat(byTitle.getValue("Soon").tone).isEqualTo(UpcomingTone.WARNING)
        assertThat(byTitle.getValue("Today").tone).isEqualTo(UpcomingTone.WARNING)
        assertThat(byTitle.getValue("Late").tone).isEqualTo(UpcomingTone.NEGATIVE)
    }

    @Test
    fun `overdue bills are kept and sort first`() = runBlocking<Unit> {
        bill("Late", due = day(-5))
        bill("Soon", due = day(1))

        assertThat(items().map { it.title }).containsExactly("Late", "Soon").inOrder()
    }

    @Test
    fun `bills beyond the window, fully paid or undated are left out`() = runBlocking<Unit> {
        bill("Beyond", due = day(20))
        bill("Settled", due = day(2), amountMinor = 500_000, paidMinor = 500_000)
        bill("Undated", due = null)
        bill("Closed", due = day(2), isPaid = true)

        assertThat(items()).isEmpty()
    }

    @Test
    fun `a bill overdue past the grace period stops being up next`() = runBlocking<Unit> {
        bill("Recent", due = day(-29))
        bill("Ancient", due = day(-31))

        assertThat(items().map { it.title }).containsExactly("Recent")
    }

    @Test
    fun `a shorter window is honoured`() = runBlocking<Unit> {
        bill("Day two", due = day(2))
        bill("Day nine", due = day(9))

        assertThat(items(days = 3).map { it.title }).containsExactly("Day two")
    }

    // ---------------------------------------------------------------- recurring

    @Test
    fun `a confirmed series is expected on its next charge date`() = runBlocking<Unit> {
        series("Netflix", next = day(4))

        val item = items().single()

        assertThat(item.kind).isEqualTo(UpcomingKind.RECURRING)
        assertThat(item.title).isEqualTo("Netflix")
        assertThat(item.amountMinor).isEqualTo(420_000)
        assertThat(item.tone).isEqualTo(UpcomingTone.NEUTRAL)
        assertThat(item.dueEpochDay).isEqualTo(day(4).toEpochDay())
        assertThat(item.deepLink).isEqualTo(UpcomingRoutes.RECURRING)
    }

    @Test
    fun `a weekly series lands once per charge inside the window`() = runBlocking<Unit> {
        series("Gym", next = day(1), intervalDays = 7.0)

        val days = items().map { it.dueEpochDay }

        assertThat(days).containsExactly(
            day(1).toEpochDay(),
            day(8).toEpochDay(),
        ).inOrder()
    }

    @Test
    fun `an unconfirmed series is expected only when fixed and confident`() = runBlocking<Unit> {
        series("Guess", next = day(2), userState = RecurringSeriesEntity.AUTO, confidence = 0.4)
        series("Sure", next = day(2), userState = RecurringSeriesEntity.AUTO, confidence = 0.9)
        series("Variable", next = day(2), userState = RecurringSeriesEntity.AUTO, confidence = 0.9, isFixed = false)

        assertThat(items().map { it.title }).containsExactly("Sure")
    }

    @Test
    fun `dismissed, undetected, paused and incoming series are never expected`() = runBlocking<Unit> {
        series("Dismissed", next = day(2), userState = RecurringSeriesEntity.DISMISSED)
        series("Gone", next = day(2), isDetected = false)
        series("Failing", next = day(2), status = "FAILING")
        series("Salary", next = day(2), flowId = 1)

        assertThat(items()).isEmpty()
    }

    @Test
    fun `a stale next charge shows once on today rather than stacking in the past`() = runBlocking<Unit> {
        // Detector last ran in May: nextAt is four monthly charges behind.
        series("Fitness Hub", next = day(-120), intervalDays = 30.0)

        val days = items().map { it.dueEpochDay }

        // One charge, today — not four rows for the four intervals that already went by.
        assertThat(days).containsExactly(today.toEpochDay())
    }

    @Test
    fun `a stale weekly series resumes on its own rhythm after the caught-up charge`() = runBlocking<Unit> {
        series("Cleaner", next = day(-30), intervalDays = 7.0)

        val days = items().map { it.dueEpochDay }

        assertThat(days).containsExactly(
            today.toEpochDay(),
            day(5).toEpochDay(),
            day(12).toEpochDay(),
        ).inOrder()
    }

    @Test
    fun `a series that is really the bill payment is not listed twice`() = runBlocking<Unit> {
        bill("SLT-MOBITEL", due = day(5))
        series("SLT-MOBITEL", next = day(6))
        series("Netflix", next = day(6))

        assertThat(items().map { it.title }).containsExactly("SLT-MOBITEL", "Netflix").inOrder()
    }

    // ---------------------------------------------------------------- fuel pass

    @Test
    fun `an even plate produces one actionable eligible row`() = runBlocking<Unit> {
        // 14 September is even; BAM-0786 ends in 6.
        fuel("BAM-0786", resetsOn = day(4))

        val rows = items()
        val byKind = rows.associateBy { it.kind }

        assertThat(rows).hasSize(1)
        assertThat(byKind.getValue(UpcomingKind.FUEL_ELIGIBLE).dueEpochDay).isEqualTo(today.toEpochDay())
        assertThat(byKind.getValue(UpcomingKind.FUEL_ELIGIBLE).tone).isEqualTo(UpcomingTone.POSITIVE)
        assertThat(byKind.getValue(UpcomingKind.FUEL_ELIGIBLE).title).isEqualTo("BAM-0786")
        assertThat(byKind.getValue(UpcomingKind.FUEL_ELIGIBLE).amountMinor).isNull()
        assertThat(byKind.getValue(UpcomingKind.FUEL_ELIGIBLE).deepLink).isEqualTo(UpcomingRoutes.FUEL_PASS)
    }

    @Test
    fun `an odd plate waits for tomorrow`() = runBlocking<Unit> {
        fuel("CAB-1233")

        val eligible = items().first { it.kind == UpcomingKind.FUEL_ELIGIBLE }

        assertThat(eligible.dueEpochDay).isEqualTo(day(1).toEpochDay())
    }

    @Test
    fun `an empty quota or a reset already past says nothing`() = runBlocking<Unit> {
        fuel("BAM-0786", balanceMilli = 0)
        fuel("CAB-1233", resetsOn = day(-1))

        assertThat(items()).isEmpty()
    }

    @Test
    fun `only the newest record per vehicle counts`() = runBlocking<Unit> {
        fuel("BAM-0786", at = today.minusDays(9), balanceMilli = 20_000, resetsOn = today.minusDays(2))
        fuel("BAM-0786", at = today.minusDays(1), balanceMilli = 9_000, resetsOn = day(5))

        assertThat(items().map { it.kind }).containsExactly(UpcomingKind.FUEL_ELIGIBLE)
    }

    @Test
    fun `a reset fallback reads as a deadline not an opportunity`() = runBlocking<Unit> {
        fuel("GOV", resetsOn = day(4))

        val reset = items().first { it.kind == UpcomingKind.FUEL_RESET }

        assertThat(reset.tone).isEqualTo(UpcomingTone.WARNING)
    }

    @Test
    fun `plate casing and whitespace do not create duplicate upcoming rows`() = runBlocking<Unit> {
        fuel(" bam-0786 ", at = today.minusDays(2), resetsOn = day(4))
        fuel("BAM-0786", at = today.minusDays(1), resetsOn = day(5))

        assertThat(items().map { it.title }).containsExactly("BAM-0786")
    }

    @Test
    fun `an eligible day is still found when a month ends on two odd days`() = runBlocking<Unit> {
        // 31 October and 1 November are both odd, so an even plate has to wait until the 2nd.
        // A two-day search would give up here and drop the row entirely.
        val onThe31st = UpcomingService(
            db = db,
            zone = zone,
            clock = { LocalDate.of(2026, 10, 31).atTime(10, 0).atZone(zone).toInstant().toEpochMilli() },
        )
        fuel("BAM-0786", at = LocalDate.of(2026, 10, 30), resetsOn = LocalDate.of(2026, 11, 6))

        val eligible = onThe31st.observe().first().first { it.kind == UpcomingKind.FUEL_ELIGIBLE }

        assertThat(eligible.dueEpochDay).isEqualTo(LocalDate.of(2026, 11, 2).toEpochDay())
    }

    @Test
    fun `a plate with no digit announces nothing to schedule`() = runBlocking<Unit> {
        fuel("GOV", resetsOn = day(3))

        assertThat(items().map { it.kind }).containsExactly(UpcomingKind.FUEL_RESET)
    }

    // ---------------------------------------------------------------- merge & order

    @Test
    fun `everything merges into one list ordered by day then by money first`() = runBlocking<Unit> {
        bill("SLT", due = day(3), amountMinor = 1_195_300)
        series("Netflix", next = day(3), amountMinor = 420_000)
        fuel("BAM-0786", resetsOn = day(3))

        val merged = items()

        assertThat(merged.map { it.kind }).containsExactly(
            UpcomingKind.FUEL_ELIGIBLE, // today
            UpcomingKind.BILL,
            UpcomingKind.RECURRING,
        ).inOrder()
        assertThat(merged.map { it.dueEpochDay }).isInOrder()
    }

    @Test
    fun `the list is stable regardless of insertion order`() = runBlocking<Unit> {
        bill("Aqua", due = day(2), amountMinor = 100_000)
        bill("Zed", due = day(2), amountMinor = 100_000)

        assertThat(items().map { it.title }).containsExactly("Aqua", "Zed").inOrder()
    }
}
