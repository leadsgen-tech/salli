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
 * Parser for Nations Trust Bank (sender `NationsSMS`) — **provisional** (no real samples yet;
 * fixtures are reconstructions).
 *
 *  - Card purchase: `… Card 4512****7788 for LKR 3,450.00 at <merchant> Available Bal: LKR 120,550.00`
 *  - CEFTS transfer: `Other Bank Transfer (CEFTS) was performed from account ...1234 for LKR 5,000.00 …`
 *  - Declines mention "declined" and are dropped as Informational.
 */
object NtbTemplate : BankTemplate {

    override val name: String = "Nations Trust Bank (provisional)"

    override val senderPatterns: List<Regex> = listOf(
        Regex("^NationsSMS$", RegexOption.IGNORE_CASE),
        Regex("^NTB$", RegexOption.IGNORE_CASE),
        Regex("^NTBSMS$", RegexOption.IGNORE_CASE),
    )

    private val cardMask = Regex("""\bCard\s+\d+\*+(\d{4})|\bCard\s+Ending\s+(\d{4})""", RegexOption.IGNORE_CASE)
    private val amount = Regex("""\bfor\s+([A-Z]{3})\s+([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE)
    private val merchant = Regex("""\bat\s+(.+?)\s+Available\s+Bal""", RegexOption.IGNORE_CASE)
    private val availBal = Regex("""Available\s+Bal[^\d]*([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE)
    private val cefts = Regex("""\(CEFTS\)""", RegexOption.IGNORE_CASE)
    private val account = Regex("""\b(?:account|a/c)\s*[.*xX-]*(\d{4})\b""", RegexOption.IGNORE_CASE)
    private val declined = Regex("""\bdeclined\b""", RegexOption.IGNORE_CASE)

    override fun tryParse(body: String, receivedAt: Long): ParseResult? {
        val text = body.trim()
        if (declined.containsMatchIn(text)) return ParseResult.Informational("NTB declined")

        val a = amount.find(text) ?: return null
        val currency = Currency.normalize(a.groupValues[1])
        val isCefts = cefts.containsMatchIn(text)
        val mask = cardMask.find(text)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }
            ?: (if (isCefts) Fields.first(account, text) else null)
            ?: return null
        val balance = Fields.first(availBal, text)
        return ParseResult.Success(
            ParsedTransaction(
                senderAddress = "NTB",
                accountNumberSuffix = mask,
                amount = Money.ofMajor(a.groupValues[2], currency),
                balance = if (currency == Currency.LKR && balance != null) Money.ofMajor(balance, Currency.LKR) else null,
                fee = null,
                flow = TransactionFlow.EXPENSE,
                type = if (isCefts) TransactionType.CEFT else TransactionType.POS,
                merchantRaw = Fields.first(merchant, text),
                location = null,
                timestamp = receivedAt,
                isDeclined = false,
                rawBody = body,
            ),
        )
    }
}
