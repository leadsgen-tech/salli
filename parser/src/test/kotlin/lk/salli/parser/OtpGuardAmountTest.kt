package lk.salli.parser

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class OtpGuardAmountTest {

    @Test
    fun `digits right after a currency token are an amount, not a code`() {
        assertThat(OtpGuard.isOtp("You received LKR 5000 from [NAME]\nDo not share OTP with anyone.")).isFalse()
        assertThat(OtpGuard.isOtp("Rs. 2500 credited. Never share your OTP.")).isFalse()
    }

    @Test
    fun `a real code near an amount is still an OTP`() {
        assertThat(OtpGuard.isOtp("425401 is the OTP to your online payment LKR 1287.00 from BOC Card 2462 at CARGILLS R.")).isTrue()
        assertThat(OtpGuard.isOtp("Your OTP at Merchant 'Temu' for USD 7.70 is 686993")).isTrue()
    }
}

class OtpGuardTokenShapeTest {
    @org.junit.jupiter.api.Test
    fun `card masks, years and suffix-currency amounts are not codes`() {
        com.google.common.truth.Truth.assertThat(OtpGuard.isOtp("Card 4512****7788 Debited LKR 3450.00 KEELLS Do not share your OTP")).isFalse()
        com.google.common.truth.Truth.assertThat(OtpGuard.isOtp("HNB Credit Card **1234 :KEELLS SUPER (Apprx) LKR 3,450.00 (14-Sep-2026 06:42:10 PM) Av.Bal : LKR 120,550.00 Do not share OTP")).isFalse()
        com.google.common.truth.Truth.assertThat(OtpGuard.isOtp("Amount(Approx.):5000.00 LKR on 14/09/2026. Never share your OTP.")).isFalse()
    }

    @org.junit.jupiter.api.Test
    fun `bare codes next to the keyword are still OTPs`() {
        com.google.common.truth.Truth.assertThat(OtpGuard.isOtp("Dear user, Please use OTP  314815 to proceed your transaction.")).isTrue()
        com.google.common.truth.Truth.assertThat(OtpGuard.isOtp("Your one time password for Flash Digital Bank is 301650. Valid for 10 minutes.")).isTrue()
        com.google.common.truth.Truth.assertThat(OtpGuard.isOtp("National Fuel Pass: One time validation (OTP) 982540\nExpire in 5 minutes")).isTrue()
    }
}
