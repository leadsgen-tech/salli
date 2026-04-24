package lk.salli.domain

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class MoneyTest {

    @Test
    fun `adds same-currency amounts`() {
        val a = Money(minorUnits = 100_00, currency = Currency.LKR)
        val b = Money(minorUnits = 50_00, currency = Currency.LKR)
        assertThat(a + b).isEqualTo(Money(150_00, Currency.LKR))
    }

    @Test
    fun `subtracts same-currency amounts, going negative if needed`() {
        val a = Money(100_00, Currency.LKR)
        val b = Money(150_00, Currency.LKR)
        assertThat(a - b).isEqualTo(Money(-50_00, Currency.LKR))
    }

    @Test
    fun `refuses to add different currencies`() {
        val lkr = Money(100_00, Currency.LKR)
        val usd = Money(1_00, Currency.USD)
        assertThrows<IllegalArgumentException> { lkr + usd }
    }

    @Test
    fun `refuses to compare different currencies`() {
        val lkr = Money(100_00, Currency.LKR)
        val usd = Money(1_00, Currency.USD)
        assertThrows<IllegalArgumentException> { lkr > usd }
    }

    @Test
    fun `unary minus negates`() {
        val a = Money(100_00, Currency.LKR)
        assertThat(-a).isEqualTo(Money(-100_00, Currency.LKR))
    }

    @Test
    fun `isZero, isPositive, isNegative flags`() {
        assertThat(Money.zero(Currency.LKR).isZero).isTrue()
        assertThat(Money(1, Currency.LKR).isPositive).isTrue()
        assertThat(Money(-1, Currency.LKR).isNegative).isTrue()
    }

    @ParameterizedTest
    @CsvSource(
        // input,        currency,  expectedMinor
        "'85000.00',     LKR,       8500000",
        "'85000',        LKR,       8500000",
        "'1,234.56',     LKR,       123456",
        "'0.10',         LKR,       10",
        "'0.1',          LKR,       10",
        "'247250.00',    LKR,       24725000",
        "'15.99',        USD,       1599",
        "'0',            LKR,       0",
        "'1,000,000.00', LKR,       100000000",
    )
    fun `parses SMS-style amounts correctly`(
        input: String,
        currency: String,
        expectedMinor: Long,
    ) {
        assertThat(Money.ofMajor(input, currency))
            .isEqualTo(Money(expectedMinor, currency))
    }

    @Test
    fun `three-decimal amounts truncate to two places`() {
        // We treat anything beyond cents as noise (SMS is always max 2dp anyway).
        val m = Money.ofMajor("1.999", Currency.LKR)
        assertThat(m.minorUnits).isEqualTo(199)
    }
}
