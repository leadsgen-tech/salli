package lk.salli.data.widget

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.util.Calendar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import lk.salli.data.db.SalliDatabase
import lk.salli.data.db.entities.AccountEntity
import lk.salli.data.db.entities.TransactionEntity
import lk.salli.data.planning.PlanningService
import lk.salli.data.prefs.SalliPreferences
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetSummaryServiceTest {

    private lateinit var db: SalliDatabase
    private lateinit var prefs: SalliPreferences
    private lateinit var planning: PlanningService
    private lateinit var service: WidgetSummaryService

    private fun local(month: Int, dayOfMonth: Int, hour: Int = 10, year: Int = 2026) =
        Calendar.getInstance().apply { set(year, month, dayOfMonth, hour, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

    private val now = local(Calendar.SEPTEMBER, 14)
    private var nextTxId = 1L

    /** Shows the raw numbers so assertions read without locale formatting in the way. */
    private val plain: (lk.salli.domain.Money) -> String = { "${it.currency} ${it.minorUnits}" }

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, SalliDatabase::class.java).allowMainThreadQueries().build()
        prefs = SalliPreferences(context)
        planning = PlanningService(db, prefs) { now }
        service = WidgetSummaryService(db, prefs, planning) { now }
        runBlocking {
            prefs.setMonthStartDay(1)
            prefs.setMonthlySpendingLimitMinor(null)
            prefs.setWidgetHideAmounts(false)
        }
    }

    @After
    fun tearDown() {
        db.close()
        runBlocking {
            prefs.setMonthStartDay(1)
            prefs.setMonthlySpendingLimitMinor(null)
            prefs.setWidgetHideAmounts(false)
        }
    }

    private fun tx(
        account: Long,
        at: Long,
        amount: Long,
        flow: Int = 0,
        currency: String = "LKR",
        declined: Boolean = false,
        group: Long? = null,
    ) = TransactionEntity(
        id = nextTxId++, accountId = account, amountMinor = amount, amountCurrency = currency, timestamp = at,
        flowId = flow, methodId = 1, typeId = 0, transferGroupId = group, isDeclined = declined, createdAt = 1L, updatedAt = 1L,
    )

    private suspend fun seed() {
        db.backup().insertAccounts(listOf(
            AccountEntity(id = 1, senderAddress = "BOC", accountSuffix = "870", displayName = "BOC (870)", currency = "LKR", accountTypeId = 0),
            AccountEntity(id = 2, senderAddress = "HNB", accountSuffix = "—", displayName = "HNB", currency = "LKR", accountTypeId = 0, isHidden = true),
        ))
        db.backup().insertTransactions(listOf(
            tx(1, local(Calendar.SEPTEMBER, 14, hour = 8), 1_500), // counts today and this period
            tx(1, local(Calendar.SEPTEMBER, 14, hour = 9), 900_000, declined = true),
            tx(1, local(Calendar.SEPTEMBER, 14, hour = 9), 777_777, group = 3), // own-transfer leg
            tx(2, local(Calendar.SEPTEMBER, 14, hour = 9), 555_555), // hidden account
            tx(1, local(Calendar.SEPTEMBER, 14, hour = 9), 2_000, currency = "USD"), // not the dominant currency
            tx(1, local(Calendar.SEPTEMBER, 14, hour = 9), 50_000, flow = 1), // income is not spending
            tx(1, local(Calendar.SEPTEMBER, 13, hour = 23), 4_000), // yesterday: period only
            tx(1, local(Calendar.SEPTEMBER, 3), 10_000), // this calendar month only
            tx(1, local(Calendar.AUGUST, 31, hour = 22), 99_999), // last month
        ))
    }

    @Test
    fun `spent today and this period follow the money rules`(): Unit = runBlocking {
        seed()
        val s = service.load()
        assertThat(s.currency).isEqualTo("LKR")
        assertThat(s.spentTodayMinor).isEqualTo(1_500)
        assertThat(s.periodSpentMinor).isEqualTo(1_500 + 4_000 + 10_000)
        assertThat(s.hideAmounts).isFalse()
        assertThat(s.spentTodayText(plain)).isEqualTo("LKR 1500")
        assertThat(s.periodSpentText(plain)).isEqualTo("LKR 15500")
    }

    @Test
    fun `this period follows the month start day`(): Unit = runBlocking {
        seed()
        prefs.setMonthStartDay(5)
        val s = service.load()
        // 5 Sep – 4 Oct: the 3 Sep and 31 Aug rows belong to the previous period.
        assertThat(s.periodSpentMinor).isEqualTo(1_500 + 4_000)
        assertThat(s.spentTodayMinor).isEqualTo(1_500)
        assertThat(s.periodLabel).isEqualTo("5 Sep 2026 – 4 Oct 2026")
    }

    @Test
    fun `safe to spend today is the planning service's per-day figure`(): Unit = runBlocking {
        seed()
        // No limit and no complete period of history: nothing to budget from.
        val none = service.load()
        assertThat(none.safeToSpendTodayMinor).isNull()
        assertThat(none.safeToSpendTodayText(plain)).isEqualTo("—")

        prefs.setMonthlySpendingLimitMinor(500_000)
        val s = service.load()
        val plan = planning.observe().first().safeToSpend
        assertThat(s.safeToSpendTodayMinor).isEqualTo(plan.perDayMinor)
        // 500,000 − 15,500 spent this month, over the 17 days from 14 to 30 September.
        assertThat(s.safeToSpendTodayMinor).isEqualTo((500_000L - 15_500) / 17)
        assertThat(s.safeToSpendTodayText(plain)).isEqualTo("LKR ${(500_000L - 15_500) / 17}")
    }

    @Test
    fun `hidden amounts render as a mask in every slot`(): Unit = runBlocking {
        seed()
        prefs.setMonthlySpendingLimitMinor(500_000)
        prefs.setWidgetHideAmounts(true)
        val s = service.load()
        assertThat(s.hideAmounts).isTrue()
        assertThat(s.spentTodayText(plain)).isEqualTo("Rs ••••")
        assertThat(s.periodSpentText(plain)).isEqualTo("Rs ••••")
        assertThat(s.safeToSpendTodayText(plain)).isEqualTo("Rs ••••")
        // A missing number is masked too, so hiding never reveals whether there is one.
        assertThat(s.copy(safeToSpendTodayMinor = null).safeToSpendTodayText(plain)).isEqualTo("Rs ••••")
    }

    @Test
    fun `a period spent mostly in another currency sums that currency like Home does`(): Unit = runBlocking {
        db.backup().insertAccounts(listOf(
            AccountEntity(id = 1, senderAddress = "HSBC", accountSuffix = "11", displayName = "HSBC", currency = "USD", accountTypeId = 0),
        ))
        db.backup().insertTransactions(listOf(
            tx(1, local(Calendar.SEPTEMBER, 14, hour = 8), 1_200, currency = "USD"),
            tx(1, local(Calendar.SEPTEMBER, 10), 800, currency = "USD"),
            tx(1, local(Calendar.SEPTEMBER, 14, hour = 9), 90_000, currency = "LKR"),
        ))
        val s = service.load()
        assertThat(s.currency).isEqualTo("USD")
        assertThat(s.spentTodayMinor).isEqualTo(1_200)
        assertThat(s.periodSpentMinor).isEqualTo(2_000)
        prefs.setWidgetHideAmounts(true)
        assertThat(service.load().spentTodayText(plain)).isEqualTo("USD ••••")
    }

    @Test
    fun `safe to spend uses the current cycle currency even when old history is mostly foreign`(): Unit = runBlocking {
        db.backup().insertAccounts(listOf(
            AccountEntity(id = 1, senderAddress = "BOC", accountSuffix = "1", displayName = "BOC", currency = "LKR", accountTypeId = 0),
        ))
        val oldUsd = (1L..6L).map { id ->
            tx(1, local(Calendar.AUGUST, id.toInt()), 1_000, currency = "USD")
        }
        db.backup().insertTransactions(oldUsd + tx(1, local(Calendar.SEPTEMBER, 14), 2_000, currency = "LKR"))
        prefs.setMonthlySpendingLimitMinor(500_000)

        val summary = service.load()
        assertThat(summary.currency).isEqualTo("LKR")
        assertThat(summary.safeToSpendCurrency).isEqualTo("LKR")
        assertThat(summary.spentTodayMinor).isEqualTo(2_000)
    }

    @Test
    fun `income currency is retained when the current period has no expenses`(): Unit = runBlocking {
        db.backup().insertAccounts(listOf(
            AccountEntity(id = 1, senderAddress = "HSBC", accountSuffix = "1", displayName = "HSBC", currency = "USD", accountTypeId = 0),
        ))
        db.backup().insertTransactions(listOf(
            tx(1, local(Calendar.SEPTEMBER, 14), 2_000, flow = 1, currency = "USD"),
        ))

        val summary = service.load()
        assertThat(summary.currency).isEqualTo("USD")
        assertThat(summary.spentTodayMinor).isEqualTo(0)
    }
}
