package lk.salli.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import lk.salli.data.db.entities.SplitExpenseEntity
import lk.salli.data.db.entities.SplitGroupEntity
import lk.salli.data.db.entities.SplitMemberEntity
import lk.salli.data.db.entities.SplitSettlementEntity
import lk.salli.data.db.entities.SplitShareEntity

@Dao
interface SplitDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGroup(group: SplitGroupEntity): Long

    @Query("SELECT * FROM split_groups WHERE id = :id")
    suspend fun groupById(id: Long): SplitGroupEntity?

    @Query("SELECT * FROM split_groups WHERE id = :id")
    fun observeGroup(id: Long): Flow<SplitGroupEntity?>

    @Query("SELECT * FROM split_groups ORDER BY archived ASC, created_at DESC, id DESC")
    fun observeGroups(): Flow<List<SplitGroupEntity>>

    @Query("UPDATE split_groups SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMember(member: SplitMemberEntity): Long

    @Query("SELECT * FROM split_members WHERE group_id = :groupId ORDER BY is_me DESC, id ASC")
    suspend fun membersOf(groupId: Long): List<SplitMemberEntity>

    @Query("SELECT * FROM split_members ORDER BY group_id ASC, is_me DESC, id ASC")
    fun observeMembers(): Flow<List<SplitMemberEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExpense(expense: SplitExpenseEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertShares(shares: List<SplitShareEntity>)

    @Query("SELECT * FROM split_expenses ORDER BY at DESC, id DESC")
    fun observeExpenses(): Flow<List<SplitExpenseEntity>>

    @Query("SELECT * FROM split_shares ORDER BY expense_id ASC, id ASC")
    fun observeShares(): Flow<List<SplitShareEntity>>

    @Query("SELECT * FROM split_expenses WHERE linked_transaction_id = :transactionId ORDER BY at DESC, id DESC LIMIT 1")
    suspend fun expenseLinkedTo(transactionId: Long): SplitExpenseEntity?

    @Query("SELECT * FROM split_shares WHERE expense_id = :expenseId ORDER BY id ASC")
    suspend fun sharesOf(expenseId: Long): List<SplitShareEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSettlement(settlement: SplitSettlementEntity): Long

    @Query("SELECT * FROM split_settlements ORDER BY at DESC, id DESC")
    fun observeSettlements(): Flow<List<SplitSettlementEntity>>

    @Query("DELETE FROM split_shares WHERE expense_id = :expenseId")
    suspend fun deleteSharesOf(expenseId: Long)

    @Query("DELETE FROM split_expenses WHERE id = :id")
    suspend fun deleteExpenseRow(id: Long)

    /** Removes an expense together with its shares. */
    @Transaction
    suspend fun deleteExpense(id: Long) {
        deleteSharesOf(id)
        deleteExpenseRow(id)
    }

    @Query("DELETE FROM split_settlements WHERE id = :id")
    suspend fun deleteSettlement(id: Long)

    @Query("DELETE FROM split_shares WHERE expense_id IN (SELECT id FROM split_expenses WHERE group_id = :groupId)")
    suspend fun deleteSharesInGroup(groupId: Long)

    @Query("DELETE FROM split_expenses WHERE group_id = :groupId")
    suspend fun deleteExpensesInGroup(groupId: Long)

    @Query("DELETE FROM split_settlements WHERE group_id = :groupId")
    suspend fun deleteSettlementsInGroup(groupId: Long)

    @Query("DELETE FROM split_members WHERE group_id = :groupId")
    suspend fun deleteMembersInGroup(groupId: Long)

    @Query("DELETE FROM split_groups WHERE id = :groupId")
    suspend fun deleteGroupRow(groupId: Long)

    /** Removes a group and everything in it. Linked bank transactions are never touched. */
    @Transaction
    suspend fun deleteGroup(groupId: Long) {
        deleteSharesInGroup(groupId)
        deleteExpensesInGroup(groupId)
        deleteSettlementsInGroup(groupId)
        deleteMembersInGroup(groupId)
        deleteGroupRow(groupId)
    }
}
