package lk.salli.data.ingest

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import lk.salli.data.db.SalliDatabase
import lk.salli.parser.utility.BillKind
import lk.salli.parser.utility.BillParser
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UtilityIngestorTest {

    private lateinit var db: SalliDatabase
    private lateinit var ingestor: UtilityIngestor

    private val issued = "- Customer name : Mr [NAME]\n- Home Telephone No : 0371234567\n- Invoice No : 26AUG_SLTNA0000000000_0000\n- Bill Period  : 2026-08-01 to 2026-08-31\n- Last month Bal : 12020.47\n- Payment Received : 6000.00\n- Charges for the Period  : 5932.44\n- Total Payable : 11952.91\n- Payment Due date : 2026-09-22\n\nPlease use https://sbill.slt.lk/ to view the bill.\nSLT-MOBITEL"
    private val issuedSeptember = issued.replace("2026-08-01 to 2026-08-31", "2026-09-01 to 2026-09-30")
        .replace("Total Payable : 11952.91", "Total Payable : 17885.35").replace("2026-09-22", "2026-10-22")
    private val reminder = "Dear customer,\nPlease settle your bill outstanding for SLT-MOBITEL Home no 0371234567 before 09.09.2026 to avoid the automated service disconnection. Your due amount up to month of July 2026 is Rs. 6020.47\nPay easily through https://billpay.slt.lk/. Ignore if already paid. Thank you"
    private val receipt = "Your bill payment Rs.6000.00 has been received to account number 0050000000 (94371234567) on 2026/09/12:09:30:00 PM.  Thank you."
    private val receiptRest = receipt.replace("Rs.6000.00", "Rs.5952.91").replace("09:30:00", "10:30:00")
    private val receiptRounded = receipt.replace("Rs.6000.00", "Rs.11950.00").replace("09:30:00", "08:30:00")
    private val receiptSeptemberFull = receipt.replace("Rs.6000.00", "Rs.17885.35").replace("09:30:00", "11:30:00")
    private val fuel = "National Fuel Pass: TRN confirmed.\n2026-09-08 06:02:23 ABC-1234\nQuota used: 8L\nWeekly Balance: 0.000L\nStation code: 102316\n(Resets on - 2026-09-13)"

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SalliDatabase::class.java)
            .allowMainThreadQueries().build()
        ingestor = UtilityIngestor(db, now = { 1_000L })
    }

    @After
    fun tearDown() = db.close()

    private suspend fun kind(sender: String, body: String, at: Long) =
        (ingestor.ingest(sender, body, at) as IngestResult.Utility).kind

    @Test
    fun `a statement stays authoritative over a later reminder`(): Unit = runBlocking {
        assertThat(kind("SLTBILL", issued, 10L)).isEqualTo("bill: stored")
        assertThat(kind("SLTMOBITEL", reminder, 20L)).isEqualTo("bill: reminder noted")
        val open = db.bills().openBills().single()
        assertThat(open.amountDueMinor).isEqualTo(1_195_291L)
        assertThat(open.kindId).isEqualTo(BillKind.ISSUED.id)
        assertThat(open.dueDate).isEqualTo(BillParser.parse("SLTBILL", issued)!!.dueDateMillis)
        assertThat(open.periodLabel).isEqualTo("2026-08-01 to 2026-08-31")
        // Recompute also repairs a row an older build overwrote with the reminder's figures.
        db.bills().update(open.copy(amountDueMinor = 602_047L, kindId = BillKind.REMINDER.id))
        ingestor.recomputeBillStatuses()
        assertThat(db.bills().openBills().single().amountDueMinor).isEqualTo(1_195_291L)
    }

    @Test
    fun `a partial payment keeps the rest due`(): Unit = runBlocking {
        ingestor.ingest("SLTBILL", issued, 10L)
        assertThat(kind("SLTBILL", receipt, 30L)).isEqualTo("bill: partly paid")
        val open = db.bills().openBills().single()
        assertThat(open.isPaid).isFalse()
        assertThat(open.paidAmountMinor).isEqualTo(600_000L)
        assertThat(open.amountDueMinor - open.paidAmountMinor!!).isEqualTo(595_291L)
        assertThat(db.bills().observePaidBills().first()).isEmpty()
    }

    @Test
    fun `a second payment that covers the balance closes the bill`(): Unit = runBlocking {
        ingestor.ingest("SLTBILL", issued, 10L)
        ingestor.ingest("SLTBILL", receipt, 30L)
        assertThat(kind("SLTBILL", receiptRest, 40L)).isEqualTo("bill: paid")
        assertThat(db.bills().openBills()).isEmpty()
        val paid = db.bills().observePaidBills().first().single()
        assertThat(paid.paidAmountMinor).isEqualTo(1_195_291L)
        assertThat(paid.paidAt).isEqualTo(40L)
    }

    @Test
    fun `a payment within rounding of the balance closes the bill`(): Unit = runBlocking {
        ingestor.ingest("SLTBILL", issued, 10L)
        assertThat(kind("SLTBILL", receiptRounded, 30L)).isEqualTo("bill: paid")
        assertThat(db.bills().openBills()).isEmpty()
    }

    @Test
    fun `a newer statement supersedes the older open bill`(): Unit = runBlocking {
        ingestor.ingest("SLTBILL", issued, 10L)
        ingestor.ingest("SLTBILL", issuedSeptember, 40L)
        assertThat(db.bills().openBills().single().periodLabel).isEqualTo("2026-09-01 to 2026-09-30")
        // Unpaid and superseded: neither open nor in the history.
        assertThat(db.bills().observePaidBills().first()).isEmpty()
        assertThat(db.bills().observeOpenCount().first()).isEqualTo(1)
    }

    @Test
    fun `a part-paid bill rolled into a new statement shows as carried forward`(): Unit = runBlocking {
        ingestor.ingest("SLTBILL", issued, 10L)
        ingestor.ingest("SLTBILL", receipt, 30L)
        ingestor.ingest("SLTBILL", issuedSeptember, 40L)
        assertThat(db.bills().openBills().single().periodLabel).isEqualTo("2026-09-01 to 2026-09-30")
        val history = db.bills().observePaidBills().first().single()
        assertThat(history.isSuperseded).isTrue()
        assertThat(history.paidAmountMinor).isEqualTo(600_000L)
    }

    @Test
    fun `replay repairs a legacy state and a second run changes nothing`(): Unit = runBlocking {
        ingestor.ingest("SLTBILL", issued, 10L)
        ingestor.ingest("SLTBILL", issuedSeptember, 40L)
        ingestor.ingest("SLTBILL", receiptSeptemberFull, 50L)
        db.bills().allByReceived().filter { it.amountDueMinor > 0 }.forEach {
            db.bills().update(it.copy(isPaid = false, isSuperseded = false, paidAt = null, paidAmountMinor = null))
        }
        assertThat(db.bills().openBills()).hasSize(2)
        assertThat(ingestor.recomputeBillStatuses()).isAtLeast(2)
        assertThat(db.bills().openBills()).isEmpty()
        val bills = db.bills().allByReceived().filter { it.amountDueMinor > 0 }
        assertThat(bills.single { it.periodLabel == "2026-08-01 to 2026-08-31" }.isSuperseded).isTrue()
        assertThat(bills.single { it.periodLabel == "2026-09-01 to 2026-09-30" }.isPaid).isTrue()
        assertThat(ingestor.recomputeBillStatuses()).isEqualTo(0)
    }

    @Test
    fun `a bill marked paid by hand stays paid through replay`(): Unit = runBlocking {
        ingestor.ingest("SLTBILL", issued, 10L)
        val id = db.bills().openBills().single().id
        db.bills().markPaidManually(id, paidAt = 99L, paidAmountMinor = 1_195_291L)
        assertThat(kind("SLTBILL", receipt, 130L)).isEqualTo("bill: payment with no open bill")
        ingestor.recomputeBillStatuses()
        val row = db.bills().byId(id)!!
        assertThat(row.isPaid).isTrue()
        assertThat(row.paidAt).isEqualTo(99L)
        assertThat(row.paidManually).isTrue()
    }

    @Test
    fun `a hand-marked bill from an older build is recognised and kept paid`(): Unit = runBlocking {
        ingestor.ingest("SLTBILL", issued, 10L)
        val row = db.bills().openBills().single()
        // Shape an older build left behind: paid, no receipt at that moment, no flag.
        db.bills().update(row.copy(isPaid = true, paidAt = 77L, paidAmountMinor = row.amountDueMinor))
        ingestor.recomputeBillStatuses()
        val after = db.bills().byId(row.id)!!
        assertThat(after.isPaid).isTrue()
        assertThat(after.paidManually).isTrue()
        assertThat(ingestor.recomputeBillStatuses()).isEqualTo(0)
    }

    @Test
    fun `a statement in credit is settled, not due`(): Unit = runBlocking {
        val credit = issued.replace("Total Payable : 11952.91", "Total Payable : -500.00")
        assertThat(kind("SLTBILL", credit, 10L)).isEqualTo("bill: in credit")
        assertThat(db.bills().openBills()).isEmpty()
        assertThat(db.bills().observePaidBills().first().single().amountDueMinor).isEqualTo(-50_000L)
    }

    @Test
    fun `a reminder with no statement yet stands in as the open bill`(): Unit = runBlocking {
        assertThat(kind("SLTMOBITEL", reminder, 20L)).isEqualTo("bill: stored")
        assertThat(db.bills().openBills().single().amountDueMinor).isEqualTo(602_047L)
    }

    @Test
    fun `a reminder with a new due date resets the reminder stage`(): Unit = runBlocking {
        ingestor.ingest("SLTMOBITEL", reminder, 20L)
        val row = db.bills().openBills().single()
        db.bills().setReminderStage(row.id, 2)
        val nextMonth = reminder.replace("before 09.09.2026", "before 09.10.2026").replace("July 2026", "August 2026")
        assertThat(kind("SLTMOBITEL", nextMonth, 30L)).isEqualTo("bill: updated")
        val after = db.bills().byId(row.id)!!
        assertThat(after.reminderStage).isEqualTo(0)
        assertThat(after.dueDate).isNotEqualTo(row.dueDate)
    }

    @Test
    fun `re-importing the same messages is a no-op`(): Unit = runBlocking {
        ingestor.ingest("SLTBILL", issued, 10L)
        ingestor.ingest("SLTMOBITEL", reminder, 20L)
        ingestor.ingest("SLTBILL", receipt, 30L)
        assertThat(kind("SLTBILL", issued, 10L)).isEqualTo("bill: duplicate")
        assertThat(kind("SLTMOBITEL", reminder, 20L)).isEqualTo("bill: duplicate")
        assertThat(kind("SLTBILL", receipt, 30L)).isEqualTo("bill: duplicate")
        assertThat(db.bills().openBills().single().paidAmountMinor).isEqualTo(600_000L)
    }

    @Test
    fun `promo from a bill sender stores nothing`(): Unit = runBlocking {
        assertThat(kind("SLTMOBITEL", "SLT-MOBITEL FIBRE! Introducing new unlimited packages.", 10L)).isEqualTo("bill: not a bill")
        assertThat(db.bills().observeAll().first()).isEmpty()
    }

    @Test
    fun `fuel pass fill-ups store once per vehicle and timestamp`(): Unit = runBlocking {
        assertThat(kind("1919", fuel, 10L)).isEqualTo("fuel-pass: stored")
        assertThat(kind("1919", fuel, 10L)).isEqualTo("fuel-pass: duplicate")
        val latest = db.fuelPass().latestPerVehicle()
        assertThat(latest).hasSize(1)
        assertThat(latest[0].vehicle).isEqualTo("ABC-1234")
        assertThat(latest[0].litresMilli).isEqualTo(8000)
        assertThat(db.fuelPass().observeVehicles().first()).containsExactly("ABC-1234")
    }

    @Test
    fun `bank ingestor hands utility senders over when wired`(): Unit = runBlocking {
        lk.salli.data.seed.Seeder(db).run()
        val bank = TransactionIngestor(
            db = db,
            categorizer = lk.salli.data.categorization.KeywordCategorizer(db.keywords()),
            typeCategorizer = lk.salli.data.categorization.TypeCategorizer(db.categories()),
            utilityIngestor = ingestor,
        )
        val r = bank.ingest("1919", fuel, 10L)
        assertThat(r).isInstanceOf(IngestResult.Utility::class.java)
        assertThat(db.fuelPass().observeCount().first()).isEqualTo(1)
    }
}
