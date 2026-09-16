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
 * Parser for DFCC Bank — **provisional** (no real samples yet; fixtures are reconstructions).
 *
 *  - `DFCC Info` credit-card alerts, upper-case field labels:
 *    `DEBITED LKR 3,450.00 ON (14/Sep/2026 18:42) CARD: <merchant> CARD**4521 AVBAL LKR 120,550.00`
 *  - `DFCC Alerts` account alerts:
 *    `Your A/C No: ****1234 has been debited with LKR3,450.00 on 14 Sep 2026 ref: <ref>. Available bal is LKR12,345.00`
 *
 * Field-anchored: direction + amount + a card/account mask are required, the rest optional.
 */
object DfccTemplate : BankTemplate {

    override val name: String = "DFCC Bank (provisional)"

    override val senderPatterns: List<Regex> = listOf(
        Regex("^DFCC Info$", RegexOption.IGNORE_CASE),
        Regex("^DFCC Alerts$", RegexOption.IGNORE_CASE),
        Regex("^DFCCINFO$", RegexOption.IGNORE_CASE),
        Regex("^DFCC$", RegexOption.IGNORE_CASE),
        Regex("^DFCC Bank$", RegexOption.IGNORE_CASE),
    )

    private val acctMask = Regex("""A/C\s+No:\s*\*+(\d+)""", RegexOption.IGNORE_CASE)
    private val acctTxn = Regex("""\b(debited|credited)\s+with\s+([A-Z]{3})\s*([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE)
    private val acctOn = Regex("""\bon\s+(\d{1,2}\s+[A-Za-z]{3}\s+\d{4})""")
    private val acctRef = Regex("""\bref:\s*(.+?)\.(?:\s|$)""", RegexOption.IGNORE_CASE)
    private val acctBal = Regex("""\bbal\s+is\s+LKR\s*([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE)

    private val cardTxn = Regex("""\b(DEBITED|CREDITED)\s+([A-Z]{3})\s+([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE)
    private val cardMask = Regex("""CARD\*\*(\d{4})""", RegexOption.IGNORE_CASE)
    private val cardMerchant = Regex("""CARD:\s+(.+?)\s+CARD\*\*""", RegexOption.IGNORE_CASE)
    private val cardOn = Regex("""\bON\s*\(([^)]+)\)""", RegexOption.IGNORE_CASE)
    private val cardAvbal = Regex("""AVBAL\s+LKR\s+([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE)

    override fun tryParse(body: String, receivedAt: Long): ParseResult? {
        val text = body.trim()

        acctTxn.find(text)?.let { m ->
            val mask = Fields.first(acctMask, text) ?: return@let
            val credited = m.groupValues[1].equals("credited", ignoreCase = true)
            val currency = Currency.normalize(m.groupValues[2])
            val balance = Fields.first(acctBal, text)
            val ref = Fields.first(acctRef, text)
            return ParseResult.Success(
                ParsedTransaction(
                    senderAddress = CANONICAL_SENDER,
                    accountNumberSuffix = mask,
                    amount = Money.ofMajor(m.groupValues[3], currency),
                    balance = if (currency == Currency.LKR && balance != null) Money.ofMajor(balance, Currency.LKR) else null,
                    fee = null,
                    flow = if (credited) TransactionFlow.INCOME else TransactionFlow.EXPENSE,
                    type = classifyAccount(ref, text),
                    merchantRaw = ref,
                    location = null,
                    timestamp = Fields.first(acctOn, text)?.let { TimeParser.parseDayMonthNameYear(it) } ?: receivedAt,
                    isDeclined = false,
                    rawBody = body,
                ),
            )
        }

        cardTxn.find(text)?.let { m ->
            val mask = Fields.first(cardMask, text) ?: return@let
            val credited = m.groupValues[1].equals("credited", ignoreCase = true)
            val currency = Currency.normalize(m.groupValues[2])
            val balance = Fields.first(cardAvbal, text)
            return ParseResult.Success(
                ParsedTransaction(
                    senderAddress = CANONICAL_SENDER,
                    accountNumberSuffix = mask,
                    amount = Money.ofMajor(m.groupValues[3], currency),
                    balance = if (currency == Currency.LKR && balance != null) Money.ofMajor(balance, Currency.LKR) else null,
                    fee = null,
                    flow = if (credited) TransactionFlow.INCOME else TransactionFlow.EXPENSE,
                    type = TransactionType.POS,
                    merchantRaw = Fields.first(cardMerchant, text),
                    location = null,
                    timestamp = Fields.first(cardOn, text)?.let { TimeParser.parseDfccCard(it) } ?: receivedAt,
                    isDeclined = false,
                    rawBody = body,
                ),
            )
        }

        return null
    }

    // The `ref:` field is the bank's own channel tag; it decides the type. Word bounds keep
    // "GREATMART" from reading as ATM and "POSTAGE" from reading as POS.
    private fun classifyAccount(ref: String?, text: String): TransactionType {
        val scope = ref ?: text
        return when {
            posWord.containsMatchIn(scope) -> TransactionType.POS
            atmWord.containsMatchIn(scope) -> TransactionType.ATM
            ceftWord.containsMatchIn(scope) -> TransactionType.CEFT
            else -> TransactionType.OTHER
        }
    }

    private val posWord = Regex("""\bPOS\b""", RegexOption.IGNORE_CASE)
    private val atmWord = Regex("""\bATM\b""", RegexOption.IGNORE_CASE)
    private val ceftWord = Regex("""\bCEFT\b""", RegexOption.IGNORE_CASE)

    /** One storage key for both DFCC sender IDs so logos and account names line up. */
    private const val CANONICAL_SENDER = "DFCC"
}
