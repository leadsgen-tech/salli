package lk.salli.app.ui

import androidx.compose.material.icons.outlined.Receipt
import com.google.common.truth.Truth.assertThat
import lk.salli.data.db.entities.TransactionEntity
import lk.salli.domain.Money
import lk.salli.domain.TransactionFlow
import lk.salli.domain.TransactionMethod
import lk.salli.domain.TransactionType
import org.junit.Test

class TimelineItemTest {

    @Test
    fun `bank transfer is titled by its counterparty`() {
        val item = row(
            type = TransactionType.ONLINE_TRANSFER,
            merchant = "Commercial Bank PLC",
            body = "Fund transfer Successful",
        ).toTimelineItem(category = null, accountDisplayName = "People's Bank 0068")

        assertThat(item.title).isEqualTo("Commercial Bank PLC")
        assertThat(item.subtitle).isEqualTo("People's Bank 0068")
        assertThat(item.merchantRaw).isEqualTo("Commercial Bank PLC")
    }

    @Test
    fun `legacy mobile bill payment keeps the biller instead of becoming a transfer`() {
        val item = row(
            type = TransactionType.MOBILE_PAYMENT,
            merchant = "Dialog",
            body = "Mobile Payment Successful, LKR 385.00 to Dialog Ref No [PHONE]",
        ).toTimelineItem(category = null, accountDisplayName = "People's Bank 0068")

        assertThat(item.type).isEqualTo(TransactionType.BILL_PAYMENT)
        assertThat(item.title).isEqualTo("Dialog")
    }

    @Test
    fun `legacy mobile transfer is normalised`() {
        val item = row(
            type = TransactionType.MOBILE_PAYMENT,
            merchant = "Commercial Bank PLC",
            body = "Your A/C 280-2001****68 has been debited by Rs. 500.25 (LPAY Tfr @08:58 22/04/2026).",
        ).toTimelineItem(category = null, accountDisplayName = "People's Bank 0068")

        assertThat(item.type).isEqualTo(TransactionType.ONLINE_TRANSFER)
        assertThat(item.title).isEqualTo("Commercial Bank PLC")
    }

    @Test
    fun `a transfer whose counterparty is only digits falls back to Transfer`() {
        val item = row(
            type = TransactionType.ONLINE_TRANSFER,
            merchant = "94279435",
            body = "Fund transfer Successful",
        ).toTimelineItem(category = null, accountDisplayName = "People's Bank 0068")

        assertThat(item.title).isEqualTo("Transfer")
    }

    @Test
    fun `two or more own transfers in a day fold into one moved row`() {
        val move = { id: Long, minor: Long -> TimelineItem(
            id = id, title = "Own transfer", subtitle = "BOC → Peoples", amount = Money(minor, "LKR"),
            flow = TransactionFlow.TRANSFER, type = TransactionType.ONLINE_TRANSFER,
            icon = androidx.compose.material.icons.Icons.Outlined.Receipt, merchantRaw = null,
            isDeclined = false, timestamp = id, isOwnTransfer = true, fromSender = "BOC", toSender = "PeoplesBank",
        ) }
        val spend = move(9, 500).copy(title = "Keells", flow = TransactionFlow.EXPENSE, isOwnTransfer = false)
        val rows = listOf(move(1, 25_000), spend, move(3, 10_000))

        val folded = foldOwnTransfers(rows, "Moved between your accounts") { n -> "$n moves" }

        assertThat(folded.map { it.title }).containsExactly("Moved between your accounts", "Keells").inOrder()
        assertThat(folded.first().amount.minorUnits).isEqualTo(35_000)
        assertThat(folded.first().subtitle).isEqualTo("2 moves")
        assertThat(folded.first().foldedMoves).isEqualTo(2)
        assertThat(foldOwnTransfers(listOf(move(1, 25_000), spend), "x") { "" }).hasSize(2)
    }

    @Test
    fun `an excluded row is flagged so lists can mute it`() {
        val item = row(
            type = TransactionType.POS,
            merchant = "KEELLS",
            body = "spent at KEELLS",
        ).copy(isHidden = true).toTimelineItem(category = null, accountDisplayName = "ComBank 4273")

        assertThat(item.isExcluded).isTrue()
    }

    @Test
    fun `ComBank mobile purchase keeps its merchant title`() {
        val item = row(
            type = TransactionType.MOBILE_PAYMENT,
            merchant = "MOBITEL DATA PLANS",
            body = "Your Q+ transaction at MOBITEL DATA PLANS for LKR 80.00 has been successful.",
            sender = "ComBank_Q+",
        ).toTimelineItem(category = null, accountDisplayName = "ComBank Q+")

        assertThat(item.type).isEqualTo(TransactionType.MOBILE_PAYMENT)
        assertThat(item.title).isEqualTo("MOBITEL DATA PLANS")
    }

    private fun row(
        type: TransactionType,
        merchant: String,
        body: String,
        sender: String = "PeoplesBank",
    ) = TransactionEntity(
        id = 1,
        accountId = 2,
        amountMinor = 25_000,
        amountCurrency = "LKR",
        timestamp = 1,
        flowId = TransactionFlow.EXPENSE.id,
        methodId = TransactionMethod.SMS.id,
        typeId = type.id,
        senderAddress = sender,
        merchantRaw = merchant,
        rawBody = body,
        createdAt = 1,
        updatedAt = 1,
    )
}
