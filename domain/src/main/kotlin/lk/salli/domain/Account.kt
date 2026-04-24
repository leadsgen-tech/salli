package lk.salli.domain

/**
 * A bank account or card the user owns. Auto-created when a new account-number-suffix is seen
 * from a known bank sender. User can rename [displayName], mark [purpose], set the [accountType].
 */
data class Account(
    val id: Long? = null,
    /** Exact SMS sender address — e.g. "BOC", "PeoplesBank", "COMBANK". */
    val senderAddress: String,
    /** Masked account identifier from SMS — e.g. "XXXXXXXXXX870", "280-2001****68", "#4273". */
    val accountNumberSuffix: String,
    /** User-editable label — defaults to something like "BOC (870)". */
    val displayName: String,
    val currency: String = Currency.LKR,
    val accountType: AccountType = AccountType.UNKNOWN,
    /** Free-form user tag — e.g. "burner", "salary", "joint". */
    val purpose: String? = null,
    /** Most recent balance we learned from SMS, if any. */
    val balance: Money? = null,
    val isArchived: Boolean = false,
)

enum class AccountType(val id: Int) {
    CURRENT(0),
    SAVINGS(1),
    CREDIT_CARD(2),
    DEBIT_CARD(3),
    WALLET(4),
    UNKNOWN(99);

    companion object {
        fun fromId(id: Int): AccountType = entries.first { it.id == id }
    }
}
