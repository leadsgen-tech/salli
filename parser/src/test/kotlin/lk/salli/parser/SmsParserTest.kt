package lk.salli.parser

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SmsParserTest {

    @Test
    fun `OTPs are short-circuited before any template runs`() {
        val result = SmsParser.parse(
            sender = "BOCONLINE",
            body = "Dear user, Please use OTP  314815 to proceed your transaction.",
            receivedAt = 0L,
        )
        assertThat(result).isInstanceOf(ParseResult.Otp::class.java)
    }

    @Test
    fun `unregistered sender returns Informational`() {
        // Reserved path for senders we don't recognize at all (marketing, personal contacts).
        val result = SmsParser.parse(
            sender = "SomeRandomPromo",
            body = "Get 20% off!",
            receivedAt = 0L,
        )
        assertThat(result).isInstanceOf(ParseResult.Informational::class.java)
    }

    @Test
    fun `registered bank sender with unrecognizable body returns Unknown`() {
        // BOC is registered. A body that doesn't match any of its templates surfaces as Unknown
        // so the UI can queue it for user review — potentially a new SMS format.
        val result = SmsParser.parse(
            sender = "BOC",
            body = "Some weird new format we've never seen before. Rs 500 maybe.",
            receivedAt = 0L,
        )
        assertThat(result).isInstanceOf(ParseResult.Unknown::class.java)
    }
}
