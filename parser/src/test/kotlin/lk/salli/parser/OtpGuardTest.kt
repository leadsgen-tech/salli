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
        // ComBank approval prompts — no "OTP"/"verification code" keyword, but unambiguous.
        "Transaction Transfer within ComBank LKR 200.00 attempted. Please use code 564256 to approve. Do NOT share this number with anyone.",
        "ComBank Digital-User Credentials Change attempted. Please use code 184759 to approve. Do NOT share this number with anyone.",
        "ComBank Digital-Bill Payment LKR 50.00 attempted. Please use code 243232 to approve. Do NOT share this number with anyone.",
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

    @Test
    fun `PINs and access codes are credentials, not transactions`() {
        listOf(
            "Dear Cardholder, 1818 is your temporary PIN for your debit card ' XXXX1117****9200 '. Please change the temporary PIN at any Commercial Bank ATM.",
            "Dear Valued Customer,Your E-PIN for HNB Debit card 4555***2189 is 273471.",
            "Welcome to Smart Passbook service. Your Access Code is 757815 to confirm the registration.",
            "Please use code XXXXXXXX6048 for your temporary password for ComBank Digital. PbgqGoDg8jk",
            "Dear Customer, please use the new access code 6946 for ComBank ePassbook Facility.",
        ).forEach { body -> com.google.common.truth.Truth.assertWithMessage(body).that(OtpGuard.isOtp(body)).isTrue() }
        // A card mask or an amount next to the word PIN is not a code.
        assertThat(OtpGuard.isOtp("Purchase at KEELLS for LKR 3,450.00 with PIN on card ending #4273.")).isFalse()
    }
}
