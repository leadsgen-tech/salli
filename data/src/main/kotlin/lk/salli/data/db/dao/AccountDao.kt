package lk.salli.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import lk.salli.data.db.entities.AccountEntity

@Dao
interface AccountDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Query("SELECT * FROM accounts WHERE sender_address = :sender AND account_suffix = :suffix LIMIT 1")
    suspend fun findBySenderAndSuffix(sender: String, suffix: String): AccountEntity?

    /**
     * Picks the user's "primary" account for a bank based on recent activity. Used when an
     * SMS (typically an orphan confirm — Mobile Payment or Fund Transfer — whose matching
     * debit is missing from the inbox) carries no account identifier. Falling back to the
     * active account avoids creating a ghost "unknown" account when only one real account
     * exists for that sender.
     */
    @Query(
        """
        SELECT a.* FROM accounts a
        LEFT JOIN transactions t ON t.account_id = a.id
        WHERE a.sender_address = :sender
          AND a.is_archived = 0
          AND a.account_suffix != '—'
        GROUP BY a.id
        ORDER BY MAX(t.timestamp) DESC, a.id DESC
        LIMIT 1
        """,
    )
    suspend fun mostRecentForSender(sender: String): AccountEntity?

    @Query("SELECT * FROM accounts ORDER BY id ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY id ASC")
    suspend fun all(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): AccountEntity?

    @Query("UPDATE accounts SET balance_minor = :balance WHERE id = :id")
    suspend fun updateBalance(id: Long, balance: Long?)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Atomically sets the account's cached balance to whatever the most-recent (by body
     * timestamp) balance-carrying transaction for that account says. One SQL statement — no
     * read-then-write race, no chance of a stale read.
     */
    @Query(
        """
        UPDATE accounts
        SET balance_minor = (
            SELECT balance_minor FROM transactions
            WHERE account_id = :accountId AND balance_minor IS NOT NULL
            ORDER BY timestamp DESC
            LIMIT 1
        )
        WHERE id = :accountId
        """,
    )
    suspend fun refreshCachedBalance(accountId: Long)
}
