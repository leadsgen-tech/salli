package lk.salli.domain

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.jupiter.api.Test

class FuelPassRulesTest {

    @Test
    fun `even plate fills on even days only`() {
        assertThat(FuelPassRules.isEligible("BAM-0786", LocalDate.of(2026, 9, 14))).isTrue()
        assertThat(FuelPassRules.isEligible("BAM-0786", LocalDate.of(2026, 9, 13))).isFalse()
    }

    @Test
    fun `odd plate fills on odd days only`() {
        assertThat(FuelPassRules.isEligible("CAB-1233", LocalDate.of(2026, 9, 13))).isTrue()
        assertThat(FuelPassRules.isEligible("CAB-1233", LocalDate.of(2026, 9, 14))).isFalse()
    }

    @Test
    fun `last eligible day before reset steps back over a wrong-parity day`() {
        // Quota resets Sunday 13 Sep; even plate's last shot is Saturday 12 Sep.
        assertThat(FuelPassRules.lastEligibleDayBefore("BAM-0786", LocalDate.of(2026, 9, 13)))
            .isEqualTo(LocalDate.of(2026, 9, 12))
        // Odd plate: 12 Sep is even, so it steps back to 11 Sep.
        assertThat(FuelPassRules.lastEligibleDayBefore("CAB-1233", LocalDate.of(2026, 9, 13)))
            .isEqualTo(LocalDate.of(2026, 9, 11))
    }

    @Test
    fun `month boundary parity works`() {
        // 31 Aug (odd) → 1 Sep (odd): two odd days in a row, an even plate skips both.
        assertThat(FuelPassRules.lastEligibleDayBefore("BAM-0786", LocalDate.of(2026, 9, 2)))
            .isEqualTo(LocalDate.of(2026, 8, 30))
    }

    @Test
    fun `plate without digits is always eligible`() {
        assertThat(FuelPassRules.isEligible("GOV", LocalDate.of(2026, 9, 13))).isTrue()
        assertThat(FuelPassRules.lastEligibleDayBefore("GOV", LocalDate.of(2026, 9, 13))).isNull()
    }
}
