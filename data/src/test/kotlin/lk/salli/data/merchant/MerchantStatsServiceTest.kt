package lk.salli.data.merchant

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import lk.salli.data.db.SalliDatabase
import lk.salli.data.db.entities.AccountEntity
import lk.salli.data.db.entities.TransactionEntity
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MerchantStatsServiceTest {

    private lateinit var db: SalliDatabase
    private lateinit var service: MerchantStatsService

    private val day = 24L * 60 * 60 * 1000
    private val base = 1_757_000_000_000L
    private var nextId = 1L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SalliDatabase::class.java,
        ).allowMainThreadQueries().build()
        service = MerchantStatsService(db)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun accounts(vararg hidden: Long) {
        db.backup().insertAccounts(
            (1L..3L).map { id ->
                AccountEntity(
                    id = id,
                    senderAddress = "BANK$id",
                    accountSuffix = "$id",
                    displayName = "Bank $id",
                    currency = "LKR",
                    accountTypeId = 0,
                    isHidden = id in hidden,
                )
            },
        )
    }

    private suspend fun tx(
        merchant: String?,
        amountMinor: Long,
        dayOffset: Long,
        accountId: Long = 1,
        currency: String = "LKR",
        flowId: Int = 0,
        declined: Boolean = false,
        hidden: Boolean = false,
        transferGroupId: Long? = null,
    ) {
        db.backup().insertTransactions(
            listOf(
                TransactionEntity(
                    id = nextId++,
                    accountId = accountId,
                    amountMinor = amountMinor,
                    amountCurrency = currency,
                    timestamp = base + dayOffset * day,
                    flowId = flowId,
                    methodId = 1,
                    typeId = 0,
                    merchantRaw = merchant,
                    isDeclined = declined,
                    isHidden = hidden,
                    transferGroupId = transferGroupId,
                    createdAt = base,
                    updatedAt = base,
                ),
            ),
        )
    }

    // ---------------------------------------------------------------- the happy path

    @Test
    fun `counts visits, total, average and the window they span`() = runBlocking<Unit> {
        accounts()
        tx("Keells Super", 428_000, dayOffset = 0)
        tx("Keells Super", 131_400, dayOffset = 5)
        tx("Keells Super", 281_000, dayOffset = 12)

        val stats = service.forMerchant("Keells Super")!!

        assertThat(stats.merchantKey).isEqualTo("Keells Super")
        assertThat(stats.count).isEqualTo(3)
        assertThat(stats.totalMinor).isEqualTo(840_400)
        assertThat(stats.avgMinor).isEqualTo(280_133)
        assertThat(stats.firstSeen).isEqualTo(base)
        assertThat(stats.lastSeen).isEqualTo(base + 12 * day)
        assertThat(stats.currency).isEqualTo("LKR")
    }

    @Test
    fun `an unseen merchant has no stats`() = runBlocking<Unit> {
        accounts()
        tx("Keells Super", 100_000, dayOffset = 0)

        assertThat(service.forMerchant("Arpico")).isNull()
    }

    @Test
    fun `a blank or missing merchant key is never looked up`() = runBlocking<Unit> {
        accounts()
        tx("Keells Super", 100_000, dayOffset = 0)

        assertThat(service.forMerchant(null)).isNull()
        assertThat(service.forMerchant("   ")).isNull()
    }

    // ---------------------------------------------------------------- normalisation

    @Test
    fun `case and surrounding whitespace do not split a merchant in two`() = runBlocking<Unit> {
        accounts()
        tx("Keells Super", 100_000, dayOffset = 0)
        tx("KEELLS SUPER", 200_000, dayOffset = 1)
        tx("  Keells Super  ", 300_000, dayOffset = 2)

        val stats = service.forMerchant("keells super")!!

        assertThat(stats.count).isEqualTo(3)
        assertThat(stats.totalMinor).isEqualTo(600_000)
    }

    @Test
    fun `a different branch stays a different merchant`() = runBlocking<Unit> {
        accounts()
        tx("Keells Super", 100_000, dayOffset = 0)
        tx("Keells Super Wattala", 900_000, dayOffset = 1)

        assertThat(service.forMerchant("Keells Super")!!.count).isEqualTo(1)
        assertThat(service.forMerchant("Keells Super Wattala")!!.count).isEqualTo(1)
    }

    // ---------------------------------------------------------------- what does not count

    @Test
    fun `declined, excluded, income and own-transfer rows never count`() = runBlocking<Unit> {
        accounts()
        tx("Keells Super", 100_000, dayOffset = 0)
        tx("Keells Super", 999_999, dayOffset = 1, declined = true)
        tx("Keells Super", 888_888, dayOffset = 2, hidden = true)
        tx("Keells Super", 777_777, dayOffset = 3, flowId = 1)
        tx("Keells Super", 666_666, dayOffset = 4, transferGroupId = 9)

        val stats = service.forMerchant("Keells Super")!!

        assertThat(stats.count).isEqualTo(1)
        assertThat(stats.totalMinor).isEqualTo(100_000)
        assertThat(stats.lastSeen).isEqualTo(base)
    }

    @Test
    fun `spending on an account hidden in Settings is left out`() = runBlocking<Unit> {
        accounts(hidden = longArrayOf(2L))
        tx("Keells Super", 100_000, dayOffset = 0, accountId = 1)
        tx("Keells Super", 555_555, dayOffset = 1, accountId = 2)

        val stats = service.forMerchant("Keells Super")!!

        assertThat(stats.count).isEqualTo(1)
        assertThat(stats.totalMinor).isEqualTo(100_000)
    }

    @Test
    fun `hiding every account that shopped there leaves no stats at all`() = runBlocking<Unit> {
        accounts(hidden = longArrayOf(1L, 2L, 3L))
        tx("Keells Super", 100_000, dayOffset = 0)

        assertThat(service.forMerchant("Keells Super")).isNull()
    }

    // ---------------------------------------------------------------- currencies

    @Test
    fun `mixed currencies report the dominant one instead of adding minor units`() = runBlocking<Unit> {
        accounts()
        tx("Steam", 500_000, dayOffset = 0)
        tx("Steam", 300_000, dayOffset = 1)
        tx("Steam", 1_999, dayOffset = 2, currency = "USD")

        val stats = service.forMerchant("Steam")!!

        assertThat(stats.currency).isEqualTo("LKR")
        assertThat(stats.count).isEqualTo(2)
        assertThat(stats.totalMinor).isEqualTo(800_000)
    }

    // ---------------------------------------------------------------- arithmetic

    @Test
    fun `the average truncates rather than rounding up`() = runBlocking<Unit> {
        accounts()
        tx("PickMe", 100, dayOffset = 0)
        tx("PickMe", 101, dayOffset = 1)

        assertThat(service.forMerchant("PickMe")!!.avgMinor).isEqualTo(100)
    }

    @Test
    fun `a single visit is its own average and both ends of the window`() = runBlocking<Unit> {
        accounts()
        tx("Barista", 145_000, dayOffset = 3)

        val stats = service.forMerchant("Barista")!!

        assertThat(stats.count).isEqualTo(1)
        assertThat(stats.avgMinor).isEqualTo(145_000)
        assertThat(stats.firstSeen).isEqualTo(stats.lastSeen)
    }
}
