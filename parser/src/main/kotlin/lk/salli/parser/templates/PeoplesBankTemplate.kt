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
 * Parser for People's Bank (sender `PeoplesBank`). Three transaction shapes plus three
 * informational variants — see [lk.salli.parser.fixtures.PeoplesBankFixtures] for each observed
 * format with example bodies.
 */
object PeoplesBankTemplate : BankTemplate {

    override val name: String = "People's Bank"

    override val senderPatterns: List<Regex> = listOf(Regex("^PeoplesBank$"))

    // Primary debit/credit:
    //   "Dear Sir/Madam, Your A/C <mask> has been <debited|credited> by Rs. <amt>
    //    (<TYPE> @HH:MM DD/MM/YYYY[ at <LOC>]).[Av_Bal: Rs. <bal> <suffix>]"
    //
    // Format variations absorbed:
    //  - Verb case varies: `debited` / `Debited` / `credited` / `Credited` → IGNORE_CASE.
    //  - Verb may carry a `(Reversal)` tag: `Credited (Reversal) by Rs. 0.99`.
    //  - Account number may be wrapped in parens: `A/C (280-2001******68)` vs `A/C 280-2001****68`.
    //  - Space before the opening `(` is optional — one real sample has `03(Cash payment…)`.
    //  - Av_Bal block is optional; its suffix text is free-form ("as of SMS", "at SMS
    //    generated", "at the time of SMS generated").
    //  - `at <location>` clause is optional (present for POS/ATM/CDM, absent for LPAY etc.).
    //  - Trailing text after the main payload varies: `.Thank You- Inquiries Dial: 1961`,
    //    `.Thank You.`, or nothing — so the tail is permissive `.*$`.
    private val primary: Regex = Regex(
        """^Dear Sir/Madam, Your A/C \(?([\d\-\*]+\d{2,})\)? has been (debited|credited)(?:\s*\(Reversal\))? by Rs\. ([\d,]+\.\d{2})\s*""" +
            """\(([^@)]+?)\s*@(\d{1,2}:\d{2})\s+(\d{1,2}/\d{1,2}/\d{2,4})(?:\s+at\s+([^)]+))?\)\.?""" +
            """(?:\s*\[Av_Bal: Rs\.\s*([\d,]+\.\d{2})[^\]]*\])?.*$""",
        RegexOption.IGNORE_CASE,
    )

    // "Mobile Payment Successful, LKR <amt> to <merchant> Ref No <ref> on <date> <time>. Call 1961"
    // (single space or double-space variations observed)
    private val mobilePayment: Regex = Regex(
        """^Mobile Payment Successful,\s+LKR\s+([\d,]+\.\d{2})\s+to\s+(.+?)\s+Ref No\s+\S+\s+on\s+([\d\-]+)\s+([\d:]+)\.\s*Call 1961$""",
    )

    // "Fund transfer  Successful. LKR <amt> to <recipient> Account <acctnum> on <date> <time>. Call 1961"
    // (note double space after "transfer")
    private val fundTransfer: Regex = Regex(
        """^Fund transfer\s+Successful\.\s+LKR\s+([\d,]+\.\d{2})\s+to\s+(.+?)\s+Account\s+\S+\s+on\s+([\d\-]+)\s+([\d:]+)\.\s*Call 1961$""",
    )

    // "Bill Payment Successful, LKR <amt> to <biller> Ref No <ref> on <date> <time>. Call 1961"
    private val billPayment: Regex = Regex(
        """^Bill Payment Successful,\s+LKR\s+([\d,]+\.\d{2})\s+to\s+(.+?)\s+Ref No\s+\S+\s+on\s+([\d\-]+)\s+([\d:]+)\.\s*Call 1961$""",
    )

    // "QR Payment Successful. LKR <amt> to Merchant <name> on <date-time>. Call 1961"
    private val qrPayment: Regex = Regex(
        """^QR Payment Successful\.\s+LKR\s+([\d,]+\.\d{2})\s+to\s+Merchant\s+(.+?)\s+on\s+([\d\-]+)\s+([\d:]+)\.?\s*(?:Call 1961)?$""",
    )

    private val informationalMarkers: List<Pair<Regex, String>> = listOf(
        Regex("""^Dear MR\b.*You have logged in""") to "people's pay login alert",
        Regex("""^Dear MR\b.*attempt to log in""") to "people's pay login failed",
        Regex("""^Hi\b.*successfully changed your login password""") to "password change",
        Regex("""^Dear Customer, to avoid""") to "account limit notice",
        Regex("""^Beware""", RegexOption.IGNORE_CASE) to "security warning",
        // Sinhala-only security bulletins — no English equivalent in the same SMS.
        Regex("""^[඀-෿]""") to "sinhala bulletin",
    )

    override fun tryParse(body: String, receivedAt: Long): ParseResult? {
        val trimmed = body.trim()

        informationalMarkers.forEach { (rx, reason) ->
            if (rx.containsMatchIn(trimmed)) return ParseResult.Informational(reason)
        }

        primary.find(trimmed)?.let { m ->
            return parsePrimary(m, body, receivedAt)
        }
        mobilePayment.find(trimmed)?.let { m ->
            return parseConfirm(m, body, receivedAt, TransactionType.MOBILE_PAYMENT)
        }
        fundTransfer.find(trimmed)?.let { m ->
            return parseConfirm(m, body, receivedAt, TransactionType.ONLINE_TRANSFER)
        }
        billPayment.find(trimmed)?.let { m ->
            return parseConfirm(m, body, receivedAt, TransactionType.MOBILE_PAYMENT)
        }
        qrPayment.find(trimmed)?.let { m ->
            return parseConfirm(m, body, receivedAt, TransactionType.POS)
        }

        return null
    }

    private fun parsePrimary(m: MatchResult, body: String, receivedAt: Long): ParseResult.Success {
        val (acctSuffixRaw, direction, amountStr, typePhrase, timeOfDay, date, locationRaw, balanceStr) = m.destructured

        // People's Bank mask length varies across their SMS vintages (we've seen both
        // `280-2001****68` and `280-2001******68` for the same physical account). Collapse any
        // run of asterisks to a fixed `..` placeholder so both formats hash to one account.
        val acctSuffix = acctSuffixRaw.replace(Regex("""\*+"""), "..")

        val type = classifyType(typePhrase.trim())
        val flow = if (direction.equals("credited", ignoreCase = true))
            TransactionFlow.INCOME else TransactionFlow.EXPENSE

        // "at <X>" is a merchant for POS purchases, a geographic location for ATM/CDM.
        val location = locationRaw.trim().takeIf { it.isNotEmpty() }
        val (merchantRaw, geoLocation) = when (type) {
            TransactionType.POS -> location to location
            TransactionType.ATM, TransactionType.CDM -> null to location
            else -> null to null
        }

        val bodyTs = TimeParser.parsePeoplesPrimary(timeOfDay, date)

        return ParseResult.Success(
            ParsedTransaction(
                senderAddress = "PeoplesBank",
                accountNumberSuffix = acctSuffix,
                amount = Money.ofMajor(amountStr, Currency.LKR),
                balance = balanceStr.takeIf { it.isNotEmpty() }
                    ?.let { Money.ofMajor(it, Currency.LKR) },
                fee = null,
                flow = flow,
                type = type,
                merchantRaw = merchantRaw,
                location = geoLocation,
                timestamp = bodyTs ?: receivedAt,
                isDeclined = false,
                rawBody = body,
            ),
        )
    }

    /**
     * Shared shape for all four confirm templates (Mobile Payment / Fund Transfer / Bill
     * Payment / QR Payment) — they all produce the same fields with a different [type].
     */
    private fun parseConfirm(
        m: MatchResult,
        body: String,
        receivedAt: Long,
        type: TransactionType,
    ): ParseResult.Success {
        val (amountStr, counterparty, date, time) = m.destructured
        val bodyTs = TimeParser.parsePeoplesConfirm("$date $time")
        return ParseResult.Success(
            ParsedTransaction(
                senderAddress = "PeoplesBank",
                accountNumberSuffix = null,
                amount = Money.ofMajor(amountStr, Currency.LKR),
                balance = null,
                fee = null,
                flow = TransactionFlow.EXPENSE,
                type = type,
                merchantRaw = counterparty.trim(),
                location = null,
                timestamp = bodyTs ?: receivedAt,
                isDeclined = false,
                rawBody = body,
            ),
        )
    }

    private fun classifyType(phrase: String): TransactionType = when {
        phrase.equals("POS", ignoreCase = true) -> TransactionType.POS
        phrase.equals("ATM", ignoreCase = true) -> TransactionType.ATM
        phrase.equals("CDM", ignoreCase = true) -> TransactionType.CDM
        phrase.contains("LPAY", ignoreCase = true) -> TransactionType.MOBILE_PAYMENT
        phrase.contains("PeoPAY", ignoreCase = true) -> TransactionType.MOBILE_PAYMENT
        phrase.contains("Just Pay", ignoreCase = true) -> TransactionType.MOBILE_PAYMENT
        // "Cash payment" is a physical cash deposit at a branch/agent — same semantic
        // bucket as a CDM (Cash Deposit Machine) deposit. Mapping to CDM means the
        // TypeCategorizer routes it to the Cash category instead of leaving it adrift
        // in Uncategorised.
        phrase.equals("Cash payment", ignoreCase = true) -> TransactionType.CDM
        else -> TransactionType.OTHER
    }
}
