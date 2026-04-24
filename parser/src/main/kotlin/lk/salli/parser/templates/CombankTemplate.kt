package lk.salli.parser.templates

import lk.salli.domain.Currency
import lk.salli.domain.Money
import lk.salli.domain.TransactionFlow
import lk.salli.domain.TransactionType
import lk.salli.parser.BankTemplate
import lk.salli.parser.ParseResult
import lk.salli.parser.ParsedTransaction
import lk.salli.parser.util.TimeParser

/**
 * Parser for Commercial Bank of Ceylon.
 *
 * ComBank sends SMS from two distinct senders: `COMBANK` (card-level) and `ComBank_Q+`
 * (account-level, branded after ComBank's Q+ mobile app). Transaction shapes differ between
 * the two but the business context is the same bank, so they're a single template.
 *
 * Shapes:
 *  - **Card purchase** (`Dear Cardholder, Purchase at …`) — POS
 *  - **Card decline** (`your card was declined …`) — DECLINED, flag set
 *  - **Card-level withdrawal** (`Withdrawal at <LOC> for LKR X on DD/MM/YY HH:MM AM|PM from card
 *    ending #NNNN. Click link …`) — ATM
 *  - **Account credit** (`Credit for Rs. X to <account> at HH:MM at DIGITAL BANKING DIVISION`)
 *    — ONLINE_TRANSFER / INCOME
 *  - **CRM deposit** (`We wish to confirm a CRM Deposit at HH:MM for Rs. X through <LOC> to
 *    your account <mask>`) — CDM / INCOME
 *  - **Dormant-account marketing** (`Dear Customer , We noticed that you have not logged …`) —
 *    Informational reject
 *
 * The card-OTP template (`Your OTP at Merchant … is <code>`) is caught by the global OtpGuard.
 * Card-level ComBank SMS don't carry a balance; account-level shapes may carry a time but
 * rarely a balance either.
 */
object CombankTemplate : BankTemplate {

    override val name: String = "Commercial Bank"

    // Both the classic COMBANK sender and ComBank_Q+ route through this template. The `+` in
    // ComBank_Q+ needs escaping in the regex.
    override val senderPatterns: List<Regex> = listOf(
        Regex("^COMBANK$"),
        Regex("^ComBank_Q\\+$"),
    )

    // "Dear Cardholder, Purchase at <merchant> for <CUR> <amt> on DD/MM/YY HH:MM AM|PM has been
    //  authorised on your debit card ending #<last4>."
    private val purchase = Regex(
        """^Dear Cardholder,\s+Purchase at\s+(.+?)\s+for\s+([A-Z]{3})\s+([\d,]+\.\d{2})\s+""" +
            """on\s+(\d{2}/\d{2}/\d{2})\s+(\d{1,2}:\d{2})\s+(AM|PM)\s+""" +
            """has been authorised on your debit card ending #(\d{4})\.$""",
    )

    // "Dear Cardholder, your card was declined due to insufficient funds. The attempted
    //  transaction amount was <CUR> <amt> at <merchant> on DD/MM/YY HH:MM AM|PM. Please check
    //  your balance and try again."
    private val declined = Regex(
        """^Dear Cardholder,\s+your card was declined due to insufficient funds\.\s+""" +
            """The attempted transaction amount was\s+([A-Z]{3})\s+([\d,]+\.\d{2})\s+at\s+""" +
            """(.+?)\s+on\s+(\d{2}/\d{2}/\d{2})\s+(\d{1,2}:\d{2})\s+(AM|PM)\.\s+""" +
            """Please check your balance and try again\.$""",
    )

    // "Withdrawal at <LOC> for LKR <amt> on DD/MM/YY HH:MM AM|PM from card ending #<last4>.
    //  Click link to view the Digital Receipt …"
    private val cardWithdrawal = Regex(
        """^Withdrawal at\s+(.+?)\s+for\s+LKR\s+([\d,]+\.\d{2})\s+on\s+(\d{2}/\d{2}/\d{2})\s+""" +
            """(\d{1,2}:\d{2})\s+(AM|PM)\s+from\s+card\s+ending\s+#(\d{4})\..*$""",
        RegexOption.DOT_MATCHES_ALL,
    )

    // "Credit for Rs. <amt> to <account> at HH:MM at DIGITAL BANKING DIVISION"
    // No date in body — falls back to receivedAt. Account here is the full (visible) number,
    // not a mask.
    private val accountCredit = Regex(
        """^Credit\s+for\s+Rs\.\s+([\d,]+\.\d{2})\s+to\s+(\S+)\s+at\s+(\d{1,2}:\d{2})\s+at\s+""" +
            """(.+?)\s*$""",
    )

    // "We wish to confirm a CRM Deposit at HH:MM for Rs. <amt> through <LOC> to your account <mask>"
    private val crmDeposit = Regex(
        """^We\s+wish\s+to\s+confirm\s+a\s+CRM\s+Deposit\s+at\s+(\d{1,2}:\d{2})\s+for\s+Rs\.\s+""" +
            """([\d,]+\.\d{2})\s+through\s+(.+?)\s+to\s+your\s+account\s+(\S+)\s*$""",
    )

    // "Dear Customer , We noticed that you have not logged into your ComBank  Digital account …"
    private val dormantReminder = Regex(
        """^Dear\s+Customer\s*,\s*We\s+noticed\s+that\s+you\s+have\s+not\s+logged\s+into\s+your\s+""" +
            """ComBank.*Digital.*$""",
        RegexOption.IGNORE_CASE,
    )

    override fun tryParse(body: String, receivedAt: Long): ParseResult? {
        val trimmed = body.trim()

        if (dormantReminder.containsMatchIn(trimmed)) {
            return ParseResult.Informational("ComBank dormant-account reminder")
        }

        purchase.find(trimmed)?.let { m ->
            val (merchant, currencyRaw, amountStr, date, time, ampm, cardLast4) = m.destructured
            val bodyTs = TimeParser.parseCombank(date, time, ampm)
            return ParseResult.Success(
                ParsedTransaction(
                    senderAddress = "COMBANK",
                    accountNumberSuffix = "#$cardLast4",
                    amount = Money.ofMajor(amountStr, Currency.normalize(currencyRaw)),
                    balance = null,
                    fee = null,
                    flow = TransactionFlow.EXPENSE,
                    type = TransactionType.POS,
                    merchantRaw = merchant.trim(),
                    location = null,
                    timestamp = bodyTs ?: receivedAt,
                    isDeclined = false,
                    rawBody = body,
                ),
            )
        }

        declined.find(trimmed)?.let { m ->
            val (currencyRaw, amountStr, merchant, date, time, ampm) = m.destructured
            val bodyTs = TimeParser.parseCombank(date, time, ampm)
            return ParseResult.Success(
                ParsedTransaction(
                    senderAddress = "COMBANK",
                    accountNumberSuffix = null,
                    amount = Money.ofMajor(amountStr, Currency.normalize(currencyRaw)),
                    balance = null,
                    fee = null,
                    flow = TransactionFlow.EXPENSE,
                    type = TransactionType.DECLINED,
                    merchantRaw = merchant.trim(),
                    location = null,
                    timestamp = bodyTs ?: receivedAt,
                    isDeclined = true,
                    rawBody = body,
                ),
            )
        }

        cardWithdrawal.find(trimmed)?.let { m ->
            val (location, amountStr, date, time, ampm, cardLast4) = m.destructured
            val bodyTs = TimeParser.parseCombank(date, time, ampm)
            return ParseResult.Success(
                ParsedTransaction(
                    senderAddress = "COMBANK",
                    accountNumberSuffix = "#$cardLast4",
                    amount = Money.ofMajor(amountStr, Currency.LKR),
                    balance = null,
                    fee = null,
                    flow = TransactionFlow.EXPENSE,
                    type = TransactionType.ATM,
                    merchantRaw = null,
                    location = location.trim().ifBlank { null },
                    timestamp = bodyTs ?: receivedAt,
                    isDeclined = false,
                    rawBody = body,
                ),
            )
        }

        crmDeposit.find(trimmed)?.let { m ->
            val (_, amountStr, location, account) = m.destructured
            return ParseResult.Success(
                ParsedTransaction(
                    senderAddress = "ComBank_Q+",
                    accountNumberSuffix = account,
                    amount = Money.ofMajor(amountStr, Currency.LKR),
                    balance = null,
                    fee = null,
                    flow = TransactionFlow.INCOME,
                    type = TransactionType.CDM,
                    merchantRaw = null,
                    location = location.trim().ifBlank { null },
                    timestamp = receivedAt,
                    isDeclined = false,
                    rawBody = body,
                ),
            )
        }

        accountCredit.find(trimmed)?.let { m ->
            val (amountStr, account, _, channel) = m.destructured
            return ParseResult.Success(
                ParsedTransaction(
                    senderAddress = "ComBank_Q+",
                    accountNumberSuffix = account,
                    amount = Money.ofMajor(amountStr, Currency.LKR),
                    balance = null,
                    fee = null,
                    flow = TransactionFlow.INCOME,
                    type = TransactionType.ONLINE_TRANSFER,
                    // The body's "at DIGITAL BANKING DIVISION" is effectively the channel label,
                    // not a payer — surface it as the merchant so users can at least see where
                    // the credit came from.
                    merchantRaw = channel.trim(),
                    location = null,
                    timestamp = receivedAt,
                    isDeclined = false,
                    rawBody = body,
                ),
            )
        }

        return null
    }
}
