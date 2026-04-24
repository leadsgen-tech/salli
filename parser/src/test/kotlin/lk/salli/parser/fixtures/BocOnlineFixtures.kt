package lk.salli.parser.fixtures

/**
 * BOCONLINE is the sender BOC uses for OTP-only traffic (internet banking login, online
 * payment confirmation). Everything from this sender must be classified as [Expectation.Otp]
 * and dropped by the global guard — templates for BOCONLINE should never return Success.
 *
 * The third case is the trap: an OTP SMS that contains a real-looking amount + merchant. A
 * naive "find LKR <amount> at <merchant>" template would create a phantom transaction. The
 * [lk.salli.parser.OtpGuard] catches this before any template runs.
 */
object BocOnlineFixtures {
    val cases: List<ParseCase> = listOf(
        ParseCase(
            label = "boconline_otp_login",
            sender = "BOCONLINE",
            body = "Dear Valued Customer, Please use 273546 as your OTP for Log-in to BOC Flex. This OTP is valid only for 2 minutes. DO NOT share this OTP with third party.",
            expected = Expectation.Otp,
        ),
        ParseCase(
            label = "boconline_otp_txn",
            sender = "BOCONLINE",
            body = "Dear user, Please use OTP  314815 to proceed your transaction.",
            expected = Expectation.Otp,
        ),
        ParseCase(
            label = "boconline_otp_with_amount_trap",
            sender = "BOCONLINE",
            body = "425401 is the one-time password (OTP) to your online payment LKR 1,287.00 from BOC Card 2462 at CARGILLS R. Thank you. Bank of Ceylon",
            expected = Expectation.Otp,
        ),
    )
}
