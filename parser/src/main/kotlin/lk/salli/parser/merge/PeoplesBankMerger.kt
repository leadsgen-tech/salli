package lk.salli.parser.merge

import lk.salli.domain.Money
import lk.salli.domain.TransactionType
import lk.salli.parser.ParsedTransaction
import kotlin.math.abs

/**
 * People's Bank sends **two** SMS for one logical outgoing transaction:
 *
 *  1. Primary debit — `Dear Sir/Madam, Your A/C … has been debited by Rs. 50025.00 (LPAY Tfr …)`
 *     Carries **account + fee-inclusive amount** plus an *optional* `Av_Bal` block. Newer SMS
 *     (observed 2026-04 onwards) drop the balance entirely; the merger must still pair them.
 *
 *  2. Confirmation — `Fund transfer Successful. LKR 50000.00 to LOLC Finance PLC …` or
 *     `Mobile Payment Successful, LKR 100.00 to Mobitel …`
 *     Carries **payee + fee-exclusive amount** but no account or balance.
 *
 * The delta (Rs 25 in the observed sample) is the bank's transfer fee.
 *
 * This merger folds both into one transaction:
 *  - `amount` from primary (fee-inclusive — what actually left the account)
 *  - `fee` = primary.amount − confirm.amount
 *  - `merchantRaw` from confirm
 *  - everything else from primary
 *
 * It handles both arrival orders — confirm-then-primary and primary-then-confirm — because SMS
 * delivery order isn't guaranteed and historical import processes them batch-style.
 */
object PeoplesBankMerger {

    /** How close the two SMS must be in time. */
    const val WINDOW_MS: Long = 5 * 60 * 1000L

    /** Maximum plausible transfer fee, in minor units (LKR cents). Rs 100. */
    const val MAX_FEE_MINOR: Long = 10_000L

    data class MergeResult(
        /** The combined transaction to persist. */
        val merged: ParsedTransaction,
        /** The earlier record (primary or confirm) to delete/update. */
        val supersedes: ParsedTransaction,
    )

    fun tryMerge(
        incoming: ParsedTransaction,
        recent: List<ParsedTransaction>,
    ): MergeResult? {
        if (incoming.senderAddress != "PeoplesBank") return null
        recent.forEach { candidate ->
            if (candidate.senderAddress != "PeoplesBank") return@forEach
            val pair = asPair(incoming, candidate) ?: return@forEach
            return merge(pair.primary, pair.confirm, superseded = candidate)
        }
        return null
    }

    private data class Pair(val primary: ParsedTransaction, val confirm: ParsedTransaction)

    private fun asPair(a: ParsedTransaction, b: ParsedTransaction): Pair? {
        val primary = pickPrimary(a, b) ?: return null
        val confirm = if (primary === a) b else a
        if (!isConfirm(confirm)) return null

        if (abs(primary.timestamp - confirm.timestamp) > WINDOW_MS) return null
        if (primary.amount.currency != confirm.amount.currency) return null
        if (primary.flow != confirm.flow) return null

        // fee = primary (fee-inclusive) − confirm (fee-exclusive), must be 0..MAX.
        val feeMinor = primary.amount.minorUnits - confirm.amount.minorUnits
        if (feeMinor < 0 || feeMinor > MAX_FEE_MINOR) return null

        return Pair(primary, confirm)
    }

    /**
     * The primary is the "debited/credited" SMS — recognised by `accountSuffix` and the
     * absence of a payee. Balance is intentionally NOT part of the test: People's Bank started
     * shipping primaries without `Av_Bal` in 2026, and gating on balance would silently break
     * the merge for every such pair (we'd end up with two rows per transfer plus a stale
     * cached balance).
     */
    private fun pickPrimary(a: ParsedTransaction, b: ParsedTransaction): ParsedTransaction? = when {
        isPrimary(a) && isConfirm(b) -> a
        isPrimary(b) && isConfirm(a) -> b
        else -> null
    }

    private fun isPrimary(p: ParsedTransaction): Boolean =
        p.accountNumberSuffix != null && p.merchantRaw == null

    private fun isConfirm(p: ParsedTransaction): Boolean =
        p.accountNumberSuffix == null && p.merchantRaw != null

    private fun merge(
        primary: ParsedTransaction,
        confirm: ParsedTransaction,
        superseded: ParsedTransaction,
    ): MergeResult {
        val feeMinor = primary.amount.minorUnits - confirm.amount.minorUnits
        val fee = if (feeMinor > 0) Money(feeMinor, primary.amount.currency) else null
        val merged = primary.copy(
            fee = fee,
            merchantRaw = confirm.merchantRaw,
            // If the confirm was a fund-transfer, prefer that over the primary's LPAY label.
            type = if (confirm.type == TransactionType.ONLINE_TRANSFER) confirm.type else primary.type,
        )
        return MergeResult(merged = merged, supersedes = superseded)
    }
}
