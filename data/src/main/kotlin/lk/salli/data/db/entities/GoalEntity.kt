package lk.salli.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * A savings goal. Progress is the sum of its [GoalContributionEntity] rows, or the linked
 * account's balance when [linkedAccountId] is set (a dedicated savings account).
 */
@Serializable
@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "target_minor")
    val targetMinor: Long,

    @ColumnInfo(name = "currency")
    val currency: String,

    @ColumnInfo(name = "target_date")
    val targetDate: Long? = null,

    @ColumnInfo(name = "linked_account_id")
    val linkedAccountId: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false,
)

/** Money the user set aside toward a goal. */
@Serializable
@Entity(tableName = "goal_contributions", indices = [Index(value = ["goal_id"])])
data class GoalContributionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "goal_id")
    val goalId: Long,

    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,

    @ColumnInfo(name = "at")
    val at: Long,

    @ColumnInfo(name = "note")
    val note: String? = null,
)
