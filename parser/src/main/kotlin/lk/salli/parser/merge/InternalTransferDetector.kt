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

    /** Hard upper bound for the fee between the two halves, in minor units (Rs 100). */
    const val MAX_FEE_MINOR: Long = 10_000L

    /** Small transfers: a flat fee up to this (Rs 30) is always plausible (LPAY charges Rs 25). */
    const val SMALL_FEE_MINOR: Long = 3_000L

    /** Larger transfers: the fee must also stay under this share of the debit. */
    const val MAX_FEE_RATIO: Double = 0.10

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

        // A leg that names its counterparty must be naming the other leg's bank. "You received
        // LKR 1,000 from [a person]" is a third party's payment, not the user's own BOC debit
        // that happened to be Rs 1,000 the same day; a POS purchase at KEELLS is never one half
        // of a transfer either. Legs with no name (bare "Online Transfer Credit") stay eligible.
        if (namesSomeoneElse(a, other = b) || namesSomeoneElse(b, other = a)) return false

        // The sending bank deducts the fee, so the debit is never smaller than the credit.
        // A credit larger than the debit is two unrelated movements that happen to be close.
        val debit = if (a.flow == TransactionFlow.EXPENSE) a else b
        val credit = if (a.flow == TransactionFlow.EXPENSE) b else a
        val fee = debit.amount.minorUnits - credit.amount.minorUnits
        return isPlausibleFee(fee = fee, debitMinor = debit.amount.minorUnits)
    }

    /** Words that identify each bank inside a counterparty string, keyed by stored sender. */
    private val bankAliases: Map<String, List<String>> = mapOf(
        "BOC" to listOf("boc", "bank of ceylon"),
        "BOCONLINE" to listOf("boc", "bank of ceylon"),
        "PeoplesBank" to listOf("people"),
        "PeoplesCard" to listOf("people"),
        "COMBANK" to listOf("combank", "commercial bank"),
        "HNB" to listOf("hnb", "hatton"),
        "SAMPATH" to listOf("sampath"),
        "SEYLAN" to listOf("seylan"),
        "SEYLANBANK" to listOf("seylan"),
        "AMANABANK" to listOf("amana"),
        "DFCC" to listOf("dfcc"),
        "NDB" to listOf("ndb"),
        "NTB" to listOf("ntb", "nations trust"),
        "PanAsiaBank" to listOf("pan asia"),
    )

    /** True when [leg] carries a counterparty name that does not mention [other]'s bank. */
    fun namesSomeoneElse(leg: ParsedTransaction, other: ParsedTransaction): Boolean {
        val name = leg.merchantRaw?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        val aliases = bankAliases[other.senderAddress]
            ?: bankAliases.entries.firstOrNull { other.senderAddress.contains(it.key, ignoreCase = true) }?.value
            ?: return false
        return aliases.none { name.contains(it, ignoreCase = true) }
    }

    /**
     * Fee sanity. Absolute caps alone paired a Rs 50 debit with a Rs 0.99 reversal (Rs 49
     * "fee"); the ratio test stops that while still accepting Rs 25 on a Rs 400 transfer.
     */
    fun isPlausibleFee(fee: Long, debitMinor: Long): Boolean {
        if (fee < 0L || fee > MAX_FEE_MINOR) return false
        if (fee <= SMALL_FEE_MINOR) return true
        return fee <= (debitMinor * MAX_FEE_RATIO).toLong()
    }
}
