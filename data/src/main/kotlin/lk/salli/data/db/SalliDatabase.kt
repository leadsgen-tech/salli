package lk.salli.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import lk.salli.data.db.dao.AccountDao
import lk.salli.data.db.dao.BudgetDao
import lk.salli.data.db.dao.CategoryDao
import lk.salli.data.db.dao.KeywordDao
import lk.salli.data.db.dao.MerchantDao
import lk.salli.data.db.dao.TransactionDao
import lk.salli.data.db.dao.TransferGroupDao
import lk.salli.data.db.dao.UnknownSmsDao
import lk.salli.data.db.entities.AccountEntity
import lk.salli.data.db.entities.BudgetAccountEntity
import lk.salli.data.db.entities.BudgetEntity
import lk.salli.data.db.entities.BudgetLineEntity
import lk.salli.data.db.entities.CategoryEntity
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
    ],
    version = 4,
    exportSchema = true,
    autoMigrations = [
        androidx.room.AutoMigration(from = 2, to = 3),
        androidx.room.AutoMigration(from = 3, to = 4),
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

    companion object {
        const val NAME: String = "salli.db"
    }
}
