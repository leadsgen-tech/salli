package lk.salli.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Pair that ties a debit transaction in bank A to a credit transaction in bank B when both
 * belong to the same cross-account movement. Transactions in a group are excluded from
 * income/expense totals.
 */
@Entity(tableName = "transfer_groups")
data class TransferGroupEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "debit_tx_id")
    val debitTxId: Long,

    @ColumnInfo(name = "credit_tx_id")
    val creditTxId: Long,

    /** 0.0..1.0 — headroom for surfacing "likely but not certain" pairs in the UI. */
    @ColumnInfo(name = "confidence")
    val confidence: Float = 1f,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
