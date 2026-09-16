package lk.salli.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import lk.salli.data.db.entities.TransactionEntity

/**
 * Per-account roll-up used as a fallback "balance" line when an account never receives a
 * balance-bearing SMS (ComBank cards, the Q+ account, etc.). [netMinor] is signed
 * (income − expense over all time, declines and transfer-grouped legs excluded). [expenseOnly]
 * lets the chip pick "Spent · Rs X" wording for card-only accounts where every row is a debit.
 */
data class AccountActivityRow(
    @ColumnInfo(name = "account_id") val accountId: Long,
    @ColumnInfo(name = "net_minor") val netMinor: Long,
    @ColumnInfo(name = "expense_minor") val expenseMinor: Long,
    @ColumnInfo(name = "income_minor") val incomeMinor: Long,
    @ColumnInfo(name = "currency") val currency: String,
)

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(tx: TransactionEntity): Long

    @Update
    suspend fun update(tx: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun byId(id: Long): TransactionEntity?

    /** Recent rows from a specific sender — feeds DuplicateDetector and PeoplesBankMerger. */
    @Query(
        """
        SELECT * FROM transactions
        WHERE sender_address = :sender AND timestamp >= :sinceTimestamp
        ORDER BY timestamp DESC
        """,
    )
    suspend fun recentFromSender(sender: String, sinceTimestamp: Long): List<TransactionEntity>

    /** The user's own category choice; `user_tagged` locks it against the startup recategorise pass. */
    @Query("UPDATE transactions SET category_id = :categoryId, user_tagged = 1, updated_at = :now WHERE id = :id")
    suspend fun setUserCategory(id: Long, categoryId: Long, now: Long)

    @Query("UPDATE transactions SET note = :note, updated_at = :now WHERE id = :id")
    suspend fun setNote(id: Long, note: String?, now: Long)

    /** Same sender and same SMS text, at any time. A bank never sends one body twice. */
    @Query("SELECT * FROM transactions WHERE sender_address = :sender AND (raw_body = :body OR raw_body = :trimmedBody) LIMIT 1")
    suspend fun findBySenderAndBody(sender: String, body: String, trimmedBody: String): TransactionEntity?

    /** Recent rows across all senders — feeds InternalTransferDetector. */
    @Query("SELECT * FROM transactions WHERE timestamp >= :sinceTimestamp ORDER BY timestamp DESC")
    suspend fun recentAll(sinceTimestamp: Long): List<TransactionEntity>

    /**
     * Per-account income/expense totals over all time. Drives Home's fallback chip line for
     * accounts whose SMS never carries a balance — see [AccountActivityRow].
     */
    @Query(
        """
        SELECT account_id,
            SUM(CASE flow_id
                    WHEN 0 THEN -amount_minor
                    WHEN 1 THEN  amount_minor
                    ELSE 0
                END) AS net_minor,
            SUM(CASE WHEN flow_id = 0 THEN amount_minor ELSE 0 END) AS expense_minor,
            SUM(CASE WHEN flow_id = 1 THEN amount_minor ELSE 0 END) AS income_minor,
            amount_currency AS currency
        FROM transactions
        WHERE is_declined = 0 AND transfer_group_id IS NULL
        GROUP BY account_id, amount_currency
        """,
    )
    fun observeActivityPerAccount(): Flow<List<AccountActivityRow>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE is_hidden = 0
        ORDER BY timestamp DESC
        LIMIT :limit
        """,
    )
    fun observeTimeline(limit: Int = 200): Flow<List<TransactionEntity>>

    /**
     * Reactive stream of all transactions within a half-open `[from, until)` timestamp window.
     * Feeds the Insights screen — we compute category breakdowns, totals, and trend deltas in
     * the ViewModel from this single query.
     *
     * is_hidden is respected so reconciliation-only rows never skew insight numbers.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE is_hidden = 0
          AND timestamp >= :fromMillis
          AND timestamp < :untilMillis
        ORDER BY timestamp DESC
        """,
    )
    fun observeInRange(fromMillis: Long, untilMillis: Long): Flow<List<TransactionEntity>>

    @Query("UPDATE transactions SET transfer_group_id = :groupId, flow_id = :flowId WHERE id = :id")
    suspend fun assignTransferGroup(id: Long, groupId: Long, flowId: Int)

    /** Undo a pairing: drop the group link and restore the leg's original direction. */
    @Query("UPDATE transactions SET transfer_group_id = NULL, flow_id = :flowId WHERE id = :id")
    suspend fun clearTransferGroup(id: Long, flowId: Int)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Removes SMS rows whose body starts with [bodyPrefix] (a LIKE pattern) from [sender]. */
    @Query("DELETE FROM transactions WHERE sender_address = :sender AND method_id = 1 AND raw_body LIKE :bodyPrefix")
    suspend fun deleteSmsRowsByBodyPrefix(sender: String, bodyPrefix: String): Int

    /** When history begins; planning skips the first, partly covered cycle. */
    @Query("SELECT MIN(timestamp) FROM transactions WHERE is_hidden = 0")
    suspend fun earliestTimestamp(): Long?

    @Query("UPDATE transactions SET account_id = :targetId WHERE account_id = :sourceId")
    suspend fun reassignAccount(sourceId: Long, targetId: Long)

    /** Used by the Seeder's startup re-categorisation pass — see Seeder.recategorizeStale. */
    @Query("SELECT * FROM transactions")
    suspend fun allForRecategorise(): List<TransactionEntity>

    @Query("UPDATE transactions SET category_id = :categoryId, updated_at = strftime('%s','now')*1000 WHERE id = :id")
    suspend fun updateCategory(id: Long, categoryId: Long)

    @Query("UPDATE transactions SET category_id = :canonical WHERE category_id = :stale")
    suspend fun remapCategory(stale: Long, canonical: Long)

    /**
     * Returns the balance from the most recent (by body timestamp) balance-carrying
     * transaction for the given account. Used by the ingestor to refresh the cached account
     * balance after every insert — using this instead of the incoming SMS's balance directly
     * means out-of-order SMS delivery can't leave stale data.
     */
    @Query(
        """
        SELECT balance_minor FROM transactions
        WHERE account_id = :accountId AND balance_minor IS NOT NULL
        ORDER BY timestamp DESC
        LIMIT 1
        """,
    )
    suspend fun latestBalanceForAccount(accountId: Long): Long?
}
