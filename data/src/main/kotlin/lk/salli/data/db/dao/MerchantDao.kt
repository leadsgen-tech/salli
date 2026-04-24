package lk.salli.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import lk.salli.data.db.entities.MerchantAliasEntity
import lk.salli.data.db.entities.MerchantEntity

@Dao
interface MerchantDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(merchant: MerchantEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlias(alias: MerchantAliasEntity): Long

    @Query("SELECT * FROM merchants WHERE id = :id")
    suspend fun byId(id: Long): MerchantEntity?

    @Query("SELECT * FROM merchants WHERE canonical_name = :name COLLATE NOCASE LIMIT 1")
    suspend fun byCanonicalName(name: String): MerchantEntity?

    @Query(
        """
        SELECT m.* FROM merchants m
        INNER JOIN merchant_aliases a ON a.merchant_id = m.id
        WHERE a.raw_name = :raw COLLATE NOCASE
        LIMIT 1
        """,
    )
    suspend fun byAlias(raw: String): MerchantEntity?
}
