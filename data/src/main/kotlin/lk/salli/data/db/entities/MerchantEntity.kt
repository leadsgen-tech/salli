package lk.salli.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "merchants")
data class MerchantEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "canonical_name")
    val canonicalName: String,

    @ColumnInfo(name = "category_id")
    val categoryId: Long? = null,

    @ColumnInfo(name = "sub_category_id")
    val subCategoryId: Long? = null,

    @ColumnInfo(name = "logo_asset")
    val logoAsset: String? = null,

    @ColumnInfo(name = "is_recurring")
    val isRecurring: Boolean = false,
)

/**
 * Maps raw SMS merchant strings (e.g. "KEELLS SUPER COLOMBO 04") to a canonical [MerchantEntity].
 * Multiple aliases per merchant are normal.
 */
@Entity(
    tableName = "merchant_aliases",
    indices = [Index(value = ["raw_name"], unique = true)],
)
data class MerchantAliasEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "merchant_id")
    val merchantId: Long,

    @ColumnInfo(name = "raw_name")
    val rawName: String,
)
