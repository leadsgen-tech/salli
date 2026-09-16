package lk.salli.domain.money

import com.google.common.truth.Truth.assertThat
import lk.salli.domain.Money
import org.junit.jupiter.api.Test

/**
 * The formatter is the app's single opinion about how a rupee is written, so these tests pin
 * the *characters*, not just the arithmetic: a hyphen where a minus sign belongs, or a lost
 * thousands separator, is a visual bug that no screenshot review would reliably catch.
 */
class MoneyFormatTest {

    @Test
    fun `LKR renders with the Rs prefix and two decimals`() {
        assertThat(MoneyFormat.format(Money(428_000, "LKR"))).isEqualTo("Rs 4,280.00")
        assertThat(MoneyFormat.format(Money(5, "LKR"))).isEqualTo("Rs 0.05")
    }

    @Test
    fun `other currencies use their ISO code rather than a guessed symbol`() {
        assertThat(MoneyFormat.format(Money(1_599, "USD"))).isEqualTo("USD 15.99")
        assertThat(MoneyFormat.format(Money(1_000, "EUR"))).isEqualTo("EUR 10.00")
    }

    @Test
    fun `grouping is three-digit Latin regardless of the default locale`() {
        val previous = java.util.Locale.getDefault()
        try {
            // A device set to a lakh-grouping locale must not change how Salli writes money.
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("en-IN"))
            assertThat(MoneyFormat.format(Money(123_456_789, "LKR"))).isEqualTo("Rs 1,234,567.89")
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }

    @Test
    fun `signed adds a real minus sign for debits and a plus for credits`() {
        assertThat(MoneyFormat.format(Money(-428_000, "LKR"), signed = true))
            .isEqualTo("−Rs 4,280.00")
        assertThat(MoneyFormat.format(Money(428_000, "LKR"), signed = true))
            .isEqualTo("+Rs 4,280.00")
    }

    @Test
    fun `zero is never signed`() {
        assertThat(MoneyFormat.format(Money(0, "LKR"), signed = true)).isEqualTo("Rs 0.00")
    }

    @Test
    fun `unsigned drops the sign entirely, including for negatives`() {
        assertThat(MoneyFormat.format(Money(-428_000, "LKR"))).isEqualTo("Rs 4,280.00")
    }

    @Test
    fun `formatWithMinus signs debits but never decorates credits`() {
        assertThat(MoneyFormat.formatWithMinus(Money(-428_000, "LKR")))
            .isEqualTo("−Rs 4,280.00")
        // No leading "+": a balance is a level, not a change.
        assertThat(MoneyFormat.formatWithMinus(Money(1_640_000, "LKR")))
            .isEqualTo("Rs 16,400.00")
        assertThat(MoneyFormat.formatWithMinus(Money(0, "LKR"))).isEqualTo("Rs 0.00")
    }

    @Test
    fun `bare is magnitude only`() {
        assertThat(MoneyFormat.bare(Money(-1_234_56, "LKR"))).isEqualTo("1,234.56")
        assertThat(MoneyFormat.bare(Money(0, "USD"))).isEqualTo("0.00")
    }

    @Test
    fun `short drops decimals and suffixes magnitude`() {
        assertThat(MoneyFormat.short(Money(84_000, "LKR"))).isEqualTo("Rs 840")
        assertThat(MoneyFormat.short(Money(120_000, "LKR"))).isEqualTo("Rs 1.2k")
        assertThat(MoneyFormat.short(Money(340_000_00, "LKR"))).isEqualTo("Rs 340k")
        assertThat(MoneyFormat.short(Money(3_400_000_00, "LKR"))).isEqualTo("Rs 3.4M")
    }

    @Test
    fun `short trims a trailing zero rather than writing 3 point 0 k`() {
        assertThat(MoneyFormat.short(Money(300_000, "LKR"))).isEqualTo("Rs 3k")
        assertThat(MoneyFormat.short(Money(2_000_000_00, "LKR"))).isEqualTo("Rs 2M")
    }

    @Test
    fun `the minor-unit entry points agree with the Money ones`() {
        val money = Money(-98_765, "LKR")
        assertThat(MoneyFormat.formatMinor(money.minorUnits, money.currency))
            .isEqualTo(MoneyFormat.format(money))
        assertThat(MoneyFormat.bareMinor(money.minorUnits)).isEqualTo(MoneyFormat.bare(money))
        assertThat(MoneyFormat.shortMinor(money.minorUnits, money.currency))
            .isEqualTo(MoneyFormat.short(money))
    }

    @Test
    fun `Long MIN_VALUE does not blow up on negation`() {
        // kotlin.math.abs(Long.MIN_VALUE) is still Long.MIN_VALUE, so a naive implementation
        // formats a negative magnitude. Nothing produces this in practice; crashing on a
        // corrupt row would still be the wrong failure mode.
        assertThat(MoneyFormat.format(Money(Long.MIN_VALUE, "LKR"))).doesNotContain("-")
        assertThat(MoneyFormat.short(Money(Long.MIN_VALUE, "LKR"))).doesNotContain("-")
    }

    @Test
    fun `symbol is Rs only for LKR`() {
        assertThat(MoneyFormat.symbol("LKR")).isEqualTo("Rs")
        assertThat(MoneyFormat.symbol("USD")).isEqualTo("USD")
    }
}
