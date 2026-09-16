package lk.salli.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** A shared-expense group (a trip, a flat, a dinner). One currency per group. */
@Serializable
@Entity(tableName = "split_groups")
data class SplitGroupEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "currency")
    val currency: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "archived", defaultValue = "0")
    val archived: Boolean = false,
)

/** A person in a group. Exactly one member per group is the user ([isMe]). */
@Serializable
@Entity(tableName = "split_members", indices = [Index(value = ["group_id"])])
data class SplitMemberEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "group_id")
    val groupId: Long,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "is_me", defaultValue = "0")
    val isMe: Boolean = false,
)

/** Something one member paid for; who owes what is in [SplitShareEntity]. */
@Serializable
@Entity(
    tableName = "split_expenses",
    indices = [Index(value = ["group_id"]), Index(value = ["linked_transaction_id"])],
)
data class SplitExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "group_id")
    val groupId: Long,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,

    @ColumnInfo(name = "paid_by_member_id")
    val paidByMemberId: Long,

    /** [lk.salli.domain.split.SplitMethod] name. */
    @ColumnInfo(name = "method")
    val method: String,

    @ColumnInfo(name = "at")
    val at: Long,

    /** The bank transaction this expense was split from, when it came from "Split this". */
    @ColumnInfo(name = "linked_transaction_id")
    val linkedTransactionId: Long? = null,

    @ColumnInfo(name = "note")
    val note: String? = null,
)

/** One member's part of an expense. Shares of an expense always sum to its amount. */
@Serializable
@Entity(
    tableName = "split_shares",
    indices = [Index(value = ["expense_id"]), Index(value = ["member_id"])],
)
data class SplitShareEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "expense_id")
    val expenseId: Long,

    @ColumnInfo(name = "member_id")
    val memberId: Long,

    @ColumnInfo(name = "share_minor")
    val shareMinor: Long,
)

/** Money one member paid another to square up. */
@Serializable
@Entity(tableName = "split_settlements", indices = [Index(value = ["group_id"])])
data class SplitSettlementEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "group_id")
    val groupId: Long,

    @ColumnInfo(name = "from_member_id")
    val fromMemberId: Long,

    @ColumnInfo(name = "to_member_id")
    val toMemberId: Long,

    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,

    @ColumnInfo(name = "at")
    val at: Long,

    @ColumnInfo(name = "linked_transaction_id")
    val linkedTransactionId: Long? = null,
)
