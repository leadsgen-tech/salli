package lk.salli.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * A repeating payment or credit found by [lk.salli.domain.recurring.RecurringDetector]. The
 * detection columns are rewritten on every recompute; [userState] and the notice markers are
 * the user's and are never overwritten by it.
 */
@Serializable
@Entity(
    tableName = "recurring_series",
    indices = [Index(value = ["series_key", "flow_id", "currency"], unique = true)],
)
data class RecurringSeriesEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "series_key")
    val seriesKey: String,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "flow_id")
    val flowId: Int,

    @ColumnInfo(name = "currency")
    val currency: String,

    /** [lk.salli.domain.recurring.Cadence] name; null for a failing subscription never charged. */
    @ColumnInfo(name = "cadence")
    val cadence: String? = null,

    @ColumnInfo(name = "interval_days")
    val intervalDays: Double? = null,

    @ColumnInfo(name = "typical_amount_minor")
    val typicalAmountMinor: Long,

    @ColumnInfo(name = "is_fixed")
    val isFixed: Boolean,

    @ColumnInfo(name = "occurrences")
    val occurrences: Int,

    @ColumnInfo(name = "declined_attempts")
    val declinedAttempts: Int = 0,

    @ColumnInfo(name = "last_at")
    val lastAt: Long,

    @ColumnInfo(name = "next_at")
    val nextAt: Long? = null,

    /** [lk.salli.domain.recurring.RecurringStatus] name. */
    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "confidence")
    val confidence: Double,

    /** [AUTO], [CONFIRMED] or [DISMISSED]. Set by the user only. */
    @ColumnInfo(name = "user_state")
    val userState: String = AUTO,

    /** False once the pattern stops showing up; such rows are kept only when the user acted on them. */
    @ColumnInfo(name = "is_detected")
    val isDetected: Boolean = true,

    /** The [nextAt] a "due tomorrow" notice was sent for, so each charge is announced once. */
    @ColumnInfo(name = "notified_due_at")
    val notifiedDueAt: Long? = null,

    /** The "keeps being declined" notice went out; reset when the series stops failing. */
    @ColumnInfo(name = "failing_notified")
    val failingNotified: Boolean = false,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
) {
    companion object {
        const val AUTO = "AUTO"
        const val CONFIRMED = "CONFIRMED"
        const val DISMISSED = "DISMISSED"
    }
}
