package lk.salli.data.categorization

import lk.salli.data.db.dao.KeywordDao
import lk.salli.data.db.entities.KeywordEntity

/**
 * Fast local categorization. First line of defense for every parsed transaction — looks up a
 * substring match against the keywords table (seed data + user edits + merchant-derived).
 *
 * Returns `null` when no keyword hits; the caller can fall back to other strategies (exact
 * merchant-alias lookup, type-based heuristics) or leave the transaction uncategorized.
 */
class KeywordCategorizer(private val keywordDao: KeywordDao) {

    data class Hit(val categoryId: Long, val subCategoryId: Long?)

    suspend fun categorize(merchantRaw: String?): Hit? {
        if (merchantRaw.isNullOrBlank()) return null
        val needle = merchantRaw.lowercase().trim()
        val hit = keywordDao.firstMatching(needle) ?: return null
        return Hit(categoryId = hit.categoryId, subCategoryId = hit.subCategoryId)
    }

    /** For testing / bulk seeding. */
    suspend fun seed(keywords: List<KeywordEntity>) {
        keywordDao.insertAll(keywords)
    }
}
