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
     * Atomically recomputes the account's cached balance.
     *
     * Strategy:
     *  - **Anchor:** the most-recent balance-carrying transaction (the SMS body included an
     *    `Av_Bal` / `Balance available` clause). That row's `balance_minor` is treated as
     *    ground truth at its timestamp.
     *  - **Imputed delta:** every newer transaction for this account that came in *without*
     *    a balance contributes to the cached balance — debits subtract, credits add. Declined
     *    attempts and transfer-grouped legs (internal transfers, net-zero) are excluded.
     *
     * This handles People's Bank debit SMS that ship without `Av_Bal` (2026 vintage) and
     * any future bank shape that omits the balance — the cached chip stays in sync without
     * needing a fresh balance-bearing SMS to land.
     *
     * If the account has *no* balance-carrying transactions at all (card-only senders like
     * ComBank `#5166`), the outer SELECT returns NULL and `balance_minor` stays unset.
     */
    @Query(
        """
        UPDATE accounts SET balance_minor = (
            SELECT (
                SELECT balance_minor FROM transactions
                WHERE account_id = :accountId AND balance_minor IS NOT NULL
                ORDER BY timestamp DESC LIMIT 1
            ) + IFNULL((
                SELECT SUM(
                    CASE flow_id
                        WHEN 0 THEN -amount_minor
                        WHEN 1 THEN amount_minor
                        ELSE 0
                    END
                )
                FROM transactions
                WHERE account_id = :accountId
                  AND balance_minor IS NULL
                  AND is_declined = 0
                  AND transfer_group_id IS NULL
                  AND timestamp > IFNULL((
                      SELECT timestamp FROM transactions
                      WHERE account_id = :accountId AND balance_minor IS NOT NULL
                      ORDER BY timestamp DESC LIMIT 1
                  ), 0)
            ), 0)
        )
        WHERE id = :accountId
        """,
    )
    suspend fun refreshCachedBalance(accountId: Long)
}
