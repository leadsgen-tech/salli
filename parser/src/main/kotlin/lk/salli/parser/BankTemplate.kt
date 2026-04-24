package lk.salli.parser

/**
 * A per-bank parser. Templates are *data* — each implementation is a small file declaring:
 *
 *  1. which SMS senders it handles ([senderPatterns]),
 *  2. how to turn a matching body into a [ParseResult] ([tryParse]).
 *
 * Stateless and side-effect-free. Community contributions add new templates; the registry in
 * [Templates] discovers them by sender.
 */
interface BankTemplate {

    /** Display label for logs and diagnostics. */
    val name: String

    /**
     * Senders this template claims. Multiple patterns allowed because banks sometimes route SMS
     * through multiple sender IDs (e.g. BOC uses both "BOC" and "BOCONLINE" for different
     * purposes, and we register separate templates for those).
     */
    val senderPatterns: List<Regex>

    /**
     * Try to parse [body]. Return:
     *  - [ParseResult.Success] for a transaction
     *  - [ParseResult.Otp] to force-drop an OTP (usually the global guard gets these first)
     *  - [ParseResult.Informational] for non-tx messages the template recognises
     *  - `null` if the template simply doesn't match — the dispatcher will try the next template
     *    or return [ParseResult.Unknown].
     *
     * [receivedAt] is the Android-reported SMS receive time, used as a fallback when the body
     * has no parseable timestamp.
     */
    fun tryParse(body: String, receivedAt: Long): ParseResult?

    fun handlesSender(sender: String): Boolean =
        senderPatterns.any { it.matches(sender) }
}
