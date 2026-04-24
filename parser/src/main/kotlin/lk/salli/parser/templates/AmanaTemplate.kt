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
 * Parser for Amana Bank PLC (sender `AMANABANK`).
 *
 * Four transaction shapes plus an informational reject. Amana's SMS don't carry an in-body
 * timestamp so all rows fall back to `receivedAt`.
 *
 *  1. **POS** — `POS trx of LKR X at <MERCHANT> authorised from your A/C ***N. Avail. Bal. …`
 *  2. **Inward CEFTS transfer (credit)** — `Inward CEFTS trf of LKR X (<REASON>) from: <PAYER> …`
 *  3. **Outbound IB CEFTS transfer (debit)** — carries an explicit transfer fee:
 *     `IB CEFTS trf of LKR X & charge of LKR Y (<REASON>) to: <PAYEE> debited from …`
 *  4. **Profit distribution** — savings-account interest credit, Islamic-banking terminology.
 *
 *  5. **Onboarding info** — `Dear Customer, Your new LKR Statement Savings A/C …` → reject.
 */
object AmanaTemplate : BankTemplate {

    override val name: String = "Amana Bank"

    override val senderPatterns: List<Regex> = listOf(Regex("^AMANABANK$"))

    // POS: "POS trx of LKR 1,647.70 at HIGGSFIELD INC. +14088370029 US authorised from your A/C ***0001. Avail. Bal. LKR 2,647.60. Enq …"
    private val posTxn = Regex(
        """^POS\s+trx\s+of\s+LKR\s+([\d,]+\.\d{2})\s+at\s+(.+?)\s+authorised\s+from\s+your\s+""" +
            """A/C\s+(\S+)\.\s+Avail\.\s*Bal\.\s*LKR\s+([\d,]+\.\d{2}).*$""",
    )

    // Inward: "Inward CEFTS trf of LKR 2,000.00 (NADHIR)  from: IBFT 115510546317 credited to your A/C ***0001. Avail Bal LKR 4,597.60. Enq …"
    private val inwardTxn = Regex(
        """^Inward\s+CEFTS\s+trf\s+of\s+LKR\s+([\d,]+\.\d{2})\s+\(([^)]*)\)\s+from:\s+""" +
            """(.+?)\s+credited\s+to\s+your\s+A/C\s+(\S+)\.\s+Avail\s+Bal\s+LKR\s+""" +
            """([\d,]+\.\d{2}).*$""",
    )

    // Outbound: "IB CEFTS trf of LKR 4,800.00 & charge of LKR 25.00 (DRESS+MARKETIN) to: AMMAAR HNB debited from your A/C ***0001. Avail. Bal. LKR  4,254.35. Enq …"
    private val outboundTxn = Regex(
        """^IB\s+CEFTS\s+trf\s+of\s+LKR\s+([\d,]+\.\d{2})\s+&\s+charge\s+of\s+LKR\s+""" +
            """([\d,]+\.\d{2})\s+\(([^)]*)\)\s+to:\s+(.+?)\s+debited\s+from\s+your\s+""" +
            """A/C\s+(\S+)\.\s+Avail\.\s*Bal\.\s*LKR\s+([\d,]+\.\d{2}).*$""",
    )

    // Profit: "Profit of your Savings A/C ***0001 has been distributed to A/C ***0001. Profit Amount (Subject to Withholding Tax)LKR 11.57. Enq …"
    // Savings-account profit under Islamic banking — equivalent to interest credit.
    private val profitTxn = Regex(
        """^Profit\s+of\s+your\s+Savings\s+A/C\s+(\S+)\s+has\s+been\s+distributed\s+to\s+""" +
            """A/C\s+\S+\.\s+Profit\s+Amount\s+\(Subject\s+to\s+Withholding\s+Tax\)\s*""" +
            """LKR\s+([\d,]+\.\d{2}).*$""",
    )

    private val onboardingInfo = Regex(
        """^Dear\s+Customer,\s+Your\s+new\s+LKR.*Savings\s+A/C.*is\s+ready\s+to\s+be\s+funded.*$""",
        RegexOption.IGNORE_CASE,
    )

    override fun tryParse(body: String, receivedAt: Long): ParseResult? {
        val trimmed = body.trim()

        if (onboardingInfo.containsMatchIn(trimmed)) {
            return ParseResult.Informational("Amana onboarding notification")
        }

        posTxn.find(trimmed)?.let { m ->
            val (amountStr, merchant, account, balanceStr) = m.destructured
            return ParseResult.Success(
                ParsedTransaction(
                    senderAddress = "AMANABANK",
                    accountNumberSuffix = account,
                    amount = Money.ofMajor(amountStr, Currency.LKR),
                    balance = Money.ofMajor(balanceStr, Currency.LKR),
                    fee = null,
                    flow = TransactionFlow.EXPENSE,
                    type = TransactionType.POS,
                    merchantRaw = merchant.trim(),
                    location = null,
                    timestamp = receivedAt,
                    isDeclined = false,
                    rawBody = body,
                ),
            )
        }

        outboundTxn.find(trimmed)?.let { m ->
            val (amountStr, feeStr, reason, payee, account, balanceStr) = m.destructured
            val feeMinor = Money.ofMajor(feeStr, Currency.LKR).minorUnits
            // Merge reason + payee into a single human-readable merchant label. The reason
            // string is free-text ("DRESS+MARKETIN") and rarely the whole story; prefixing
            // the payee keeps the counterparty visible in Timeline.
            val merchantLabel = buildString {
                append(payee.trim())
                val r = reason.trim()
                if (r.isNotEmpty()) append(" (").append(r).append(')')
            }
            return ParseResult.Success(
                ParsedTransaction(
                    senderAddress = "AMANABANK",
                    accountNumberSuffix = account,
                    amount = Money.ofMajor(amountStr, Currency.LKR),
                    balance = Money.ofMajor(balanceStr, Currency.LKR),
                    fee = if (feeMinor > 0) Money(feeMinor, Currency.LKR) else null,
                    flow = TransactionFlow.EXPENSE,
                    type = TransactionType.CEFT,
                    merchantRaw = merchantLabel,
                    location = null,
                    timestamp = receivedAt,
                    isDeclined = false,
                    rawBody = body,
                ),
            )
        }

        inwardTxn.find(trimmed)?.let { m ->
            val (amountStr, reason, payer, account, balanceStr) = m.destructured
            val merchantLabel = buildString {
                append(payer.trim())
                val r = reason.trim()
                if (r.isNotEmpty()) append(" (").append(r).append(')')
            }
            return ParseResult.Success(
                ParsedTransaction(
                    senderAddress = "AMANABANK",
                    accountNumberSuffix = account,
                    amount = Money.ofMajor(amountStr, Currency.LKR),
                    balance = Money.ofMajor(balanceStr, Currency.LKR),
                    fee = null,
                    flow = TransactionFlow.INCOME,
                    type = TransactionType.CEFT,
                    merchantRaw = merchantLabel,
                    location = null,
                    timestamp = receivedAt,
                    isDeclined = false,
                    rawBody = body,
                ),
            )
        }

        profitTxn.find(trimmed)?.let { m ->
            val (account, amountStr) = m.destructured
            return ParseResult.Success(
                ParsedTransaction(
                    senderAddress = "AMANABANK",
                    accountNumberSuffix = account,
                    amount = Money.ofMajor(amountStr, Currency.LKR),
                    balance = null,
                    fee = null,
                    flow = TransactionFlow.INCOME,
                    type = TransactionType.OTHER,
                    merchantRaw = "Savings profit",
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
