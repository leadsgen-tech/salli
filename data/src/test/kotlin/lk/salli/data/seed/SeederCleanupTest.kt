package lk.salli.data.seed

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import lk.salli.data.categorization.KeywordCategorizer
import lk.salli.data.categorization.TypeCategorizer
import lk.salli.data.db.SalliDatabase
import lk.salli.data.db.entities.AccountEntity
import lk.salli.data.db.entities.TransactionEntity
import lk.salli.data.ingest.TransactionIngestor
import lk.salli.domain.AccountType
import lk.salli.domain.TransactionFlow
import lk.salli.domain.TransactionMethod
import lk.salli.domain.TransactionType
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SeederCleanupTest {

    private lateinit var db: SalliDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SalliDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun row(accountId: Long, sender: String, body: String) = TransactionEntity(
        accountId = accountId, amountMinor = 100_000, amountCurrency = "LKR", timestamp = 1_000L,
        flowId = TransactionFlow.INCOME.id, methodId = TransactionMethod.SMS.id, typeId = TransactionType.ONLINE_TRANSFER.id,
        senderAddress = sender, rawBody = body, createdAt = 1L, updatedAt = 1L,
    )

    @Test
    fun `hnb payment notices booked by an older build are removed with their placeholder account`(): Unit = runBlocking {
        Seeder(db).run()
        val hnb = db.accounts().insert(
            AccountEntity(senderAddress = "HNB", accountSuffix = "—", displayName = "HNB", currency = "LKR", accountTypeId = AccountType.UNKNOWN.id, isHidden = true),
        )
        val boc = db.accounts().insert(
            AccountEntity(senderAddress = "BOC", accountSuffix = "870", displayName = "BOC (870)", currency = "LKR", accountTypeId = AccountType.UNKNOWN.id),
        )
        db.transactions().insert(row(hnb, "HNB", "You received LKR 1,000 from [NAME]\nDo not share OTP with anyone."))
        db.transactions().insert(row(boc, "BOC", "Online Transfer Credit Rs 1000.00 To A/C No XXXXXXXXXX870. Balance available Rs 5638.10 - Thank you for banking with BOC"))

        Seeder(db).run()

        assertThat(db.transactions().allForRecategorise().map { it.senderAddress }).containsExactly("BOC")
        assertThat(db.accounts().all().map { it.senderAddress }).containsExactly("BOC")
    }

    @Test
    fun `a fresh ingest of an hnb payment notice books nothing`(): Unit = runBlocking {
        Seeder(db).run()
        val ingestor = TransactionIngestor(db, KeywordCategorizer(db.keywords()), TypeCategorizer(db.categories()))
        ingestor.ingest("HNB", "You received LKR 15,000 from [NAME]\nDo not share this number with anyone", 1_000L)
        assertThat(db.transactions().allForRecategorise()).isEmpty()
        assertThat(db.accounts().all()).isEmpty()
    }
}
