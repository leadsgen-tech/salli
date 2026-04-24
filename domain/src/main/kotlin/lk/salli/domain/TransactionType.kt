package lk.salli.domain

/**
 * Nature of the transaction — this is the fine-grained type the parser extracts from the SMS
 * body (POS vs ATM vs CEFT transfer, etc). Distinct from [TransactionFlow] which is only the
 * direction. Used for icon/label selection and categorisation heuristics.
 */
enum class TransactionType(val id: Int) {
    /** Point-of-sale card purchase, physical or online. */
    POS(0),

    /** ATM cash withdrawal or deposit. */
    ATM(1),

    /** Cash deposit machine. */
    CDM(2),

    /** Cheque deposit or clearance. */
    CHEQUE(3),

    /** Online transfer via internet banking. */
    ONLINE_TRANSFER(4),

    /** CEFT (Common Electronic Fund Transfer) — interbank same-day. */
    CEFT(5),

    /** SLIPS / inter-bank transfer. */
    SLIPS(6),

    /** Mobile wallet payment (People's Pay, Genie, eZ Cash, Frimi). */
    MOBILE_PAYMENT(7),

    /** Bank / card fee or charge. */
    FEE(8),

    /** Declined attempt — no money actually moved. */
    DECLINED(9),

    /** Balance reconciliation (system-generated). */
    BALANCE_CORRECTION(10),

    /** Anything else. */
    OTHER(99);

    companion object {
        fun fromId(id: Int): TransactionType = entries.first { it.id == id }
    }
}
