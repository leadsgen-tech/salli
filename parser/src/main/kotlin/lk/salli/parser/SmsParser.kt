package lk.salli.parser

import lk.salli.parser.util.SenderId

/**
 * The only public entry point. Dispatches to the right [BankTemplate] after running the global
 * [OtpGuard]. This stays tiny on purpose — all the complexity lives in per-bank templates.
 */
object SmsParser {

    fun parse(sender: String, body: String, receivedAt: Long): ParseResult {
        val senderId = SenderId.normalize(sender)
        if (OtpGuard.isOtp(body)) return ParseResult.Otp

        val candidates = Templates.forSender(senderId)
        if (candidates.isEmpty()) {
            // A bank we know exists but have no template for yet → Unknown, so the message
            // lands in the review queue and the user can report the format. Anything else
            // (promos, personal contacts) is dropped: we don't even queue it.
            return if (Templates.isKnownBankSender(senderId)) {
                ParseResult.Unknown(senderId, body)
            } else {
                ParseResult.Informational("sender not registered: $senderId")
            }
        }

        for (template in candidates) {
            val result = template.tryParse(body, receivedAt)
            if (result != null) return result
        }

        return ParseResult.Unknown(senderId, body)
    }
}
