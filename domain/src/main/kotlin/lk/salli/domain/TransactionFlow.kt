package lk.salli.domain

/** Direction of a transaction from the user's perspective. */
enum class TransactionFlow(val id: Int) {
    /** Money leaving the user. */
    EXPENSE(0),

    /** Money arriving to the user. */
    INCOME(1),

    /** Movement between the user's own accounts — net-zero impact on personal balance. */
    TRANSFER(2),

    /**
     * The SMS implies a transaction but direction is ambiguous (e.g. a parser that can tell
     * amount but not flow). Resolved later by the merge/transfer-detector stages, or surfaced
     * to the user.
     */
    AMBIGUOUS(9);

    companion object {
        fun fromId(id: Int): TransactionFlow = entries.first { it.id == id }
    }
}
