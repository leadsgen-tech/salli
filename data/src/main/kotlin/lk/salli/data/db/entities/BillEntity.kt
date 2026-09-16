package lk.salli.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import kotlinx.serialization.Serializable
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One utility bill as the biller last described it. An issued bill and its later reminders
 * collapse onto the same row (the newest outstanding figure wins); a payment receipt flips
 * [isPaid]. [rawBodyHash] is unique so re-importing the inbox is a no-op.
 */
@Serializable
@Entity(
    tableName = "bills",
    indices = [
        Index(value = ["raw_body_hash"], unique = true),
        Index(value = ["biller", "account_ref", "is_paid"]),
    ],
)
data class BillEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "biller")
    val biller: String,

    @ColumnInfo(name = "account_ref")
    val accountRef: String,

    @ColumnInfo(name = "amount_due_minor")
    val amountDueMinor: Long,

    @ColumnInfo(name = "currency")
    val currency: String,

    @ColumnInfo(name = "due_date")
    val dueDate: Long? = null,

    @ColumnInfo(name = "period_label")
    val periodLabel: String? = null,

    /** See [lk.salli.parser.utility.BillKind.id] — the latest message kind seen for this bill. */
    @ColumnInfo(name = "kind_id")
    val kindId: Int,

    @ColumnInfo(name = "is_paid", defaultValue = "0")
    val isPaid: Boolean = false,

    @ColumnInfo(name = "paid_at")
    val paidAt: Long? = null,

    /** Total paid toward this bill so far. Below [amountDueMinor] while a bill is part-paid. */
    @ColumnInfo(name = "paid_amount_minor")
    val paidAmountMinor: Long? = null,

    /** The user tapped "Mark paid". The status replay never reopens such a bill. */
    @ColumnInfo(name = "paid_manually", defaultValue = "0")
    val paidManually: Boolean = false,

    /**
     * A newer statement for the same account has arrived. SLT-style bills are cumulative
     * (the new "Total Payable" already includes any unpaid balance), so the old row is neither
     * open nor paid; it just stops mattering.
     */
    @ColumnInfo(name = "is_superseded", defaultValue = "0")
    val isSuperseded: Boolean = false,

    /** 0 = none, 1 = "due in N days" sent, 2 = "due today" sent. */
    @ColumnInfo(name = "reminder_stage", defaultValue = "0")
    val reminderStage: Int = 0,

    @ColumnInfo(name = "raw_body_hash")
    val rawBodyHash: String,

    @ColumnInfo(name = "raw_body")
    val rawBody: String,

    /** When the originating SMS reached the phone. */
    @ColumnInfo(name = "received_at")
    val receivedAt: Long,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
