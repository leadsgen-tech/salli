package lk.salli.parser.merge

import lk.salli.parser.ParsedTransaction
import kotlin.math.abs

/**
 * Detects when the bank has re-sent the same SMS we already processed.
 *
 * Observed in real data: BOC sometimes emits the same `CEFT Transfer Debit` SMS twice within a
 * few seconds. Both messages would produce identical parsed transactions — naïvely persisting
 * both would double-count the debit against the user's balance.
 *
 * Signals ranked by strength:
 *  - **Balance** (when both messages carry one) is the strongest discriminator. Two genuine
 *    back-to-back transactions of the same amount must each leave a different balance.
 *  - **Merchant** (when balance is absent, e.g. ComBank purchases) substitutes: two genuine
 *    purchases of the same amount at different merchants are distinguishable.
 *  - **Time window** of 5 minutes bounds the comparison — outside that we trust the signal is
 *    noise rather than a dup. This also naturally rejects a monthly subscription that debits
 *    the exact same amount every month.
 */
object DuplicateDetector {

    /** Time window inside which two messages with matching keys are considered the same event. */
    const val WINDOW_MS: Long = 5 * 60 * 1000L

    /**
     * @return the earlier [ParsedTransaction] from [recent] that [candidate] duplicates, or
     *         null if [candidate] is new.
     */
    fun findDuplicate(
        candidate: ParsedTransaction,
        recent: List<ParsedTransaction>,
    ): ParsedTransaction? = recent.firstOrNull { isDuplicate(candidate, it) }

    private fun isDuplicate(a: ParsedTransaction, b: ParsedTransaction): Boolean {
        if (a.senderAddress != b.senderAddress) return false
        if (a.amount != b.amount) return false
        if (a.isDeclined != b.isDeclined) return false
        if (a.accountNumberSuffix != b.accountNumberSuffix) return false
        if (abs(a.timestamp - b.timestamp) > WINDOW_MS) return false

        // If both carry balances they must match (post-transaction balance is a fingerprint).
        // If exactly one carries a balance, the other side is likely a secondary notification
        // (e.g. Mobile Payment confirm) from the same event — merging happens elsewhere, we
        // don't flag as duplicate here.
        if (a.balance != null && b.balance != null) {
            return a.balance == b.balance
        }

        // Neither carries a balance — use the merchant as the discriminator. ComBank SMS always
        // include a merchant, so this works for the card-transaction case.
        if (a.balance == null && b.balance == null) {
            return a.merchantRaw == b.merchantRaw
        }

        return false
    }
}
