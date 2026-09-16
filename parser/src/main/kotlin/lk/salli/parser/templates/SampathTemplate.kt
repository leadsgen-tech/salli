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
 * Parser for Sampath Bank — **provisional**.
 *
 * No redacted real samples yet. Field phrases were reconstructed from publicly observable
 * format evidence (sender IDs and the wording other SL trackers key on); the fixture bodies are
 * reconstructions, not real SMS. Two senders:
 *
 *  - `SAMPCCTXN` credit-card authorisations:
 *    `Cr Crd no..**1234 Auth Pmt LKR 1,250.00 at <merchant> Avl Bal LKR 95,000.00`
 *    "Avl Bal" is the remaining credit limit; stored as balance like other card alerts.
 *  - `SAMPATHTXN` account alerts (debit card / Vishwa transfers):
 *    `LKR 1,250.00 debited from AC **1234 at <merchant> 123456 …` or
 *    `LKR 5,000.00 debited from AC **1234 for <payee> -123456 on DD/MM/YYYY HH:MM:SS`
 *
 * Extraction is field-anchored: the essentials (amount + direction + card/account mask) must be
 * present, everything else is optional. Bodies missing the essentials fall through to Unknown.
 */
object SampathTemplate : BankTemplate {

    override val name: String = "Sampath Bank (provisional)"

    override val senderPatterns: List<Regex> = listOf(
        Regex("^SAMPCCTXN$", RegexOption.IGNORE_CASE),
        Regex("^SAMPATHTXN$", RegexOption.IGNORE_CASE),
        Regex("^SAMPATH$", RegexOption.IGNORE_CASE),
        Regex("^SampathBank$", RegexOption.IGNORE_CASE),
    )

    private val ccCard = Regex("""Cr\s+Crd\s+no\.*\s*\*+(\d{4})""", RegexOption.IGNORE_CASE)
    private val ccAuth = Regex("""Auth\s+Pmt\s+([A-Z]{3})\s+([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE)
    private val ccMerchant = Regex("""\bat\s+(.+?)\s+(?:on\s+[A-Z]{3}-\d{2}\b|Avl\s+Bal\b)""", RegexOption.IGNORE_CASE)
    private val availBal = Regex("""Avl\s+Bal\s+LKR\s+([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE)

    private val acctAmount = Regex("""\b([A-Z]{3})\s+([\d,]+\.\d{2})\s+(debited|credited)\b""", RegexOption.IGNORE_CASE)
    private val acctMask = Regex("""\bAC\s+\*+(\d{2,6})""", RegexOption.IGNORE_CASE)
    private val acctMerchant = Regex("""\bat\s+(.+?)\s+\d{6,}""", RegexOption.IGNORE_CASE)
    private val acctPayee = Regex("""\bfor\s+(.+?)\s+-\d+""", RegexOption.IGNORE_CASE)
    private val acctStamp = Regex("""(\d{2}/\d{2}/\d{4})\s+(\d{2}:\d{2}:\d{2})""")
    // Word-bounded: "TREATMENT" must not read as an ATM withdrawal.
    private val atmWord = Regex("""\bATM\b""", RegexOption.IGNORE_CASE)

    /**
     * Whatever sender ID delivered the alert, rows are stored under one canonical key so the
     * bundled logo lookup and account display names match (same contract as ComBank, which
     * accepts `ComBank_Q+` but always emits `COMBANK`).
     */
    private const val CANONICAL_SENDER = "SAMPATH"

    override fun tryParse(body: String, receivedAt: Long): ParseResult? {
        val text = body.trim()

        val auth = ccAuth.find(text)
        val card = Fields.first(ccCard, text)
        if (auth != null && card != null) {
            val currency = Currency.normalize(auth.groupValues[1])
            val balance = Fields.first(availBal, text)
            return ParseResult.Success(
                ParsedTransaction(
                    senderAddress = CANONICAL_SENDER,
                    accountNumberSuffix = card,
                    amount = Money.ofMajor(auth.groupValues[2], currency),
                    balance = if (currency == Currency.LKR && balance != null) Money.ofMajor(balance, Currency.LKR) else null,
                    fee = null,
                    flow = TransactionFlow.EXPENSE,
                    type = TransactionType.POS,
                    merchantRaw = Fields.first(ccMerchant, text),
                    location = null,
                    timestamp = receivedAt,
                    isDeclined = false,
                    rawBody = body,
                ),
            )
        }

        val amount = acctAmount.find(text)
        val mask = Fields.first(acctMask, text)
        if (amount != null && mask != null) {
            val currency = Currency.normalize(amount.groupValues[1])
            val credited = amount.groupValues[3].equals("credited", ignoreCase = true)
            val merchant = Fields.first(acctMerchant, text)
            val payee = Fields.first(acctPayee, text)
            val type = when {
                atmWord.containsMatchIn(text) -> TransactionType.ATM
                merchant != null -> TransactionType.POS
                payee != null -> TransactionType.ONLINE_TRANSFER
                else -> TransactionType.OTHER
            }
            val stamp = acctStamp.find(text)
            val balance = Fields.first(availBal, text)
            return ParseResult.Success(
                ParsedTransaction(
                    senderAddress = CANONICAL_SENDER,
                    accountNumberSuffix = mask,
                    amount = Money.ofMajor(amount.groupValues[2], currency),
                    balance = if (currency == Currency.LKR && balance != null) Money.ofMajor(balance, Currency.LKR) else null,
                    fee = null,
                    flow = if (credited) TransactionFlow.INCOME else TransactionFlow.EXPENSE,
                    type = type,
                    merchantRaw = merchant ?: payee,
                    location = null,
                    timestamp = stamp?.let { TimeParser.parseSampathAccount(it.groupValues[1], it.groupValues[2]) } ?: receivedAt,
                    isDeclined = false,
                    rawBody = body,
                ),
            )
        }

        return null
    }
}
