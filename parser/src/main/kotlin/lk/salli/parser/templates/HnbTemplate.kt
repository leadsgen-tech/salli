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
 * Parser for Hatton National Bank (sender `HNB`).
 *
 * HNB uses four distinct SMS shapes:
 *
 *  1. **Account debit/credit** — single-line, drives the balance.
 *     `LKR <amt> debited to Ac No:<mask> on DD/MM/YY HH:MM:SS Reason:<tag> Bal:LKR <bal> Protect …`
 *     Direction (`debited` / `credited`) maps to flow; the `Reason:` tag classifies the type
 *     (`MB:…` → ONLINE_TRANSFER, `CEFT-…` → CEFT, else OTHER).
 *
 *  2. **Fee / alert-charge** — two-line, distinctive preamble.
 *     `A Transaction for LKR X has been debit ed to Ac No:… on DD/MM/YY HH:MM:SS .`
 *     `Remarks :HNB Alert Charges.Bal: LKR Y`
 *     Always FEE flow; the `debit ed` typo (extra space) is HNB's own, we match as-is.
 *
 *  3. **Card SMS alert** — single-line card-notify, may carry USD amount with LKR balance.
 *     `HNB SMS ALERT:<CHANNEL>, Account:<mask>,Location:<merchant>, <CC>,Amount(Approx.):X <CUR>,Av.Bal:Y LKR,Date:DD.MM.YY,Time:HH:MM, Hot Line:…`
 *     When the amount currency isn't LKR the balance is in a different currency — we drop
 *     balance on FX rows to avoid mixing minor units.
 *
 *  4. **ATM Withdrawal e-Receipt** — multi-line, ten fields including an explicit Txn Fee.
 *
 *  5. **Payment notice** — `You received LKR 10,000 from <NAME>` plus a trilingual disclaimer.
 *     HNB sends this to the *person being paid* when an HNB customer pays them. The money lands
 *     in the recipient's own bank, which sends its own credit SMS, and the recipient often has
 *     no HNB account at all. Booking it would invent an HNB account and double-count the
 *     credit, so it is recognised and dropped.
 *
 *  6. **Credit-card alert** (provisional, reconstructed from format evidence, no real sample yet)
 *     `… **1234 :<merchant> (Apprx) LKR <amt> (DD-MMM-YYYY hh:mm:ss AM) Av.Bal : LKR <bal>`
 *
 * OTPs from this sender are caught by the global OtpGuard so we don't dispatch them here.
 */
object HnbTemplate : BankTemplate {

    override val name: String = "Hatton National Bank"

    override val senderPatterns: List<Regex> = listOf(Regex("^HNB$"))

    // Shape 1 — account debit/credit.
    // Amounts under a rupee arrive as "LKR .41" and a zero balance as "LKR .00": the integer
    // part is optional everywhere HNB prints money.
    private val accountTxn = Regex(
        """^LKR\s+([\d,]*\.\d{2})\s+(debited|credited)\s+to\s+Ac\s+No:(\S+)\s+on\s+""" +
            """(\d{2}/\d{2}/\d{2})\s+(\d{2}:\d{2}:\d{2})\s+Reason:(.+?)\s+Bal:\s*LKR\s*""" +
            """([\d,]*\.\d{2}).*$""",
        RegexOption.DOT_MATCHES_ALL,
    )

    // Shape 2 — fee / alert charge. The primary tell is the "A Transaction for LKR …" preamble
    // and the "debit ed" typo; match defensively with \s* between "debit" and "ed".
    // "has been debit ed" is a charge; "has been credit ed" is a refund of one (FX markup).
    private val feeAlert = Regex(
        """^A\s+Transaction\s+for\s+LKR\s+([\d,]*\.\d{2})\s+has\s+been\s+(debit|credit)\s*ed\s+to\s+""" +
            """Ac\s+No:(\S+)\s+on\s+(\d{2}/\d{2}/\d{2})\s+(\d{2}:\d{2}:\d{2})\s*\.\s*""" +
            """Remarks\s*:(.+?)\.\s*Bal:\s*LKR\s*([\d,]*\.\d{2}).*$""",
        RegexOption.DOT_MATCHES_ALL,
    )

    // Shape 3 — card SMS alert. Location has a trailing ", CC" (2-letter country) before
    // Amount; anchoring on ",Amount(Approx.):" keeps the merchant capture tight.
    // Two spellings in the wild: "ALERT:PURCHASE, Account:" and "ALERT: PURCHASE, Debit account:".
    private val cardAlert = Regex(
        """^HNB\s+SMS\s+ALERT:\s*\S+,\s*(?:Debit\s+)?[Aa]ccount:(\S+?),\s*Location:(.+?),\s*([A-Z]{2}),\s*""" +
            """Amount\(Approx\.\):\s*([\d,]*\.\d{2})\s+([A-Z]{3}),\s*Av\.Bal:\s*(-?[\d,]*\.\d{2})""" +
            """\s+LKR,\s*Date:(\d{2}\.\d{2}\.\d{2}),\s*Time:(\d{1,2}:\d{2}).*$""",
    )

    // Shape 4 — multi-line ATM receipt. DOTALL so `.` matches newlines.
    // "HNB SMS ALERT:Transaction for Amt(Approx.):  549.00 LKR declined due to insufficient
    //  funds. … Account: 0190***5845,Location: PickMe Ride, LK,Avl Bal: 276.02 LKR,Date: 24.07.26,
    //  Time:06:54" — nothing moved, but a failing subscription is worth seeing.
    private val declinedCard = Regex(
        """^HNB\s+SMS\s+ALERT:\s*Transaction\s+for\s+Amt\(Approx\.\):\s*([\d,]*\.\d{2})\s+([A-Z]{3})\s+""" +
            """declined.*?Account:\s*(\S+?),\s*Location:\s*(.+?),\s*([A-Z]{2}),\s*Avl\s+Bal:\s*(-?[\d,]*\.\d{2})""" +
            """\s+LKR,\s*Date:\s*(\d{2}\.\d{2}\.\d{2}),\s*Time:\s*(\d{1,2}:\d{2}).*$""",
        RegexOption.DOT_MATCHES_ALL,
    )

    // "TRANSACTION REVERSAL, Credit account:0190***5845,Location:PAYPAL, LU,Amount:1.00 USD,
    //  Av.Bal:40260.92 LKR,Date:14.06.26,Time:20:45" — money coming back.
    private val reversal = Regex(
        """^TRANSACTION\s+REVERSAL,\s*Credit\s+account:(\S+?),\s*Location:(.+?),\s*([A-Z]{2}),\s*""" +
            """Amount:\s*([\d,]*\.\d{2})\s+([A-Z]{3}),\s*Av\.Bal:\s*(-?[\d,]*\.\d{2})\s+LKR,\s*""" +
            """Date:(\d{2}\.\d{2}\.\d{2}),\s*Time:(\d{1,2}:\d{2}).*$""",
        RegexOption.DOT_MATCHES_ALL,
    )

    // The charge alert sometimes arrives cut after the time, with no remarks and no balance.
    private val feeAlertShort = Regex(
        """^A\s+Transaction\s+for\s+LKR\s+([\d,]*\.\d{2})\s+has\s+been\s+(debit|credit)\s*ed\s+to\s+""" +
            """Ac\s+No:(\S+)\s+on\s+(\d{2}/\d{2}/\d{2})\s+(\d{2}:\d{2}:\d{2})\s*\.?\s*$""",
    )

    private val atmReceipt = Regex(
        """^HNB\s+ATM\s+Withdrawal\s+e-Receipt\s+Amt\(Approx\.\):\s*([\d,]+\.\d{2})\s+LKR\s+""" +
            """A/C:\s*(\S+)\s+Txn\s+Fee:\s*([\d,]+\.\d{2})\s*LKR\s+Location:\s*(.+?)\s+""" +
            """Term\s+ID:\s*\S+\s+Date:\s*(\d{2}\.\d{2}\.\d{2})\s+Time:\s*(\d{1,2}:\d{2})\s+""" +
            """Txn\s+No:\s*\S+\s+Avl\s+Bal:\s*([\d,]+\.\d{2})\s+LKR.*$""",
        RegexOption.DOT_MATCHES_ALL,
    )

    // Shape 5 — payment notice to the payee. Only the first line matters; disclaimers vary.
    private val received = Regex(
        """^You\s+received\s+(LKR|USD|EUR|GBP)\s+([\d,]+(?:\.\d{1,2})?)\s+from\s+(.+?)\s*(?:\n|$)""",
        RegexOption.IGNORE_CASE,
    )

    // Shape 6 — credit-card alert (provisional). Anchored on the "(Apprx)" token which no other
    // HNB shape uses; the Av.Bal tail is optional.
    private val creditCard = Regex(
        """\*\*(\d{4})\s*:\s*(.+?)\s+\(Apprx\)\s+([A-Z]{3})\s+([\d,]+\.\d{2})\s*""" +
            """\((\d{1,2}-[A-Za-z]{3}-\d{4}\s+\d{1,2}:\d{2}:\d{2}\s+[AP]M)\)""" +
            """(?:.*?Av\.Bal\s*:\s*LKR\s+([\d,]+\.\d{2}))?""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    override fun tryParse(body: String, receivedAt: Long): ParseResult? {
        val trimmed = body.trim()

        if (received.containsMatchIn(trimmed)) {
            return ParseResult.Informational("HNB payment notice (money lands in the recipient's own bank)")
        }

        creditCard.find(trimmed)?.let { m ->
            val card = m.groupValues[1]
            val merchant = m.groupValues[2].trim()
            val currency = Currency.normalize(m.groupValues[3])
            val amountStr = m.groupValues[4]
            val stamp = m.groupValues[5]
            val balanceStr = m.groupValues[6]
            val sameCurrency = currency == Currency.LKR
            return ParseResult.Success(
                ParsedTransaction(
                    senderAddress = "HNB",
                    accountNumberSuffix = card,
                    amount = Money.ofMajor(amountStr, currency),
                    balance = if (sameCurrency && balanceStr.isNotBlank()) Money.ofMajor(balanceStr, Currency.LKR) else null,
                    fee = null,
                    flow = TransactionFlow.EXPENSE,
                    type = TransactionType.POS,
                    merchantRaw = merchant,
                    location = null,
                    timestamp = TimeParser.parseHnbCard(stamp) ?: receivedAt,
                    isDeclined = false,
                    rawBody = body,
                ),
            )
        }

        accountTxn.find(trimmed)?.let { m ->
            val (amountStr, direction, account, date, time, reason, balanceStr) = m.destructured
            val credited = direction.equals("credited", ignoreCase = true)
            val type = classifyAccountType(reason)
            val bodyTs = TimeParser.parseHnbAccount(date, time)
            return ParseResult.Success(
                ParsedTransaction(
                    senderAddress = "HNB",
                    accountNumberSuffix = account,
                    amount = Money.ofMajor(amountStr, Currency.LKR),
                    balance = Money.ofMajor(balanceStr, Currency.LKR),
                    fee = null,
                    flow = if (credited) TransactionFlow.INCOME else TransactionFlow.EXPENSE,
                    type = type,
                    // Reason is a free-text tag ("MB:Sadah", "CEFT-[NAME]"); not a merchant,
                    // but useful as a secondary label when no dedicated payee field exists.
                    merchantRaw = reason.trim().takeIf { it.isNotBlank() },
                    location = null,
                    timestamp = bodyTs ?: receivedAt,
                    isDeclined = false,
                    rawBody = body,
                ),
            )
        }

        feeAlert.find(trimmed)?.let { m ->
            val (amountStr, direction, account, date, time, remarks, balanceStr) = m.destructured
            val bodyTs = TimeParser.parseHnbAccount(date, time)
            val credited = direction.equals("credit", ignoreCase = true)
            return ParseResult.Success(
                ParsedTransaction(
                    senderAddress = "HNB",
                    accountNumberSuffix = account,
                    amount = Money.ofMajor(amountStr, Currency.LKR),
                    balance = Money.ofMajor(balanceStr, Currency.LKR),
                    fee = null,
                    flow = if (credited) TransactionFlow.INCOME else TransactionFlow.EXPENSE,
                    type = TransactionType.FEE,
                    merchantRaw = remarks.trim().takeIf { it.isNotBlank() },
                    location = null,
                    timestamp = bodyTs ?: receivedAt,
                    isDeclined = false,
                    rawBody = body,
                ),
            )
        }

        feeAlertShort.find(trimmed)?.let { m ->
            val (amountStr, direction, account, date, time) = m.destructured
            val credited = direction.equals("credit", ignoreCase = true)
            return ParseResult.Success(
                ParsedTransaction(
                    senderAddress = "HNB",
                    accountNumberSuffix = account,
                    amount = Money.ofMajor(amountStr, Currency.LKR),
                    balance = null,
                    fee = null,
                    flow = if (credited) TransactionFlow.INCOME else TransactionFlow.EXPENSE,
                    type = TransactionType.FEE,
                    merchantRaw = null,
                    location = null,
                    timestamp = TimeParser.parseHnbAccount(date, time) ?: receivedAt,
                    isDeclined = false,
                    rawBody = body,
                ),
            )
        }

        reversal.find(trimmed)?.let { m ->
            val (account, merchant, _, amountStr, currencyRaw, balanceStr, date, time) = m.destructured
            val currency = Currency.normalize(currencyRaw)
            return ParseResult.Success(
                ParsedTransaction(
                    senderAddress = "HNB",
                    accountNumberSuffix = account,
                    amount = Money.ofMajor(amountStr, currency),
                    balance = if (currency == Currency.LKR) Money.ofMajor(balanceStr, Currency.LKR) else null,
                    fee = null,
                    flow = TransactionFlow.INCOME,
                    type = TransactionType.OTHER,
                    merchantRaw = merchant.trim(),
                    location = null,
                    timestamp = TimeParser.parseHnbDot(date, time) ?: receivedAt,
                    isDeclined = false,
                    rawBody = body,
                ),
            )
        }

        declinedCard.find(trimmed)?.let { m ->
            val (amountStr, currencyRaw, account, merchant, _, balanceStr, date, time) = m.destructured
            val currency = Currency.normalize(currencyRaw)
            return ParseResult.Success(
                ParsedTransaction(
                    senderAddress = "HNB",
                    accountNumberSuffix = account,
                    amount = Money.ofMajor(amountStr, currency),
                    balance = if (currency == Currency.LKR) Money.ofMajor(balanceStr, Currency.LKR) else null,
                    fee = null,
                    flow = TransactionFlow.EXPENSE,
                    type = TransactionType.DECLINED,
                    merchantRaw = merchant.trim(),
                    location = null,
                    timestamp = TimeParser.parseHnbDot(date, time) ?: receivedAt,
                    isDeclined = true,
                    rawBody = body,
                ),
            )
        }

        cardAlert.find(trimmed)?.let { m ->
            val (account, merchant, _, amountStr, currencyRaw, balanceStr, date, time) = m.destructured
            val currency = Currency.normalize(currencyRaw)
            val bodyTs = TimeParser.parseHnbDot(date, time)
            // HNB quotes FX transactions with the amount in foreign currency but the balance
            // always in LKR. Our entity model assumes amount and balance share currency, so
            // we drop the balance on FX rows rather than mis-report it.
            val sameCurrency = currency == Currency.LKR
            return ParseResult.Success(
                ParsedTransaction(
                    senderAddress = "HNB",
                    accountNumberSuffix = account,
                    amount = Money.ofMajor(amountStr, currency),
                    balance = if (sameCurrency) Money.ofMajor(balanceStr, Currency.LKR) else null,
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

        atmReceipt.find(trimmed)?.let { m ->
            val (amountStr, account, feeStr, location, date, time, balanceStr) = m.destructured
            val bodyTs = TimeParser.parseHnbDot(date, time)
            val feeMinor = Money.ofMajor(feeStr, Currency.LKR).minorUnits
            return ParseResult.Success(
                ParsedTransaction(
                    senderAddress = "HNB",
                    accountNumberSuffix = account,
                    amount = Money.ofMajor(amountStr, Currency.LKR),
                    balance = Money.ofMajor(balanceStr, Currency.LKR),
                    fee = if (feeMinor > 0) Money(feeMinor, Currency.LKR) else null,
                    flow = TransactionFlow.EXPENSE,
                    type = TransactionType.ATM,
                    merchantRaw = null,
                    // HNB's ATM location is "BANK NAME         , LKA" — trim the country suffix
                    // and collapse padding whitespace so the UI reads cleanly.
                    location = location.trim().removeSuffix(", LKA").trim().ifBlank { null },
                    timestamp = bodyTs ?: receivedAt,
                    isDeclined = false,
                    rawBody = body,
                ),
            )
        }

        return null
    }

    private fun classifyAccountType(reason: String): TransactionType = when {
        reason.startsWith("CEFT", ignoreCase = true) -> TransactionType.CEFT
        reason.startsWith("MB:", ignoreCase = true) -> TransactionType.ONLINE_TRANSFER
        reason.contains("SLIPS", ignoreCase = true) -> TransactionType.SLIPS
        reason.contains("ATM", ignoreCase = true) -> TransactionType.ATM
        reason.contains("Cheque", ignoreCase = true) -> TransactionType.CHEQUE
        else -> TransactionType.OTHER
    }
}
