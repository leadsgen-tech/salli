package lk.salli.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "icon_name")
    val iconName: String,

    @ColumnInfo(name = "color_seed")
    val colorSeed: Int,

    @ColumnInfo(name = "is_system")
    val isSystem: Boolean = true,
)

@Entity(tableName = "sub_categories")
data class SubCategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "category_id")
    val categoryId: Long,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "icon_name")
    val iconName: String? = null,
)
