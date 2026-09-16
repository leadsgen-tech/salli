package lk.salli.parser

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class KnownSendersTest {

    @Test
    fun `known bank sender without a template is queued as Unknown`() {
        val result = SmsParser.parse(
            sender = "NSB",
            body = "LKR 1,250.00 debited from your A/C XXXX1234 on 14/09/2026 at KEELLS SUPER.",
            receivedAt = 0L,
        )
        assertThat(result).isInstanceOf(ParseResult.Unknown::class.java)
    }

    @Test
    fun `known bank sender match is case-insensitive`() {
        assertThat(Templates.isKnownBankSender("hsbc")).isTrue()
        assertThat(Templates.isKnownBankSender("Pizza Hut")).isFalse()
    }

    @Test
    fun `OTP from a known bank sender is still dropped before queueing`() {
        val result = SmsParser.parse(
            sender = "HSBC",
            body = "Your OTP for the online transaction is 482913. Do not share it.",
            receivedAt = 0L,
        )
        assertThat(result).isInstanceOf(ParseResult.Otp::class.java)
    }
}
