package lk.salli.domain.recurring

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class RecurringDetectorTest {

    private val day = 24L * 60 * 60 * 1000
    private val t0 = 1_767_830_400_000L // 2026-01-08T00:00Z

    private fun tx(
        id: Long,
        who: String?,
        days: Double,
        amount: Long,
        flow: Int = 0,
        declined: Boolean = false,
        own: Boolean = false,
        category: String? = null,
        currency: String = "LKR",
    ) = RecurringInput(id, who, flow, currency, amount, t0 + (days * day).toLong(), declined, own, category)

    /** The real LOLC Finance shape from a user's inbox: 5 charges, gaps 35/39/30/33 days. */
    private val lolc = listOf(
        tx(1, "LOLC Finance PLC", 0.0, 4_502_500),
        tx(2, "LOLC Finance PLC", 35.0, 4_502_500),
        tx(3, "LOLC Finance PLC", 74.0, 4_502_500),
        tx(4, "LOLC Finance PLC", 104.0, 5_002_500),
        tx(5, "LOLC Finance PLC", 137.0, 5_002_500),
    )
    private val lolcLast = t0 + 137 * day

    @Test
    fun `counterparty keys drop digits, punctuation and corporate suffixes`() {
        assertThat(RecurringDetector.counterpartyKey("APPLE.COM/BILL")).isEqualTo("apple bill")
        assertThat(RecurringDetector.counterpartyKey("LOLC Finance PLC")).isEqualTo("lolc finance")
        assertThat(RecurringDetector.counterpartyKey("People's Bank")).isEqualTo("people s bank")
        assertThat(RecurringDetector.counterpartyKey("Card 4512****7788 KEELLS #12")).isEqualTo("card keells")
        assertThat(RecurringDetector.counterpartyKey("  ")).isNull()
        assertThat(RecurringDetector.counterpartyKey("1234")).isNull()
    }

    @Test
    fun `a lease instalment comes out monthly and fixed`() {
        val series = RecurringDetector.detect(lolc, now = lolcLast + 10 * day).single()
        assertThat(series.key).isEqualTo("lolc finance")
        assertThat(series.cadence).isEqualTo(Cadence.MONTHLY)
        assertThat(series.isFixed).isTrue()
        assertThat(series.occurrences).isEqualTo(5)
        assertThat(series.typicalAmountMinor).isEqualTo(4_502_500)
        assertThat(series.intervalDays).isEqualTo(33.0)
        assertThat(series.nextAt).isEqualTo(lolcLast + 33 * day)
        assertThat(series.status).isEqualTo(RecurringStatus.ACTIVE)
        assertThat(series.displayName).isEqualTo("LOLC Finance PLC")
    }

    @Test
    fun `status follows the expected next charge`() {
        assertThat(RecurringDetector.detect(lolc, lolcLast + 28 * day).single().status).isEqualTo(RecurringStatus.DUE_SOON)
        assertThat(RecurringDetector.detect(lolc, lolcLast + 36 * day).single().status).isEqualTo(RecurringStatus.DUE_SOON)
        assertThat(RecurringDetector.detect(lolc, lolcLast + 60 * day).single().status).isEqualTo(RecurringStatus.MISSED)
    }

    @Test
    fun `irregular transfers to a bank are not recurring`() {
        val gaps = listOf(1, 4, 2, 9, 3, 1, 6, 2, 8, 5, 1, 3, 7, 2, 4, 10, 1, 2, 6, 3)
        val amounts = listOf(500_000L, 5_000_000L, 60_000L, 9_650_000L, 5_200_000L)
        var d = 0.0
        val rows = gaps.mapIndexed { i, g ->
            d += g
            tx(100L + i, "Commercial Bank PLC", d, amounts[i % amounts.size])
        }
        assertThat(RecurringDetector.detect(rows, now = t0 + 90 * day)).isEmpty()
    }

    @Test
    fun `a bank payee needs a fixed amount, any other payee may vary`() {
        val amounts = listOf(2_502_500L, 1_000_000L, 5_000_000L, 2_502_500L, 800_000L)
        val sampath = amounts.mapIndexed { i, a -> tx(10L + i, "Sampath Bank", i * 30.0, a) }
        assertThat(RecurringDetector.detect(sampath, now = t0 + 130 * day)).isEmpty()

        val gym = amounts.mapIndexed { i, a -> tx(20L + i, "Fitness Hub Colombo", i * 30.0, a) }
        val series = RecurringDetector.detect(gym, now = t0 + 130 * day).single()
        assertThat(series.isFixed).isFalse()
        assertThat(series.cadence).isEqualTo(Cadence.MONTHLY)
    }

    @Test
    fun `a variable amount needs four charges`() {
        val rows = listOf(tx(1, "Fitness Hub", 0.0, 100_000), tx(2, "Fitness Hub", 30.0, 250_000), tx(3, "Fitness Hub", 60.0, 90_000))
        assertThat(RecurringDetector.detect(rows, now = t0 + 70 * day)).isEmpty()
    }

    @Test
    fun `grocery runs never qualify`() {
        val rows = (0 until 4).map { tx(30L + it, "KEELLS SUPER", it * 30.0, 500_000, category = "Groceries") }
        assertThat(RecurringDetector.detect(rows, now = t0 + 100 * day)).isEmpty()
    }

    @Test
    fun `sms resends within two hours count once`() {
        val rows = lolc.take(4) + tx(9, "LOLC Finance PLC", 104.0 + 1.0 / 24, 5_002_500)
        assertThat(RecurringDetector.detect(rows, now = t0 + 110 * day).single().occurrences).isEqualTo(4)
    }

    @Test
    fun `own transfers are ignored`() {
        val rows = (0 until 4).map { tx(40L + it, "Bank Of Ceylon - BOC", it * 30.0, 2_002_500, own = true) }
        assertThat(RecurringDetector.detect(rows, now = t0 + 100 * day)).isEmpty()
    }

    @Test
    fun `a subscription whose last two attempts were declined is failing`() {
        val rows = listOf(
            tx(1, "APPLE.COM/BILL", 0.0, 999, currency = "USD"),
            tx(2, "APPLE.COM/BILL", 30.0, 999, currency = "USD"),
            tx(3, "APPLE.COM/BILL", 60.0, 999, currency = "USD"),
            tx(4, "APPLE.COM/BILL", 90.0, 999, declined = true, currency = "USD"),
            tx(5, "APPLE.COM/BILL", 91.0, 999, declined = true, currency = "USD"),
        )
        val series = RecurringDetector.detect(rows, now = t0 + 92 * day).single()
        assertThat(series.status).isEqualTo(RecurringStatus.FAILING)
        assertThat(series.declinedAttempts).isEqualTo(2)
        assertThat(series.cadence).isEqualTo(Cadence.MONTHLY)
    }

    @Test
    fun `card retries within minutes are one refusal`() {
        val rows = listOf(
            tx(1, "APPLE.COM/BILL", 0.0, 999, currency = "USD"),
            tx(2, "APPLE.COM/BILL", 30.0, 999, currency = "USD"),
            tx(3, "APPLE.COM/BILL", 60.0, 999, currency = "USD"),
            tx(4, "APPLE.COM/BILL", 90.0, 999, declined = true, currency = "USD"),
            tx(5, "APPLE.COM/BILL", 90.0 + 0.5 / 24, 999, declined = true, currency = "USD"),
        )
        assertThat(RecurringDetector.detect(rows, now = t0 + 92 * day).single().status).isNotEqualTo(RecurringStatus.FAILING)
    }

    @Test
    fun `a merchant that only ever declines is failing when retried for weeks and still trying`() {
        val weeks = listOf(
            tx(1, "REPLIT, INC.", 0.0, 2_500, declined = true, currency = "USD"),
            tx(2, "REPLIT, INC.", 14.0, 2_500, declined = true, currency = "USD"),
            tx(3, "REPLIT, INC.", 31.0, 2_500, declined = true, currency = "USD"),
        )
        val failing = RecurringDetector.detect(weeks, now = t0 + 35 * day).single()
        assertThat(failing.status).isEqualTo(RecurringStatus.FAILING)
        assertThat(failing.cadence).isNull()
        assertThat(failing.nextAt).isNull()

        val sameWeek = weeks.take(1) + tx(2, "REPLIT, INC.", 3.0, 2_500, declined = true, currency = "USD")
        assertThat(RecurringDetector.detect(sameWeek, now = t0 + 5 * day)).isEmpty()
        assertThat(RecurringDetector.detect(weeks, now = t0 + 120 * day)).isEmpty()
    }

    @Test
    fun `a regular income is detected too`() {
        val rows = (0 until 4).map { tx(50L + it, "ACME SALARY", it * 31.0, 25_000_000, flow = 1) }
        val series = RecurringDetector.detect(rows, now = t0 + 100 * day).single()
        assertThat(series.flowId).isEqualTo(1)
        assertThat(series.isFixed).isTrue()
    }
}
