package lk.salli.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import lk.salli.data.db.entities.BillEntity

@Dao
interface BillDao {

    /** Returns -1 when the same SMS body was already stored. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(bill: BillEntity): Long

    @Update
    suspend fun update(bill: BillEntity)

    @Query("SELECT * FROM bills WHERE id = :id")
    suspend fun byId(id: Long): BillEntity?

    @Query("SELECT * FROM bills WHERE raw_body_hash = :hash LIMIT 1")
    suspend fun byHash(hash: String): BillEntity?

    @Query(
        """
        SELECT * FROM bills
        WHERE biller = :biller AND account_ref = :accountRef AND is_paid = 0 AND is_superseded = 0
        ORDER BY received_at DESC LIMIT 1
        """,
    )
    suspend fun newestOpenFor(biller: String, accountRef: String): BillEntity?

    /** Every still-open bill for the account that arrived before [before]. */
    @Query(
        """
        SELECT * FROM bills
        WHERE biller = :biller AND account_ref = :accountRef AND is_paid = 0 AND is_superseded = 0
          AND received_at < :before
        ORDER BY received_at ASC
        """,
    )
    suspend fun openForBefore(biller: String, accountRef: String, before: Long): List<BillEntity>

    @Query("SELECT * FROM bills WHERE is_paid = 0 AND is_superseded = 0 ORDER BY CASE WHEN due_date IS NULL THEN 1 ELSE 0 END, due_date ASC, received_at DESC")
    fun observeOpenBills(): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills WHERE is_paid = 0 AND is_superseded = 0 ORDER BY due_date ASC")
    suspend fun openBills(): List<BillEntity>

    @Query("SELECT * FROM bills ORDER BY received_at ASC, id ASC")
    suspend fun allByReceived(): List<BillEntity>

    /** One account's messages in arrival order, for the status replay. */
    @Query("SELECT * FROM bills WHERE biller = :biller AND account_ref = :accountRef ORDER BY received_at ASC, id ASC")
    suspend fun forAccount(biller: String, accountRef: String): List<BillEntity>

    @Query("SELECT DISTINCT biller, account_ref FROM bills")
    suspend fun billAccounts(): List<BillAccountKey>

    @Query("UPDATE bills SET is_superseded = 1, updated_at = :at WHERE id = :id")
    suspend fun markSuperseded(id: Long, at: Long)

    @Query("SELECT * FROM bills ORDER BY is_paid ASC, received_at DESC")
    fun observeAll(): Flow<List<BillEntity>>

    /**
     * History list: settled bills (statements in credit included) plus bills that were part-paid
     * before a newer statement rolled them over. Excludes the zero-amount shadow rows stored for
     * folded reminders and receipts, which exist only for de-duplication.
     */
    @Query(
        """
        SELECT * FROM bills
        WHERE (is_paid = 1 AND is_superseded = 0 AND (amount_due_minor > 0 OR kind_id = 0))
           OR (is_superseded = 1 AND IFNULL(paid_amount_minor, 0) > 0 AND amount_due_minor > 0)
        ORDER BY IFNULL(paid_at, received_at) DESC, received_at DESC
        LIMIT :limit
        """,
    )
    fun observePaidBills(limit: Int = 30): Flow<List<BillEntity>>

    /** "Mark paid" from the Bills screen. Flagged so the status replay keeps it paid. */
    @Query("UPDATE bills SET is_paid = 1, paid_manually = 1, paid_at = :paidAt, paid_amount_minor = :paidAmountMinor, updated_at = :paidAt WHERE id = :id")
    suspend fun markPaidManually(id: Long, paidAt: Long, paidAmountMinor: Long?)

    @Query("UPDATE bills SET reminder_stage = :stage WHERE id = :id")
    suspend fun setReminderStage(id: Long, stage: Int)

    @Query("SELECT COUNT(*) FROM bills WHERE is_paid = 0 AND is_superseded = 0")
    fun observeOpenCount(): Flow<Int>

    @Query("DELETE FROM bills")
    suspend fun deleteAll()
}

/** A (biller, account) pair that has at least one stored bill message. */
data class BillAccountKey(
    @ColumnInfo(name = "biller") val biller: String,
    @ColumnInfo(name = "account_ref") val accountRef: String,
)
