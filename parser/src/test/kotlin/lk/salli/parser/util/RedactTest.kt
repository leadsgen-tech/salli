package lk.salli.parser.util

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class RedactTest {

    @Test
    fun `account numbers keep only their last four digits`() {
        assertThat(Redact.body("Online Transfer Debit Rs 1,234.56 From A/C No 7012345678. Balance available Rs 9,876.54"))
            .isEqualTo("Online Transfer Debit Rs 1,234.56 From A/C No XXXXXX5678. Balance available Rs 9,876.54")
        assertThat(Redact.body("Your A/C 280-2001-1234567 has been debited by Rs. 500.25"))
            .isEqualTo("Your A/C XXX-XXXX-XXX4567 has been debited by Rs. 500.25")
    }

    @Test
    fun `amounts balances dates and times are untouched`() {
        val body = "CEFT Transfer Debit Rs 1250000.00 on 03/09/2026 at 14:03. Av_Bal: Rs. 166424.55 as of SMS. Ref 12345"
        assertThat(Redact.body(body)).isEqualTo(body)
        assertThat(Redact.body("Balance LKR 12345678 as of today")).isEqualTo("Balance LKR 12345678 as of today")
    }

    @Test
    fun `already masked cards and short references stay as they are`() {
        val body = "Card 4512****7788 Debited LKR 3450.00 KEELLS. Cash Cheque CHQ/NO 410587 cleared."
        assertThat(Redact.body(body)).isEqualTo(body)
    }

    @Test
    fun `long reference numbers are masked`() {
        assertThat(Redact.body("Ref No 123456789012 for your payment"))
            .isEqualTo("Ref No XXXXXXXX9012 for your payment")
    }

    @Test
    fun `phone numbers and emails are replaced`() {
        assertThat(Redact.body("Call 0771234567 or +94 77 1234567 or write to help@bank.lk"))
            .isEqualTo("Call [PHONE] or [PHONE] or write to [EMAIL]")
        assertThat(Redact.body("Home Telephone No : 0371234567")).isEqualTo("Home Telephone No : [PHONE]")
    }

    @Test
    fun `names after greetings and titles are replaced but generic greetings are kept`() {
        assertThat(Redact.body("Dear Nabil Ahamed, your bill is ready")).isEqualTo("Dear [NAME], your bill is ready")
        assertThat(Redact.body("Customer name : Mr Nabil Ahamed")).isEqualTo("Customer name : Mr [NAME]")
        assertThat(Redact.body("Dear Valued Customer, Please use the app")).isEqualTo("Dear Valued Customer, Please use the app")
        assertThat(Redact.body("Dear Sir/Madam, Your A/C has been credited")).isEqualTo("Dear Sir/Madam, Your A/C has been credited")
    }

    @Test
    fun `codes near otp words are sensitive, amounts near them are not`() {
        assertThat(Redact.isSensitiveCode("Your OTP is 482913. It expires in 5 minutes.")).isTrue()
        assertThat(Redact.isSensitiveCode("Use 273546 as your OTP for Log-in")).isTrue()
        assertThat(Redact.isSensitiveCode("Rs 10295.04 debited. Never share your OTP with anyone.")).isFalse()
        assertThat(Redact.isSensitiveCode("Card purchase Rs 4,280.00 at KEELLS SUPER")).isFalse()
    }
}
