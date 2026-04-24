package lk.salli.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import lk.salli.data.db.entities.TransferGroupEntity

@Dao
interface TransferGroupDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(group: TransferGroupEntity): Long

    @Query("SELECT * FROM transfer_groups WHERE id = :id")
    suspend fun byId(id: Long): TransferGroupEntity?
}
