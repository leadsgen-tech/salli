package lk.salli.parser.templates

import lk.salli.domain.Currency
import lk.salli.domain.Money
import lk.salli.domain.TransactionFlow
import lk.salli.domain.TransactionType
import lk.salli.parser.BankTemplate
import lk.salli.parser.ParseResult
import lk.salli.parser.ParsedTransaction

/**
 * Parser for BOC (Bank of Ceylon). Sender ID: `BOC`.
 *
 * Single transaction template; body must match end-to-end so informational notices
 * (tax reminders, etc.) naturally fall through to `null` → Unknown.
 */
object BocTemplate : BankTemplate {

    override val name: String = "BOC"

    override val senderPatterns: List<Regex> = listOf(Regex("^BOC$"))

    private val typeMap: Map<String, Pair<TransactionType, TransactionFlow>> = mapOf(
        "ATM Withdrawal" to (TransactionType.ATM to TransactionFlow.EXPENSE),
        "ATM Cash Deposit" to (TransactionType.ATM to TransactionFlow.INCOME),
        "Cash Deposit" to (TransactionType.CDM to TransactionFlow.INCOME),
        "Cheque Deposit" to (TransactionType.CHEQUE to TransactionFlow.INCOME),
        "Online Transfer Debit" to (TransactionType.ONLINE_TRANSFER to TransactionFlow.EXPENSE),
        "Online Transfer Credit" to (TransactionType.ONLINE_TRANSFER to TransactionFlow.INCOME),
        "CEFT Transfer Debit" to (TransactionType.CEFT to TransactionFlow.EXPENSE),
        "CEFT Transfer Credit" to (TransactionType.CEFT to TransactionFlow.INCOME),
        "Transfer Credit" to (TransactionType.ONLINE_TRANSFER to TransactionFlow.INCOME),
        "Transfer Debit" to (TransactionType.ONLINE_TRANSFER to TransactionFlow.EXPENSE),
        "POS/ATM Transaction" to (TransactionType.POS to TransactionFlow.EXPENSE),
    )

    // Anchored ^…$ so partial matches (e.g. "Dear Customer, Self-declaration…") fail.
    // Group captures:
    //   1 = transaction type phrase
    //   2 = amount (with thousand separators, 2dp)
    //   3 = direction word: "From" (debit) or "To" (credit)
    //   4 = account number suffix — just the trailing digits after the X-mask
    //   5 = balance
    private val pattern: Regex = Regex(
        """^(ATM Withdrawal|ATM Cash Deposit|Cash Deposit|Cheque Deposit|""" +
            """Online Transfer Debit|Online Transfer Credit|""" +
            """CEFT Transfer Debit|CEFT Transfer Credit|""" +
            """Transfer Credit|Transfer Debit|POS/ATM Transaction)""" +
            """ Rs ([\d,]+\.\d{2}) (From|To) A/C No X+(\d{3,6})\.""" +
            """ Balance available Rs ([\d,]+\.\d{2})""" +
            """ - Thank you for banking with BOC$""",
    )

    // ACH cheque-clearing SMS have an extra "CHQ/NO <number>" segment between the type and the
    // amount. Captures mirror the main pattern's order (type, amount, direction, suffix, balance).
    private val achClearingPattern: Regex = Regex(
        """^(ACH Clearing Debit|ACH Clearing Credit)\s+CHQ/NO\s+\d+""" +
            """\s+Rs ([\d,]+\.\d{2}) (From|To) A/C No X+(\d{3,6})\.""" +
            """ Balance available Rs ([\d,]+\.\d{2})""" +
            """ - Thank you for banking with BOC$""",
    )

    private val achTypeMap: Map<String, Pair<TransactionType, TransactionFlow>> = mapOf(
        "ACH Clearing Debit" to (TransactionType.CHEQUE to TransactionFlow.EXPENSE),
        "ACH Clearing Credit" to (TransactionType.CHEQUE to TransactionFlow.INCOME),
    )

    private val nonTransactionMarkers: List<Regex> = listOf(
        Regex("""^Dear Customer,""", RegexOption.IGNORE_CASE),
        Regex("""^Dear Flex User,""", RegexOption.IGNORE_CASE),
        Regex("""^Final Reminder""", RegexOption.IGNORE_CASE),
    )

    override fun tryParse(body: String, receivedAt: Long): ParseResult? {
        val trimmed = body.trim()

        if (nonTransactionMarkers.any { it.containsMatchIn(trimmed) }) {
            return ParseResult.Informational("BOC non-transaction notice")
        }

        pattern.find(trimmed)?.let { match ->
            val (typePhrase, amountStr, direction, maskedAcct, balanceStr) = match.destructured
            val (type, flow) = typeMap[typePhrase] ?: return null
            return buildResult(body, receivedAt, type, flow, direction, amountStr, maskedAcct, balanceStr)
        }

        achClearingPattern.find(trimmed)?.let { match ->
            val (typePhrase, amountStr, direction, maskedAcct, balanceStr) = match.destructured
            val (type, flow) = achTypeMap[typePhrase] ?: return null
            return buildResult(body, receivedAt, type, flow, direction, amountStr, maskedAcct, balanceStr)
        }

        return null
    }

    private fun buildResult(
        body: String,
        receivedAt: Long,
        type: TransactionType,
        flow: TransactionFlow,
        direction: String,
        amountStr: String,
        maskedAcct: String,
        balanceStr: String,
    ): ParseResult? {
        // Cross-validate: "From" ⇒ EXPENSE, "To" ⇒ INCOME. If the direction word disagrees with
        // the type phrase, we trust neither — safer to surface as Unknown.
        val directionFlow = if (direction == "From") TransactionFlow.EXPENSE else TransactionFlow.INCOME
        if (directionFlow != flow) return null

        return ParseResult.Success(
            ParsedTransaction(
                senderAddress = "BOC",
                accountNumberSuffix = maskedAcct,
                amount = Money.ofMajor(amountStr, Currency.LKR),
                balance = Money.ofMajor(balanceStr, Currency.LKR),
                fee = null,
                flow = flow,
                type = type,
                merchantRaw = null,
                location = null,
                timestamp = receivedAt,
                isDeclined = false,
                rawBody = body,
            ),
        )
    }
}
