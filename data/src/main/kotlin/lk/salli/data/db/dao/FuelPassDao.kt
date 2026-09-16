package lk.salli.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import lk.salli.data.db.entities.FuelPassRecordEntity

@Dao
interface FuelPassDao {

    /** Returns -1 when this (vehicle, timestamp) fill-up is already stored. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: FuelPassRecordEntity): Long

    @Query("SELECT DISTINCT vehicle FROM fuel_pass_records ORDER BY vehicle")
    fun observeVehicles(): Flow<List<String>>

    @Query("SELECT * FROM fuel_pass_records WHERE vehicle = :vehicle ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecordsForVehicle(vehicle: String, limit: Int = 8): Flow<List<FuelPassRecordEntity>>

    @Query("SELECT * FROM fuel_pass_records ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<FuelPassRecordEntity>>

    /** The newest fill-up per vehicle — carries the current weekly balance and reset date. */
    @Query(
        """
        SELECT f.* FROM fuel_pass_records f
        WHERE f.timestamp = (SELECT MAX(timestamp) FROM fuel_pass_records WHERE vehicle = f.vehicle)
        ORDER BY f.vehicle
        """,
    )
    suspend fun latestPerVehicle(): List<FuelPassRecordEntity>

    @Query("UPDATE fuel_pass_records SET reminder_sent = 1 WHERE id = :id")
    suspend fun markReminderSent(id: Long)

    @Query("SELECT COUNT(*) FROM fuel_pass_records")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM fuel_pass_records")
    suspend fun deleteAll()
}
