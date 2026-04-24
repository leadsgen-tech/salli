package lk.salli.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import lk.salli.data.db.entities.AccountEntity
import lk.salli.data.db.entities.TransactionEntity
import lk.salli.data.mapper.toEntity
import lk.salli.data.mapper.toParsed
import lk.salli.domain.Currency
import lk.salli.domain.Money
import lk.salli.domain.TransactionFlow
import lk.salli.domain.TransactionMethod
import lk.salli.domain.TransactionType
import lk.salli.parser.ParsedTransaction
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SalliDatabaseTest {

    private lateinit var db: SalliDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SalliDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `insert account then lookup by sender and suffix`() = runBlocking {
        val id = db.accounts().insert(
            AccountEntity(
                senderAddress = "BOC",
                accountSuffix = "870",
                displayName = "BOC (870)",
                currency = Currency.LKR,
                accountTypeId = 0,
            ),
        )
        val found = db.accounts().findBySenderAndSuffix("BOC", "870")
        assertThat(found).isNotNull()
        assertThat(found!!.id).isEqualTo(id)
        assertThat(found.displayName).isEqualTo("BOC (870)")
    }

    @Test
    fun `insert transaction and read it back via toParsed roundtrip`() = runBlocking {
        val accountId = db.accounts().insert(
            AccountEntity(
                senderAddress = "BOC",
                accountSuffix = "870",
                displayName = "BOC (870)",
                currency = Currency.LKR,
                accountTypeId = 0,
            ),
        )

        val parsed = ParsedTransaction(
            senderAddress = "BOC",
            accountNumberSuffix = "870",
            amount = Money(5_000_000, Currency.LKR),
            balance = Money(9_000_000, Currency.LKR),
            fee = null,
            flow = TransactionFlow.EXPENSE,
            type = TransactionType.CEFT,
            merchantRaw = null,
            location = null,
            timestamp = 1_712_486_700_000L,
            isDeclined = false,
            rawBody = "CEFT Transfer Debit Rs 50,000.00 From A/C No XXXXXXXXXX870…",
        )

        val inserted = db.transactions().insert(
            parsed.toEntity(accountId = accountId, method = TransactionMethod.SMS, nowMillis = 42L),
        )

        val row = db.transactions().byId(inserted)
        assertThat(row).isNotNull()
        val projected = row!!.toParsed()
        assertThat(projected.amount).isEqualTo(parsed.amount)
        assertThat(projected.balance).isEqualTo(parsed.balance)
        assertThat(projected.flow).isEqualTo(parsed.flow)
        assertThat(projected.type).isEqualTo(parsed.type)
        assertThat(projected.accountNumberSuffix).isEqualTo("870")
    }

    @Test
    fun `recentFromSender returns rows within the time window only`() = runBlocking {
        val accountId = db.accounts().insert(
            AccountEntity(
                senderAddress = "BOC",
                accountSuffix = "870",
                displayName = "BOC",
                currency = Currency.LKR,
                accountTypeId = 0,
            ),
        )
        val now = 10_000_000_000L
        db.transactions().insert(tx(accountId, timestamp = now - 1000))
        db.transactions().insert(tx(accountId, timestamp = now - 1_000_000))
        db.transactions().insert(tx(accountId, timestamp = now - 10_000_000))

        val recent = db.transactions().recentFromSender("BOC", sinceTimestamp = now - 5_000_000)
        assertThat(recent).hasSize(2)
    }

    private fun tx(accountId: Long, timestamp: Long) = TransactionEntity(
        accountId = accountId,
        amountMinor = 100_000,
        amountCurrency = Currency.LKR,
        timestamp = timestamp,
        flowId = TransactionFlow.EXPENSE.id,
        methodId = TransactionMethod.SMS.id,
        typeId = TransactionType.POS.id,
        senderAddress = "BOC",
        accountSuffix = "870",
        rawBody = "",
        createdAt = timestamp,
        updatedAt = timestamp,
    )
}
