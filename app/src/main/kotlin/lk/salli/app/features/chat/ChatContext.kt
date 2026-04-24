package lk.salli.app.features.chat

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import lk.salli.data.db.SalliDatabase
import lk.salli.domain.TransactionFlow

/**
 * Builds the compact "what's going on with my money" brief that gets stuffed into the LLM
 * prompt on every chat turn. Keep it tight — Qwen 0.5B's context is limited, and long
 * prompts also make inference noticeably slower on-device. Target: under ~400 tokens.
 *
 * The brief includes:
 *  - headline balance & account list
 *  - this-month spent/received/net
 *  - top categories by spend (last 30d)
 *  - top merchants by spend (last 30d)
 *  - last ~8 transactions
 *
 * We deliberately omit deep history. The model's job is to answer "how much on X this month"
 * and "what's my biggest expense" — not to recount every transaction we've ever seen.
 */
class ChatContextBuilder @Inject constructor(
    private val db: SalliDatabase,
) {
    suspend fun build(): String {
        val accounts = db.accounts().all()
        val categories = db.categories().all().associateBy { it.id }

        val nowMs = System.currentTimeMillis()
        val monthStart = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val thirtyDaysAgo = nowMs - 30L * 24 * 60 * 60 * 1000

        val txnsThisMonth = db.transactions().recentAll(monthStart)
            .filter { !it.isDeclined && it.transferGroupId == null }
        val txns30d = db.transactions().recentAll(thirtyDaysAgo)
            .filter { !it.isDeclined && it.transferGroupId == null }
        val recent = db.transactions().recentAll(nowMs - 90L * 24 * 60 * 60 * 1000)
            .take(8)

        val spentMonth = txnsThisMonth
            .filter { it.flowId == TransactionFlow.EXPENSE.id }
            .sumOf { it.amountMinor }
        val receivedMonth = txnsThisMonth
            .filter { it.flowId == TransactionFlow.INCOME.id }
            .sumOf { it.amountMinor }

        val categoryTotals = txns30d
            .filter { it.flowId == TransactionFlow.EXPENSE.id }
            .groupBy { it.categoryId }
            .mapValues { (_, list) -> list.sumOf { it.amountMinor } to list.size }
            .entries
            .sortedByDescending { it.value.first }
            .take(5)

        val merchantTotals = txns30d
            .filter { it.flowId == TransactionFlow.EXPENSE.id && !it.merchantRaw.isNullOrBlank() }
            .groupBy { it.merchantRaw!!.trim() }
            .mapValues { (_, list) -> list.sumOf { it.amountMinor } to list.size }
            .entries
            .sortedByDescending { it.value.first }
            .take(6)

        return buildString {
            appendLine("USER FINANCIAL SNAPSHOT (on-device data, Sri Lankan rupees).")
            appendLine()
            appendLine("Accounts (${accounts.size}):")
            accounts.forEach { a ->
                val bal = a.balanceMinor?.let { formatRupees(it) } ?: "unknown"
                appendLine("- ${a.displayName}: $bal")
            }
            appendLine()
            appendLine("${monthName(nowMs)} so far:")
            appendLine("- Spent: ${formatRupees(spentMonth)}")
            appendLine("- Received: ${formatRupees(receivedMonth)}")
            appendLine("- Net: ${formatRupeesSigned(receivedMonth - spentMonth)}")

            if (categoryTotals.isNotEmpty()) {
                appendLine()
                appendLine("Top spend categories (last 30 days):")
                categoryTotals.forEach { (catId, pair) ->
                    val (total, count) = pair
                    val name = categories[catId]?.name ?: "Uncategorised"
                    appendLine("- $name: ${formatRupees(total)} ($count txns)")
                }
            }

            if (merchantTotals.isNotEmpty()) {
                appendLine()
                appendLine("Top merchants (last 30 days):")
                merchantTotals.forEach { (merchant, pair) ->
                    val (total, count) = pair
                    appendLine("- $merchant: ${formatRupees(total)} ($count txns)")
                }
            }

            if (recent.isNotEmpty()) {
                appendLine()
                appendLine("Last ${recent.size} transactions:")
                recent.forEach { t ->
                    val sign = if (t.flowId == TransactionFlow.EXPENSE.id) "-" else "+"
                    val where = t.merchantRaw?.takeIf { it.isNotBlank() } ?: "(no merchant)"
                    appendLine("- ${formatDate(t.timestamp)} | $sign${formatRupees(t.amountMinor)} | $where")
                }
            }
        }
    }

    private fun formatRupees(minor: Long): String {
        val major = kotlin.math.abs(minor) / 100
        val cents = kotlin.math.abs(minor) % 100
        val formatter = java.text.NumberFormat.getIntegerInstance(Locale.US)
        return "Rs ${formatter.format(major)}.${"%02d".format(cents)}"
    }

    private fun formatRupeesSigned(minor: Long): String =
        (if (minor < 0) "-" else "+") + formatRupees(minor)

    private val dateFmt = SimpleDateFormat("d MMM", Locale.US)
    private fun formatDate(ms: Long): String = dateFmt.format(Date(ms))

    private val monthFmt = SimpleDateFormat("MMMM", Locale.US)
    private fun monthName(ms: Long): String = monthFmt.format(Date(ms))
}
