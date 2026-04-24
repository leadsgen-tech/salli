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
 * Parser for Commercial Bank of Ceylon (sender `COMBANK`).
 *
 * Two transaction templates:
 *  - Purchase authorised — regular POS.
 *  - Declined — the attempt still produces an SMS and is worth storing (flagged
 *    [ParsedTransaction.isDeclined]) so the UI can group by merchant and reveal the user's
 *    failing subscriptions.
 *
 * The card-OTP template ("Your OTP at Merchant … is <code>") is caught by the global OtpGuard.
 *
 * ComBank SMS never includes a balance — [ParsedTransaction.balance] is always `null` here.
 */
object CombankTemplate : BankTemplate {

    override val name: String = "Commercial Bank"

    override val senderPatterns: List<Regex> = listOf(Regex("^COMBANK$"))

    // "Dear Cardholder, Purchase at <merchant> for <CUR> <amt> on DD/MM/YY HH:MM AM|PM has been
    //  authorised on your debit card ending #<last4>."
    private val purchase: Regex = Regex(
        """^Dear Cardholder,\s+Purchase at\s+(.+?)\s+for\s+([A-Z]{3})\s+([\d,]+\.\d{2})\s+""" +
            """on\s+(\d{2}/\d{2}/\d{2})\s+(\d{1,2}:\d{2})\s+(AM|PM)\s+""" +
            """has been authorised on your debit card ending #(\d{4})\.$""",
    )

    // "Dear Cardholder, your card was declined due to insufficient funds. The attempted
    //  transaction amount was <CUR> <amt> at <merchant> on DD/MM/YY HH:MM AM|PM. Please check
    //  your balance and try again."
    private val declined: Regex = Regex(
        """^Dear Cardholder,\s+your card was declined due to insufficient funds\.\s+""" +
            """The attempted transaction amount was\s+([A-Z]{3})\s+([\d,]+\.\d{2})\s+at\s+""" +
            """(.+?)\s+on\s+(\d{2}/\d{2}/\d{2})\s+(\d{1,2}:\d{2})\s+(AM|PM)\.\s+""" +
            """Please check your balance and try again\.$""",
    )

    override fun tryParse(body: String, receivedAt: Long): ParseResult? {
        val trimmed = body.trim()

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

        return null
    }
}
