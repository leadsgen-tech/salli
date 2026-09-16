package lk.salli.parser.utility

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Test

/**
 * SLT-MOBITEL bodies are redacted real samples. Dialog, CEB and NWSDB bodies are
 * reconstructions from public field evidence (`_reconstructed`), pending real samples.
 */
class BillParserTest {

    private fun day(y: Int, m: Int, d: Int): Long =
        LocalDate.of(y, m, d).atStartOfDay(ZoneId.of("Asia/Colombo")).toInstant().toEpochMilli()

    @Test
    fun `slt issued bill`() {
        val body = "- Customer name : Mr [NAME]\n- Home Telephone No : 0371234567\n- Invoice No : 26AUG_SLTNA0000000000_0000\n- Bill Period  : 2026-08-01 to 2026-08-31\n- Last month Bal : 12020.47\n- Payment Received : 6000.00\n- Charges for the Period  : 5932.44\n- Total Payable : 11952.91\n- Payment Due date : 2026-09-22\n\nPlease use https://sbill.slt.lk/Public/Bi?iv=XXXX to view the bill.\n\nUse last 4 digits of your mobile number as the password to open the file\nSLT-MOBITEL"
        val bill = BillParser.parse("SLTBILL", body)!!
        assertThat(bill.biller).isEqualTo("SLT-MOBITEL")
        assertThat(bill.kind).isEqualTo(BillKind.ISSUED)
        assertThat(bill.accountRef).isEqualTo("0371234567")
        assertThat(bill.amountDueMinor).isEqualTo(1195291)
        assertThat(bill.dueDateMillis).isEqualTo(day(2026, 9, 22))
        assertThat(bill.periodLabel).isEqualTo("2026-08-01 to 2026-08-31")
    }

    @Test
    fun `slt payment received normalises the international phone to the local form`() {
        val body = "Your bill payment Rs.6000.00 has been received to account number 0050000000 (94371234567) on 2026/08/12:09:30:00 PM.  Thank you."
        val bill = BillParser.parse("SLTBILL", body)!!
        assertThat(bill.kind).isEqualTo(BillKind.PAYMENT_RECEIVED)
        assertThat(bill.paidAmountMinor).isEqualTo(600000)
        assertThat(bill.amountDueMinor).isEqualTo(0)
        assertThat(bill.accountRef).isEqualTo("0371234567")
    }

    @Test
    fun `slt reminder with due date, month label and integer amount`() {
        val body = "Dear customer,Please settle your bill outstanding for SLT-MOBITEL Home no 0371234567 before 08.02.2026 to avoid the automated service disconnection. Your due amount up to month of  December 2025 is Rs. 7236 Pay easily through https://billpay.slt.lk/. Ignore if already paid. Thank you"
        val bill = BillParser.parse("SLTMOBITEL", body)!!
        assertThat(bill.kind).isEqualTo(BillKind.REMINDER)
        assertThat(bill.amountDueMinor).isEqualTo(723600)
        assertThat(bill.dueDateMillis).isEqualTo(day(2026, 2, 8))
        assertThat(bill.periodLabel).isEqualTo("December 2025")
        assertThat(bill.accountRef).isEqualTo("0371234567")
    }

    @Test
    fun `slt due-soon nudge without a date`() {
        val body = "Dear customer,\nYour due date is reaching. SLT-MOBITEL Home bill 0371234567 payable is Rs.5890. You can pay your bill easily by https://billpay.slt.lk/ MySLT app, mCash, scanning the QR code, or through other digital payment channels. Ignore if already paid. Thank you"
        val bill = BillParser.parse("SLTMOBITEL", body)!!
        assertThat(bill.kind).isEqualTo(BillKind.REMINDER)
        assertThat(bill.amountDueMinor).isEqualTo(589000)
        assertThat(bill.dueDateMillis).isNull()
    }

    @Test
    fun `slt overdue`() {
        val body = "Dear Customer,\nYour SLT-MOBITEL Home bill 0371234567 of Rs. 6088.03 is overdue. Please note that late payment charges have been applied to your account. To settle your outstanding balance, please pay through https://billpay.slt.lk/ MySLT app, mCash, QR code scanning, or other digital channels. Ignore if already paid. Thank you"
        val bill = BillParser.parse("SLTMOBITEL", body)!!
        assertThat(bill.kind).isEqualTo(BillKind.OVERDUE)
        assertThat(bill.amountDueMinor).isEqualTo(608803)
    }

    @Test
    fun `slt promos, surveys and service notices are not bills`() {
        listOf(
            "SLT-MOBITEL FIBRE!\n\nIntroducing new unlimited packages with the fastest, most stable internet.\nExperience the Unlimited Revolution with SLT-MOBITEL Fibre, with packages starting at Rs. 5,900/-\n\nCall 1212 for more information.",
            "Your timely payment earns you a free 24-hour YouTube bundle! Redeem on MySLT App/Portal before 24-MAR-26 as our token of gratitude.",
            "94371234567 service restored. Pls. rate us. Reply FB<space>1-5 (1-Dissatisfied, 5-Excellent) or https://cs.slt.lk/feedback/shortener/XXXX SLT",
            "We kindly invite you to share your feedback to help us serve you better.\n\nTake the survey now:\nhttps://forms.gle/XXXX",
        ).forEach { assertThat(BillParser.parse("SLTMOBITEL", it)).isNull() }
    }

    @Test
    fun `dialog postpaid bill _reconstructed`() {
        val body = "Dialog Mobile bill for 0771234567. Bill period: From 01-Aug-26 To 31-Aug-26. Bill value for the period: Rs2,450.00. Bill due date: 20-Sep-26. Total outstanding as at 05-Sep-26: Rs2,450.00. Pay via MyDialog."
        val bill = BillParser.parse("Dialog", body)!!
        assertThat(bill.biller).isEqualTo("Dialog")
        assertThat(bill.kind).isEqualTo(BillKind.ISSUED)
        assertThat(bill.accountRef).isEqualTo("0771234567")
        assertThat(bill.amountDueMinor).isEqualTo(245000)
        assertThat(bill.dueDateMillis).isEqualTo(day(2026, 9, 20))
        assertThat(bill.periodLabel).isEqualTo("01-Aug-26 to 31-Aug-26")
    }

    @Test
    fun `dialog promos in Sinhala are not bills`() {
        assertThat(BillParser.parse("Dialog", "හිතවත් පාරිභෝගිකය, Dialog 5G Ultra ජාලය අත්විදින්න https://dialog.lk/5G")).isNull()
        assertThat(BillParser.parse("Dialog", "Dear Customer, Manage all your Dialog connections instantly via the MyDialog App.")).isNull()
    }

    @Test
    fun `ceb electricity bill _reconstructed`() {
        val body = "CEB e-Bill. A/C No: 4326011203. Reading Date: 2026-09-02. Monthly Bill: Rs. 3,450.00. Total Due: Rs. 3,450.00. Please pay by 2026-09-20 to avoid disconnection."
        val bill = BillParser.parse("CEB", body)!!
        assertThat(bill.biller).isEqualTo("CEB")
        assertThat(bill.accountRef).isEqualTo("4326011203")
        assertThat(bill.amountDueMinor).isEqualTo(345000)
        assertThat(bill.dueDateMillis).isEqualTo(day(2026, 9, 20))
        assertThat(bill.periodLabel).isEqualTo("Reading 2026-09-02")
    }

    @Test
    fun `nwsdb water bill _reconstructed`() {
        val body = "NWSDB Water Bill. A/C No : 12/34/567/890. Period : 01-08-2026 to 31-08-2026. Monthly Charges : Rs. 1,250.00. Total Due : Rs. 1,250.00. Due Date : 25-09-2026"
        val bill = BillParser.parse("NWSDB", body)!!
        assertThat(bill.biller).isEqualTo("NWSDB")
        assertThat(bill.accountRef).isEqualTo("12/34/567/890")
        assertThat(bill.amountDueMinor).isEqualTo(125000)
        assertThat(bill.dueDateMillis).isEqualTo(day(2026, 9, 25))
        assertThat(bill.periodLabel).isEqualTo("01-08-2026 to 31-08-2026")
    }

    @Test
    fun `bank senders are never bills`() {
        assertThat(BillParser.parse("BOC", "Total Payable : 100.00 Home Telephone No : 0371234567")).isNull()
    }

    @Test
    fun `slt statement in credit keeps its negative total`() {
        val body = "- Customer name : Mr [NAME]\n- Home Telephone No : 0371234567\n- Invoice No : 26AUG_SLTNA0000000000_0000\n- Bill Period  : 2026-08-01 to 2026-08-31\n- Last month Bal : 0.00\n- Payment Received : 6500.00\n- Charges for the Period  : 6000.00\n- Total Payable : -500.00\n- Payment Due date : 2026-09-22\n\nPlease use https://sbill.slt.lk/Public/Bi?iv=XXXX to view the bill.\nSLT-MOBITEL"
        val bill = BillParser.parse("SLTBILL", body)!!
        assertThat(bill.kind).isEqualTo(BillKind.ISSUED)
        // Kept negative on purpose: the ingestor treats a statement at or below zero as settled.
        assertThat(bill.amountDueMinor).isEqualTo(-50_000L)
    }
}
