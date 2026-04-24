package lk.salli.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One Account row per (senderAddress, accountNumberSuffix) we've ever seen. Created the first
 * time an SMS from that combination is successfully parsed.
 */
@Entity(
    tableName = "accounts",
    indices = [Index(value = ["sender_address", "account_suffix"], unique = true)],
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "sender_address")
    val senderAddress: String,

    @ColumnInfo(name = "account_suffix")
    val accountSuffix: String,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "currency")
    val currency: String,

    /** See [lk.salli.domain.AccountType.id]. */
    @ColumnInfo(name = "account_type_id")
    val accountTypeId: Int,

    @ColumnInfo(name = "purpose")
    val purpose: String? = null,

    @ColumnInfo(name = "balance_minor")
    val balanceMinor: Long? = null,

    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false,
)
