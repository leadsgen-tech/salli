package lk.salli.parser.templates

import lk.salli.parser.BankTemplate
import lk.salli.parser.ParseResult

/**
 * Sender `BOCONLINE` is BOC's OTP channel — login codes, online-payment OTPs. Nothing here is
 * ever a transaction. The global [lk.salli.parser.OtpGuard] catches these first, but we still
 * register this template so the sender is **recognized** (rather than falling through to
 * `ParseResult.Informational("sender not registered")`), and so any future non-OTP message
 * from BOCONLINE gets classified as `Informational` instead of `Unknown`.
 */
object BocOnlineTemplate : BankTemplate {

    override val name: String = "BOC Online (OTP channel)"

    override val senderPatterns: List<Regex> = listOf(Regex("^BOCONLINE$"))

    override fun tryParse(body: String, receivedAt: Long): ParseResult =
        ParseResult.Informational("BOCONLINE is OTP-only")
}
