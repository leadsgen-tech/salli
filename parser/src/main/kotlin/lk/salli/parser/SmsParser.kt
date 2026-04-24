package lk.salli.parser

/**
 * The only public entry point. Dispatches to the right [BankTemplate] after running the global
 * [OtpGuard]. This stays tiny on purpose — all the complexity lives in per-bank templates.
 */
object SmsParser {

    fun parse(sender: String, body: String, receivedAt: Long): ParseResult {
        if (OtpGuard.isOtp(body)) return ParseResult.Otp

        val candidates = Templates.forSender(sender)
        if (candidates.isEmpty()) {
            // Sender isn't a known bank → we don't even queue it. Unknown is reserved for
            // messages from a *known* bank sender that no template could classify — those
            // are likely new transaction formats worth surfacing to the user.
            return ParseResult.Informational("sender not registered: $sender")
        }

        for (template in candidates) {
            val result = template.tryParse(body, receivedAt)
            if (result != null) return result
        }

        return ParseResult.Unknown(sender, body)
    }
}
