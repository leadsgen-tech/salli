package lk.salli.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import lk.salli.data.db.SalliDatabase
import lk.salli.data.db.entities.AccountEntity
import lk.salli.data.db.entities.BillEntity
import lk.salli.data.db.entities.BudgetAccountEntity
import lk.salli.data.db.entities.BudgetEntity
import lk.salli.data.db.entities.BudgetLineEntity
import lk.salli.data.db.entities.CategoryEntity
import lk.salli.data.db.entities.FuelPassRecordEntity
import lk.salli.data.db.entities.GoalContributionEntity
import lk.salli.data.db.entities.GoalEntity
import lk.salli.data.db.entities.RecurringSeriesEntity
import lk.salli.data.db.entities.SplitExpenseEntity
import lk.salli.data.db.entities.SplitGroupEntity
import lk.salli.data.db.entities.SplitMemberEntity
import lk.salli.data.db.entities.SplitSettlementEntity
import lk.salli.data.db.entities.SplitShareEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import lk.salli.data.db.entities.KeywordEntity
import lk.salli.data.db.entities.MerchantAliasEntity
import lk.salli.data.db.entities.MerchantEntity
import lk.salli.data.db.entities.SubCategoryEntity
import lk.salli.data.db.entities.TransactionEntity
import lk.salli.data.db.entities.TransferGroupEntity
import lk.salli.data.db.entities.UnknownSmsEntity
import lk.salli.data.prefs.SalliPreferences
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRoundTripTest {

    private lateinit var db: SalliDatabase
    private lateinit var prefs: SalliPreferences
    private lateinit var manager: BackupManager

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, SalliDatabase::class.java).allowMainThreadQueries().build()
        prefs = SalliPreferences(context)
        manager = BackupManager(db, prefs, context, appVersion = "test")
    }

    @After
    fun tearDown() {
        db.close()
        runBlocking {
            prefs.setUserName(""); prefs.setMonthStartDay(1); prefs.setSummaryDaily(false)
            prefs.setSummaryHour(SalliPreferences.DEFAULT_SUMMARY_HOUR); prefs.setMonthlySpendingLimitMinor(null)
            prefs.setWidgetHideAmounts(false); prefs.setAppLockEnabled(false); prefs.setAppLockAfterSeconds(0); prefs.setHideInRecents(false)
        }
    }

    private suspend fun seed() {
        val dao = db.backup()
        dao.insertCategories(listOf(CategoryEntity(id = 1, name = "Groceries", iconName = "cart", colorSeed = 1)))
        dao.insertSubCategories(listOf(SubCategoryEntity(id = 1, categoryId = 1, name = "Supermarket")))
        dao.insertAccounts(listOf(
            AccountEntity(id = 1, senderAddress = "BOC", accountSuffix = "870", displayName = "BOC (870)", currency = "LKR", accountTypeId = 0, balanceMinor = 12345),
            AccountEntity(id = 2, senderAddress = "PeoplesBank", accountSuffix = "..68", displayName = "PB", currency = "LKR", accountTypeId = 0, isHidden = true),
        ))
        dao.insertMerchants(listOf(MerchantEntity(id = 1, canonicalName = "Keells", categoryId = 1)))
        dao.insertMerchantAliases(listOf(MerchantAliasEntity(id = 1, merchantId = 1, rawName = "KEELLS SUPER")))
        dao.insertKeywords(listOf(KeywordEntity(id = 1, keyword = "keells", categoryId = 1, source = "seed")))
        dao.insertTransactions(listOf(
            TransactionEntity(id = 10, accountId = 1, amountMinor = 250000, amountCurrency = "LKR", timestamp = 1_000L, flowId = 0, methodId = 1, typeId = 0, merchantRaw = "KEELLS SUPER", rawBody = "raw sms body", createdAt = 1L, updatedAt = 1L),
            TransactionEntity(id = 11, accountId = 2, amountMinor = 250000, amountCurrency = "LKR", timestamp = 2_000L, flowId = 2, methodId = 1, typeId = 4, transferGroupId = 5, note = "own", createdAt = 1L, updatedAt = 1L),
            TransactionEntity(id = 12, accountId = 1, amountMinor = 247500, amountCurrency = "LKR", timestamp = 2_100L, flowId = 2, methodId = 1, typeId = 4, transferGroupId = 5, createdAt = 1L, updatedAt = 1L),
        ))
        dao.insertTransferGroups(listOf(TransferGroupEntity(id = 5, debitTxId = 11, creditTxId = 12, createdAt = 1L)))
        dao.insertBudgets(listOf(BudgetEntity(id = 3, name = "Food", currency = "LKR", createdAt = 1L, periodStartDay = 25)))
        dao.insertBudgetLines(listOf(BudgetLineEntity(id = 1, budgetId = 3, categoryId = 1, amountMinor = 5_000_000)))
        dao.insertBudgetAccounts(listOf(BudgetAccountEntity(id = 1, budgetId = 3, accountId = 1)))
        dao.insertUnknownSms(listOf(UnknownSmsEntity(id = 1, senderAddress = "NSB", body = "hello", receivedAt = 3L)))
        dao.insertBills(listOf(BillEntity(id = 1, biller = "SLT-MOBITEL", accountRef = "0371234567", amountDueMinor = 1195291, currency = "LKR", dueDate = 4L, periodLabel = "Aug", kindId = 0, rawBodyHash = "h1", rawBody = "b", receivedAt = 3L, createdAt = 3L, updatedAt = 3L)))
        dao.insertFuelPassRecords(listOf(FuelPassRecordEntity(id = 1, vehicle = "ABC-1234", litresMilli = 8000, weeklyBalanceMilli = 0, stationCode = "1", timestamp = 5L, resetsOn = 6L, rawBody = "f", createdAt = 5L)))
        dao.insertRecurringSeries(listOf(RecurringSeriesEntity(id = 1, seriesKey = "lolc finance", displayName = "LOLC Finance PLC", flowId = 0, currency = "LKR", cadence = "MONTHLY", intervalDays = 33.0, typicalAmountMinor = 4_502_500, isFixed = true, occurrences = 5, lastAt = 7L, nextAt = 8L, status = "MISSED", confidence = 0.81, userState = "CONFIRMED", updatedAt = 7L)))
        dao.insertGoals(listOf(GoalEntity(id = 1, name = "Trip", targetMinor = 40_000_000, currency = "LKR", targetDate = 9L, createdAt = 1L)))
        dao.insertGoalContributions(listOf(GoalContributionEntity(id = 1, goalId = 1, amountMinor = 500_000, at = 2L, note = "first")))
        dao.insertSplitGroups(listOf(SplitGroupEntity(id = 4, name = "Ella trip", currency = "LKR", createdAt = 1L)))
        dao.insertSplitMembers(listOf(
            SplitMemberEntity(id = 7, groupId = 4, name = "Nabil", isMe = true),
            SplitMemberEntity(id = 8, groupId = 4, name = "Aadhil"),
        ))
        dao.insertSplitExpenses(listOf(SplitExpenseEntity(id = 9, groupId = 4, title = "Hotel", amountMinor = 1_200_000, paidByMemberId = 7, method = "EQUAL", at = 3L, linkedTransactionId = 10, note = "two nights")))
        dao.insertSplitShares(listOf(
            SplitShareEntity(id = 1, expenseId = 9, memberId = 7, shareMinor = 600_000),
            SplitShareEntity(id = 2, expenseId = 9, memberId = 8, shareMinor = 600_000),
        ))
        dao.insertSplitSettlements(listOf(SplitSettlementEntity(id = 1, groupId = 4, fromMemberId = 8, toMemberId = 7, amountMinor = 200_000, at = 4L, linkedTransactionId = 12)))
        prefs.setUserName("Nabil"); prefs.setMonthStartDay(25); prefs.setSummaryDaily(true); prefs.setSummaryHour(21)
        prefs.setMonthlySpendingLimitMinor(20_000_000)
        prefs.setWidgetHideAmounts(true); prefs.setAppLockEnabled(true); prefs.setAppLockAfterSeconds(60); prefs.setHideInRecents(true)
    }

    @Test
    fun `restore reproduces every table and ordinary settings while keeping device security`(): Unit = runBlocking {
        seed()
        val before = manager.buildDocument()
        assertThat(before.tables.rowCount).isEqualTo(27)
        val json = BackupCodec.encode(before)
        assertThat(json).contains("\"format\":\"salli-backup\"")
        assertThat(json).contains("raw sms body")

        // Wipe the data and change ordinary settings. Security/privacy settings are intentionally
        // kept at their current device values by restore, regardless of the backup contents.
        db.clearAllTables()
        prefs.setUserName(""); prefs.setMonthStartDay(1); prefs.setSummaryDaily(false)
        prefs.setMonthlySpendingLimitMinor(null)
        prefs.setWidgetHideAmounts(false); prefs.setAppLockEnabled(false); prefs.setAppLockAfterSeconds(0); prefs.setHideInRecents(false)
        assertThat(db.backup().allTransactions()).isEmpty()

        manager.restore(json.byteInputStream())

        val after = manager.buildDocument()
        assertThat(after.tables).isEqualTo(before.tables)
        assertThat(after.preferences.userName).isEqualTo("Nabil")
        assertThat(after.preferences.monthStartDay).isEqualTo(25)
        assertThat(after.preferences.summaryDaily).isTrue()
        assertThat(after.preferences.summaryHour).isEqualTo(21)
        assertThat(after.preferences.monthlySpendingLimitMinor).isEqualTo(20_000_000)
        assertThat(after.preferences.widgetHideAmounts).isFalse()
        assertThat(after.preferences.appLockEnabled).isFalse()
        assertThat(after.preferences.appLockAfterSeconds).isEqualTo(0)
        assertThat(after.preferences.hideInRecents).isFalse()
        assertThat(db.recurring().all().single().userState).isEqualTo("CONFIRMED")
        // Ids are preserved, so the transfer pairing still points at the right legs.
        val group = db.transferGroups().byId(5)!!
        assertThat(group.debitTxId).isEqualTo(11)
        assertThat(db.transactions().byId(12)!!.transferGroupId).isEqualTo(5)
        assertThat(db.accounts().byId(2)!!.isHidden).isTrue()
        // Split ids survive too, so shares still point at their expense and members.
        assertThat(db.split().sharesOf(9).map { it.memberId }).containsExactly(7L, 8L)
        assertThat(db.split().expenseLinkedTo(10)!!.title).isEqualTo("Hotel")
    }

    @Test
    fun `restore refuses files that are not salli backups`(): Unit = runBlocking {
        seed()
        val bad = """{"format":"other","version":1,"exportedAt":0,"schemaVersion":1,"tables":{},"preferences":{}}"""
        val error = runCatching { manager.restore(bad.byteInputStream()) }.exceptionOrNull()
        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        // Nothing was touched.
        assertThat(db.backup().allTransactions()).hasSize(3)
        val garbage = runCatching { manager.restore("not json".byteInputStream()) }.exceptionOrNull()
        assertThat(garbage).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `a backup written before the planning tables existed still restores`(): Unit = runBlocking {
        seed()
        val full = Json.parseToJsonElement(BackupCodec.encode(manager.buildDocument())).jsonObject
        val newerKeys = setOf("recurringSeries", "goals", "goalContributions", "splitGroups", "splitMembers", "splitExpenses", "splitShares", "splitSettlements")
        val oldTables = JsonObject(full.getValue("tables").jsonObject.filterKeys { it !in newerKeys })
        val oldFile = JsonObject(full + ("tables" to oldTables)).toString()

        manager.restore(oldFile.byteInputStream())
        assertThat(db.backup().allGoals()).isEmpty()
        assertThat(db.backup().allRecurringSeries()).isEmpty()
        assertThat(db.backup().allSplitGroups()).isEmpty()
        assertThat(db.backup().allSplitShares()).isEmpty()
        assertThat(db.backup().allTransactions()).hasSize(3)
    }
}
