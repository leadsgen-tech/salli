package lk.salli.data.transactions

import com.google.common.truth.Truth.assertThat
import lk.salli.data.db.entities.TransactionEntity
import lk.salli.domain.TransactionFlow
import lk.salli.domain.TransactionType
import org.junit.Test

class TransactionSpendingTest {

    private fun tx(
        id: Long,
        currency: String,
        flow: TransactionFlow = TransactionFlow.EXPENSE,
        declined: Boolean = false,
        transferGroupId: Long? = null,
        accountId: Long = 1,
        type: TransactionType = TransactionType.POS,
        amount: Long = 100,
        sender: String? = null,
        body: String? = null,
    ) = TransactionEntity(
        id = id,
        accountId = accountId,
        amountMinor = amount,
        amountCurrency = currency,
        timestamp = id,
        flowId = flow.id,
        methodId = 1,
        typeId = type.id,
        isDeclined = declined,
        transferGroupId = transferGroupId,
        senderAddress = sender,
        rawBody = body,
        createdAt = id,
        updatedAt = id,
    )

    @Test
    fun `a transfer to someone is moved, not spent`() {
        val rows = listOf(
            tx(1, "LKR", type = TransactionType.POS, amount = 4_280),
            tx(2, "LKR", type = TransactionType.CEFT, amount = 175_000),
            tx(3, "LKR", type = TransactionType.ONLINE_TRANSFER, amount = 25_000),
            tx(4, "LKR", type = TransactionType.SLIPS, amount = 1_000, declined = true),
        )

        assertThat(TransactionSpending.totalMinor(rows, "LKR")).isEqualTo(4_280)
        assertThat(TransactionSpending.movedMinor(rows, "LKR")).isEqualTo(200_000)
        assertThat(TransactionSpending.counts(rows[1])).isFalse()
        assertThat(TransactionSpending.movesMoney(rows[1])).isTrue()
        assertThat(TransactionSpending.movesMoney(rows[3])).isFalse()
    }

    @Test
    fun `legacy People's Bank mobile rows resolve by body before the split`() {
        val transfer = tx(1, "LKR", type = TransactionType.MOBILE_PAYMENT, amount = 2_400, sender = "PeoplesBank", body = "Fund transfer Successful. LKR 2400.00 to ...")
        val bill = tx(2, "LKR", type = TransactionType.MOBILE_PAYMENT, amount = 385, sender = "PeoplesBank", body = "Mobile Payment Successful, LKR 385.00 to Dialog")
        val wallet = tx(3, "LKR", type = TransactionType.MOBILE_PAYMENT, amount = 80, sender = "ComBank_Q+", body = "Fund transfer")

        assertThat(TransactionSpending.movesMoney(transfer)).isTrue()
        assertThat(TransactionSpending.counts(bill)).isTrue()
        assertThat(TransactionSpending.counts(wallet)).isTrue()
    }

    @Test
    fun `a transfer between own accounts is moved once, by what arrived`() {
        val rows = listOf(
            tx(1, "LKR", flow = TransactionFlow.TRANSFER, type = TransactionType.CEFT, amount = 25_025, transferGroupId = 7),
            tx(2, "LKR", flow = TransactionFlow.TRANSFER, type = TransactionType.CEFT, amount = 25_000, transferGroupId = 7, accountId = 2),
            tx(3, "LKR", flow = TransactionFlow.TRANSFER, type = TransactionType.CEFT, amount = 10_000, transferGroupId = 8),
        )

        assertThat(TransactionSpending.movedMinor(rows, "LKR")).isEqualTo(35_000)
        assertThat(TransactionSpending.totalMinor(rows, "LKR")).isEqualTo(0)
        assertThat(TransactionSpending.movedMinor(rows, "LKR", hiddenAccountIds = setOf(2))).isEqualTo(35_025)
    }

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
