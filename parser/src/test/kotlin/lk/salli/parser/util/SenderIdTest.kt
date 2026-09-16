package lk.salli.parser.util

import com.google.common.truth.Truth.assertThat
import lk.salli.parser.ParseResult
import lk.salli.parser.SmsParser
import org.junit.jupiter.api.Test

class SenderIdTest {

    @Test
    fun `strips line breaks, invisible marks and padding`() {
        assertThat(SenderId.normalize("COMBANK\n")).isEqualTo("COMBANK")
        assertThat(SenderId.normalize("COMBANK\r\n")).isEqualTo("COMBANK")
        assertThat(SenderId.normalize("‎HNB‏")).isEqualTo("HNB")
        assertThat(SenderId.normalize(" eZ Cash ")).isEqualTo("eZ Cash")
        assertThat(SenderId.normalize("ComBank_Q+")).isEqualTo("ComBank_Q+")
    }

    @Test
    fun `a ComBank alert whose sender ID ends in a line break is still parsed`() {
        val body = "Dear Cardholder, Purchase at ThePapare Colombo 02 LK for LKR 278.00 on 16/02/26 09:27 PM has been authorised on your debit card ending #4273."
        assertThat(SmsParser.parse("COMBANK\n", body, 0L)).isInstanceOf(ParseResult.Success::class.java)
    }
}
