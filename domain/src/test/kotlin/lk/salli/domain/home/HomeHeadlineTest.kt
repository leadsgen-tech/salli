package lk.salli.domain.home

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class HomeHeadlineTest {

    private fun bill(
        biller: String = "SLT",
        amountMinor: Long = 1_195_300,
        currency: String = "LKR",
        dueEpochDay: Long = 20_700,
    ) = HeadlineBill(biller, amountMinor, currency, dueEpochDay)

    private fun budget(
        name: String = "Monthly essentials",
        overByMinor: Long = 250_000,
        currency: String = "LKR",
    ) = HeadlineBudget(name, overByMinor, currency)

    /** Everything is shouting at once; only the top tier may answer. */
    private val everything = HomeHeadlineInput(
        overdueBills = listOf(bill()),
        billsDueToday = listOf(bill(biller = "CEB")),
        budgetsOver = listOf(budget()),
        unknownSmsCount = 4,
        newSinceLastOpenCount = 3,
        safeToSpendTodayMinor = 412_000,
        currency = "LKR",
    )

    // ---------------------------------------------------------------- nothing to say

    @Test
    fun `no inputs produces no headline`() {
        assertThat(HomeHeadline.of(HomeHeadlineInput())).isNull()
    }

    @Test
    fun `zero and negative counts never win their tier`() {
        val result = HomeHeadline.of(
            HomeHeadlineInput(unknownSmsCount = 0, newSinceLastOpenCount = -2),
        )
        assertThat(result).isNull()
    }

    // ---------------------------------------------------------------- each branch

    @Test
    fun `overdue bill wins over everything`() {
        val result = HomeHeadline.of(everything)

        assertThat(result).isEqualTo(
            Headline(
                HeadlineKind.BILL_OVERDUE,
                HeadlineParams(count = 1, amountMinor = 1_195_300, currency = "LKR", label = "SLT"),
            ),
        )
    }

    @Test
    fun `bill due today wins once nothing is overdue`() {
        val result = HomeHeadline.of(everything.copy(overdueBills = emptyList()))

        assertThat(result).isEqualTo(
            Headline(
                HeadlineKind.BILL_DUE_TODAY,
                HeadlineParams(count = 1, amountMinor = 1_195_300, currency = "LKR", label = "CEB"),
            ),
        )
    }

    @Test
    fun `budget over wins once no bill is due`() {
        val result = HomeHeadline.of(
            everything.copy(overdueBills = emptyList(), billsDueToday = emptyList()),
        )

        assertThat(result).isEqualTo(
            Headline(
                HeadlineKind.BUDGET_OVER,
                HeadlineParams(
                    count = 1,
                    amountMinor = 250_000,
                    currency = "LKR",
                    label = "Monthly essentials",
                ),
            ),
        )
    }

    @Test
    fun `unknown sms wins once no money is late`() {
        val result = HomeHeadline.of(
            everything.copy(
                overdueBills = emptyList(),
                billsDueToday = emptyList(),
                budgetsOver = emptyList(),
            ),
        )

        assertThat(result).isEqualTo(Headline(HeadlineKind.UNKNOWN_SMS, HeadlineParams(count = 4)))
    }

    @Test
    fun `new since last open wins once the review queue is empty`() {
        val result = HomeHeadline.of(
            everything.copy(
                overdueBills = emptyList(),
                billsDueToday = emptyList(),
                budgetsOver = emptyList(),
                unknownSmsCount = 0,
            ),
        )

        assertThat(result)
            .isEqualTo(Headline(HeadlineKind.NEW_SINCE_LAST_OPEN, HeadlineParams(count = 3)))
    }

    @Test
    fun `safe to spend is the quiet default`() {
        val result = HomeHeadline.of(
            HomeHeadlineInput(safeToSpendTodayMinor = 412_000, currency = "LKR"),
        )

        assertThat(result).isEqualTo(
            Headline(
                HeadlineKind.SAFE_TO_SPEND,
                HeadlineParams(amountMinor = 412_000, currency = "LKR"),
            ),
        )
    }

    @Test
    fun `no budget means no safe-to-spend line`() {
        assertThat(HomeHeadline.of(HomeHeadlineInput(safeToSpendTodayMinor = null))).isNull()
    }

    @Test
    fun `nothing left to spend says nothing rather than Rs 0`() {
        assertThat(HomeHeadline.of(HomeHeadlineInput(safeToSpendTodayMinor = 0L))).isNull()
        assertThat(HomeHeadline.of(HomeHeadlineInput(safeToSpendTodayMinor = -5_000L))).isNull()
    }

    // ---------------------------------------------------------------- full ordering

    @Test
    fun `peeling the tiers away walks the priority list exactly once`() {
        var input = everything
        val walked = mutableListOf<HeadlineKind>()

        while (true) {
            val headline = HomeHeadline.of(input) ?: break
            walked += headline.kind
            input = when (headline.kind) {
                HeadlineKind.BILL_OVERDUE -> input.copy(overdueBills = emptyList())
                HeadlineKind.BILL_DUE_TODAY -> input.copy(billsDueToday = emptyList())
                HeadlineKind.BUDGET_OVER -> input.copy(budgetsOver = emptyList())
                HeadlineKind.UNKNOWN_SMS -> input.copy(unknownSmsCount = 0)
                HeadlineKind.NEW_SINCE_LAST_OPEN -> input.copy(newSinceLastOpenCount = 0)
                HeadlineKind.SAFE_TO_SPEND -> input.copy(safeToSpendTodayMinor = null)
            }
        }

        assertThat(walked).containsExactly(
            HeadlineKind.BILL_OVERDUE,
            HeadlineKind.BILL_DUE_TODAY,
            HeadlineKind.BUDGET_OVER,
            HeadlineKind.UNKNOWN_SMS,
            HeadlineKind.NEW_SINCE_LAST_OPEN,
            HeadlineKind.SAFE_TO_SPEND,
        ).inOrder()
        // Every kind is reachable — a new kind without a tier would fail this.
        assertThat(walked).hasSize(HeadlineKind.entries.size)
    }

    // ---------------------------------------------------------------- tie-breaks

    @Test
    fun `the most overdue bill is the one named`() {
        val result = HomeHeadline.of(
            HomeHeadlineInput(
                overdueBills = listOf(
                    bill(biller = "CEB", dueEpochDay = 20_700, amountMinor = 900_000),
                    bill(biller = "SLT", dueEpochDay = 20_695, amountMinor = 100),
                    bill(biller = "Water", dueEpochDay = 20_699, amountMinor = 500_000),
                ),
            ),
        )

        assertThat(result?.params?.label).isEqualTo("SLT")
        assertThat(result?.params?.count).isEqualTo(3)
    }

    @Test
    fun `bills overdue the same day break the tie on amount then name`() {
        val sameDay = listOf(
            bill(biller = "CEB", dueEpochDay = 20_690, amountMinor = 400_000),
            bill(biller = "Aqua", dueEpochDay = 20_690, amountMinor = 400_000),
            bill(biller = "SLT", dueEpochDay = 20_690, amountMinor = 700_000),
        )

        assertThat(HomeHeadline.of(HomeHeadlineInput(overdueBills = sameDay))?.params?.label)
            .isEqualTo("SLT")
        assertThat(
            HomeHeadline.of(HomeHeadlineInput(overdueBills = sameDay.filter { it.amountMinor == 400_000L }))
                ?.params?.label,
        ).isEqualTo("Aqua")
    }

    @Test
    fun `the largest bill due today is the one named`() {
        val result = HomeHeadline.of(
            HomeHeadlineInput(
                billsDueToday = listOf(
                    bill(biller = "CEB", amountMinor = 400_000),
                    bill(biller = "SLT", amountMinor = 1_195_300),
                ),
            ),
        )

        assertThat(result?.params?.label).isEqualTo("SLT")
        assertThat(result?.params?.amountMinor).isEqualTo(1_195_300)
        assertThat(result?.params?.count).isEqualTo(2)
    }

    @Test
    fun `the budget furthest over is the one named`() {
        val result = HomeHeadline.of(
            HomeHeadlineInput(
                budgetsOver = listOf(
                    budget(name = "Eating out", overByMinor = 120_000),
                    budget(name = "Monthly essentials", overByMinor = 250_000),
                ),
            ),
        )

        assertThat(result?.params?.label).isEqualTo("Monthly essentials")
        assertThat(result?.params?.amountMinor).isEqualTo(250_000)
        assertThat(result?.params?.count).isEqualTo(2)
    }

    @Test
    fun `equal overspends break the tie by name so the line never flickers`() {
        val budgets = listOf(budget(name = "Zed", overByMinor = 10), budget(name = "Alpha", overByMinor = 10))

        assertThat(HomeHeadline.of(HomeHeadlineInput(budgetsOver = budgets))?.params?.label)
            .isEqualTo("Alpha")
        assertThat(HomeHeadline.of(HomeHeadlineInput(budgetsOver = budgets.reversed()))?.params?.label)
            .isEqualTo("Alpha")
    }

    @Test
    fun `currency travels with the amount it belongs to`() {
        val result = HomeHeadline.of(
            HomeHeadlineInput(
                overdueBills = listOf(bill(currency = "USD", amountMinor = 4_999)),
                currency = "LKR",
            ),
        )

        assertThat(result?.params?.currency).isEqualTo("USD")
    }
}
