package lk.salli.data.planning

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import lk.salli.data.db.SalliDatabase
import lk.salli.data.db.entities.AccountEntity
import lk.salli.data.db.entities.RecurringSeriesEntity
import lk.salli.data.db.entities.TransactionEntity
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecurringServiceTest {

    private val day = 24L * 60 * 60 * 1000
    private val t0 = 1_767_830_400_000L
    private lateinit var db: SalliDatabase
    private lateinit var service: RecurringService

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SalliDatabase::class.java)
            .allowMainThreadQueries().build()
        service = RecurringService(db) { t0 + 150 * day }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seedLease(hidden: Boolean = false) {
        db.backup().insertAccounts(listOf(AccountEntity(id = 1, senderAddress = "PeoplesBank", accountSuffix = "..68", displayName = "PB", currency = "LKR", accountTypeId = 0, isHidden = hidden)))
        db.backup().insertTransactions(
            listOf(0, 35, 74, 104, 137).mapIndexed { i, d ->
                TransactionEntity(id = 100L + i, accountId = 1, amountMinor = 4_502_500, amountCurrency = "LKR", timestamp = t0 + d * day, flowId = 0, methodId = 1, typeId = 4, merchantRaw = "LOLC Finance PLC", createdAt = 1L, updatedAt = 1L)
            },
        )
    }

    @Test
    fun `recompute stores a detected series and a second pass changes nothing`(): Unit = runBlocking {
        seedLease()
        assertThat(service.recompute()).isEqualTo(1)
        val row = db.recurring().all().single()
        assertThat(row.displayName).isEqualTo("LOLC Finance PLC")
        assertThat(row.cadence).isEqualTo("MONTHLY")
        assertThat(row.isFixed).isTrue()
        assertThat(row.userState).isEqualTo(RecurringSeriesEntity.AUTO)
        assertThat(service.recompute()).isEqualTo(0)
    }

    @Test
    fun `the user's choice survives recompute and a confirmed series is kept when it stops showing`(): Unit = runBlocking {
        seedLease()
        service.recompute()
        val id = db.recurring().all().single().id
        service.setUserState(id, RecurringSeriesEntity.CONFIRMED)
        service.recompute()
        assertThat(db.recurring().all().single().userState).isEqualTo(RecurringSeriesEntity.CONFIRMED)

        db.backup().deleteTransactions()
        service.recompute()
        val kept = db.recurring().all().single()
        assertThat(kept.isDetected).isFalse()
        assertThat(kept.userState).isEqualTo(RecurringSeriesEntity.CONFIRMED)
    }

    @Test
    fun `an untouched series that stops showing is removed`(): Unit = runBlocking {
        seedLease()
        service.recompute()
        db.backup().deleteTransactions()
        service.recompute()
        assertThat(db.recurring().all()).isEmpty()
    }

    @Test
    fun `hidden accounts are ignored`(): Unit = runBlocking {
        seedLease(hidden = true)
        service.recompute()
        assertThat(db.recurring().all()).isEmpty()
    }
}
