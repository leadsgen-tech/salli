package lk.salli.domain.split

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SplitMathTest {

    private fun p(id: Long, value: Long = 0L) = SplitParticipant(id, value)
    private fun ok(result: SplitResult): Map<Long, Long> = (result as SplitResult.Ok).shares

    @Test
    fun `rs 1000 split three ways gives the extra cent to the first person`() {
        val shares = ok(SplitMath.shares(100_000, listOf(p(1), p(2), p(3)), SplitMethod.EQUAL))
        assertThat(shares.values.toList()).containsExactly(33_334L, 33_333L, 33_333L).inOrder()
    }

    @Test
    fun `equal and weighted shares always reconcile to the total`() {
        for (total in listOf(1L, 2L, 99L, 100L, 101L, 12_345L, 999_999L, 10_000_000_001L)) {
            for (n in 1..7) {
                val people = (1..n).map { p(it.toLong(), value = (it * 37L) % 11 + 1) }
                assertThat(ok(SplitMath.shares(total, people, SplitMethod.EQUAL)).values.sum()).isEqualTo(total)
                assertThat(ok(SplitMath.shares(total, people, SplitMethod.WEIGHTS)).values.sum()).isEqualTo(total)
            }
        }
    }

    @Test
    fun `weights follow the ratios and a zero weight pays nothing`() {
        val shares = ok(SplitMath.shares(10_001, listOf(p(1, 50), p(2, 25), p(3, 25), p(4, 0)), SplitMethod.WEIGHTS))
        assertThat(shares).containsExactly(1L, 5_001L, 2L, 2_500L, 3L, 2_500L, 4L, 0L)
    }

    @Test
    fun `exact amounts must add up to the total`() {
        assertThat(ok(SplitMath.shares(10_000, listOf(p(1, 6_000), p(2, 4_000)), SplitMethod.EXACT)))
            .containsExactly(1L, 6_000L, 2L, 4_000L)
        assertThat(SplitMath.shares(10_000, listOf(p(1, 6_000), p(2, 3_999)), SplitMethod.EXACT))
            .isEqualTo(SplitResult.Invalid(SplitError.EXACT_MISMATCH))
        assertThat(SplitMath.shares(10_000, listOf(p(1, 10_001), p(2, 0)), SplitMethod.EXACT))
            .isEqualTo(SplitResult.Invalid(SplitError.EXACT_MISMATCH))
    }

    @Test
    fun `invalid inputs are refused with a reason`() {
        assertThat(SplitMath.shares(100, emptyList(), SplitMethod.EQUAL)).isEqualTo(SplitResult.Invalid(SplitError.NO_PARTICIPANTS))
        assertThat(SplitMath.shares(0, listOf(p(1)), SplitMethod.EQUAL)).isEqualTo(SplitResult.Invalid(SplitError.NON_POSITIVE_TOTAL))
        assertThat(SplitMath.shares(100, listOf(p(1), p(1)), SplitMethod.EQUAL)).isEqualTo(SplitResult.Invalid(SplitError.DUPLICATE_PARTICIPANT))
        assertThat(SplitMath.shares(100, listOf(p(1, -1), p(2, 101)), SplitMethod.EXACT)).isEqualTo(SplitResult.Invalid(SplitError.NEGATIVE_VALUE))
        assertThat(SplitMath.shares(100, listOf(p(1, 0), p(2, 0)), SplitMethod.WEIGHTS)).isEqualTo(SplitResult.Invalid(SplitError.INVALID_WEIGHTS))
        assertThat(SplitMath.shares(100, listOf(p(1, SplitMath.MAX_WEIGHT + 1)), SplitMethod.WEIGHTS)).isEqualTo(SplitResult.Invalid(SplitError.INVALID_WEIGHTS))
        assertThat(SplitMath.currencyError("LKR", "USD")).isEqualTo(SplitError.CURRENCY_MISMATCH)
        assertThat(SplitMath.currencyError("LKR", "lkr")).isNull()
    }

    @Test
    fun `balances are zero-sum and positive means owed`() {
        // Nabil pays 12,000 for three people; Aadhil pays 3,000 for Nabil and himself; Aadhil then settles 1,000.
        val hotel = ExpenseInput(paidBy = 1, amountMinor = 1_200_000, shares = ok(SplitMath.shares(1_200_000, listOf(p(1), p(2), p(3)), SplitMethod.EQUAL)))
        val dinner = ExpenseInput(paidBy = 2, amountMinor = 300_000, shares = mapOf(1L to 150_000L, 2L to 150_000L))
        val net = SplitMath.balances(listOf(1L, 2L, 3L), listOf(hotel, dinner), listOf(SettlementInput(from = 2, to = 1, amountMinor = 100_000)))
        assertThat(net.values.sum()).isEqualTo(0L)
        assertThat(net[1L]).isEqualTo(550_000L)
        assertThat(net[2L]).isEqualTo(-150_000L)
        assertThat(net[3L]).isEqualTo(-400_000L)
    }

    @Test
    fun `four people settle in at most three transfers that clear every balance`() {
        val balances = mapOf(1L to 50_000L, 2L to 10_000L, 3L to -40_000L, 4L to -20_000L)
        val transfers = SplitMath.simplify(balances)
        assertThat(transfers)
            .containsExactly(Transfer(3, 1, 40_000), Transfer(4, 1, 10_000), Transfer(4, 2, 10_000))
            .inOrder()
        val after = balances.toMutableMap()
        transfers.forEach {
            after[it.from] = after.getValue(it.from) + it.amountMinor
            after[it.to] = after.getValue(it.to) - it.amountMinor
        }
        assertThat(after.values.toSet()).containsExactly(0L)
    }

    @Test
    fun `an equal four-way expense leaves three people owing the payer`() {
        val shares = ok(SplitMath.shares(40_001, listOf(p(1), p(2), p(3), p(4)), SplitMethod.EQUAL))
        val net = SplitMath.balances(listOf(1L, 2L, 3L, 4L), listOf(ExpenseInput(1, 40_001, shares)), emptyList())
        assertThat(net.values.sum()).isEqualTo(0L)
        val transfers = SplitMath.simplify(net)
        assertThat(transfers.map { it.to }.toSet()).containsExactly(1L)
        assertThat(transfers.sumOf { it.amountMinor }).isEqualTo(net.getValue(1L))
    }
}
