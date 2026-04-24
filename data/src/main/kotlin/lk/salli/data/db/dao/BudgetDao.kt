package lk.salli.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import lk.salli.data.db.entities.BudgetAccountEntity
import lk.salli.data.db.entities.BudgetEntity
import lk.salli.data.db.entities.BudgetLineEntity

@Dao
interface BudgetDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(budget: BudgetEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLines(lines: List<BudgetLineEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAccountLinks(links: List<BudgetAccountEntity>): List<Long>

    @Update
    suspend fun update(budget: BudgetEntity)

    @Query("DELETE FROM budget_lines WHERE budget_id = :budgetId")
    suspend fun clearLinesFor(budgetId: Long)

    @Query("DELETE FROM budget_accounts WHERE budget_id = :budgetId")
    suspend fun clearAccountLinksFor(budgetId: Long)

    @Delete
    suspend fun delete(budget: BudgetEntity)

    @Query("SELECT * FROM budgets ORDER BY created_at DESC")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): BudgetEntity?

    @Query("SELECT * FROM budget_lines WHERE budget_id = :budgetId")
    suspend fun linesFor(budgetId: Long): List<BudgetLineEntity>

    @Query("SELECT * FROM budget_lines")
    fun observeAllLines(): Flow<List<BudgetLineEntity>>

    @Query("SELECT * FROM budget_accounts")
    fun observeAllAccountLinks(): Flow<List<BudgetAccountEntity>>

    /**
     * Replace all category caps for the given budget atomically. Used by both the create flow
     * (which has no prior lines) and the edit flow.
     */
    @Transaction
    suspend fun replaceLines(budgetId: Long, lines: List<BudgetLineEntity>) {
        clearLinesFor(budgetId)
        if (lines.isNotEmpty()) insertLines(lines.map { it.copy(budgetId = budgetId) })
    }

    /**
     * Replace the account scope for the given budget atomically. Empty list means
     * "all accounts" — no rows kept.
     */
    @Transaction
    suspend fun replaceAccountLinks(budgetId: Long, accountIds: List<Long>) {
        clearAccountLinksFor(budgetId)
        if (accountIds.isNotEmpty()) {
            insertAccountLinks(
                accountIds.map { BudgetAccountEntity(budgetId = budgetId, accountId = it) },
            )
        }
    }
}
