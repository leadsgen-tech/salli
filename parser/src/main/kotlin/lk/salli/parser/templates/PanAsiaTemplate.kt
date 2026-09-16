package lk.salli.parser.templates

import lk.salli.domain.Currency
import lk.salli.domain.Money
import lk.salli.domain.TransactionFlow
import lk.salli.domain.TransactionType
import lk.salli.parser.BankTemplate
import lk.salli.parser.ParseResult
import lk.salli.parser.ParsedTransaction
import lk.salli.parser.util.Fields

/**
 * Parser for Pan Asia Bank credit cards (sender `PanAsiaBank`) — **provisional**.
 *
 * Same card-authorisation wording as Sampath (shared card processor):
 * `Cr Crd no..**1234 Auth Pmt LKR 1,250.00 at <merchant> on SEP-14 Avl Bal LKR 95,000.00`
 * The "on MMM-DD" stamp has no year, so the SMS receive time is used as the timestamp.
 * No real samples yet; fixture bodies are reconstructions.
 */
object PanAsiaTemplate : BankTemplate {

    override val name: String = "Pan Asia Bank (provisional)"

    override val senderPatterns: List<Regex> = listOf(
        Regex("^PanAsiaBank$", RegexOption.IGNORE_CASE),
        Regex("^PANASIA$", RegexOption.IGNORE_CASE),
        Regex("^Pan Asia$", RegexOption.IGNORE_CASE),
    )

    private val card = Regex("""Cr\s+Crd\s+no\.*\s*\*+(\d{4})""", RegexOption.IGNORE_CASE)
    private val auth = Regex("""Auth\s+Pmt\s+([A-Z]{3})\s+([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE)
    private val merchant = Regex("""\bat\s+(.+?)\s+(?:on\s+[A-Z]{3}-\d{2}\b|Avl\s+Bal\b)""", RegexOption.IGNORE_CASE)
    private val availBal = Regex("""Avl\s+Bal\s+LKR\s+([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE)

    override fun tryParse(body: String, receivedAt: Long): ParseResult? {
        val text = body.trim()
        val a = auth.find(text) ?: return null
        val c = Fields.first(card, text) ?: return null
        val currency = Currency.normalize(a.groupValues[1])
        val balance = Fields.first(availBal, text)
        return ParseResult.Success(
            ParsedTransaction(
                senderAddress = "PanAsiaBank",
                accountNumberSuffix = c,
                amount = Money.ofMajor(a.groupValues[2], currency),
                balance = if (currency == Currency.LKR && balance != null) Money.ofMajor(balance, Currency.LKR) else null,
                fee = null,
                flow = TransactionFlow.EXPENSE,
                type = TransactionType.POS,
                merchantRaw = Fields.first(merchant, text),
                location = null,
                timestamp = receivedAt,
                isDeclined = false,
                rawBody = body,
            ),
        )
    }
}
