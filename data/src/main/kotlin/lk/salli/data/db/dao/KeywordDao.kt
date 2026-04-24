package lk.salli.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import lk.salli.data.db.entities.KeywordEntity

@Dao
interface KeywordDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(keywords: List<KeywordEntity>): List<Long>

    @Query("SELECT * FROM keywords")
    suspend fun all(): List<KeywordEntity>

    /**
     * Case-insensitive substring hit. SQLite `LIKE` is case-insensitive by default for ASCII;
     * the [kwPattern] argument should already include `%` wildcards.
     */
    @Query("SELECT * FROM keywords WHERE :kwPattern LIKE '%' || keyword || '%' LIMIT 1")
    suspend fun firstMatching(kwPattern: String): KeywordEntity?

    @Query("UPDATE keywords SET category_id = :canonical WHERE category_id = :stale")
    suspend fun remapCategory(stale: Long, canonical: Long)
}
