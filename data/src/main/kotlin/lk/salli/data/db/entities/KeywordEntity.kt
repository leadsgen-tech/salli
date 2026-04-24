package lk.salli.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local categorization cache. Populated from (a) seed data shipped with the app, (b) user's
 * manual re-categorizations (c) derived merchant→category mappings. Allows fast pre-classification
 * without scanning merchants.
 */
@Entity(
    tableName = "keywords",
    indices = [Index(value = ["keyword"], unique = true)],
)
data class KeywordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Always lowercased & trimmed before insert. */
    @ColumnInfo(name = "keyword")
    val keyword: String,

    @ColumnInfo(name = "category_id")
    val categoryId: Long,

    @ColumnInfo(name = "sub_category_id")
    val subCategoryId: Long? = null,

    /** "seed" | "user" | "merchant-derived" */
    @ColumnInfo(name = "source")
    val source: String,
)
