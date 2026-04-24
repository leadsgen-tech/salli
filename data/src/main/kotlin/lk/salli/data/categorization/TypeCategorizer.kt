package lk.salli.data.categorization

import lk.salli.data.db.dao.CategoryDao
import lk.salli.domain.TransactionFlow
import lk.salli.domain.TransactionType

/**
 * Fallback categorization when the keyword matcher came up empty. Uses the transaction's
 * parsed type + flow to guess a sensible category — e.g. an ATM withdrawal should land in
 * "Cash", a CEFT transfer in "Transfers", a cheque deposit in "Salary" (best guess), etc.
 *
 * Without this, every transfer and cash withdrawal drops into Uncategorised and dominates
 * Insights / Top Spenders / Budgets with meaningless bulk.
 *
 * Resolves category names → IDs lazily on first use so the seeder's insertion order doesn't
 * matter. Cached after first resolve since category IDs are stable for the app's lifetime.
 */
class TypeCategorizer(private val categoryDao: CategoryDao) {

    @Volatile private var cache: Map<String, Long>? = null

    suspend fun categorize(
        type: TransactionType,
        @Suppress("UNUSED_PARAMETER") flow: TransactionFlow,
    ): Long? {
        val lookup = cache ?: run {
            categoryDao.all().associate { it.name to it.id }.also { cache = it }
        }
        val name: String? = when (type) {
            TransactionType.ATM, TransactionType.CDM -> "Cash"
            // Cheques are a transfer mechanism, not necessarily salary. Previously we tagged
            // incoming cheques as Salary, which was wrong more often than right (refunds,
            // client payments, gifts all come via cheque too). Neutral default is Transfers;
            // users re-tag manually and in future a merchant/payer rule can promote specific
            // cheque sources back to Salary.
            TransactionType.CHEQUE,
            TransactionType.ONLINE_TRANSFER,
            TransactionType.CEFT,
            TransactionType.SLIPS,
            TransactionType.MOBILE_PAYMENT ->
                "Transfers"
            TransactionType.FEE -> "Fees"
            // POS without a keyword hit — land in Shopping as a sensible catch-all.
            // Users can re-categorise individual rows via the transaction detail sheet and
            // (post-v1) that'll save a merchant→category alias for future matches.
            TransactionType.POS -> "Shopping"
            TransactionType.BALANCE_CORRECTION -> null // system rows — stay uncategorised
            TransactionType.DECLINED, TransactionType.OTHER -> null
        }
        return name?.let { lookup[it] }
    }
}
