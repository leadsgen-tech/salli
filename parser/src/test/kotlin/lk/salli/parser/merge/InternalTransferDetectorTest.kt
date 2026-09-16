package lk.salli.parser.merge

import com.google.common.truth.Truth.assertThat
import lk.salli.domain.Currency
import lk.salli.domain.Money
import lk.salli.domain.TransactionFlow
import lk.salli.domain.TransactionType
import lk.salli.parser.ParsedTransaction
import org.junit.jupiter.api.Test

class InternalTransferDetectorTest {

    @Test
    fun `pairs real BOC credit with PeoplesBank debit - same inbox scenario`() {
        // 2026-04-07 10:05: user moved Rs 50000 from People's to BOC. Fee Rs 25 on Peoples side.
        val peoplesDebit = peoplesDebit(timestamp = 1_712_486_700_000L, amountMinor = 5_002_500)
        val bocCredit = bocCredit(timestamp = 1_712_486_710_000L, amountMinor = 5_000_000)
        val counterpart = InternalTransferDetector.findCounterpart(bocCredit, listOf(peoplesDebit))
        assertThat(counterpart).isEqualTo(peoplesDebit)
    }

    @Test
    fun `reverse lookup - peoples debit arriving second still pairs`() {
        val bocCredit = bocCredit(timestamp = 1_712_486_700_000L, amountMinor = 5_000_000)
        val peoplesDebit = peoplesDebit(timestamp = 1_712_486_710_000L, amountMinor = 5_002_500)
        val counterpart = InternalTransferDetector.findCounterpart(peoplesDebit, listOf(bocCredit))
        assertThat(counterpart).isEqualTo(bocCredit)
    }

    @Test
    fun `does not pair two debits - both sides must net to zero`() {
        val debit1 = peoplesDebit(timestamp = 1_000_000_000L, amountMinor = 5_000_000)
        val debit2 = bocDebit(timestamp = 1_000_001_000L, amountMinor = 5_000_000)
        assertThat(InternalTransferDetector.findCounterpart(debit2, listOf(debit1))).isNull()
    }

    @Test
    fun `does not pair across the 48 hour window`() {
        val peoplesDebit = peoplesDebit(timestamp = 1_000_000_000L, amountMinor = 5_000_000)
        val bocCredit = bocCredit(
            timestamp = 1_000_000_000L + 49L * 60 * 60 * 1000,
            amountMinor = 5_000_000,
        )
        assertThat(InternalTransferDetector.findCounterpart(bocCredit, listOf(peoplesDebit))).isNull()
    }

    @Test
    fun `does not pair when amount delta exceeds plausible fee`() {
        // Rs 500 delta — that's a different transaction, not a fee.
        val peoplesDebit = peoplesDebit(timestamp = 1_000_000_000L, amountMinor = 5_050_000)
        val bocCredit = bocCredit(timestamp = 1_000_001_000L, amountMinor = 5_000_000)
        assertThat(InternalTransferDetector.findCounterpart(bocCredit, listOf(peoplesDebit))).isNull()
    }

    @Test
    fun `does not pair same-sender transactions`() {
        // Same-bank transfers (e.g. BOC current → BOC savings) appear as a single BOC SMS,
        // not two, so this case shouldn't even arise — but we guard against mis-pairing two
        // unrelated BOC events that happen to look complementary.
        val bocDebit = bocDebit(timestamp = 1_000_000_000L, amountMinor = 5_000_000)
        val bocCredit = bocCredit(timestamp = 1_000_001_000L, amountMinor = 5_000_000)
        assertThat(InternalTransferDetector.findCounterpart(bocCredit, listOf(bocDebit))).isNull()
    }

    @Test
    fun `does not pair across different currencies`() {
        val usdDebit = ParsedTransaction(
            senderAddress = "COMBANK",
            accountNumberSuffix = "#4273",
            amount = Money(1_599, Currency.USD),
            balance = null,
            fee = null,
            flow = TransactionFlow.EXPENSE,
            type = TransactionType.POS,
            merchantRaw = "APPLE",
            location = null,
            timestamp = 1_000_000_000L,
            isDeclined = false,
            rawBody = "",
        )
        val lkrCredit = bocCredit(timestamp = 1_000_001_000L, amountMinor = 1_599)
        assertThat(InternalTransferDetector.findCounterpart(lkrCredit, listOf(usdDebit))).isNull()
    }

    @Test
    fun `declined transactions never pair`() {
        val decline = ParsedTransaction(
            senderAddress = "COMBANK",
            accountNumberSuffix = "#4273",
            amount = Money(5_000_000, Currency.LKR),
            balance = null,
            fee = null,
            flow = TransactionFlow.EXPENSE,
            type = TransactionType.DECLINED,
            merchantRaw = "X",
            location = null,
            timestamp = 1_000_000_000L,
            isDeclined = true,
            rawBody = "",
        )
        val credit = bocCredit(timestamp = 1_000_001_000L, amountMinor = 5_000_000)
        assertThat(InternalTransferDetector.findCounterpart(credit, listOf(decline))).isNull()
    }

    // --- Fixtures ---

    private fun peoplesDebit(timestamp: Long, amountMinor: Long) = ParsedTransaction(
        senderAddress = "PeoplesBank",
        accountNumberSuffix = "280-2001****68",
        amount = Money(amountMinor, Currency.LKR),
        balance = Money(1_046_27, Currency.LKR),
        fee = null,
        flow = TransactionFlow.EXPENSE,
        type = TransactionType.MOBILE_PAYMENT,
        merchantRaw = null,
        location = null,
        timestamp = timestamp,
        isDeclined = false,
        rawBody = "",
    )

    private fun bocDebit(timestamp: Long, amountMinor: Long) = ParsedTransaction(
        senderAddress = "BOC",
        accountNumberSuffix = "870",
        amount = Money(amountMinor, Currency.LKR),
        balance = Money(100_000, Currency.LKR),
        fee = null,
        flow = TransactionFlow.EXPENSE,
        type = TransactionType.CEFT,
        merchantRaw = null,
        location = null,
        timestamp = timestamp,
        isDeclined = false,
        rawBody = "",
    )

    private fun bocCredit(timestamp: Long, amountMinor: Long) = ParsedTransaction(
        senderAddress = "BOC",
        accountNumberSuffix = "870",
        amount = Money(amountMinor, Currency.LKR),
        balance = Money(5_000_000, Currency.LKR),
        fee = null,
        flow = TransactionFlow.INCOME,
        type = TransactionType.ONLINE_TRANSFER,
        merchantRaw = null,
        location = null,
        timestamp = timestamp,
        isDeclined = false,
        rawBody = "",
    )
}

class InternalTransferFeeRuleTest {
    @org.junit.jupiter.api.Test
    fun `flat small fee is plausible on any size`() {
        com.google.common.truth.Truth.assertThat(InternalTransferDetector.isPlausibleFee(fee = 2_500, debitMinor = 40_000)).isTrue()
        com.google.common.truth.Truth.assertThat(InternalTransferDetector.isPlausibleFee(fee = 2_500, debitMinor = 3_502_500)).isTrue()
    }

    @org.junit.jupiter.api.Test
    fun `fee above ten percent of a small debit is not a transfer`() {
        // Rs 50 debit vs Rs 0.99 credit — the real false pairing from a user's phone.
        com.google.common.truth.Truth.assertThat(InternalTransferDetector.isPlausibleFee(fee = 4_901, debitMinor = 5_000)).isFalse()
    }

    @org.junit.jupiter.api.Test
    fun `credit larger than debit is never a transfer`() {
        com.google.common.truth.Truth.assertThat(InternalTransferDetector.isPlausibleFee(fee = -8_703, debitMinor = 12_000_000)).isFalse()
    }

    @org.junit.jupiter.api.Test
    fun `fee over the hard cap is rejected even on huge debits`() {
        com.google.common.truth.Truth.assertThat(InternalTransferDetector.isPlausibleFee(fee = 15_000, debitMinor = 100_000_000)).isFalse()
    }
}


class InternalTransferNamedCounterpartyTest {
    private fun leg(sender: String, flow: lk.salli.domain.TransactionFlow, minor: Long, merchant: String?, ts: Long = 1_000L) =
        lk.salli.parser.ParsedTransaction(
            senderAddress = sender, accountNumberSuffix = "1", amount = lk.salli.domain.Money(minor, "LKR"),
            balance = null, fee = null, flow = flow, type = lk.salli.domain.TransactionType.ONLINE_TRANSFER,
            merchantRaw = merchant, location = null, timestamp = ts, isDeclined = false, rawBody = "",
        )

    @org.junit.jupiter.api.Test
    fun `a credit from a named person never pairs with a same-size debit`() {
        val hnbCredit = leg("HNB", lk.salli.domain.TransactionFlow.INCOME, 100_000, "AADHIL M M")
        val bocDebit = leg("BOC", lk.salli.domain.TransactionFlow.EXPENSE, 102_500, null)
        com.google.common.truth.Truth.assertThat(InternalTransferDetector.findCounterpart(hnbCredit, listOf(bocDebit))).isNull()
        com.google.common.truth.Truth.assertThat(InternalTransferDetector.findCounterpart(bocDebit, listOf(hnbCredit))).isNull()
    }

    @org.junit.jupiter.api.Test
    fun `a debit that names the other bank still pairs`() {
        val pbDebit = leg("PeoplesBank", lk.salli.domain.TransactionFlow.EXPENSE, 4_002_500, "Bank Of Ceylon - BOC")
        val bocCredit = leg("BOC", lk.salli.domain.TransactionFlow.INCOME, 4_000_000, null)
        com.google.common.truth.Truth.assertThat(InternalTransferDetector.findCounterpart(pbDebit, listOf(bocCredit))).isEqualTo(bocCredit)
    }

    @org.junit.jupiter.api.Test
    fun `a card purchase is never half of a transfer`() {
        val pos = leg("COMBANK", lk.salli.domain.TransactionFlow.EXPENSE, 500_000, "KEELLS SUPER")
        val credit = leg("BOC", lk.salli.domain.TransactionFlow.INCOME, 500_000, null)
        com.google.common.truth.Truth.assertThat(InternalTransferDetector.findCounterpart(pos, listOf(credit))).isNull()
    }
}
