package lk.salli.parser

/**
 * Global pre-filter — any SMS that looks like an OTP is dropped before templates run.
 *
 * Why a global guard instead of per-template handling: some banks deliver OTPs through the same
 * sender ID as regular transactions, and crucially some OTP SMS embed a real transaction amount
 * (see BOCONLINE: "425401 is the OTP to your online payment LKR 1287.00 from BOC Card 2462 at
 * CARGILLS R."). A naive "extract amount" template would create a phantom transaction. The
 * guard makes that class of bug impossible by short-circuiting first.
 */
object OtpGuard {
    private val otpPattern = Regex(
        """\b(otp|one[\s-]?time[\s-]?password|verification[\s-]?code)\b""",
        RegexOption.IGNORE_CASE,
    )

    fun isOtp(body: String): Boolean = otpPattern.containsMatchIn(body)
}
