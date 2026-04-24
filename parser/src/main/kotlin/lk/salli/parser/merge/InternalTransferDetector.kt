package lk.salli.parser.merge

import lk.salli.domain.TransactionFlow
import lk.salli.parser.ParsedTransaction
import kotlin.math.abs

/**
 * Pairs a debit in bank A with a credit in bank B (or vice versa) that together represent a
 * single movement between accounts the user owns.
 *
 * Real example from the user's inbox on 2026-04-07:
 *   PeoplesBank  debit 50025 LKR from 280-2001****68 at 10:05  (with a 25 fee)
 *   BOC          credit 50000 LKR to  XXXXXXXXXX870   at 10:05
 * Same currency, opposite flows, different senders, amounts within a plausible fee, seconds
 * apart → paired. Both live in the DB, but the pair is marked as an internal transfer and
 * excluded from the user's income/expense totals.
 *
 * The detector is signal-only: it returns the counterpart candidate. Creating the transfer
 * group, updating both rows with `transferGroupId`, and reclassifying both as
 * [TransactionFlow.TRANSFER] happens in the ingestion layer.
 */
object InternalTransferDetector {

    /** How far apart the two halves can be. 48 h covers slow interbank settlement windows. */
    const val WINDOW_MS: Long = 48L * 60 * 60 * 1000L

    /** Upper bound for the fee delta between the two halves, in minor units (Rs 100). */
    const val MAX_FEE_MINOR: Long = 10_000L

    fun findCounterpart(
        incoming: ParsedTransaction,
        recent: List<ParsedTransaction>,
    ): ParsedTransaction? {
        if (!isEligible(incoming)) return null
        return recent.firstOrNull { candidate -> isEligible(candidate) && isCounterpart(incoming, candidate) }
    }

    private fun isEligible(p: ParsedTransaction): Boolean {
        if (p.isDeclined) return false
        return p.flow == TransactionFlow.EXPENSE || p.flow == TransactionFlow.INCOME
    }

    private fun isCounterpart(a: ParsedTransaction, b: ParsedTransaction): Boolean {
        // Must be different banks — same-bank transfers (e.g. BOC → BOC savings) will be
        // represented by a single BOC SMS and don't have a counterpart to pair.
        if (a.senderAddress == b.senderAddress) return false

        // Same currency — cross-currency transfers don't exist in this market for retail.
        if (a.amount.currency != b.amount.currency) return false

        // One expense + one income.
        val flows = setOf(a.flow, b.flow)
        if (flows != setOf(TransactionFlow.EXPENSE, TransactionFlow.INCOME)) return false

        if (abs(a.timestamp - b.timestamp) > WINDOW_MS) return false

        val delta = abs(a.amount.minorUnits - b.amount.minorUnits)
        if (delta > MAX_FEE_MINOR) return false

        return true
    }
}
