package lk.salli.parser.util

/**
 * Masks the parts of a bank SMS that identify a person before the text leaves the phone, and
 * leaves everything a template needs alone: amounts, balances, dates, times, merchant text and
 * the bank's exact wording. Masked digits become `X`, which is how the committed fixtures are
 * already written, so templates tolerate it in account positions.
 *
 * Rules, in order: e-mail addresses → `[EMAIL]`; Sri Lankan phone numbers → `[PHONE]`;
 * a name after Dear/Hi/Mr/Mrs/Ms → `[NAME]`; any run of 7+ digits (accounts, cards,
 * reference numbers, with dashes or spaces inside) keeps its last four digits only.
 */
object Redact {
    private val email = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val phone = Regex("""(?<![\d\-/])(?:\+94[\s-]?\d{2}[\s-]?\d{7}|0\d{2}[\s-]?\d{7})(?![\d\-/])""")
    private val greetingName = Regex(
        """\b(Dear|Hi|Hello)\s+(?!(?:Valued|Customer|Sir|Madam|Cardholder|Client|Member|User|Subscriber)\b)([A-Z][A-Za-z.'\-]*(?:\s+[A-Z][A-Za-z.'\-]*){0,3})""",
    )
    private val titledName = Regex("""\b(Mr|Mrs|Ms|Miss|Dr)\.?\s+(?!\[NAME])([A-Z][A-Za-z.'\-]*(?:\s+[A-Z][A-Za-z.'\-]*){0,3})""")
    private val digitRun = Regex("""\d(?:[\d\- ]*\d)?""")
    private val currencyBefore = Regex("""(?i)(?:rs\.?|lkr|usd|eur|gbp|inr|aed)\s*:?\s*$""")
    private val codeWord = Regex("""(?i)\b(?:otp|one[\s-]?time\s+(?:password|passcode|pin)|verification\s+code|security\s+code|passcode|activation\s+code)\b""")
    private val bareCode = Regex("""(?<![\d.,])\d{4,8}(?![\d.,]*\d)""")

    /** The body with personal identifiers masked; amounts and wording untouched. */
    fun body(text: String): String {
        var out = email.replace(text, "[EMAIL]")
        out = phone.replace(out, "[PHONE]")
        out = greetingName.replace(out) { "${it.groupValues[1]} [NAME]" }
        out = titledName.replace(out) { "${it.groupValues[1]} [NAME]" }
        out = digitRun.replace(out) { match -> maskIfIdentifier(out, match) }
        return out
    }

    /**
     * Belt and braces on top of [lk.salli.parser.OtpGuard]: a 4–8 digit code within 40
     * characters of an OTP-like word. Amounts (digits next to a currency or a decimal part)
     * never count as codes.
     */
    fun isSensitiveCode(text: String): Boolean {
        val words = codeWord.findAll(text).map { it.range.first }.toList()
        if (words.isEmpty()) return false
        return bareCode.findAll(text).any { code ->
            val before = text.substring(0, code.range.first)
            if (currencyBefore.containsMatchIn(before.takeLast(8))) return@any false
            words.any { w -> kotlin.math.abs(w - code.range.first) <= 40 }
        }
    }

    private fun maskIfIdentifier(text: String, match: MatchResult): String {
        val run = match.value
        val digits = run.count { it.isDigit() }
        if (digits < 7) return run
        val before = text.substring(0, match.range.first)
        // An amount: currency just before, or a decimal part right after.
        if (currencyBefore.containsMatchIn(before.takeLast(8))) return run
        val after = text.substring(match.range.last + 1)
        if (after.startsWith('.') && after.length > 1 && after[1].isDigit()) return run
        var keep = 4
        val sb = StringBuilder(run.length)
        for (i in run.indices.reversed()) {
            val ch = run[i]
            sb.append(if (ch.isDigit() && keep-- <= 0) 'X' else ch)
        }
        return sb.reverse().toString()
    }
}
