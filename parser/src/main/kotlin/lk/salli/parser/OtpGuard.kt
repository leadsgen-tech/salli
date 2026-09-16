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
 *
 * Amounts, card masks, dates and balances are not codes even when an OTP disclaimer sits in
 * the same message — HNB appends "Do not share OTP" to real transactions, and a card alert
 * like "Card 4512****7788 Debited LKR 3450.00 … Do not share your OTP" must still be booked.
 */
object OtpGuard {

    private val keyword = """(otp|one[\s-]?time[\s-]?password|verification[\s-]?code|one[\s-]?time[\s-]?code)"""
    // A "code" is a bare 4-8 digit run: not glued to a card mask (4512****7788), not part of
    // a decimal, date or time, and not an amount with a currency before or after it.
    private val currency = """(?:LKR|USD|EUR|GBP|INR|Rs|Rs\.)"""
    private val code =
        """(?<![\d*#.,/\-:])(?<!\b$currency\s{0,3})\b\d{4,8}\b(?![.,/\-:]\d|\s*\*|\s{0,3}$currency\b)"""

    private val otpWithCode = Regex(
        // digit-then-keyword (e.g. "425401 is the OTP", "508926 as OTP") or
        // keyword-then-digit (e.g. "Your OTP at Merchant 'Temu' for USD 7.70 is 686993").
        // The middle uses `.` (any non-newline) so decimal amounts like "7.70" don't break
        // the match; length cap keeps the distance plausible.
        """(?:$code.{0,80}?\b$keyword\b)|(?:\b$keyword\b.{0,80}?$code)""",
        RegexOption.IGNORE_CASE,
    )

    // ComBank's transaction-approval prompts use neither "OTP" nor "verification code" —
    // they read `… attempted. Please use code NNNNNN to approve. Do NOT share this number …`
    // Matched separately so we don't relax the main `keyword` set into something that catches
    // arbitrary "code" mentions in transactional SMS.
    private val approvalCode = Regex(
        """\bplease\s+use\s+code\s+\d{4,8}\s+to\s+approve\b""",
        RegexOption.IGNORE_CASE,
    )

    fun isOtp(body: String): Boolean =
        otpWithCode.containsMatchIn(body) || approvalCode.containsMatchIn(body)
}
