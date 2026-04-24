package lk.salli.data.seed

import lk.salli.data.categorization.KeywordCategorizer
import lk.salli.data.categorization.TypeCategorizer
import lk.salli.data.db.SalliDatabase
import lk.salli.data.db.entities.KeywordEntity
import lk.salli.domain.TransactionFlow
import lk.salli.domain.TransactionType

/**
 * Installs seed categories + keyword mappings. Idempotent — safe to call on every startup
 * because both inserts use OnConflictStrategy.IGNORE.
 *
 * Also re-applies categorisation to existing transactions whose `categoryId` looks wrong.
 * Real-world transactions drifted into the wrong bucket (e.g. null-merchant BOC transfers
 * tagged as "Food & Dining") and staying silent about it isn't an option — re-running the
 * standard pipeline on every startup is cheap and self-heals.
 */
class Seeder(private val db: SalliDatabase) {

    private companion object {
        /** Matches TransactionIngestor.DEFAULT_SUFFIX. */
        const val PLACEHOLDER_SUFFIX = "—"
    }

    suspend fun run() {
        // One-shot dedupe for DBs that have accumulated phantom category rows from an
        // earlier build where every launch kept inserting fresh copies (no unique index on
        // name, and `OnConflictStrategy.IGNORE` only fires on PK conflict). Must run before
        // anything else reads category ids.
        deduplicateCategories()

        // Collapses legacy damage from an earlier bug where the duplicate detector missed
        // re-parsed PeoplesBank confirms with post-merge shape differences. Safe to run
        // every boot — a no-op on clean DBs.
        deduplicateByRawBody()
        fusePlaceholderAccounts()

        // Insert only categories whose names aren't already present — avoids re-introducing
        // the duplicates we just cleaned up.
        val existingNames = db.categories().all().map { it.name }.toSet()
        val missing = SeedCategories.all.filter { it.name !in existingNames }
        if (missing.isNotEmpty()) {
            db.categories().insertAll(missing)
        }

        val byName = db.categories().all().associateBy { it.name }

        val kwEntities = SeedKeywords.byCategory.flatMap { (categoryName, keywords) ->
            val categoryId = byName[categoryName]?.id ?: return@flatMap emptyList()
            keywords.map { kw ->
                KeywordEntity(
                    keyword = kw.lowercase().trim(),
                    categoryId = categoryId,
                    source = "seed",
                )
            }
        }

        if (kwEntities.isNotEmpty()) {
            db.keywords().insertAll(kwEntities)
        }

        recategorizeStale()
    }

    /**
     * Repoints every foreign reference to the lowest-id category per name, then drops the
     * duplicate rows. Safe when no duplicates exist (iterates over an empty list).
     */
    private suspend fun deduplicateCategories() {
        val all = db.categories().all()
        val byName = all.groupBy { it.name }
        val remaps = mutableMapOf<Long, Long>()  // stale id → canonical id
        val toDelete = mutableListOf<Long>()
        for ((_, rows) in byName) {
            if (rows.size <= 1) continue
            val canonical = rows.minBy { it.id }.id
            for (row in rows) {
                if (row.id != canonical) {
                    remaps[row.id] = canonical
                    toDelete += row.id
                }
            }
        }
        if (remaps.isEmpty()) return

        for ((stale, canonical) in remaps) {
            db.transactions().remapCategory(stale, canonical)
            db.keywords().remapCategory(stale, canonical)
            db.categories().deleteById(stale)
        }
    }

    /**
     * Collapses groups of transactions that share an identical raw SMS body — symptom of an
     * older build where a re-parsed PeoplesBank confirm slipped past the duplicate detector
     * and inserted afresh on every pull-to-refresh. Keeps the lowest-id row per group and
     * drops the rest.
     */
    private suspend fun deduplicateByRawBody() {
        val all = db.transactions().allForRecategorise()
        val groups = all
            .filter { !it.rawBody.isNullOrBlank() }
            .groupBy { it.rawBody }
        for ((_, rows) in groups) {
            if (rows.size <= 1) continue
            val keeper = rows.minBy { it.id }.id
            for (row in rows) {
                if (row.id != keeper) db.transactions().deleteById(row.id)
            }
        }
    }

    /**
     * Merges "—" placeholder accounts into their sender's real account. Placeholders are
     * created the first time an orphan confirm SMS arrives (no account number in the body);
     * once a primary SMS reveals the real suffix, subsequent orphans already route correctly
     * via [lk.salli.data.db.dao.AccountDao.mostRecentForSender], but any transactions already
     * attached to the placeholder stay attached until this pass moves them.
     */
    private suspend fun fusePlaceholderAccounts() {
        val accounts = db.accounts().all()
        val bySender = accounts.groupBy { it.senderAddress }
        for ((_, rows) in bySender) {
            val placeholder = rows.firstOrNull { it.accountSuffix == PLACEHOLDER_SUFFIX } ?: continue
            val real = rows.firstOrNull { it.accountSuffix != PLACEHOLDER_SUFFIX } ?: continue
            db.transactions().reassignAccount(placeholder.id, real.id)
            db.accounts().deleteById(placeholder.id)
        }
    }

    /**
     * Walk every transaction, compute what category it *should* have right now, and write
     * it back if it differs. Cheap — one pass, one row update per drift.
     *
     * Why: earlier build(s) stamped rows with the wrong category (likely a race between
     * seeder and first import, or a since-fixed categorizer bug). The symptoms persist in
     * the DB because categoryId is never recomputed after insert. This pass closes that gap.
     */
    private suspend fun recategorizeStale() {
        val keywordCat = KeywordCategorizer(db.keywords())
        val typeCat = TypeCategorizer(db.categories())

        val all = db.transactions().allForRecategorise()
        for (row in all) {
            // Respect user intent — anything they explicitly tagged (via detail sheet or
            // the notification inline reply) is load-bearing. Leave it alone.
            if (row.userTagged) continue
            val type = TransactionType.fromId(row.typeId)
            val flow = TransactionFlow.fromId(row.flowId)
            val kw = keywordCat.categorize(row.merchantRaw)
            val fallback = if (kw == null) typeCat.categorize(type, flow) else null
            val desired = kw?.categoryId ?: fallback
            if (desired != null && desired != row.categoryId) {
                db.transactions().updateCategory(row.id, desired)
            }
        }
    }
}
