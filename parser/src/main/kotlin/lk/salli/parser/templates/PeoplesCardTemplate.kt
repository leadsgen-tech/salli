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
 * Parser for People's Bank credit cards (sender `PeoplesCard`) — **provisional**. Account
 * alerts come from `PeoplesBank` and live in [PeoplesBankTemplate]; card alerts use a different
 * sender and wording:
 * `Peoples Card X-X-X-1234 trxn LKR 3,450.00 @ <merchant>. [Av.Bal: LKR 120,550.00]`
 * No real samples yet; fixture bodies are reconstructions.
 */
object PeoplesCardTemplate : BankTemplate {

    override val name: String = "People's Bank Card (provisional)"

    override val senderPatterns: List<Regex> = listOf(Regex("^PeoplesCard$", RegexOption.IGNORE_CASE))

    private val card = Regex("""Peoples\s+Card\s+X-X-X-(\d+)""", RegexOption.IGNORE_CASE)
    private val txn = Regex("""\btrxn\s+([A-Z]{3})\s+([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE)
    private val merchant = Regex("""@\s*(.+?)\s*\.(?:\s|\[|$)""")
    private val availBal = Regex("""\[Av\.Bal:\s*LKR\s+([\d,]+\.\d{2})\]""", RegexOption.IGNORE_CASE)

    override fun tryParse(body: String, receivedAt: Long): ParseResult? {
        val text = body.trim()
        val t = txn.find(text) ?: return null
        val c = Fields.first(card, text) ?: return null
        val currency = Currency.normalize(t.groupValues[1])
        val balance = Fields.first(availBal, text)
        return ParseResult.Success(
            ParsedTransaction(
                senderAddress = "PeoplesCard",
                accountNumberSuffix = c,
                amount = Money.ofMajor(t.groupValues[2], currency),
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
