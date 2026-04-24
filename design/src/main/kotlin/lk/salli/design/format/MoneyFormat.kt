package lk.salli.design.format

import lk.salli.domain.Money
import java.text.NumberFormat
import java.util.Locale

/**
 * Human-facing formatting for [Money]. Kept here (rather than in :domain) because formatting is
 * a presentation concern — different surfaces might want different forms.
 */
object MoneyFormat {

    private val lkrFormatter: NumberFormat = NumberFormat.getNumberInstance(Locale("en", "LK")).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
        isGroupingUsed = true
    }

    private val usdFormatter: NumberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
        isGroupingUsed = true
    }

    /**
     * `"Rs 1,234.56"` for LKR, `"USD 15.99"` for others. Signed when [signed] is true; the
     * caller is responsible for passing the already-signed amount (e.g. negative for expenses).
     */
    fun format(money: Money, signed: Boolean = false): String {
        val absMinor = kotlin.math.abs(money.minorUnits)
        val major = absMinor / 100.0
        val (formatter, symbol) = when (money.currency) {
            "LKR" -> lkrFormatter to "Rs"
            "USD" -> usdFormatter to "USD"
            else -> usdFormatter to money.currency
        }
        val body = "$symbol ${formatter.format(major)}"
        return when {
            signed && money.minorUnits < 0 -> "−$body"
            signed && money.minorUnits > 0 -> "+$body"
            else -> body
        }
    }

    /** Compact form without currency symbol, for dense layouts. */
    fun formatBare(money: Money): String {
        val absMinor = kotlin.math.abs(money.minorUnits)
        val major = absMinor / 100.0
        val formatter = if (money.currency == "LKR") lkrFormatter else usdFormatter
        return formatter.format(major)
    }
}
