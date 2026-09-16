package lk.salli.data.backup

import kotlinx.serialization.Serializable
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
import lk.salli.data.prefs.PreferencesSnapshot

/**
 * The on-disk shape of a Salli backup. `format` and `version` are checked before anything is
 * restored; unknown keys are ignored so a newer app can still read an older file.
 */
@Serializable
data class BackupDocument(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val exportedAt: Long,
    val appVersion: String? = null,
    val schemaVersion: Int,
    val tables: BackupTables,
    val preferences: PreferencesSnapshot,
) {
    companion object {
        const val FORMAT = "salli-backup"
        const val VERSION = 1
    }
}

@Serializable
data class BackupTables(
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val subCategories: List<SubCategoryEntity> = emptyList(),
    val merchants: List<MerchantEntity> = emptyList(),
    val merchantAliases: List<MerchantAliasEntity> = emptyList(),
    val keywords: List<KeywordEntity> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val transferGroups: List<TransferGroupEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
    val budgetLines: List<BudgetLineEntity> = emptyList(),
    val budgetAccounts: List<BudgetAccountEntity> = emptyList(),
    val unknownSms: List<UnknownSmsEntity> = emptyList(),
    val bills: List<BillEntity> = emptyList(),
    val fuelPassRecords: List<FuelPassRecordEntity> = emptyList(),
    // Added with schema 9. Optional with empty defaults so files written before still restore.
    val recurringSeries: List<RecurringSeriesEntity> = emptyList(),
    val goals: List<GoalEntity> = emptyList(),
    val goalContributions: List<GoalContributionEntity> = emptyList(),
    // Added with schema 10. Optional with empty defaults so files written before still restore.
    val splitGroups: List<SplitGroupEntity> = emptyList(),
    val splitMembers: List<SplitMemberEntity> = emptyList(),
    val splitExpenses: List<SplitExpenseEntity> = emptyList(),
    val splitShares: List<SplitShareEntity> = emptyList(),
    val splitSettlements: List<SplitSettlementEntity> = emptyList(),
) {
    val rowCount: Int
        get() = accounts.size + categories.size + subCategories.size + merchants.size +
            merchantAliases.size + keywords.size + transactions.size + transferGroups.size +
            budgets.size + budgetLines.size + budgetAccounts.size + unknownSms.size +
            bills.size + fuelPassRecords.size + recurringSeries.size + goals.size + goalContributions.size +
            splitGroups.size + splitMembers.size + splitExpenses.size + splitShares.size + splitSettlements.size
}
