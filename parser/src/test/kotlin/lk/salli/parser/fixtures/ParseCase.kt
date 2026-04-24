package lk.salli.parser.fixtures

import lk.salli.domain.TransactionFlow
import lk.salli.domain.TransactionType

/**
 * One test scenario — an SMS and the outcome we expect [lk.salli.parser.SmsParser.parse] to
 * produce. Used by per-bank parameterized tests.
 *
 * All bodies here are **redacted** versions of real SMS we've observed. Account numbers keep
 * only their last 3-4 digits; names are `[NAME]`; phone numbers are `[PHONE]`; transaction and
 * balance amounts are replaced with plausible but non-real values so no actual financial
 * information is committed to the repo.
 */
data class ParseCase(
    val label: String,
    val sender: String,
    val body: String,
    val expected: Expectation,
)

sealed interface Expectation {
    data class Success(
        val type: TransactionType,
        val flow: TransactionFlow,
        val amountMinor: Long,
        val currency: String,
        val balanceMinor: Long? = null,
        val accountSuffix: String? = null,
        val merchantRaw: String? = null,
        val location: String? = null,
        val isDeclined: Boolean = false,
        val feeMinor: Long? = null,
    ) : Expectation

    data object Otp : Expectation

    data class Informational(val reason: String? = null) : Expectation

    data object Unknown : Expectation
}
