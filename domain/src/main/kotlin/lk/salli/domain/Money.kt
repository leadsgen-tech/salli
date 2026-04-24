package lk.salli.domain

/**
 * A monetary amount in a specific currency. Stored in minor units (e.g. cents) as a [Long] to
 * avoid floating-point errors on arithmetic. Currency is ISO 4217.
 *
 * Arithmetic only permitted between matching currencies; cross-currency operations must go
 * through an explicit conversion step (not provided in v1 — we don't convert automatically).
 */
data class Money(
    val minorUnits: Long,
    val currency: String,
) {
    operator fun plus(other: Money): Money {
        require(currency == other.currency) {
            "Cannot add $currency and ${other.currency} directly"
        }
        return Money(minorUnits + other.minorUnits, currency)
    }

    operator fun minus(other: Money): Money {
        require(currency == other.currency) {
            "Cannot subtract ${other.currency} from $currency directly"
        }
        return Money(minorUnits - other.minorUnits, currency)
    }

    operator fun unaryMinus(): Money = Money(-minorUnits, currency)

    operator fun times(scalar: Int): Money = Money(minorUnits * scalar, currency)
    operator fun times(scalar: Long): Money = Money(minorUnits * scalar, currency)

    operator fun compareTo(other: Money): Int {
        require(currency == other.currency) {
            "Cannot compare $currency to ${other.currency} directly"
        }
        return minorUnits.compareTo(other.minorUnits)
    }

    val isZero: Boolean get() = minorUnits == 0L
    val isPositive: Boolean get() = minorUnits > 0L
    val isNegative: Boolean get() = minorUnits < 0L

    companion object {
        fun zero(currency: String): Money = Money(0L, currency)

        /** Parses a human-readable amount like "1,234.56" into minor units (assumes 2 decimals). */
        fun ofMajor(majorWithDecimal: String, currency: String): Money {
            val cleaned = majorWithDecimal.replace(",", "").trim()
            val parts = cleaned.split(".")
            val whole = parts[0].toLong()
            val fraction = when {
                parts.size == 1 -> 0L
                parts[1].length == 1 -> parts[1].toLong() * 10
                parts[1].length == 2 -> parts[1].toLong()
                parts[1].length > 2 -> parts[1].substring(0, 2).toLong()
                else -> 0L
            }
            val minorUnits = if (whole < 0 || cleaned.startsWith("-")) {
                whole * 100 - fraction
            } else {
                whole * 100 + fraction
            }
            return Money(minorUnits, currency)
        }
    }
}
