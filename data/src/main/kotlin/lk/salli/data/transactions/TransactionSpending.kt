package lk.salli.data.transactions

import lk.salli.data.db.entities.TransactionEntity
import lk.salli.domain.Currency
import lk.salli.domain.TransactionFlow
import lk.salli.domain.TransactionType

/**
 * Shared rules for every spending total shown by Home, Plan, Insights, the summaries and the
 * widget.
 *
 * Money leaves an account in two very different ways. A card swipe, a bill, an ATM withdrawal
 * is **spent**: it is gone, and it is what budgets and safe-to-spend are about. A transfer to
 * another person or another bank is **moved**: it left this account but bought nothing, and
 * counting it as spending makes "spent this period" read as a million rupees for someone whose
 * money mostly passes through. The two are reported side by side and never added together.
 * Transfers between the user's own accounts (paired legs) are moved as well, counted once.
 */
object TransactionSpending {

    /** Transfer channels: the money went somewhere, it was not spent. */
    private val MOVING_TYPES = setOf(
        TransactionType.ONLINE_TRANSFER,
        TransactionType.CEFT,
        TransactionType.SLIPS,
    )

    /** True for money that was spent: a real, unpaired expense that is not a transfer. */
    fun counts(transaction: TransactionEntity, hiddenAccountIds: Set<Long> = emptySet()): Boolean =
        isRealExpense(transaction, hiddenAccountIds) && canonicalType(transaction) !in MOVING_TYPES

    /** True for money that was moved to someone else: a real, unpaired expense that is a transfer. */
    fun movesMoney(transaction: TransactionEntity, hiddenAccountIds: Set<Long> = emptySet()): Boolean =
        isRealExpense(transaction, hiddenAccountIds) && canonicalType(transaction) in MOVING_TYPES

    private fun isRealExpense(transaction: TransactionEntity, hiddenAccountIds: Set<Long>): Boolean =
        !transaction.isDeclined &&
            transaction.transferGroupId == null &&
            transaction.accountId !in hiddenAccountIds &&
            transaction.flowId == TransactionFlow.EXPENSE.id

    /**
     * The type as the user understands it. Older People's Bank rows stored transfers and bill
     * payments under MOBILE_PAYMENT; the body tells them apart, so existing installs read
     * consistently without a migration.
     */
    fun canonicalType(transaction: TransactionEntity): TransactionType =
        canonicalType(TransactionType.fromId(transaction.typeId), transaction.senderAddress, transaction.rawBody)

    fun canonicalType(type: TransactionType, senderAddress: String?, rawBody: String?): TransactionType = when {
        type != TransactionType.MOBILE_PAYMENT -> type
        !senderAddress.orEmpty().trim().equals("PeoplesBank", ignoreCase = true) -> type
        rawBody.orEmpty().contains("Mobile Payment Successful", ignoreCase = true) -> TransactionType.BILL_PAYMENT
        rawBody.orEmpty().contains("LPAY Tfr", ignoreCase = true) ||
            rawBody.orEmpty().contains("PeoPAY", ignoreCase = true) ||
            rawBody.orEmpty().contains("Just Pay", ignoreCase = true) ||
            rawBody.orEmpty().contains("Fund transfer", ignoreCase = true) -> TransactionType.ONLINE_TRANSFER
        else -> type
    }

    fun dominantCurrency(
        transactions: List<TransactionEntity>,
        hiddenAccountIds: Set<Long> = emptySet(),
    ): String {
        val visibleMovements = transactions.asSequence().filter {
            !it.isDeclined && it.transferGroupId == null && it.accountId !in hiddenAccountIds
        }
        val expenses = visibleMovements.filter { it.flowId == TransactionFlow.EXPENSE.id }.toList()
        val candidates = expenses.ifEmpty {
            transactions.filter {
                !it.isDeclined && it.transferGroupId == null && it.accountId !in hiddenAccountIds &&
                    it.flowId == TransactionFlow.INCOME.id
            }
        }
        return candidates
            .groupingBy { it.amountCurrency }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: Currency.LKR
    }

    /** Spent, in [currency]. */
    fun totalMinor(
        transactions: List<TransactionEntity>,
        currency: String,
        hiddenAccountIds: Set<Long> = emptySet(),
    ): Long = transactions
        .asSequence()
        .filter { counts(it, hiddenAccountIds) && it.amountCurrency == currency }
        .sumOf { it.amountMinor }

    /**
     * Moved, in [currency]: transfers to others plus transfers between the user's own accounts.
     * A paired transfer is counted once, by what arrived (the smaller leg; the difference is the
     * sending bank's fee). A lone leg whose partner is outside [transactions] counts as itself.
     */
    fun movedMinor(
        transactions: List<TransactionEntity>,
        currency: String,
        hiddenAccountIds: Set<Long> = emptySet(),
    ): Long {
        val toOthers = transactions.asSequence()
            .filter { movesMoney(it, hiddenAccountIds) && it.amountCurrency == currency }
            .sumOf { it.amountMinor }
        val ownLegs = transactions.filter {
            it.transferGroupId != null && !it.isDeclined &&
                it.accountId !in hiddenAccountIds && it.amountCurrency == currency
        }
        val betweenOwn = ownLegs.groupBy { it.transferGroupId!! }.values.sumOf { legs -> legs.minOf { it.amountMinor } }
        return toOthers + betweenOwn
    }
}
