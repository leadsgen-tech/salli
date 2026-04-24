package lk.salli.domain

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class CurrencyTest {

    @ParameterizedTest
    @CsvSource(
        "Rs,     LKR",
        "Rs.,    LKR",
        "rs,     LKR",
        "RS.,    LKR",
        "LKR,    LKR",
        "lkr,    LKR",
        "USD,    USD",
        "usd,    USD",
        "US\$,   USD",
        "\$,     USD",
        "EUR,    EUR",
        "€,      EUR",
        "GBP,    GBP",
        "£,      GBP",
        "INR,    INR",
        "XYZ,    XYZ",
    )
    fun `normalizes common currency strings`(raw: String, expected: String) {
        assertThat(Currency.normalize(raw)).isEqualTo(expected)
    }
}
