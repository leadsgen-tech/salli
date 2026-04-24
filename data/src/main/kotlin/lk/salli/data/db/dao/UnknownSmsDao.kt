package lk.salli.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import lk.salli.data.db.entities.UnknownSmsEntity

@Dao
interface UnknownSmsDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: UnknownSmsEntity): Long

    @Query("SELECT * FROM unknown_sms WHERE resolution IS NULL ORDER BY received_at DESC")
    fun observePending(): Flow<List<UnknownSmsEntity>>

    @Query("UPDATE unknown_sms SET resolution = :resolution WHERE id = :id")
    suspend fun resolve(id: Long, resolution: String)

    @Query("SELECT COUNT(*) FROM unknown_sms WHERE resolution IS NULL")
    fun observePendingCount(): Flow<Int>

    @Query("DELETE FROM unknown_sms WHERE id = :id")
    suspend fun deleteById(id: Long)
}
