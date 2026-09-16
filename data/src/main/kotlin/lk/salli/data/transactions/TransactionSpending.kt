package lk.salli.data.transactions

import lk.salli.data.db.entities.TransactionEntity
import lk.salli.domain.Currency
import lk.salli.domain.TransactionFlow

/** Shared rules for every spending total shown by Home, planning and the widget. */
object TransactionSpending {

    fun counts(transaction: TransactionEntity, hiddenAccountIds: Set<Long> = emptySet()): Boolean =
        !transaction.isDeclined &&
            transaction.transferGroupId == null &&
            transaction.accountId !in hiddenAccountIds &&
            transaction.flowId == TransactionFlow.EXPENSE.id

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

    fun totalMinor(
        transactions: List<TransactionEntity>,
        currency: String,
        hiddenAccountIds: Set<Long> = emptySet(),
    ): Long = transactions
        .asSequence()
        .filter { counts(it, hiddenAccountIds) && it.amountCurrency == currency }
        .sumOf { it.amountMinor }
}
