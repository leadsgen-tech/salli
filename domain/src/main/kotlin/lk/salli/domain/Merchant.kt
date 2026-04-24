package lk.salli.domain

/**
 * A canonical merchant. Raw merchant strings from SMS (e.g. "KEELLS SUPER COLOMBO 04") map to a
 * single canonical [Merchant] via the alias table (in `:data`). Having a canonical entity means
 * we can attach a logo, a default category, and a "recurring" flag for subscription detection.
 */
data class Merchant(
    val id: Long? = null,
    val canonicalName: String,
    val categoryId: Long? = null,
    val subCategoryId: Long? = null,
    /** Relative path within `assets/merchants/` — e.g. "keells.jpg". */
    val logoAsset: String? = null,
    /** Set when the same merchant appears ≥3 times in ≥2 distinct months. */
    val isRecurring: Boolean = false,
)
