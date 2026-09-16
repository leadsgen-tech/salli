package lk.salli.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import kotlinx.serialization.Serializable
import androidx.room.Index
import androidx.room.PrimaryKey

/** One National Fuel Pass fill-up. Volumes are millilitres. Unique per (vehicle, timestamp). */
@Serializable
@Entity(
    tableName = "fuel_pass_records",
    indices = [
        Index(value = ["vehicle", "timestamp"], unique = true),
        Index(value = ["vehicle"]),
    ],
)
data class FuelPassRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "vehicle")
    val vehicle: String,

    @ColumnInfo(name = "litres_ml")
    val litresMilli: Long,

    @ColumnInfo(name = "weekly_balance_ml")
    val weeklyBalanceMilli: Long,

    @ColumnInfo(name = "station_code")
    val stationCode: String? = null,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    /** Midnight of the day the weekly quota resets, when the SMS said so. */
    @ColumnInfo(name = "resets_on")
    val resetsOn: Long? = null,

    /** Set once the "last day to fill up" reminder for this week has been posted. */
    @ColumnInfo(name = "reminder_sent", defaultValue = "0")
    val reminderSent: Boolean = false,

    @ColumnInfo(name = "raw_body")
    val rawBody: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
