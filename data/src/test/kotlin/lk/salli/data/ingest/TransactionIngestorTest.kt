package lk.salli.data.ingest

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import lk.salli.data.categorization.KeywordCategorizer
import lk.salli.data.categorization.TypeCategorizer
import lk.salli.data.db.SalliDatabase
import lk.salli.data.seed.Seeder
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * End-to-end ingestion tests driven by redacted SMS samples identical in shape to what the
 * user's phone receives in real life. Verifies that the sequence parser → detectors →
 * categorizer → Room actually produces the expected DB state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TransactionIngestorTest {

    private lateinit var db: SalliDatabase
    private lateinit var ingestor: TransactionIngestor

    // Anchor our synthetic clock at 2026-04-22 10:00 SL so templates that parse body
    // timestamps (PeoplesBank, ComBank) produce values close to `clock`; receivedAt-only
    // templates (BOC) see `clock` itself and the two stay within the detector's 48h window.
    private var clock: Long = LocalDateTime.of(2026, 4, 22, 10, 0, 0)
        .atZone(ZoneId.of("Asia/Colombo")).toInstant().toEpochMilli()

    @Before
    fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SalliDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        Seeder(db).run()
        ingestor = TransactionIngestor(
            db = db,
            categorizer = KeywordCategorizer(db.keywords()),
            typeCategorizer = TypeCategorizer(db.categories()),
            now = { clock },
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `BOC transaction SMS is parsed, categorized, and inserted with an auto-created account`() = runBlocking {
        val body = "Online Transfer Debit Rs 1,234.56 From A/C No XXXXXXXXXX999. Balance available Rs 9,876.54 - Thank you for banking with BOC"
        val result = ingestor.ingest(sender = "BOC", body = body, receivedAt = clock)

        assertThat(result).isInstanceOf(IngestResult.Inserted::class.java)
        val insertedId = (result as IngestResult.Inserted).transactionId
        val row = db.transactions().byId(insertedId)!!

        assertThat(row.amountMinor).isEqualTo(123456)
        assertThat(row.balanceMinor).isEqualTo(987654)
        assertThat(row.accountSuffix).isEqualTo("999")

        // Account auto-created with the balance reflected.
        val account = db.accounts().findBySenderAndSuffix("BOC", "999")
        assertThat(account).isNotNull()
        assertThat(account!!.balanceMinor).isEqualTo(987654)
    }

    @Test
    fun `exact BOC re-send within seconds is rejected as Duplicate`() = runBlocking {
        val body = "CEFT Transfer Debit Rs 500.25 From A/C No XXXXXXXXXX999. Balance available Rs 14,376.29 - Thank you for banking with BOC"
        val first = ingestor.ingest("BOC", body, receivedAt = clock)
        assertThat(first).isInstanceOf(IngestResult.Inserted::class.java)

        clock += 3_000 // +3s
        val second = ingestor.ingest("BOC", body, receivedAt = clock)
        assertThat(second).isInstanceOf(IngestResult.Duplicate::class.java)

        // Only one row in DB.
        val allBoc = db.transactions().recentFromSender("BOC", sinceTimestamp = 0L)
        assertThat(allBoc).hasSize(1)
    }

    @Test
    fun `PeoplesBank primary + Fund-transfer confirm merge into one transaction with fee`() = runBlocking {
        val primaryBody = "Dear Sir/Madam, Your A/C 280-2001****68 has been debited by Rs. 500.25 (LPAY Tfr @08:58 22/04/2026).[Av_Bal: Rs. 1,046.27 at the time of SMS generated]"
        val confirmBody = "Fund transfer  Successful. LKR 500.00 to LOLC Finance PLC Account 20810007495 on 2026-04-22 08:58:39. Call 1961"

        val r1 = ingestor.ingest("PeoplesBank", primaryBody, receivedAt = clock)
        assertThat(r1).isInstanceOf(IngestResult.Inserted::class.java)
        val primaryId = (r1 as IngestResult.Inserted).transactionId

        clock += 2_000
        val r2 = ingestor.ingest("PeoplesBank", confirmBody, receivedAt = clock)
        assertThat(r2).isInstanceOf(IngestResult.Merged::class.java)
        assertThat((r2 as IngestResult.Merged).transactionId).isEqualTo(primaryId)

        val merged = db.transactions().byId(primaryId)!!
        assertThat(merged.amountMinor).isEqualTo(50025)
        assertThat(merged.feeMinor).isEqualTo(25)
        assertThat(merged.merchantRaw).isEqualTo("LOLC Finance PLC")

        // Still only one row.
        val allPeoples = db.transactions().recentFromSender("PeoplesBank", 0L)
        assertThat(allPeoples).hasSize(1)
    }

    @Test
    fun `PeoplesBank primary without Av_Bal still merges and decrements cached balance`() = runBlocking {
        // Establish a baseline balance via an earlier credit that DID carry Av_Bal.
        val creditBody = "Dear Sir/Madam, Your A/C 280-2001****68 has been credited by Rs. 10000.00 (LPAY Tfr @13:53 27/04/2026).[Av_Bal: Rs. 10996.27 at the time of SMS generated]"
        val rCredit = ingestor.ingest("PeoplesBank", creditBody, receivedAt = clock)
        assertThat(rCredit).isInstanceOf(IngestResult.Inserted::class.java)

        clock += 24L * 60 * 60 * 1000 // next day
        // Today: confirm + primary for a fund transfer to Commercial Bank PLC. Primary has
        // NO `Av_Bal` block — the 2026 vintage People's Bank ships.
        val confirmBody = "Fund transfer  Successful. LKR 6500.00 to Commercial Bank PLC Account 8014912920 on 2026-04-28 00:21:46. Call 1961"
        val primaryBody = "Dear Sir/Madam, Your A/C 280-2001****68 has been debited by Rs. 6525.00 (LPAY Tfr @00:21 28/04/2026). Thank You- Inquiries Dial: 1961"

        val rConfirm = ingestor.ingest("PeoplesBank", confirmBody, receivedAt = clock)
        assertThat(rConfirm).isInstanceOf(IngestResult.Inserted::class.java)

        clock += 4_000
        val rPrimary = ingestor.ingest("PeoplesBank", primaryBody, receivedAt = clock)
        assertThat(rPrimary).isInstanceOf(IngestResult.Merged::class.java)

        // Exactly one transfer row in DB (besides the original credit).
        val allPeoples = db.transactions().recentFromSender("PeoplesBank", 0L)
        assertThat(allPeoples).hasSize(2)
        val transfer = allPeoples.first { it.merchantRaw == "Commercial Bank PLC" }
        assertThat(transfer.amountMinor).isEqualTo(652_500) // primary's fee-inclusive amount
        assertThat(transfer.feeMinor).isEqualTo(2_500)
        assertThat(transfer.balanceMinor).isNull()

        // Cached balance is the credit's Av_Bal (10996.27) MINUS the merged debit (6525.00) =
        // 4471.27, even though the debit SMS itself never carried a balance.
        val account = db.accounts().findBySenderAndSuffix("PeoplesBank", "280-2001..68")
        assertThat(account).isNotNull()
        assertThat(account!!.balanceMinor).isEqualTo(447_127)
    }

    @Test
    fun `BOC credit + PeoplesBank debit of the same amount get paired as an internal transfer`() = runBlocking {
        // PeoplesBank side — debit 500.25 (includes fee). Body time matches `clock` so the
        // BOC side (which has no date in its body and uses receivedAt) falls within the
        // detector's 48h window.
        val peoplesBody = "Dear Sir/Madam, Your A/C 280-2001****68 has been debited by Rs. 500.25 (LPAY Tfr @10:00 22/04/2026).[Av_Bal: Rs. 114.33 at the time of SMS generated]"
        // BOC side — credit 500.00 to own account
        val bocBody = "Transfer Credit Rs 500.00 To A/C No XXXXXXXXXX999. Balance available Rs 500.50 - Thank you for banking with BOC"

        val rPeoples = ingestor.ingest("PeoplesBank", peoplesBody, receivedAt = clock)
        assertThat(rPeoples).isInstanceOf(IngestResult.Inserted::class.java)

        clock += 10_000 // +10s
        val rBoc = ingestor.ingest("BOC", bocBody, receivedAt = clock)
        assertThat(rBoc).isInstanceOf(IngestResult.Paired::class.java)

        val paired = rBoc as IngestResult.Paired
        assertThat(paired.counterpartId).isEqualTo((rPeoples as IngestResult.Inserted).transactionId)

        // Both rows reclassified as TRANSFER.
        val allRecent = db.transactions().recentAll(0L)
        assertThat(allRecent).hasSize(2)
        assertThat(allRecent.all { it.transferGroupId == paired.groupId }).isTrue()
        assertThat(allRecent.all { it.flowId == lk.salli.domain.TransactionFlow.TRANSFER.id }).isTrue()
    }

    @Test
    fun `ComBank purchase at Keells auto-categorizes via seed keyword`() = runBlocking {
        val body = "Dear Cardholder, Purchase at KEELLS SUPER COLOMBO 04 for LKR 3,450.00 on 15/04/26 10:24 AM has been authorised on your debit card ending #4273."
        val result = ingestor.ingest("COMBANK", body, receivedAt = clock)

        assertThat(result).isInstanceOf(IngestResult.Inserted::class.java)
        val tx = db.transactions().byId((result as IngestResult.Inserted).transactionId)!!
        assertThat(tx.merchantRaw).isEqualTo("KEELLS SUPER COLOMBO 04")
        assertThat(tx.categoryId).isNotNull()

        // That category should be "Groceries" per seed data.
        val cat = db.categories().byId(tx.categoryId!!)
        assertThat(cat?.name).isEqualTo("Groceries")
    }

    @Test
    fun `ComBank declined attempt is stored with isDeclined flag and does NOT pair`() = runBlocking {
        val body = "Dear Cardholder, your card was declined due to insufficient funds. The attempted transaction amount was USD 15.99 at APPLE.COM/BILL SINGAPORE SG on 08/03/26 04:43 PM. Please check your balance and try again."
        val result = ingestor.ingest("COMBANK", body, receivedAt = clock)

        assertThat(result).isInstanceOf(IngestResult.Inserted::class.java)
        val tx = db.transactions().byId((result as IngestResult.Inserted).transactionId)!!
        assertThat(tx.isDeclined).isTrue()
        assertThat(tx.amountCurrency).isEqualTo("USD")
        assertThat(tx.amountMinor).isEqualTo(1599)
    }

    @Test
    fun `OTP SMS is dropped, nothing persisted`() = runBlocking {
        val body = "Dear user, Please use OTP  314815 to proceed your transaction."
        val result = ingestor.ingest("BOCONLINE", body, receivedAt = clock)
        assertThat(result).isInstanceOf(IngestResult.Dropped::class.java)
        assertThat(db.transactions().recentAll(0L)).isEmpty()
        assertThat(db.accounts().findBySenderAndSuffix("BOCONLINE", "—")).isNull()
    }

    @Test
    fun `unknown SMS from unregistered sender is Dropped (not queued)`() = runBlocking {
        val result = ingestor.ingest("RandomPromo", "50% off!", receivedAt = clock)
        assertThat(result).isInstanceOf(IngestResult.Dropped::class.java)
    }

    @Test
    fun `unparseable SMS from registered bank sender is Queued for review`() = runBlocking {
        val result = ingestor.ingest(
            sender = "BOC",
            body = "Some weird new format BOC just invented Rs 999",
            receivedAt = clock,
        )
        assertThat(result).isInstanceOf(IngestResult.Queued::class.java)
    }
}
