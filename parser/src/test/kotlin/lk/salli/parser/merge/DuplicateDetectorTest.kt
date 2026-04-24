package lk.salli.parser.merge

import com.google.common.truth.Truth.assertThat
import lk.salli.domain.Currency
import lk.salli.domain.Money
import lk.salli.domain.TransactionFlow
import lk.salli.domain.TransactionType
import lk.salli.parser.ParsedTransaction
import org.junit.jupiter.api.Test

class DuplicateDetectorTest {

    @Test
    fun `identical BOC re-send within seconds is flagged`() {
        // Real scenario from the user's inbox 2026-04-22: the same CEFT debit arrived twice,
        // seconds apart, with matching amount + balance.
        val first = bocDebit(timestamp = 1_000_000_000L, amountMinor = 5_002_500, balanceMinor = 8_592_910)
        val second = first.copy(timestamp = 1_000_003_000L) // +3 seconds
        val dup = DuplicateDetector.findDuplicate(second, listOf(first))
        assertThat(dup).isEqualTo(first)
    }

    @Test
    fun `two genuine transfers of the same amount with different balances are kept`() {
        val first = bocDebit(timestamp = 1_000_000_000L, amountMinor = 5_000_000, balanceMinor = 9_000_000)
        val secondMinuteLater = bocDebit(
            timestamp = 1_000_060_000L, // +1 minute
            amountMinor = 5_000_000,
            balanceMinor = 4_000_000, // different — indicates this is a real second transfer
        )
        val dup = DuplicateDetector.findDuplicate(secondMinuteLater, listOf(first))
        assertThat(dup).isNull()
    }

    @Test
    fun `same-amount transactions six minutes apart are not duplicates`() {
        val first = bocDebit(timestamp = 1_000_000_000L, amountMinor = 5_000_000, balanceMinor = 9_000_000)
        val later = first.copy(timestamp = 1_000_360_000L) // +6 minutes
        val dup = DuplicateDetector.findDuplicate(later, listOf(first))
        assertThat(dup).isNull()
    }

    @Test
    fun `combank purchase without balance uses merchant as discriminator`() {
        val tx1 = combankPurchase(
            timestamp = 1_000_000_000L,
            amountMinor = 150_000,
            merchantRaw = "APPLE.COM/BILL SG",
        )
        val tx2Dup = tx1.copy(timestamp = 1_000_060_000L) // +1 min, same merchant → dup
        val tx2Diff = tx1.copy(
            timestamp = 1_000_060_000L,
            merchantRaw = "KEELLS SUPER COLOMBO 04",
        )

        assertThat(DuplicateDetector.findDuplicate(tx2Dup, listOf(tx1))).isEqualTo(tx1)
        assertThat(DuplicateDetector.findDuplicate(tx2Diff, listOf(tx1))).isNull()
    }

    @Test
    fun `declined status prevents a declined SMS from shadowing a real purchase`() {
        val real = combankPurchase(timestamp = 1_000_000_000L, amountMinor = 150_000, merchantRaw = "X")
        val declined = real.copy(
            isDeclined = true,
            type = TransactionType.DECLINED,
            timestamp = 1_000_000_500L,
        )
        // Even within the window with identical merchant + amount, declined ≠ real — both must live.
        assertThat(DuplicateDetector.findDuplicate(declined, listOf(real))).isNull()
    }

    @Test
    fun `different accounts under same bank are not duplicates`() {
        val acc870 = bocDebit(timestamp = 1_000_000_000L, amountMinor = 5_000_000, balanceMinor = 9_000_000)
            .copy(accountNumberSuffix = "870")
        val acc999 = acc870.copy(accountNumberSuffix = "999", timestamp = 1_000_010_000L)
        assertThat(DuplicateDetector.findDuplicate(acc999, listOf(acc870))).isNull()
    }

    @Test
    fun `empty recent list means nothing is a duplicate`() {
        val tx = bocDebit(timestamp = 1_000_000_000L, amountMinor = 5_000_000, balanceMinor = 9_000_000)
        assertThat(DuplicateDetector.findDuplicate(tx, emptyList())).isNull()
    }

    // --- Fixtures ---

    private fun bocDebit(
        timestamp: Long,
        amountMinor: Long,
        balanceMinor: Long,
    ) = ParsedTransaction(
        senderAddress = "BOC",
        accountNumberSuffix = "870",
        amount = Money(amountMinor, Currency.LKR),
        balance = Money(balanceMinor, Currency.LKR),
        fee = null,
        flow = TransactionFlow.EXPENSE,
        type = TransactionType.CEFT,
        merchantRaw = null,
        location = null,
        timestamp = timestamp,
        isDeclined = false,
        rawBody = "redacted",
    )

    private fun combankPurchase(
        timestamp: Long,
        amountMinor: Long,
        merchantRaw: String,
    ) = ParsedTransaction(
        senderAddress = "COMBANK",
        accountNumberSuffix = "#4273",
        amount = Money(amountMinor, Currency.LKR),
        balance = null,
        fee = null,
        flow = TransactionFlow.EXPENSE,
        type = TransactionType.POS,
        merchantRaw = merchantRaw,
        location = null,
        timestamp = timestamp,
        isDeclined = false,
        rawBody = "redacted",
    )
}
