package lk.salli.data.summary

import java.text.NumberFormat
import java.util.Locale
import lk.salli.data.db.entities.TransactionEntity
import lk.salli.domain.TransactionFlow

/** One notification's worth of text. */
data class SpendingSummary(val title: String, val text: String)

/**
 * Turns a window of transactions into a one-line spending summary. Pure: no DB, no clock, so
 * the worker stays thin and this stays unit-testable. Declined rows, both legs of an own
 * transfer and hidden accounts never count; totals use the dominant currency of the window.
 */
object SummaryBuilder {

    fun build(
        title: String,
        txns: List<TransactionEntity>,
        categoryNames: Map<Long, String>,
        hiddenAccountIds: Set<Long>,
    ): SpendingSummary? {
        val real = txns.filter { !it.isDeclined && it.transferGroupId == null && it.accountId !in hiddenAccountIds }
        val spend = real.filter { it.flowId == TransactionFlow.EXPENSE.id }
        val income = real.filter { it.flowId == TransactionFlow.INCOME.id }
        if (spend.isEmpty() && income.isEmpty()) return null

        val currency = (spend + income).groupBy { it.amountCurrency }.maxByOrNull { it.value.size }!!.key
        val spendIn = spend.filter { it.amountCurrency == currency }
        val incomeIn = income.filter { it.amountCurrency == currency }
        val spentMinor = spendIn.sumOf { it.amountMinor }
        val incomeMinor = incomeIn.sumOf { it.amountMinor }

        val parts = ArrayList<String>(3)
        parts += if (spendIn.isEmpty()) "Nothing spent" else {
            val n = spendIn.size
            "${money(spentMinor, currency)} spent in $n transaction${if (n == 1) "" else "s"}"
        }
        spendIn.groupBy { it.categoryId }
            .maxByOrNull { (_, list) -> list.sumOf { it.amountMinor } }
            ?.let { (catId, list) ->
                val name = catId?.let { categoryNames[it] } ?: "Uncategorised"
                parts += "Top: $name ${money(list.sumOf { it.amountMinor }, currency)}"
            }
        if (incomeMinor > 0) parts += "In: ${money(incomeMinor, currency)}"
        return SpendingSummary(title = title, text = parts.joinToString(" · "))
    }

    private fun money(minor: Long, currency: String): String {
        val symbol = if (currency == "LKR") "Rs " else "$currency "
        val abs = kotlin.math.abs(minor)
        val whole = NumberFormat.getIntegerInstance(Locale.US).format(abs / 100)
        val cents = abs % 100
        return if (cents == 0L) "$symbol$whole" else "$symbol$whole.${"%02d".format(cents)}"
    }
}
