package lk.salli.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A persisted transaction. Enum fields are stored as integer IDs (matching
 * [lk.salli.domain.TransactionFlow.id] etc.) rather than ordinals, so reorderings don't corrupt
 * existing rows.
 *
 * Monetary fields use minor units (cents) as [Long]; currency on the amount itself lives in
 * [amountCurrency]. Balance and fee currencies are always equal to [amountCurrency] — no column
 * duplication needed.
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["account_id"]),
        Index(value = ["timestamp"]),
        Index(value = ["sender_address", "timestamp"]),
        Index(value = ["transfer_group_id"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "account_id")
    val accountId: Long,

    @ColumnInfo(name = "merchant_id")
    val merchantId: Long? = null,

    @ColumnInfo(name = "category_id")
    val categoryId: Long? = null,

    @ColumnInfo(name = "sub_category_id")
    val subCategoryId: Long? = null,

    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,

    @ColumnInfo(name = "amount_currency")
    val amountCurrency: String,

    @ColumnInfo(name = "fee_minor")
    val feeMinor: Long? = null,

    @ColumnInfo(name = "balance_minor")
    val balanceMinor: Long? = null,

    /** Epoch millis. */
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "flow_id")
    val flowId: Int,

    @ColumnInfo(name = "method_id")
    val methodId: Int,

    @ColumnInfo(name = "type_id")
    val typeId: Int,

    @ColumnInfo(name = "sender_address")
    val senderAddress: String? = null,

    @ColumnInfo(name = "account_suffix")
    val accountSuffix: String? = null,

    @ColumnInfo(name = "merchant_raw")
    val merchantRaw: String? = null,

    @ColumnInfo(name = "location")
    val location: String? = null,

    /** Original SMS body, kept for retry/audit. */
    @ColumnInfo(name = "raw_body")
    val rawBody: String? = null,

    @ColumnInfo(name = "transfer_group_id")
    val transferGroupId: Long? = null,

    @ColumnInfo(name = "is_hidden")
    val isHidden: Boolean = false,

    @ColumnInfo(name = "is_declined")
    val isDeclined: Boolean = false,

    @ColumnInfo(name = "note")
    val note: String? = null,

    /**
     * True when the user explicitly chose a category/merchant for this row (via the detail
     * sheet or the notification inline reply). Guards the startup recategorise pass from
     * stomping user intent — see Seeder.recategorizeStale.
     */
    @ColumnInfo(name = "user_tagged", defaultValue = "0")
    val userTagged: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
