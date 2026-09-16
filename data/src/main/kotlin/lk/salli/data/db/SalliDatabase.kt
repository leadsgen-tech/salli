package lk.salli.data.db

import lk.salli.data.db.dao.SplitDao
import lk.salli.data.db.entities.SplitExpenseEntity
import lk.salli.data.db.entities.SplitGroupEntity
import lk.salli.data.db.entities.SplitMemberEntity
import lk.salli.data.db.entities.SplitSettlementEntity
import lk.salli.data.db.entities.SplitShareEntity
import lk.salli.data.db.dao.RecurringDao
import lk.salli.data.db.dao.GoalDao
import lk.salli.data.db.entities.RecurringSeriesEntity
import lk.salli.data.db.entities.GoalEntity
import lk.salli.data.db.entities.GoalContributionEntity
import androidx.room.Database
import androidx.room.RoomDatabase
import lk.salli.data.db.dao.AccountDao
import lk.salli.data.db.dao.BackupDao
import lk.salli.data.db.dao.BillDao
import lk.salli.data.db.dao.BudgetDao
import lk.salli.data.db.dao.CategoryDao
import lk.salli.data.db.dao.FuelPassDao
import lk.salli.data.db.dao.KeywordDao
import lk.salli.data.db.dao.MerchantDao
import lk.salli.data.db.dao.TransactionDao
import lk.salli.data.db.dao.TransferGroupDao
import lk.salli.data.db.dao.UnknownSmsDao
import lk.salli.data.db.entities.AccountEntity
import lk.salli.data.db.entities.BillEntity
import lk.salli.data.db.entities.BudgetAccountEntity
import lk.salli.data.db.entities.BudgetEntity
import lk.salli.data.db.entities.BudgetLineEntity
import lk.salli.data.db.entities.CategoryEntity
import lk.salli.data.db.entities.FuelPassRecordEntity
import lk.salli.data.db.entities.KeywordEntity
import lk.salli.data.db.entities.MerchantAliasEntity
import lk.salli.data.db.entities.MerchantEntity
import lk.salli.data.db.entities.SubCategoryEntity
import lk.salli.data.db.entities.TransactionEntity
import lk.salli.data.db.entities.TransferGroupEntity
import lk.salli.data.db.entities.UnknownSmsEntity

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        SubCategoryEntity::class,
        MerchantEntity::class,
        MerchantAliasEntity::class,
        KeywordEntity::class,
        TransactionEntity::class,
        TransferGroupEntity::class,
        UnknownSmsEntity::class,
        BudgetEntity::class,
        BudgetLineEntity::class,
        BudgetAccountEntity::class,
        BillEntity::class,
        FuelPassRecordEntity::class,
        RecurringSeriesEntity::class,
        GoalEntity::class,
        GoalContributionEntity::class,
        SplitGroupEntity::class,
        SplitMemberEntity::class,
        SplitExpenseEntity::class,
        SplitShareEntity::class,
        SplitSettlementEntity::class,
    ],
    version = 10,
    exportSchema = true,
    autoMigrations = [
        androidx.room.AutoMigration(from = 2, to = 3),
        androidx.room.AutoMigration(from = 3, to = 4),
        // v5: accounts.is_hidden (Settings toggle for account visibility).
        androidx.room.AutoMigration(from = 4, to = 5),
        // v6: bills + fuel_pass_records tables (utility trackers).
        androidx.room.AutoMigration(from = 5, to = 6),
        // v7: bills.is_superseded (newer statement replaces an older open bill).
        androidx.room.AutoMigration(from = 6, to = 7),
        // v8: bills.paid_manually (hand-marked bills survive the status replay).
        androidx.room.AutoMigration(from = 7, to = 8),
        // v9: recurring_series, goals, goal_contributions (planning features).
        androidx.room.AutoMigration(from = 8, to = 9),
        // v10: split_groups, split_members, split_expenses, split_shares, split_settlements.
        androidx.room.AutoMigration(from = 9, to = 10),
    ],
)
abstract class SalliDatabase : RoomDatabase() {
    abstract fun accounts(): AccountDao
    abstract fun categories(): CategoryDao
    abstract fun merchants(): MerchantDao
    abstract fun keywords(): KeywordDao
    abstract fun transactions(): TransactionDao
    abstract fun transferGroups(): TransferGroupDao
    abstract fun unknownSms(): UnknownSmsDao
    abstract fun budgets(): BudgetDao
    abstract fun bills(): BillDao
    abstract fun fuelPass(): FuelPassDao
    abstract fun backup(): BackupDao
    abstract fun recurring(): RecurringDao
    abstract fun goals(): GoalDao
    abstract fun split(): SplitDao

    companion object {
        const val NAME: String = "salli.db"
    }
}
