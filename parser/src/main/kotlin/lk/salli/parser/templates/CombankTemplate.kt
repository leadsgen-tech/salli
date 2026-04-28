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

    // "Debit for Rs. <amt> from <account> at HH:MM at DIGITAL BANKING DIVISION" — mirror of
    // accountCredit (note "from" instead of "to"). Account is the full visible number.
    private val accountDebit = Regex(
        """^Debit\s+for\s+Rs\.\s+([\d,]+\.\d{2})\s+from\s+(\S+)\s+at\s+(\d{1,2}:\d{2})\s+at\s+""" +
            """(.+?)\s*$""",
    )

    // "Dear Customer, your transaction made via "Justpay" for Rs.<amt> has been approved
    //  sucessfully. Thank you"  ← bank's typo retained.
    // Note `Rs.` has NO space before the amount in this shape (e.g. `Rs.15600.00`).
    private val justpay = Regex(
        """^Dear\s+Customer,\s+your\s+transaction\s+made\s+via\s+"?Justpay"?\s+for\s+""" +
            """Rs\.?\s*([\d,]+\.\d{2})\s+has\s+been\s+approved.*$""",
        RegexOption.IGNORE_CASE,
    )

    // "We wish to confirm a CRM Deposit at HH:MM for Rs. <amt> through <LOC> to your account
    //  <mask>" — optionally followed by a receipt URL (`https://vas.combank.net/rec/…`). The
    //  trailing URL is real-world; fixtures historically didn't carry it which is why the
    //  regex previously over-anchored on the account token.
    private val crmDeposit = Regex(
        """^We\s+wish\s+to\s+confirm\s+a\s+CRM\s+Deposit\s+at\s+(\d{1,2}:\d{2})\s+for\s+Rs\.\s+""" +
            """([\d,]+\.\d{2})\s+through\s+(.+?)\s+to\s+your\s+account\s+(\S+)(?:\s+\S+)*\s*$""",
    )

    // Dormant / deactivated ComBank Digital reminders. Three observed variants:
    //   "Dear Customer, We noticed that you have not logged into your ComBank Digital …"
    //   "Dear Customer, as informed we have noticed that you have not accessed your ComBank
    //    Digital account in the past 30 days …"
    //   "Dear Customer, This is to inform you that due to security reasons, your ComBank
    //    Digital online banking facility was deactivated recently …"
    // All share the "ComBank Digital" mention with no transactional content; bucket them so
    // they don't pollute the Unknown queue.
    // Two lookaheads so either ordering works:
    //   "…not logged into your ComBank Digital…"  (keyword before)
    //   "…ComBank Digital…was deactivated…"        (keyword after)
    private val dormantReminder = Regex(
        """^Dear\s+Customer\s*,""" +
            """(?=.*ComBank\s+Digital)""" +
            """(?=.*(?:not\s+logged|not\s+accessed|deactivated)).*$""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    // "Your temporary password for Flash Digital Banking is …" / "Flash Digital Banking
    // password changed successfully …" — onboarding chatter for ComBank's Flash app.
    private val flashAccountAdmin = Regex(
        """^(Your\s+temporary\s+password\s+for\s+Flash\s+Digital\s+Banking|""" +
            """Flash\s+Digital\s+Banking\s+password\s+changed\s+successfully).*$""",
        RegexOption.IGNORE_CASE,
    )

    // "Fund transfer request WT30197520 P 2025-06-17 at 03:36:16 via Combank Online to <NAME>
    //  for LKR 6000.00 Inquiries+94112353596."
    // The reference token (`WT…`) ends with a space-separated `P` flag; recipient name is
    // free-form and may contain spaces, dots, dashes; amount has no thousand separator in this
    // shape (e.g. `6000.00`, `15000.00`).
    private val fundTransfer = Regex(
        """^Fund\s+transfer\s+request\s+(\S+)\s+P\s+(\d{4}-\d{1,2}-\d{1,2})\s+at\s+""" +
            """(\d{1,2}:\d{2}:\d{2})\s+via\s+Combank\s+Online\s+to\s+(.+?)\s+for\s+""" +
            """([A-Z]{3})\s+([\d,]+\.\d{2})\s+Inquiries.*$""",
    )

    // "Bill Payment in the amount of LKR 50.00 was received on 20/03/2026 from the ComBank
    //  Digital." — the only bill-payment shape that includes an amount, so it's the only one
    //  that becomes a real transaction.
    private val billPaymentReceived = Regex(
        """^Bill\s+Payment\s+in\s+the\s+amount\s+of\s+([A-Z]{3})\s+([\d,]+\.\d{2})\s+""" +
            """was\s+received\s+on\s+(\d{1,2}/\d{1,2}/\d{4})\s+from\s+the\s+ComBank\s+Digital\.\s*$""",
    )

    // "<biller>  bill payment effected through Combank Digital.  Status - Completed. Reference
    //  O… For inquiries +94…" — the biller-side confirmation. No amount in body, so the row
    //  can't become a transaction; surfaced as Informational so it doesn't pollute the Unknown
    //  queue. The matching debit-with-amount arrives separately via [billPaymentReceived].
    private val billPaymentEffected = Regex(
        """^.+?\s+bill\s+payment\s+effected\s+through\s+Combank\s+Digital\..*Status\s+-\s+Completed\..*$""",
        RegexOption.IGNORE_CASE,
    )

    // "Dear Customer, your attempt to login to ComBank Digital failed. For inquiries +94…"
    private val loginFailed = Regex(
        """^Dear\s+Customer,\s+your\s+attempt\s+to\s+login\s+to\s+ComBank\s+Digital\s+failed\b.*$""",
        RegexOption.IGNORE_CASE,
    )

    // "Dear Customer, a scheduled system maintenance will take place on …"
    // Some variants wrap onto multiple lines, so we let `.` span newlines.
    private val scheduledMaintenance = Regex(
        """^Dear\s+Customer,\s+a\s+scheduled\s+system\s+maintenance\s+will\s+take\s+place.*$""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    // "Your Domestic Payment of Rs 300.00 to account NNN has been rejected …"
    // "Your Fund Transfer reference WT… has been rejected …"
    // No money actually moved; treat as Informational so the rejection notice doesn't pollute
    // the Unknown queue or get logged as an expense.
    private val rejectedPayment = Regex(
        """^Your\s+(Domestic\s+Payment|Fund\s+Transfer).+has\s+been\s+rejected\b.*$""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    // ComBank_Q+ outgoing shapes.
    //
    //   "Your Q+ transaction at MOBITEL DATA PLANS for LKR 80.00 has been successful.
    //    Reference Number 610813178925  (Mobile Number - 0713099969.)"
    // No date in body — falls back to receivedAt.
    private val qPlusTransaction: Regex = Regex(
        """^Your\s+Q\+\s+transaction\s+at\s+(.+?)\s+for\s+([A-Z]{3})\s+([\d,]+\.\d{2})\s+""" +
            """has been successful.*$""",
        RegexOption.DOT_MATCHES_ALL,
    )

    //   "Dear Customer,Your Fund Transfer of Rs. 1,900.00 to Account Number XXXXXXXXXXX8871
    //    is successful. Reference Number is 610714138575."
    // Note: NO space after the comma, account number is masked, no date.
    private val qPlusFundTransfer: Regex = Regex(
        """^Dear\s+Customer,\s*Your\s+Fund\s+Transfer\s+of\s+Rs\.\s+([\d,]+\.\d{2})\s+to\s+""" +
            """Account\s+Number\s+(\S+)\s+is\s+successful\b.*$""",
        RegexOption.IGNORE_CASE,
    )

    override fun tryParse(body: String, receivedAt: Long): ParseResult? {
        val trimmed = body.trim()

        if (dormantReminder.containsMatchIn(trimmed)) {
            return ParseResult.Informational("ComBank dormant-account reminder")
        }
        if (loginFailed.containsMatchIn(trimmed)) {
            return ParseResult.Informational("ComBank login failed")
        }
        if (scheduledMaintenance.containsMatchIn(trimmed)) {
            return ParseResult.Informational("ComBank scheduled maintenance")
        }
        if (billPaymentEffected.containsMatchIn(trimmed)) {
            return ParseResult.Informational("ComBank bill payment effected (no amount)")
        }
        if (rejectedPayment.containsMatchIn(trimmed)) {
            return ParseResult.Informational("ComBank payment rejected")
        }
        if (flashAccountAdmin.containsMatchIn(trimmed)) {
            return ParseResult.Informational("ComBank Flash account admin")
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
                    senderAddress = "COMBANK",
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
                    senderAddress = "COMBANK",
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

        accountDebit.find(trimmed)?.let { m ->
            val (amountStr, account, _, channel) = m.destructured
            return ParseResult.Success(
                ParsedTransaction(
                    senderAddress = "COMBANK",
                    accountNumberSuffix = account,
                    amount = Money.ofMajor(amountStr, Currency.LKR),
                    balance = null,
                    fee = null,
                    flow = TransactionFlow.EXPENSE,
                    type = TransactionType.ONLINE_TRANSFER,
                    merchantRaw = channel.trim(),
                    location = null,
                    timestamp = receivedAt,
                    isDeclined = false,
                    rawBody = body,
                ),
            )
        }

        justpay.find(trimmed)?.let { m ->
            val (amountStr) = m.destructured
            return ParseResult.Success(
                ParsedTransaction(
                    senderAddress = "COMBANK",
                    accountNumberSuffix = null,
                    amount = Money.ofMajor(amountStr, Currency.LKR),
                    balance = null,
                    fee = null,
                    flow = TransactionFlow.EXPENSE,
                    type = TransactionType.MOBILE_PAYMENT,
                    merchantRaw = "Justpay",
                    location = null,
                    timestamp = receivedAt,
                    isDeclined = false,
                    rawBody = body,
                ),
            )
        }

        fundTransfer.find(trimmed)?.let { m ->
            val (_, date, time, recipient, currencyRaw, amountStr) = m.destructured
            val bodyTs = TimeParser.parseCombankFundTransfer(date, time)
            return ParseResult.Success(
                ParsedTransaction(
                    senderAddress = "COMBANK",
                    accountNumberSuffix = null,
                    amount = Money.ofMajor(amountStr, Currency.normalize(currencyRaw)),
                    balance = null,
                    fee = null,
                    flow = TransactionFlow.EXPENSE,
                    type = TransactionType.ONLINE_TRANSFER,
                    merchantRaw = recipient.trim(),
                    location = null,
                    timestamp = bodyTs ?: receivedAt,
                    isDeclined = false,
                    rawBody = body,
                ),
            )
        }

        billPaymentReceived.find(trimmed)?.let { m ->
            val (currencyRaw, amountStr, date) = m.destructured
            val bodyTs = TimeParser.parseCombankBillPayment(date)
            return ParseResult.Success(
                ParsedTransaction(
                    senderAddress = "COMBANK",
                    accountNumberSuffix = null,
                    amount = Money.ofMajor(amountStr, Currency.normalize(currencyRaw)),
                    balance = null,
                    fee = null,
                    flow = TransactionFlow.EXPENSE,
                    type = TransactionType.MOBILE_PAYMENT,
                    // No biller name in the body — best we can surface is the channel.
                    merchantRaw = "ComBank Digital",
                    location = null,
                    timestamp = bodyTs ?: receivedAt,
                    isDeclined = false,
                    rawBody = body,
                ),
            )
        }

        qPlusTransaction.find(trimmed)?.let { m ->
            val (merchant, currencyRaw, amountStr) = m.destructured
            return ParseResult.Success(
                ParsedTransaction(
                    senderAddress = "COMBANK",
                    accountNumberSuffix = null,
                    amount = Money.ofMajor(amountStr, Currency.normalize(currencyRaw)),
                    balance = null,
                    fee = null,
                    flow = TransactionFlow.EXPENSE,
                    type = TransactionType.MOBILE_PAYMENT,
                    merchantRaw = merchant.trim(),
                    location = null,
                    timestamp = receivedAt,
                    isDeclined = false,
                    rawBody = body,
                ),
            )
        }

        qPlusFundTransfer.find(trimmed)?.let { m ->
            val (amountStr, accountMask) = m.destructured
            return ParseResult.Success(
                ParsedTransaction(
                    senderAddress = "COMBANK",
                    accountNumberSuffix = null,
                    amount = Money.ofMajor(amountStr, Currency.LKR),
                    balance = null,
                    fee = null,
                    flow = TransactionFlow.EXPENSE,
                    type = TransactionType.ONLINE_TRANSFER,
                    // Use the masked counterpart account as the merchant label so the row
                    // tells the user where the money went.
                    merchantRaw = "Account $accountMask",
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
