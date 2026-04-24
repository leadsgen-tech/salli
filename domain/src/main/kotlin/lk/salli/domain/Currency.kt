package lk.salli.domain

/**
 * ISO 4217 currency codes we actually see in Sri Lankan bank SMS. Not exhaustive — just the
 * ones observed. Everything else flows through as raw strings without special handling.
 */
object Currency {
    const val LKR = "LKR"
    const val USD = "USD"
    const val EUR = "EUR"
    const val GBP = "GBP"
    const val INR = "INR"

    /** Currencies where "Rs" or "Rs." appears in SMS but refers to LKR specifically. */
    val RUPEE_ALIASES = setOf("Rs", "Rs.", "LKR")

    fun normalize(raw: String): String = when (raw.trim().uppercase()) {
        in setOf("RS", "RS.", "LKR") -> LKR
        "USD", "US$", "$" -> USD
        "EUR", "€" -> EUR
        "GBP", "£" -> GBP
        "INR", "RS.IN", "₹" -> INR
        else -> raw.trim().uppercase()
    }
}
