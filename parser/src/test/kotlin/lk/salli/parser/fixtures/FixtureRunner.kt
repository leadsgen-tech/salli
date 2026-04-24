package lk.salli.parser.fixtures

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import lk.salli.parser.ParseResult
import lk.salli.parser.SmsParser

/**
 * Shared harness — each bank's test feeds its [ParseCase]s through this and every assertion
 * happens in one place. Keeps per-bank test files tiny.
 */
object FixtureRunner {
    fun run(case: ParseCase) {
        val actual = SmsParser.parse(case.sender, case.body, receivedAt = FIXED_RECEIVED_AT)

        when (val expected = case.expected) {
            is Expectation.Success -> {
                assertWithMessage("case '${case.label}' → expected Success but got $actual")
                    .that(actual)
                    .isInstanceOf(ParseResult.Success::class.java)
                val tx = (actual as ParseResult.Success).tx

                assertThat(tx.amount.minorUnits).isEqualTo(expected.amountMinor)
                assertThat(tx.amount.currency).isEqualTo(expected.currency)
                assertThat(tx.flow).isEqualTo(expected.flow)
                assertThat(tx.type).isEqualTo(expected.type)
                assertThat(tx.isDeclined).isEqualTo(expected.isDeclined)

                if (expected.balanceMinor != null) {
                    assertThat(tx.balance?.minorUnits).isEqualTo(expected.balanceMinor)
                } else {
                    assertThat(tx.balance).isNull()
                }

                if (expected.accountSuffix != null) {
                    assertThat(tx.accountNumberSuffix).isEqualTo(expected.accountSuffix)
                }
                if (expected.merchantRaw != null) {
                    assertThat(tx.merchantRaw).isEqualTo(expected.merchantRaw)
                }
                if (expected.location != null) {
                    assertThat(tx.location).isEqualTo(expected.location)
                }
                if (expected.feeMinor != null) {
                    assertThat(tx.fee?.minorUnits).isEqualTo(expected.feeMinor)
                }
            }

            is Expectation.Otp ->
                assertWithMessage("case '${case.label}' → expected Otp but got $actual")
                    .that(actual)
                    .isInstanceOf(ParseResult.Otp::class.java)

            is Expectation.Informational ->
                assertWithMessage("case '${case.label}' → expected Informational but got $actual")
                    .that(actual)
                    .isInstanceOf(ParseResult.Informational::class.java)

            is Expectation.Unknown ->
                assertWithMessage("case '${case.label}' → expected Unknown but got $actual")
                    .that(actual)
                    .isInstanceOf(ParseResult.Unknown::class.java)
        }
    }

    /** Arbitrary fixed epoch — the parser falls back to this when body has no timestamp. */
    const val FIXED_RECEIVED_AT: Long = 1_713_960_000_000L // 2024-04-24 12:00:00 UTC
}
