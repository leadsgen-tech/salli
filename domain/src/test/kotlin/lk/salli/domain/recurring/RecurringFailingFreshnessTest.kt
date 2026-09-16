package lk.salli.domain.recurring

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class RecurringFailingFreshnessTest {
    private val day = 24L * 60 * 60 * 1000
    private val start = 1_700_000_000_000L

    private fun attempt(id: Long, atDay: Long, declined: Boolean = false) = RecurringInput(
        id = id, counterparty = "REPLIT, INC.", flowId = 0, currency = "USD", amountMinor = 2_000,
        timestamp = start + atDay * day, isDeclined = declined, isOwnTransfer = false, categoryName = null,
    )

    /** Five monthly charges, then two refusals around day 150. */
    private val history = (0L until 5L).map { attempt(it, it * 30) } +
        listOf(attempt(10, 150, declined = true), attempt(11, 152, declined = true))

    @Test
    fun `refusals from long ago do not keep a charged series failing`() {
        val series = RecurringDetector.detect(history, start + 560 * day).single { it.flowId == 0 }
        assertThat(series.status).isNotEqualTo(RecurringStatus.FAILING)
    }

    @Test
    fun `recent refusals after real charges are failing`() {
        val series = RecurringDetector.detect(history, start + 160 * day).single { it.flowId == 0 }
        assertThat(series.status).isEqualTo(RecurringStatus.FAILING)
    }
}
