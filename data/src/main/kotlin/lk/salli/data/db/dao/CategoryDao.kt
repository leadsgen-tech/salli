package lk.salli.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import lk.salli.data.db.entities.CategoryEntity
import lk.salli.data.db.entities.SubCategoryEntity

@Dao
interface CategoryDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSub(subs: List<SubCategoryEntity>): List<Long>

    @Query("SELECT * FROM categories ORDER BY id ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY id ASC")
    suspend fun all(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun byId(id: Long): CategoryEntity?

    @Query("SELECT * FROM sub_categories WHERE category_id = :categoryId ORDER BY id ASC")
    suspend fun subsFor(categoryId: Long): List<SubCategoryEntity>

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Re-points a **system** category at a design-token palette slot and icon name. Guarded on
     * `is_system` so a category the user made (or re-coloured) is never overwritten by a seed
     * upgrade. Used only by [lk.salli.data.seed.Seeder].
     */
    @Query(
        "UPDATE categories SET color_seed = :colorSeed, icon_name = :iconName " +
            "WHERE id = :id AND is_system = 1",
    )
    suspend fun updateSystemStyling(id: Long, colorSeed: Int, iconName: String)
}
