package lk.salli.domain

/**
 * Core domain model. The Room entity (in `:data`) maps to and from this — domain layer stays
 * pure Kotlin so the parser and business logic can be tested without Android.
 */
data class Transaction(
    val id: Long? = null,
    val accountId: Long,
    val amount: Money,
    val fee: Money? = null,
    val flow: TransactionFlow,
    val type: TransactionType,
    val method: TransactionMethod,
    val timestamp: Long, // epoch millis
    val merchantRaw: String? = null,
    val merchantId: Long? = null,
    val categoryId: Long? = null,
    val subCategoryId: Long? = null,
    val balance: Money? = null,
    val transferGroupId: Long? = null,
    val senderAddress: String? = null,
    val rawBody: String? = null,
    val note: String? = null,
    val isHidden: Boolean = false,
    val isDeclined: Boolean = false,
    val createdAt: Long = timestamp,
    val updatedAt: Long = timestamp,
)
