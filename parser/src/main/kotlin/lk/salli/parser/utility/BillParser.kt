package lk.salli.parser.utility

import lk.salli.domain.Currency
import lk.salli.domain.Money

/**
 * Turns utility-bill SMS into [ParsedBill]s. Everything else from these senders (promos,
 * surveys, service notices) returns null so the caller can drop it.
 *
 * Verified on real samples: SLT-MOBITEL fixed-line bills (issued, reminder, overdue, payment
 * received). Reconstructed from field evidence only, no real sample yet: Dialog postpaid,
 * CEB electricity, NWSDB water — those shapes are labelled `_reconstructed` in the tests.
 */
object BillParser {

    private const val SLT = "SLT-MOBITEL"
    private const val DIALOG = "Dialog"
    private const val CEB = "CEB"
    private const val NWSDB = "NWSDB"

    private val amount = """(-?[\d,]+(?:\.\d{1,2})?)"""

    // --- SLT-MOBITEL ---------------------------------------------------------------------
    private val sltIssuedPhone = Regex("""Home\s+Telephone\s+No\s*:\s*(\d{9,11})""", RegexOption.IGNORE_CASE)
    private val sltIssuedPeriod = Regex("""Bill\s+Period\s*:\s*(\d{4}-\d{2}-\d{2})\s+to\s+(\d{4}-\d{2}-\d{2})""", RegexOption.IGNORE_CASE)
    private val sltIssuedTotal = Regex("""Total\s+Payable\s*:\s*$amount""", RegexOption.IGNORE_CASE)
    private val sltIssuedDue = Regex("""Payment\s+Due\s+date\s*:\s*(\d{4}-\d{2}-\d{2})""", RegexOption.IGNORE_CASE)

    private val sltPayment = Regex(
        """bill\s+payment\s+Rs\.?\s*$amount\s+has\s+been\s+received\s+to\s+account\s+number\s+(\d+)\s*(?:\((\d+)\))?""",
        RegexOption.IGNORE_CASE,
    )
    private val sltReminder = Regex(
        """settle\s+your\s+bill\s+outstanding\s+for\s+SLT-MOBITEL\s+Home\s+no\s+(\d{9,11})\s+before\s+(\d{1,2}\.\d{1,2}\.\d{4}).*?due\s+amount\s+up\s+to\s+month\s+of\s+([A-Za-z]+\s+\d{4})\s+is\s+Rs\.?\s*$amount""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val sltDueSoon = Regex(
        """due\s+date\s+is\s+reaching\.?\s+SLT-MOBITEL\s+Home\s+bill\s+(\d{9,11})\s+payable\s+is\s+Rs\.?\s*$amount""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val sltOverdue = Regex(
        """SLT-MOBITEL\s+Home\s+bill\s+(\d{9,11})\s+of\s+Rs\.?\s*$amount\s+is\s+overdue""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    // --- Dialog postpaid (reconstructed) ------------------------------------------------
    private val dialogAccount = Regex("""Dialog\s+Mobile\s+bill\s+for\s+(\d{9,11})""", RegexOption.IGNORE_CASE)
    private val dialogPeriod = Regex("""Bill\s+period\s*:\s*From\s+(\d{1,2}-[A-Za-z]{3}-\d{2})\s+To\s+(\d{1,2}-[A-Za-z]{3}-\d{2})""", RegexOption.IGNORE_CASE)
    private val dialogValue = Regex("""Bill\s+value\s+for\s+the\s+period\s*:\s*Rs\.?\s*$amount""", RegexOption.IGNORE_CASE)
    private val dialogDue = Regex("""Bill\s+due\s+date\s*:\s*(\d{1,2}-[A-Za-z]{3}-\d{2})""", RegexOption.IGNORE_CASE)
    private val dialogOutstanding = Regex("""Total\s+outstanding\s+as\s+at\s+\d{1,2}-[A-Za-z]{3}-\d{2}\s*:\s*Rs\.?\s*$amount""", RegexOption.IGNORE_CASE)

    // --- CEB electricity (reconstructed) ------------------------------------------------
    private val cebMarker = Regex("""\bCEB\b|Ceylon\s+Electricity""", RegexOption.IGNORE_CASE)
    private val cebAccount = Regex("""A/C\s+No\s*:\s*(\d{6,12})""", RegexOption.IGNORE_CASE)
    private val cebTotalDue = Regex("""Total\s+Due\s*:\s*Rs\.?\s*$amount""", RegexOption.IGNORE_CASE)
    private val cebMonthly = Regex("""Monthly\s+Bill\s*:\s*Rs\.?\s*$amount""", RegexOption.IGNORE_CASE)
    private val cebReading = Regex("""Reading\s+Date\s*:\s*(\d{4}-\d{2}-\d{2})""", RegexOption.IGNORE_CASE)
    private val cebPayBy = Regex("""\bby\s+(\d{4}-\d{2}-\d{2})""", RegexOption.IGNORE_CASE)

    // --- NWSDB water (reconstructed) ------------------------------------------------------
    private val nwsdbMarker = Regex("""NWSDB|Water\s+Board|Water\s+Bill""", RegexOption.IGNORE_CASE)
    private val nwsdbAccount = Regex("""A/C\s+No\s*:\s*([\d/]{5,20})""", RegexOption.IGNORE_CASE)
    private val nwsdbTotalDue = Regex("""Total\s+Due\s*:\s*Rs\.?\s*$amount""", RegexOption.IGNORE_CASE)
    private val nwsdbMonthly = Regex("""Monthly\s+Charges\s*:\s*Rs\.?\s*$amount""", RegexOption.IGNORE_CASE)
    private val nwsdbPeriod = Regex("""Period\s*:\s*(\d{2}-\d{2}-\d{4})\s+to\s+(\d{2}-\d{2}-\d{4})""", RegexOption.IGNORE_CASE)
    private val nwsdbDue = Regex("""Due\s+Date\s*:\s*(\d{2}-\d{2}-\d{4})""", RegexOption.IGNORE_CASE)

    fun parse(sender: String, body: String): ParsedBill? {
        if (!UtilitySenders.isBillSender(sender)) return null
        return parseBody(body)
    }

    /**
     * Sender-agnostic parse for bodies we already stored (the bills table keeps the text but
     * not the sender). CEB and water rely on their in-body markers here.
     */
    fun parseBody(body: String): ParsedBill? {
        val text = body.trim()
        return parseSlt(text) ?: parseDialog(text) ?: parseCeb("", text) ?: parseNwsdb("", text)
    }

    private fun parseSlt(text: String): ParsedBill? {
        sltPayment.find(text)?.let { m ->
            val paid = minor(m.groupValues[1])
            val ref = normalisePhone(m.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() } ?: m.groupValues[2])
            return ParsedBill(SLT, ref, 0L, Currency.LKR, null, null, BillKind.PAYMENT_RECEIVED, paid, text)
        }
        sltOverdue.find(text)?.let { m ->
            return ParsedBill(SLT, normalisePhone(m.groupValues[1]), minor(m.groupValues[2]), Currency.LKR, null, null, BillKind.OVERDUE, null, text)
        }
        sltReminder.find(text)?.let { m ->
            return ParsedBill(
                biller = SLT,
                accountRef = normalisePhone(m.groupValues[1]),
                amountDueMinor = minor(m.groupValues[4]),
                currency = Currency.LKR,
                dueDateMillis = UtilityDates.dayMillis(m.groupValues[2]),
                periodLabel = m.groupValues[3].replace(Regex("""\s+"""), " ").trim(),
                kind = BillKind.REMINDER,
                paidAmountMinor = null,
                rawBody = text,
            )
        }
        sltDueSoon.find(text)?.let { m ->
            return ParsedBill(SLT, normalisePhone(m.groupValues[1]), minor(m.groupValues[2]), Currency.LKR, null, null, BillKind.REMINDER, null, text)
        }
        val phone = sltIssuedPhone.find(text)?.groupValues?.get(1)
        val total = sltIssuedTotal.find(text)?.groupValues?.get(1)
        if (phone != null && total != null) {
            val period = sltIssuedPeriod.find(text)
            return ParsedBill(
                biller = SLT,
                accountRef = normalisePhone(phone),
                amountDueMinor = minor(total),
                currency = Currency.LKR,
                dueDateMillis = sltIssuedDue.find(text)?.let { UtilityDates.dayMillis(it.groupValues[1]) },
                periodLabel = period?.let { "${it.groupValues[1]} to ${it.groupValues[2]}" },
                kind = BillKind.ISSUED,
                paidAmountMinor = null,
                rawBody = text,
            )
        }
        return null
    }

    private fun parseDialog(text: String): ParsedBill? {
        val account = dialogAccount.find(text)?.groupValues?.get(1) ?: return null
        val due = dialogOutstanding.find(text)?.groupValues?.get(1)
            ?: dialogValue.find(text)?.groupValues?.get(1)
            ?: return null
        val period = dialogPeriod.find(text)
        return ParsedBill(
            biller = DIALOG,
            accountRef = normalisePhone(account),
            amountDueMinor = minor(due),
            currency = Currency.LKR,
            dueDateMillis = dialogDue.find(text)?.let { UtilityDates.dayMillis(it.groupValues[1]) },
            periodLabel = period?.let { "${it.groupValues[1]} to ${it.groupValues[2]}" },
            kind = BillKind.ISSUED,
            paidAmountMinor = null,
            rawBody = text,
        )
    }

    private fun parseCeb(sender: String, text: String): ParsedBill? {
        if (!(sender.contains("CEB", ignoreCase = true) || cebMarker.containsMatchIn(text))) return null
        val account = cebAccount.find(text)?.groupValues?.get(1) ?: return null
        val due = cebTotalDue.find(text)?.groupValues?.get(1)
            ?: cebMonthly.find(text)?.groupValues?.get(1)
            ?: return null
        return ParsedBill(
            biller = CEB,
            accountRef = account,
            amountDueMinor = minor(due),
            currency = Currency.LKR,
            dueDateMillis = cebPayBy.find(text)?.let { UtilityDates.dayMillis(it.groupValues[1]) },
            periodLabel = cebReading.find(text)?.let { "Reading ${it.groupValues[1]}" },
            kind = BillKind.ISSUED,
            paidAmountMinor = null,
            rawBody = text,
        )
    }

    private fun parseNwsdb(sender: String, text: String): ParsedBill? {
        if (!(sender.contains("NWSDB", ignoreCase = true) || nwsdbMarker.containsMatchIn(text))) return null
        val account = nwsdbAccount.find(text)?.groupValues?.get(1) ?: return null
        val due = nwsdbTotalDue.find(text)?.groupValues?.get(1)
            ?: nwsdbMonthly.find(text)?.groupValues?.get(1)
            ?: return null
        val period = nwsdbPeriod.find(text)
        return ParsedBill(
            biller = NWSDB,
            accountRef = account,
            amountDueMinor = minor(due),
            currency = Currency.LKR,
            dueDateMillis = nwsdbDue.find(text)?.let { UtilityDates.dayMillis(it.groupValues[1]) },
            periodLabel = period?.let { "${it.groupValues[1]} to ${it.groupValues[2]}" },
            kind = BillKind.ISSUED,
            paidAmountMinor = null,
            rawBody = text,
        )
    }

    private fun minor(text: String): Long = Money.ofMajor(text, Currency.LKR).minorUnits

    /** `94372066858` and `0372066858` are the same connection; store the local form. */
    internal fun normalisePhone(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return if (digits.length == 11 && digits.startsWith("94")) "0" + digits.substring(2) else digits
    }
}
