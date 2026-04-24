package lk.salli.parser

import lk.salli.domain.Money
import lk.salli.domain.TransactionFlow
import lk.salli.domain.TransactionType

/**
 * Outcome of running a single SMS through the parser pipeline.
 *
 * We treat this as a closed set — every SMS must fall into exactly one of these buckets. That
 * keeps the downstream ingestion logic simple: [Success] → persist, [Otp] / [Informational] →
 * drop silently, [Unknown] → queue for user review.
 */
sealed interface ParseResult {
    /** Successfully extracted a transaction. */
    data class Success(val tx: ParsedTransaction) : ParseResult

    /** The body is an OTP (including OTPs that mention an amount/merchant). Drop. */
    data object Otp : ParseResult

    /** A non-transactional message from a known bank sender (login alert, tax notice, promo). */
    data class Informational(val reason: String) : ParseResult

    /** Sender looks like a bank but no template matched. Queue for user review. */
    data class Unknown(val sender: String, val body: String) : ParseResult
}

/**
 * Intermediate representation — what the parser produces before the ingestion layer turns it
 * into a persisted [lk.salli.domain.Transaction]. The parser doesn't know about account IDs or
 * categories — those are resolved downstream in `:data`.
 */
data class ParsedTransaction(
    val senderAddress: String,
    val accountNumberSuffix: String?,
    val amount: Money,
    val balance: Money?,
    val fee: Money?,
    val flow: TransactionFlow,
    val type: TransactionType,
    val merchantRaw: String?,
    val location: String?,
    /** Epoch millis parsed from the body if possible, else the SMS `receivedAt`. */
    val timestamp: Long,
    val isDeclined: Boolean,
    val rawBody: String,
)
