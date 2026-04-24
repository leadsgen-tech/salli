package lk.salli.domain

/** How the transaction entered the system. */
enum class TransactionMethod(val id: Int) {
    /** Entered manually via the UI. */
    MANUAL(0),

    /** Auto-parsed from an SMS broadcast in real time. */
    SMS(1),

    /** Brought in during the first-run historical import pass. */
    IMPORTED(2),

    /** System-generated (balance reconciliation, transfer pairing). */
    SYSTEM(3);

    companion object {
        fun fromId(id: Int): TransactionMethod = entries.first { it.id == id }
    }
}
