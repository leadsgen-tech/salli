package lk.salli.parser.utility

/** What a bill SMS is telling us. */
enum class BillKind(val id: Int) {
    /** A new bill for a period, with the total payable and (usually) a due date. */
    ISSUED(0),

    /** "Please settle before …" — same bill, nudging. Carries the current outstanding. */
    REMINDER(1),

    /** Past the due date; late charges may apply. */
    OVERDUE(2),

    /** The biller acknowledging a payment against the account. */
    PAYMENT_RECEIVED(3);

    companion object {
        fun fromId(id: Int): BillKind = entries.first { it.id == id }
    }
}

/**
 * A utility bill extracted from an SMS. [accountRef] is the biller's identifier for the
 * connection (phone number, electricity account, water account), normalised so an issued bill
 * and its later payment receipt match even when the biller formats the number differently.
 */
data class ParsedBill(
    val biller: String,
    val accountRef: String,
    /** Amount currently payable. For [BillKind.PAYMENT_RECEIVED] this is 0. */
    val amountDueMinor: Long,
    val currency: String,
    val dueDateMillis: Long?,
    val periodLabel: String?,
    val kind: BillKind,
    val paidAmountMinor: Long?,
    val rawBody: String,
)

/** One fill-up confirmation from the National Fuel Pass (sender 1919). Volumes in millilitres. */
data class ParsedFuelTransaction(
    val vehicle: String,
    val litresMilli: Long,
    val weeklyBalanceMilli: Long,
    val stationCode: String?,
    val timestampMillis: Long,
    /** Midnight (Colombo) of the day the weekly quota resets, when the SMS states it. */
    val resetsOnMillis: Long?,
    val rawBody: String,
)
