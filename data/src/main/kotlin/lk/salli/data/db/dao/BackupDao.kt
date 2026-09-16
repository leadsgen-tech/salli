package lk.salli.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
import lk.salli.data.db.entities.KeywordEntity
import lk.salli.data.db.entities.MerchantAliasEntity
import lk.salli.data.db.entities.MerchantEntity
import lk.salli.data.db.entities.SubCategoryEntity
import lk.salli.data.db.entities.TransactionEntity
import lk.salli.data.db.entities.TransferGroupEntity
import lk.salli.data.db.entities.UnknownSmsEntity

/**
 * Whole-table reads and writes for the JSON backup. Inserts keep the ids that come from the
 * file (Room only auto-generates when the id is 0), so foreign keys survive a round trip.
 * Deletes are ordered children-first so Room's FK enforcement never trips.
 */
@Dao
interface BackupDao {

    @Query("SELECT * FROM accounts ORDER BY id") suspend fun allAccounts(): List<AccountEntity>
    @Query("SELECT * FROM categories ORDER BY id") suspend fun allCategories(): List<CategoryEntity>
    @Query("SELECT * FROM sub_categories ORDER BY id") suspend fun allSubCategories(): List<SubCategoryEntity>
    @Query("SELECT * FROM merchants ORDER BY id") suspend fun allMerchants(): List<MerchantEntity>
    @Query("SELECT * FROM merchant_aliases ORDER BY id") suspend fun allMerchantAliases(): List<MerchantAliasEntity>
    @Query("SELECT * FROM keywords ORDER BY id") suspend fun allKeywords(): List<KeywordEntity>
    @Query("SELECT * FROM transactions ORDER BY id") suspend fun allTransactions(): List<TransactionEntity>
    @Query("SELECT * FROM transfer_groups ORDER BY id") suspend fun allTransferGroups(): List<TransferGroupEntity>
    @Query("SELECT * FROM budgets ORDER BY id") suspend fun allBudgets(): List<BudgetEntity>
    @Query("SELECT * FROM budget_lines ORDER BY id") suspend fun allBudgetLines(): List<BudgetLineEntity>
    @Query("SELECT * FROM budget_accounts ORDER BY id") suspend fun allBudgetAccounts(): List<BudgetAccountEntity>
    @Query("SELECT * FROM unknown_sms ORDER BY id") suspend fun allUnknownSms(): List<UnknownSmsEntity>
    @Query("SELECT * FROM bills ORDER BY id") suspend fun allBills(): List<BillEntity>
    @Query("SELECT * FROM fuel_pass_records ORDER BY id") suspend fun allFuelPassRecords(): List<FuelPassRecordEntity>
    @Query("SELECT * FROM recurring_series ORDER BY id") suspend fun allRecurringSeries(): List<RecurringSeriesEntity>
    @Query("SELECT * FROM goals ORDER BY id") suspend fun allGoals(): List<GoalEntity>
    @Query("SELECT * FROM goal_contributions ORDER BY id") suspend fun allGoalContributions(): List<GoalContributionEntity>
    @Query("SELECT * FROM split_groups ORDER BY id") suspend fun allSplitGroups(): List<SplitGroupEntity>
    @Query("SELECT * FROM split_members ORDER BY id") suspend fun allSplitMembers(): List<SplitMemberEntity>
    @Query("SELECT * FROM split_expenses ORDER BY id") suspend fun allSplitExpenses(): List<SplitExpenseEntity>
    @Query("SELECT * FROM split_shares ORDER BY id") suspend fun allSplitShares(): List<SplitShareEntity>
    @Query("SELECT * FROM split_settlements ORDER BY id") suspend fun allSplitSettlements(): List<SplitSettlementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAccounts(rows: List<AccountEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCategories(rows: List<CategoryEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSubCategories(rows: List<SubCategoryEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertMerchants(rows: List<MerchantEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertMerchantAliases(rows: List<MerchantAliasEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertKeywords(rows: List<KeywordEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTransactions(rows: List<TransactionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTransferGroups(rows: List<TransferGroupEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertBudgets(rows: List<BudgetEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertBudgetLines(rows: List<BudgetLineEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertBudgetAccounts(rows: List<BudgetAccountEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertUnknownSms(rows: List<UnknownSmsEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertBills(rows: List<BillEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertFuelPassRecords(rows: List<FuelPassRecordEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertRecurringSeries(rows: List<RecurringSeriesEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertGoals(rows: List<GoalEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertGoalContributions(rows: List<GoalContributionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSplitGroups(rows: List<SplitGroupEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSplitMembers(rows: List<SplitMemberEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSplitExpenses(rows: List<SplitExpenseEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSplitShares(rows: List<SplitShareEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSplitSettlements(rows: List<SplitSettlementEntity>)

    @Query("DELETE FROM budget_accounts") suspend fun deleteBudgetAccounts()
    @Query("DELETE FROM budget_lines") suspend fun deleteBudgetLines()
    @Query("DELETE FROM budgets") suspend fun deleteBudgets()
    @Query("DELETE FROM transfer_groups") suspend fun deleteTransferGroups()
    @Query("DELETE FROM transactions") suspend fun deleteTransactions()
    @Query("DELETE FROM merchant_aliases") suspend fun deleteMerchantAliases()
    @Query("DELETE FROM merchants") suspend fun deleteMerchants()
    @Query("DELETE FROM keywords") suspend fun deleteKeywords()
    @Query("DELETE FROM sub_categories") suspend fun deleteSubCategories()
    @Query("DELETE FROM categories") suspend fun deleteCategories()
    @Query("DELETE FROM accounts") suspend fun deleteAccounts()
    @Query("DELETE FROM unknown_sms") suspend fun deleteUnknownSms()
    @Query("DELETE FROM bills") suspend fun deleteBills()
    @Query("DELETE FROM fuel_pass_records") suspend fun deleteFuelPassRecords()
    @Query("DELETE FROM recurring_series") suspend fun deleteRecurringSeries()
    @Query("DELETE FROM goal_contributions") suspend fun deleteGoalContributions()
    @Query("DELETE FROM goals") suspend fun deleteGoals()
    @Query("DELETE FROM split_shares") suspend fun deleteSplitShares()
    @Query("DELETE FROM split_settlements") suspend fun deleteSplitSettlements()
    @Query("DELETE FROM split_expenses") suspend fun deleteSplitExpenses()
    @Query("DELETE FROM split_members") suspend fun deleteSplitMembers()
    @Query("DELETE FROM split_groups") suspend fun deleteSplitGroups()
}
