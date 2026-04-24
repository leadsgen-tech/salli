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
 * Parser for Seylan Bank (sender `SEYLAN` — alt `SEYLANBANK`).
 *
 * Two shapes:
 *
 *  1. **Card debit** — one line, single uniform template covering POS, ATM, and card-fee
 *     charges. The `at <text>` segment embeds merchant, optional location, optional country
 *     code, separated by variable whitespace padding. We classify the type heuristically from
 *     the merchant text: bank names → ATM, `FEES/CHARGE` → FEE, else POS.
 *
 *  2. **Login notification** — `Dear MR <NAME>, You have logged in to Seylan Online Banking…`.
 *     Informational; rejected here so it never hits the Unknown queue.
 */
object SeylanTemplate : BankTemplate {

    override val name: String = "Seylan Bank"

    override val senderPatterns: List<Regex> = listOf(
        Regex("^SEYLAN$"),
        Regex("^SEYLANBANK$"),
    )

    // "Seylan Card ...3687 debit Txn 9566324039 of LKR 5,000.00 done on 19/12/2025 04:18:36 PM at <MERCHANT/LOC>. Avl bal 2,505.03"
    private val cardDebit = Regex(
        """^Seylan\s+Card\s+\.+(\d{3,4})\s+debit\s+Txn\s+\S+\s+of\s+LKR\s+([\d,]+\.\d{2})\s+""" +
            """done\s+on\s+(\d{2}/\d{2}/\d{4})\s+(\d{1,2}:\d{2}:\d{2})\s+(AM|PM)\s+at\s+""" +
            """(.+?)\.\s*Avl\s+bal\s+([\d,]+\.\d{2})\s*$""",
    )

    private val loginInfo = Regex(
        """^Dear\s+(?:MR|MRS|MISS|MS)\s+.+?,\s*You\s+have\s+logged\s+in\s+to\s+Seylan\s+""" +
            """Online\s+Banking/Mobile\s+Banking.*$""",
        RegexOption.IGNORE_CASE,
    )

    override fun tryParse(body: String, receivedAt: Long): ParseResult? {
        val trimmed = body.trim()

        if (loginInfo.containsMatchIn(trimmed)) {
            return ParseResult.Informational("Seylan login notification")
        }

        cardDebit.find(trimmed)?.let { m ->
            val (cardLast, amountStr, date, time, ampm, rawTail, balanceStr) = m.destructured
            // Classify against the *full* tail (before splitting) so keyword hits like
            // "DEBIT CARD ANNUAL FEES" aren't lost to a premature merchant/location split.
            val type = classifyType(rawTail)
            val (merchant, location) = splitMerchantAndLocation(rawTail)
            val bodyTs = TimeParser.parseSeylan(date, time, ampm)
            return ParseResult.Success(
                ParsedTransaction(
                    senderAddress = "SEYLAN",
                    accountNumberSuffix = "#$cardLast",
                    amount = Money.ofMajor(amountStr, Currency.LKR),
                    balance = Money.ofMajor(balanceStr, Currency.LKR),
                    fee = null,
                    flow = TransactionFlow.EXPENSE,
                    type = type,
                    merchantRaw = merchant,
                    location = location,
                    timestamp = bodyTs ?: receivedAt,
                    isDeclined = false,
                    rawBody = body,
                ),
            )
        }

        return null
    }

    /**
     * Seylan bundles merchant, location, and country-code into one whitespace-padded blob like
     * `Nippon Bag City          KURUNEGALA   LK`. Strip the trailing `LK` country code when
     * present and use whitespace-run splits to separate the parts.
     */
    private fun splitMerchantAndLocation(raw: String): Pair<String, String?> {
        val cleaned = raw.replace(Regex("\\s+"), " ").trim()
        val withoutCountry = cleaned.removeSuffix(" LK").removeSuffix(" US").trim()
        // If the remainder has two or more words, treat the last as the location (city),
        // everything before as merchant. Single-word or sparse strings stay as merchant only.
        val lastSpace = withoutCountry.lastIndexOf(' ')
        if (lastSpace < 0) return withoutCountry to null
        val maybeCity = withoutCountry.substring(lastSpace + 1)
        // A "city" should be mostly alphabetic and short-ish; otherwise it's just part of the
        // merchant name and the split is wrong.
        if (maybeCity.length in 3..20 && maybeCity.all { it.isLetter() || it == '-' }) {
            return withoutCountry.substring(0, lastSpace).trim() to maybeCity
        }
        return withoutCountry to null
    }

    private fun classifyType(merchant: String): TransactionType {
        val m = merchant.uppercase()
        return when {
            m.contains("FEES") || m.contains("CHARGE") -> TransactionType.FEE
            // ATMs typically advertise as a bank name or an ATM-network acronym. CRMn is
            // HNB-style cash-recycle machine; "BR" / "Offsite" are Seylan's own ATM sites.
            m.contains("PEOPLES BANK") || m.contains("HNB") || m.contains("COMMERCIAL") ||
                m.contains("SAMPATH") || m.contains("BOC") || m.contains("NTB") ||
                m.contains("DFCC") || m.contains("ICBS") || m.contains("OFFSITE") ||
                Regex("""\bCRM\s*\d*\b""").containsMatchIn(m) ||
                Regex("""\bBR\b""").containsMatchIn(m) -> TransactionType.ATM
            else -> TransactionType.POS
        }
    }
}
