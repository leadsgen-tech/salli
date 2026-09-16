package lk.salli.parser.templates

import lk.salli.domain.Currency
import lk.salli.domain.Money
import lk.salli.domain.TransactionFlow
import lk.salli.domain.TransactionType
import lk.salli.parser.BankTemplate
import lk.salli.parser.ParseResult
import lk.salli.parser.ParsedTransaction
import lk.salli.parser.util.Fields
import lk.salli.parser.util.TimeParser

/**
 * Parser for NDB Bank cards — **provisional** (no real samples yet; fixtures are reconstructions).
 *
 *  - `NDB CARD` transaction alerts, one line, merchant then a reference, then an ISO stamp:
 *    `Card 4512****7788 Debited LKR 3,450.00 <merchant>  <ref> 2026-09-14 18:42:10 Avl Bal 120,550.00`
 *  - `NDB ALERTS` monthly statement notices (`… card ending ****1234 … as at DD-MMM-YY is Rs. …
 *    min payment of Rs. … is due by …`) carry no transaction → Informational.
 */
object NdbTemplate : BankTemplate {

    override val name: String = "NDB Bank (provisional)"

    override val senderPatterns: List<Regex> = listOf(
        Regex("^NDB CARD$", RegexOption.IGNORE_CASE),
        Regex("^NDB ALERTS$", RegexOption.IGNORE_CASE),
        Regex("^NDB$", RegexOption.IGNORE_CASE),
        Regex("^NDBBANK$", RegexOption.IGNORE_CASE),
    )

    private val cardMask = Regex("""\bCard\s+\d+\*+(\d{4})""", RegexOption.IGNORE_CASE)
    private val txn = Regex("""\b(Debited|Credited)\s+([A-Z]{3})\s+([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE)
    // Merchant runs from the amount up to the double-space before the reference, the ISO stamp,
    // or the balance — whichever comes first.
    private val merchant = Regex(
        """\b(?:Debited|Credited)\s+[A-Z]{3}\s+[\d,]+\.\d{2}\s+(.+?)(?:\s{2,}|\s+\d{4}-\d{2}-\d{2}\s|\s+Avl\s+Bal\b|$)""",
        RegexOption.IGNORE_CASE,
    )
    private val stamp = Regex("""(\d{4}-\d{2}-\d{2}\s\d{2}:\d{2}:\d{2})""")
    private val availBal = Regex("""Avl\s+Bal\s+(?:LKR\s+)?([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE)
    private val statement = Regex(
        """card\s+ending\s+\*+\d+.*?\bas\s+at\b.*?\bis\s+Rs\.""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    override fun tryParse(body: String, receivedAt: Long): ParseResult? {
        val text = body.trim()

        if (statement.containsMatchIn(text)) return ParseResult.Informational("NDB card statement")

        val t = txn.find(text) ?: return null
        val mask = Fields.first(cardMask, text) ?: return null
        val credited = t.groupValues[1].equals("credited", ignoreCase = true)
        val currency = Currency.normalize(t.groupValues[2])
        val balance = Fields.first(availBal, text)
        return ParseResult.Success(
            ParsedTransaction(
                senderAddress = "NDB",
                accountNumberSuffix = mask,
                amount = Money.ofMajor(t.groupValues[3], currency),
                balance = if (currency == Currency.LKR && balance != null) Money.ofMajor(balance, Currency.LKR) else null,
                fee = null,
                flow = if (credited) TransactionFlow.INCOME else TransactionFlow.EXPENSE,
                type = TransactionType.POS,
                merchantRaw = Fields.first(merchant, text),
                location = null,
                timestamp = Fields.first(stamp, text)?.let { TimeParser.parseIsoDateTime(it) } ?: receivedAt,
                isDeclined = false,
                rawBody = body,
            ),
        )
    }
}
