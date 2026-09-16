package lk.salli.domain.planning

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class RunwayTest {

    @Test
    fun `balance lasts as many days as the average spend allows, unknown balances listed apart`() {
        val r = Runway.compute(
            now = 0L,
            accounts = listOf(AccountBalance("BOC", 1_000_000), AccountBalance("PB", 500_000), AccountBalance("ComBank card", null)),
            spendInWindowMinor = 900_000,
            windowDays = 90,
        )
        assertThat(r.balanceMinor).isEqualTo(1_500_000)
        assertThat(r.countedAccounts).containsExactly("BOC", "PB")
        assertThat(r.notCountedAccounts).containsExactly("ComBank card")
        assertThat(r.avgDailySpendMinor).isEqualTo(10_000)
        assertThat(r.days).isEqualTo(150)
        assertThat(r.untilMillis).isEqualTo(150L * 24 * 60 * 60 * 1000)
    }

    @Test
    fun `no spending means no runway figure and an overdrawn balance means zero days`() {
        assertThat(Runway.compute(0L, listOf(AccountBalance("BOC", 1_000)), 0, 90).days).isNull()
        assertThat(Runway.compute(0L, listOf(AccountBalance("BOC", -5_000)), 900_000, 90).days).isEqualTo(0)
    }
}
