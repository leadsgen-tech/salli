package lk.salli.data.summary

import com.google.common.truth.Truth.assertThat
import lk.salli.data.db.entities.TransactionEntity
import org.junit.Test

class SummaryBuilderTest {

    private fun tx(
        amount: Long,
        flow: Int = 0,
        account: Long = 1,
        category: Long? = null,
        declined: Boolean = false,
        group: Long? = null,
        currency: String = "LKR",
    ) = TransactionEntity(
        accountId = account, amountMinor = amount, amountCurrency = currency, timestamp = 1L,
        flowId = flow, methodId = 1, typeId = 0, categoryId = category, isDeclined = declined,
        transferGroupId = group, createdAt = 1L, updatedAt = 1L,
    )

    private val names = mapOf(1L to "Groceries", 2L to "Transport")

    @Test
    fun `sums spend, names the top category and reports income`() {
        val s = SummaryBuilder.build(
            "Today",
            listOf(tx(210_000, category = 1), tx(150_000, category = 2), tx(65_000, category = 1), tx(500_000, flow = 1)),
            names, emptySet(),
        )!!
        assertThat(s.title).isEqualTo("Today")
        assertThat(s.text).isEqualTo("Rs 4,250 spent in 3 transactions · Top: Groceries Rs 2,750 · In: Rs 5,000")
    }

    @Test
    fun `declined rows, transfer legs and hidden accounts never count`() {
        val s = SummaryBuilder.build(
            "Today",
            listOf(
                tx(100_000, category = 1),
                tx(900_000, declined = true),
                tx(800_000, group = 7),
                tx(700_000, account = 9),
            ),
            names, hiddenAccountIds = setOf(9L),
        )!!
        assertThat(s.text).isEqualTo("Rs 1,000 spent in 1 transaction · Top: Groceries Rs 1,000")
    }

    @Test
    fun `nothing to say yields null and cents show when present`() {
        assertThat(SummaryBuilder.build("Today", emptyList(), names, emptySet())).isNull()
        assertThat(SummaryBuilder.build("Today", listOf(tx(900_000, declined = true)), names, emptySet())).isNull()
        val s = SummaryBuilder.build("Today", listOf(tx(123_45)), names, emptySet())!!
        assertThat(s.text).startsWith("Rs 123.45 spent in 1 transaction · Top: Uncategorised Rs 123.45")
    }

    @Test
    fun `dominant currency wins when a foreign card purchase sneaks in`() {
        val s = SummaryBuilder.build(
            "Today",
            listOf(tx(100_000), tx(200_000), tx(999, currency = "USD")),
            names, emptySet(),
        )!!
        assertThat(s.text).startsWith("Rs 3,000 spent in 2 transactions")
    }
}
