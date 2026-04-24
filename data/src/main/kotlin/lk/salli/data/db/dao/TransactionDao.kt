package lk.salli.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import lk.salli.data.db.entities.TransactionEntity

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

    /** Recent rows across all senders — feeds InternalTransferDetector. */
    @Query("SELECT * FROM transactions WHERE timestamp >= :sinceTimestamp ORDER BY timestamp DESC")
    suspend fun recentAll(sinceTimestamp: Long): List<TransactionEntity>

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

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

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
