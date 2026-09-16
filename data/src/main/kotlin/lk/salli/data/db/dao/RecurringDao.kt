package lk.salli.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import lk.salli.data.db.entities.RecurringSeriesEntity

@Dao
interface RecurringDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(series: RecurringSeriesEntity): Long

    @Update
    suspend fun update(series: RecurringSeriesEntity)

    @Query("DELETE FROM recurring_series WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM recurring_series ORDER BY id")
    suspend fun all(): List<RecurringSeriesEntity>

    @Query("SELECT * FROM recurring_series ORDER BY CASE WHEN next_at IS NULL THEN 1 ELSE 0 END, next_at ASC, display_name ASC")
    fun observeAll(): Flow<List<RecurringSeriesEntity>>

    @Query("UPDATE recurring_series SET user_state = :state, updated_at = :at WHERE id = :id")
    suspend fun setUserState(id: Long, state: String, at: Long)

    @Query("UPDATE recurring_series SET notified_due_at = :nextAt WHERE id = :id")
    suspend fun setNotifiedDueAt(id: Long, nextAt: Long)

    @Query("UPDATE recurring_series SET failing_notified = :notified WHERE id = :id")
    suspend fun setFailingNotified(id: Long, notified: Boolean)
}
