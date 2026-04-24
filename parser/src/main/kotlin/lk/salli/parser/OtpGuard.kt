package lk.salli.parser

/**
 * Global pre-filter — any SMS that looks like an OTP is dropped before templates run.
 *
 * Why a global guard instead of per-template handling: some banks deliver OTPs through the same
 * sender ID as regular transactions, and crucially some OTP SMS embed a real transaction amount
 * (see BOCONLINE: "425401 is the OTP to your online payment LKR 1287.00 from BOC Card 2462 at
 * CARGILLS R."). A naive "extract amount" template would create a phantom transaction. The
 * guard makes that class of bug impossible by short-circuiting first.
 *
 * Matching keyword alone is too aggressive — HNB's transactional SMS carry the disclaimer
 * `*DO NOT SHARE ACCOUNT DETAILS /OTP*`, which is not an OTP. We require a 4-8 digit code within
 * a small window of the keyword to flip the switch; real OTPs always have a code adjacent.
 */
object OtpGuard {

    private val keyword = """(otp|one[\s-]?time[\s-]?password|verification[\s-]?code|one[\s-]?time[\s-]?code)"""
    private val code = """\b\d{4,8}\b"""

    private val otpWithCode = Regex(
        // digit-then-keyword (e.g. "425401 is the OTP", "508926 as OTP") or
        // keyword-then-digit (e.g. "Your OTP at Merchant 'Temu' for USD 7.70 is 686993").
        // The middle uses `.` (any non-newline) so decimal amounts like "7.70" don't break
        // the match; length cap keeps the distance plausible.
        """(?:$code.{0,80}?\b$keyword\b)|(?:\b$keyword\b.{0,80}?$code)""",
        RegexOption.IGNORE_CASE,
    )

    fun isOtp(body: String): Boolean = otpWithCode.containsMatchIn(body)
}
