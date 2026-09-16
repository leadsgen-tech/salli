package lk.salli.data.transactions

import com.google.common.truth.Truth.assertThat
import lk.salli.data.db.entities.TransactionEntity
import lk.salli.domain.TransactionFlow
import org.junit.Test

class TransactionSpendingTest {

    private fun tx(
        id: Long,
        currency: String,
        flow: TransactionFlow = TransactionFlow.EXPENSE,
        declined: Boolean = false,
        transferGroupId: Long? = null,
        accountId: Long = 1,
    ) = TransactionEntity(
        id = id,
        accountId = accountId,
        amountMinor = 100,
        amountCurrency = currency,
        timestamp = id,
        flowId = flow.id,
        methodId = 1,
        typeId = 0,
        isDeclined = declined,
        transferGroupId = transferGroupId,
        createdAt = id,
        updatedAt = id,
    )

    @Test
    fun `dominant currency uses eligible expenses and totals share the same rules`() {
        val rows = listOf(
            tx(1, "LKR"),
            tx(2, "LKR"),
            tx(3, "USD"),
            tx(4, "USD", declined = true),
            tx(5, "USD", transferGroupId = 9),
            tx(6, "USD", accountId = 2),
        )

        assertThat(TransactionSpending.dominantCurrency(rows, hiddenAccountIds = setOf(2))).isEqualTo("LKR")
        assertThat(TransactionSpending.totalMinor(rows, "LKR", hiddenAccountIds = setOf(2))).isEqualTo(200)
    }

    @Test
    fun `income currency is the fallback when there are no eligible expenses`() {
        val rows = listOf(
            tx(1, "USD", flow = TransactionFlow.INCOME),
            tx(2, "USD", flow = TransactionFlow.INCOME),
            tx(3, "LKR", flow = TransactionFlow.INCOME),
            tx(4, "LKR", declined = true),
        )

        assertThat(TransactionSpending.dominantCurrency(rows)).isEqualTo("USD")
        assertThat(TransactionSpending.totalMinor(rows, "USD")).isEqualTo(0)
    }
}
