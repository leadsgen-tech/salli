package lk.salli.parser.merge

import com.google.common.truth.Truth.assertThat
import lk.salli.domain.Currency
import lk.salli.domain.Money
import lk.salli.domain.TransactionFlow
import lk.salli.domain.TransactionType
import lk.salli.parser.ParsedTransaction
import org.junit.jupiter.api.Test

class PeoplesBankMergerTest {

    @Test
    fun `merges primary-then-confirm for a fund transfer with fee`() {
        // Real scenario from inbox: 50025 debit (fee-inclusive), then 50000 confirm to LOLC.
        val primary = peoplesPrimaryDebit(
            timestamp = 1_000_000_000L,
            amountMinor = 5_002_500,
            balanceMinor = 104_627,
        )
        val confirm = peoplesFundTransferConfirm(
            timestamp = 1_000_002_000L, // +2s
            amountMinor = 5_000_000,
            merchantRaw = "LOLC Finance PLC",
        )
        val result = PeoplesBankMerger.tryMerge(confirm, recent = listOf(primary))
        assertThat(result).isNotNull()
        val merged = result!!.merged
        assertThat(merged.amount.minorUnits).isEqualTo(5_002_500)
        assertThat(merged.fee?.minorUnits).isEqualTo(2_500)
        assertThat(merged.merchantRaw).isEqualTo("LOLC Finance PLC")
        assertThat(merged.balance?.minorUnits).isEqualTo(104_627)
        assertThat(merged.type).isEqualTo(TransactionType.ONLINE_TRANSFER)
        assertThat(result.supersedes).isEqualTo(primary)
    }

    @Test
    fun `merges primary that arrives without an Av_Bal block (2026 vintage)`() {
        // Real scenario: confirm "LKR 6500.00 to Commercial Bank PLC", primary "Rs. 6525.00
        // (LPAY Tfr)" with no Av_Bal. Pre-fix the merger required a non-null balance on the
        // primary and refused to pair, leaving two rows.
        val confirm = peoplesFundTransferConfirm(
            timestamp = 1_000_000_000L,
            amountMinor = 650_000,
            merchantRaw = "Commercial Bank PLC",
        )
        val primary = peoplesPrimaryDebit(
            timestamp = 1_000_004_000L, // +4s
            amountMinor = 652_500,
            balanceMinor = null,
        )
        val result = PeoplesBankMerger.tryMerge(primary, recent = listOf(confirm))
        assertThat(result).isNotNull()
        assertThat(result!!.merged.amount.minorUnits).isEqualTo(652_500)
        assertThat(result.merged.fee?.minorUnits).isEqualTo(2_500)
        assertThat(result.merged.merchantRaw).isEqualTo("Commercial Bank PLC")
        assertThat(result.merged.balance).isNull() // primary didn't carry one
        assertThat(result.merged.type).isEqualTo(TransactionType.ONLINE_TRANSFER)
    }

    @Test
    fun `merges in reverse order - confirm-then-primary`() {
        val confirm = peoplesFundTransferConfirm(
            timestamp = 1_000_000_000L,
            amountMinor = 5_000_000,
            merchantRaw = "LOLC Finance PLC",
        )
        val primary = peoplesPrimaryDebit(
            timestamp = 1_000_003_000L, // +3s
            amountMinor = 5_002_500,
            balanceMinor = 104_627,
        )
        val result = PeoplesBankMerger.tryMerge(primary, recent = listOf(confirm))
        assertThat(result).isNotNull()
        assertThat(result!!.merged.fee?.minorUnits).isEqualTo(2_500)
        assertThat(result.supersedes).isEqualTo(confirm)
    }

    @Test
    fun `mobile-payment confirm merges and keeps MOBILE_PAYMENT type`() {
        val primary = peoplesPrimaryDebit(
            timestamp = 1_000_000_000L,
            amountMinor = 10_000, // Rs 100.00
            balanceMinor = 585_268,
        )
        val confirm = ParsedTransaction(
            senderAddress = "PeoplesBank",
            accountNumberSuffix = null,
            amount = Money(10_000, Currency.LKR),
            balance = null,
            fee = null,
            flow = TransactionFlow.EXPENSE,
            type = TransactionType.MOBILE_PAYMENT,
            merchantRaw = "Mobitel",
            location = null,
            timestamp = 1_000_002_000L,
            isDeclined = false,
            rawBody = "Mobile Payment Successful, LKR 100.00 to Mobitel …",
        )
        val result = PeoplesBankMerger.tryMerge(confirm, recent = listOf(primary))
        assertThat(result).isNotNull()
        assertThat(result!!.merged.type).isEqualTo(TransactionType.MOBILE_PAYMENT)
        assertThat(result.merged.merchantRaw).isEqualTo("Mobitel")
        // No fee when amounts match — primary for a reload often equals confirm.
        assertThat(result.merged.fee).isNull()
    }

    @Test
    fun `no merge across the 5 minute window`() {
        val primary = peoplesPrimaryDebit(
            timestamp = 1_000_000_000L,
            amountMinor = 5_002_500,
            balanceMinor = 104_627,
        )
        val confirm = peoplesFundTransferConfirm(
            timestamp = 1_000_400_000L, // +6m 40s
            amountMinor = 5_000_000,
            merchantRaw = "LOLC Finance PLC",
        )
        assertThat(PeoplesBankMerger.tryMerge(confirm, listOf(primary))).isNull()
    }

    @Test
    fun `no merge when fee delta would be implausibly large`() {
        // Rs 200 delta — beyond the Rs 100 fee cap, so these are genuinely two transactions.
        val primary = peoplesPrimaryDebit(
            timestamp = 1_000_000_000L,
            amountMinor = 5_020_000,
            balanceMinor = 100_000,
        )
        val confirm = peoplesFundTransferConfirm(
            timestamp = 1_000_002_000L,
            amountMinor = 5_000_000,
            merchantRaw = "Someone",
        )
        assertThat(PeoplesBankMerger.tryMerge(confirm, listOf(primary))).isNull()
    }

    @Test
    fun `no merge when confirm amount exceeds primary amount`() {
        // Confirm > primary implies they belong to different events.
        val primary = peoplesPrimaryDebit(
            timestamp = 1_000_000_000L,
            amountMinor = 5_000_000,
            balanceMinor = 100_000,
        )
        val confirm = peoplesFundTransferConfirm(
            timestamp = 1_000_002_000L,
            amountMinor = 5_100_000, // bigger
            merchantRaw = "Someone",
        )
        assertThat(PeoplesBankMerger.tryMerge(confirm, listOf(primary))).isNull()
    }

    @Test
    fun `does not touch non-PeoplesBank senders`() {
        val bocDebit = ParsedTransaction(
            senderAddress = "BOC",
            accountNumberSuffix = "870",
            amount = Money(5_002_500, Currency.LKR),
            balance = Money(100_000, Currency.LKR),
            fee = null,
            flow = TransactionFlow.EXPENSE,
            type = TransactionType.CEFT,
            merchantRaw = null,
            location = null,
            timestamp = 1_000_000_000L,
            isDeclined = false,
            rawBody = "",
        )
        val confirm = peoplesFundTransferConfirm(
            timestamp = 1_000_002_000L,
            amountMinor = 5_000_000,
            merchantRaw = "Someone",
        )
        assertThat(PeoplesBankMerger.tryMerge(confirm, listOf(bocDebit))).isNull()
    }

    // --- Fixtures ---

    private fun peoplesPrimaryDebit(
        timestamp: Long,
        amountMinor: Long,
        balanceMinor: Long?,
    ) = ParsedTransaction(
        senderAddress = "PeoplesBank",
        accountNumberSuffix = "280-2001****68",
        amount = Money(amountMinor, Currency.LKR),
        balance = balanceMinor?.let { Money(it, Currency.LKR) },
        fee = null,
        flow = TransactionFlow.EXPENSE,
        type = TransactionType.MOBILE_PAYMENT,
        merchantRaw = null,
        location = null,
        timestamp = timestamp,
        isDeclined = false,
        rawBody = "",
    )

    private fun peoplesFundTransferConfirm(
        timestamp: Long,
        amountMinor: Long,
        merchantRaw: String,
    ) = ParsedTransaction(
        senderAddress = "PeoplesBank",
        accountNumberSuffix = null,
        amount = Money(amountMinor, Currency.LKR),
        balance = null,
        fee = null,
        flow = TransactionFlow.EXPENSE,
        type = TransactionType.ONLINE_TRANSFER,
        merchantRaw = merchantRaw,
        location = null,
        timestamp = timestamp,
        isDeclined = false,
        rawBody = "",
    )
}
