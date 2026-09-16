package lk.salli.data.planning

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.util.Calendar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import lk.salli.data.db.SalliDatabase
import lk.salli.data.db.entities.AccountEntity
import lk.salli.data.db.entities.BillEntity
import lk.salli.data.db.entities.GoalEntity
import lk.salli.data.db.entities.RecurringSeriesEntity
import lk.salli.data.db.entities.TransactionEntity
import lk.salli.data.prefs.SalliPreferences
import lk.salli.domain.planning.BudgetBasis
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlanningServiceTest {

    private lateinit var db: SalliDatabase
    private lateinit var prefs: SalliPreferences

    private fun local(month: Int, dayOfMonth: Int, year: Int = 2026) =
        Calendar.getInstance().apply { set(year, month, dayOfMonth, 10, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

    private val now = local(Calendar.SEPTEMBER, 14)
    private var nextTxId = 1L

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, SalliDatabase::class.java).allowMainThreadQueries().build()
        prefs = SalliPreferences(context)
        runBlocking {
            prefs.setMonthStartDay(1)
            prefs.setMonthlySpendingLimitMinor(null)
        }
    }

    @After
    fun tearDown() {
        db.close()
        runBlocking { prefs.setMonthlySpendingLimitMinor(null) }
    }

    private fun tx(account: Long, at: Long, amount: Long, flow: Int = 0, declined: Boolean = false, group: Long? = null) =
        TransactionEntity(id = nextTxId++, accountId = account, amountMinor = amount, amountCurrency = "LKR", timestamp = at, flowId = flow, methodId = 1, typeId = 0, transferGroupId = group, isDeclined = declined, createdAt = 1L, updatedAt = 1L)

    private fun series(name: String, state: String, confidence: Double, nextAt: Long, amount: Long) = RecurringSeriesEntity(
        seriesKey = name.lowercase(), displayName = name, flowId = 0, currency = "LKR", cadence = "MONTHLY", intervalDays = 30.0,
        typicalAmountMinor = amount, isFixed = true, occurrences = 4, lastAt = nextAt - 30L * 24 * 60 * 60 * 1000, nextAt = nextAt,
        status = "ACTIVE", confidence = confidence, userState = state, updatedAt = 1L,
    )

    private suspend fun seed() {
        val dao = db.backup()
        dao.insertAccounts(listOf(
            AccountEntity(id = 1, senderAddress = "BOC", accountSuffix = "870", displayName = "BOC (870)", currency = "LKR", accountTypeId = 0, balanceMinor = 1_000_000),
            AccountEntity(id = 2, senderAddress = "PeoplesBank", accountSuffix = "..68", displayName = "PB", currency = "LKR", accountTypeId = 0),
            AccountEntity(id = 3, senderAddress = "HNB", accountSuffix = "—", displayName = "HNB", currency = "LKR", accountTypeId = 0, balanceMinor = 9_000_000, isHidden = true),
        ))
        dao.insertTransactions(listOf(
            tx(1, local(Calendar.MAY, 20), 7_000), // history starts here
            tx(1, local(Calendar.JUNE, 10), 120_000),
            tx(1, local(Calendar.JULY, 15), 80_000),
            tx(2, local(Calendar.AUGUST, 15), 100_000),
            tx(1, local(Calendar.AUGUST, 20), 50_000, flow = 1), // income: not spending
            tx(1, local(Calendar.SEPTEMBER, 5), 5_000),
            tx(1, local(Calendar.SEPTEMBER, 6), 999_999, declined = true),
            tx(1, local(Calendar.SEPTEMBER, 7), 777_777, group = 9), // own-transfer leg
            tx(3, local(Calendar.SEPTEMBER, 8), 555_555), // hidden account
        ))
        db.bills().insert(BillEntity(biller = "SLT-MOBITEL", accountRef = "0371234567", amountDueMinor = 120_000, currency = "LKR", dueDate = local(Calendar.SEPTEMBER, 22), kindId = 0, paidAmountMinor = 20_000, rawBodyHash = "a", rawBody = "a", receivedAt = 1L, createdAt = 1L, updatedAt = 1L))
        db.bills().insert(BillEntity(biller = "CEB", accountRef = "1", amountDueMinor = 40_000, currency = "LKR", dueDate = local(Calendar.OCTOBER, 20), kindId = 0, rawBodyHash = "b", rawBody = "b", receivedAt = 1L, createdAt = 1L, updatedAt = 1L))
        db.recurring().insert(series("Fitness Hub", RecurringSeriesEntity.CONFIRMED, 0.5, local(Calendar.SEPTEMBER, 20), 10_000))
        db.recurring().insert(series("Netflix", RecurringSeriesEntity.AUTO, 0.5, local(Calendar.SEPTEMBER, 18), 3_000))
        db.recurring().insert(series("Old Gym", RecurringSeriesEntity.DISMISSED, 0.9, local(Calendar.SEPTEMBER, 19), 8_000))
        db.recurring().insert(series("SLT-MOBITEL", RecurringSeriesEntity.CONFIRMED, 0.9, local(Calendar.SEPTEMBER, 21), 12_000))
        db.goals().insertGoal(GoalEntity(name = "Trip", targetMinor = 400_000, currency = "LKR", targetDate = local(Calendar.DECEMBER, 20), createdAt = 1L))
        db.goals().insertGoal(GoalEntity(name = "Savings pot", targetMinor = 400_000, currency = "LKR", targetDate = local(Calendar.DECEMBER, 20), linkedAccountId = 1, createdAt = 1L))
    }

    @Test
    fun `safe to spend uses the median of complete cycles and every commitment`(): Unit = runBlocking {
        seed()
        val snap = PlanningService(db, prefs) { now }.observe().first()
        val s = snap.safeToSpend

        assertThat((s.basis as BudgetBasis.MedianOfCycles).cycles.map { it.spentMinor }).containsExactly(100_000L, 80_000L, 120_000L).inOrder()
        assertThat(s.budgetMinor).isEqualTo(100_000)
        assertThat(s.spentMinor).isEqualTo(5_000)
        assertThat(s.commitments.map { it.label }).containsExactly("Fitness Hub", "SLT-MOBITEL bill", "Goal: Trip").inOrder()
        assertThat(s.committedMinor).isEqualTo(10_000 + 100_000 + 100_000)
        assertThat(s.leftMinor).isEqualTo(100_000 - 5_000 - 210_000)
        assertThat(s.perDayMinor).isEqualTo(0)

        val r = snap.runway
        assertThat(r.balanceMinor).isEqualTo(1_000_000)
        assertThat(r.countedAccounts).containsExactly("BOC (870)")
        assertThat(r.notCountedAccounts).containsExactly("PB")
        assertThat(r.windowDays).isEqualTo(90)
        assertThat(r.avgDailySpendMinor).isEqualTo((80_000L + 100_000 + 5_000) / 90)
        assertThat(r.days).isEqualTo((1_000_000 / ((80_000L + 100_000 + 5_000) / 90)).toInt())
    }

    @Test
    fun `a user limit replaces the median`(): Unit = runBlocking {
        seed()
        prefs.setMonthlySpendingLimitMinor(500_000)
        val s = PlanningService(db, prefs) { now }.observe().first().safeToSpend
        assertThat(s.basis).isEqualTo(BudgetBasis.UserLimit(500_000))
        assertThat(s.leftMinor).isEqualTo(500_000 - 5_000 - 210_000)
        assertThat(s.perDayMinor).isEqualTo((500_000L - 5_000 - 210_000) / 17)
    }

    @Test
    fun `a cycle history only partly covers is left out of the median`(): Unit = runBlocking {
        db.backup().insertAccounts(listOf(AccountEntity(id = 1, senderAddress = "BOC", accountSuffix = "870", displayName = "BOC (870)", currency = "LKR", accountTypeId = 0)))
        db.backup().insertTransactions(listOf(tx(1, local(Calendar.JULY, 15), 80_000), tx(1, local(Calendar.AUGUST, 15), 100_000)))
        val s = PlanningService(db, prefs) { now }.observe().first().safeToSpend
        assertThat((s.basis as BudgetBasis.MedianOfCycles).cycles.map { it.spentMinor }).containsExactly(100_000L)
        assertThat(s.budgetMinor).isEqualTo(100_000)
    }
}
