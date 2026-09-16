package lk.salli.domain.money

import lk.salli.domain.Money
import java.text.NumberFormat
import java.util.Locale

/**
 * The one money formatter.
 *
 * Before this existed there were five near-identical private copies scattered across Home,
 * Insights, Budgets, the timeline row mapper and the notification builder, each with its own
 * opinion about whether "Rs" gets a space and whether a minus sign is a hyphen or a real
 * U+2212. They drifted. Now there is one, it lives in `:domain` so every module (including the
 * pure-Kotlin ones and, later, a KMP build) can reach it, and the presentation layer only
 * decides *which* form to ask for.
 *
 * Conventions, fixed here and nowhere else:
 *  - LKR renders as `Rs 1,234.56`; every other currency renders as `USD 15.99` (ISO code,
 *    space, amount) because we do not ship a symbol table we can defend.
 *  - Grouping is always 3-digit Latin, never locale-shifted — a Sri Lankan user reading an
 *    English app should not get lakh grouping from a device locale we didn't choose.
 *  - The signed form uses U+2212 MINUS SIGN, not a hyphen, so amounts line up in tabular
 *    figures and don't look like a stray dash.
 */
object MoneyFormat {

    private const val MINUS = "−"

    /** 3-digit grouping, exactly two decimals, no currency. Locale-independent on purpose. */
    private val amountFormat: NumberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
        isGroupingUsed = true
    }

    /** 3-digit grouping, no decimals — used by the compact form. */
    private val wholeFormat: NumberFormat = NumberFormat.getIntegerInstance(Locale.US).apply {
        isGroupingUsed = true
    }

    /** `"Rs"` for LKR, otherwise the ISO code itself. */
    fun symbol(currency: String): String = if (currency == "LKR") "Rs" else currency

    /**
     * `"Rs 1,234.56"`. With [signed], a negative amount gets a leading minus and a positive one
     * a leading plus; zero is never signed.
     */
    fun format(money: Money, signed: Boolean = false): String =
        formatMinor(money.minorUnits, money.currency, signed)

    /**
     * Minor-unit variant for the call sites that never built a [Money]. Deliberately a
     * different *name* rather than an overload: `MoneyFormat::format` is used as a callable
     * reference in a couple of places, and an overload set makes that reference ambiguous.
     */
    fun formatMinor(minorUnits: Long, currency: String, signed: Boolean = false): String {
        val body = "${symbol(currency)} ${bareMinor(minorUnits)}"
        return when {
            !signed || minorUnits == 0L -> body
            minorUnits < 0L -> "$MINUS$body"
            else -> "+$body"
        }
    }

    /**
     * `"−Rs 4,280.00"` for a negative amount, `"Rs 16,400.00"` for a positive one.
     *
     * A third form because balances and nets need exactly this: a minus when money is gone,
     * and *no* plus when it isn't. A leading "+" on a balance reads as a change rather than a
     * level, which is why [format]'s `signed = true` (which adds one) is wrong here.
     */
    fun formatWithMinus(money: Money): String =
        if (money.minorUnits < 0L) MINUS + format(money) else format(money)

    /** `"1,234.56"` — magnitude only, no currency, no sign. For hero numbers that typeset the
     *  currency separately. */
    fun bare(money: Money): String = bareMinor(money.minorUnits)

    /** @see bare */
    fun bareMinor(minorUnits: Long): String {
        val abs = if (minorUnits == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(minorUnits)
        return amountFormat.format(abs / 100.0)
    }

    /**
     * `"Rs 1.2k"` / `"Rs 3.4M"` / `"Rs 840"` — decimals dropped, magnitude suffixed. For axis
     * labels and dense chart callouts where two decimal places are noise.
     */
    fun short(money: Money): String = shortMinor(money.minorUnits, money.currency)

    /** @see short */
    fun shortMinor(minorUnits: Long, currency: String): String {
        val abs = if (minorUnits == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(minorUnits)
        val major = abs / 100
        val prefix = "${symbol(currency)} "
        return when {
            major >= 1_000_000 -> prefix + trimTrailingZero(major / 1_000_000.0) + "M"
            major >= 1_000 -> prefix + trimTrailingZero(major / 1_000.0) + "k"
            else -> prefix + wholeFormat.format(major)
        }
    }

    /** `"1.2"` not `"1.20"`, and `"3"` not `"3.0"`. */
    private fun trimTrailingZero(value: Double): String {
        val oneDp = String.format(Locale.US, "%.1f", value)
        return oneDp.removeSuffix(".0")
    }
}
