package lk.salli.parser

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class OtpGuardTest {

    @ParameterizedTest
    @ValueSource(strings = [
        "Please use 273546 as your OTP for Log-in to BOC Flex.",
        "Dear user, Please use OTP  314815 to proceed your transaction.",
        "use 508926 as OTP. [for People's Pay transaction]",
        "Your OTP at Merchant 'Temu' for USD 7.70 is 686993",
        // The nasty one — contains a real amount but is still an OTP.
        "425401 is the one-time password (OTP) to your online payment LKR 1287.00 from BOC Card 2462 at CARGILLS R.",
        "Your verification code is 123456",
        "Use one time password 999000",
    ])
    fun `identifies OTP messages`(body: String) {
        assertThat(OtpGuard.isOtp(body)).isTrue()
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "Online Transfer Debit Rs 85000.00 From A/C No XXXXXXXXXX870. Balance available Rs 929.10 - Thank you for banking with BOC",
        "Dear Cardholder, Purchase at APPLE.COM/BILL SINGAPORE SG for USD 15.99 on 08/03/26 05:09 PM has been authorised on your debit card ending #4273.",
        "Mobile Payment Successful, LKR 100.00 to Mobitel Ref No 0713099969",
        "Dear Customer, Self-declaration forms for YA 2025/26 valid up to 31 March 2026.",
    ])
    fun `does not flag genuine transaction messages as OTP`(body: String) {
        assertThat(OtpGuard.isOtp(body)).isFalse()
    }

    @Test
    fun `empty body is not an OTP`() {
        assertThat(OtpGuard.isOtp("")).isFalse()
    }
}
