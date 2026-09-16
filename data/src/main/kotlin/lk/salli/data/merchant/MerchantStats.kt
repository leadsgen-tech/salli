package lk.salli.data.merchant

import lk.salli.data.db.SalliDatabase

/**
 * How much of the user's money one merchant has taken, over all time.
 *
 * Feeds the transaction detail header — "Rs 18,400 across 14 visits · avg Rs 1,314" — so every
 * figure here is about *spending*: declined attempts, own-transfer legs, rows the user excluded
 * and accounts switched off in Settings are all out, exactly as they are on Home.
 */
data class MerchantStats(
    /** The merchant text as stored, trimmed. Case is whatever the bank sent. */
    val merchantKey: String,
    val count: Int,
    val totalMinor: Long,
    /** [totalMinor] ÷ [count], truncated. Never divides by zero — see [MerchantStatsService]. */
    val avgMinor: Long,
    /** Epoch millis of the first and last counted transaction. */
    val firstSeen: Long,
    val lastSeen: Long,
    val currency: String,
)

/**
 * Canonical form of a merchant name, shared by the stats query, the row title and
 * `MerchantLogos.resolve` in the design module: trim the edges, compare case-insensitively.
 *
 * Deliberately *not* aggressive — "Keells Super" and "KEELLS SUPER" are one merchant, but
 * "Keells Super" and "Keells Super Wattala" are two, because collapsing them would silently
 * merge branches the bank chose to distinguish.
 */
object MerchantKey {
    /** Null when there is no merchant to key on (transfers, ATM withdrawals, fees). */
    fun of(merchantRaw: String?): String? = merchantRaw?.trim()?.takeIf { it.isNotEmpty() }
}

class MerchantStatsService(private val db: SalliDatabase) {

    /**
     * Stats for [merchantRaw], or null when the merchant has no counted spending — a name that
     * only ever appeared on declined attempts, on a hidden account, or not at all.
     *
     * When a merchant was charged in more than one currency (a POS abroad) the currency with the
     * most transactions wins; mixing minor units across currencies would produce a number that
     * means nothing.
     */
    suspend fun forMerchant(merchantRaw: String?): MerchantStats? {
        val key = MerchantKey.of(merchantRaw) ?: return null
        val hidden = db.accounts().all().filter { it.isHidden }.map { it.id }
        // Room needs a non-empty list for `NOT IN`; -1 can never be a row id.
        val hiddenOrSentinel = hidden.ifEmpty { listOf(NO_HIDDEN_ACCOUNT) }

        val dominant = db.transactions().merchantTotals(key, hiddenOrSentinel).firstOrNull() ?: return null
        if (dominant.count <= 0) return null

        return MerchantStats(
            merchantKey = key,
            count = dominant.count,
            totalMinor = dominant.totalMinor,
            avgMinor = dominant.totalMinor / dominant.count,
            firstSeen = dominant.firstSeen,
            lastSeen = dominant.lastSeen,
            currency = dominant.currency,
        )
    }

    private companion object {
        const val NO_HIDDEN_ACCOUNT = -1L
    }
}
