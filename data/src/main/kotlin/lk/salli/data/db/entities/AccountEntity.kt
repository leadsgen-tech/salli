package lk.salli.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import kotlinx.serialization.Serializable
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One Account row per (senderAddress, accountNumberSuffix) we've ever seen. Created the first
 * time an SMS from that combination is successfully parsed.
 */
@Serializable
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

    /**
     * User toggled the account off in Settings. Hidden accounts keep ingesting SMS (so
     * nothing is lost) but drop out of every screen, total and chart until toggled back.
     */
    @ColumnInfo(name = "is_hidden", defaultValue = "0")
    val isHidden: Boolean = false,
)
