package lk.salli.parser.fixtures

import lk.salli.domain.Currency
import lk.salli.domain.TransactionFlow
import lk.salli.domain.TransactionType

/**
 * Redacted samples from HNB (sender `HNB`).
 *
 * Four SMS shapes + the OTP reject:
 *  - Account debit / credit, single-line, classified by the `Reason:` tag (MB/CEFT/…)
 *  - Fee-charge alert ("A Transaction for LKR … has been debit ed …") — typo in HNB's text
 *  - Card SMS alert — supports LKR and foreign-currency amounts (balance dropped on FX)
 *  - Multi-line ATM e-Receipt
 *  - OTP with verification code (caught by global OtpGuard)
 */
object HnbFixtures {
    val cases: List<ParseCase> = listOf(
        ParseCase(
            label = "hnb_otp_mb_transfer",
            sender = "HNB",
            body = "Your verification code is 123456. Use this to verify your transfer with HNB Digital Banking\nමෙම අංකය කිසිවෙකුට නොකියන්න.\nஇந்த இலக்கத்தை எவருடனும் பகிர வேண்டாம்.\n Do not share this number with anyone",
            expected = Expectation.Otp,
        ),
        ParseCase(
            label = "hnb_mb_debit",
            sender = "HNB",
            body = "LKR 10,025.00 debited to Ac No:01902XXXXX33 on 04/09/25 17:20:15 Reason:MB:Sadah Bal:LKR 6,597.54 Protect from scams *DO NOT SHARE ACCOUNT DETAILS /OTP* Hotline 0112462462",
            expected = Expectation.Success(
                type = TransactionType.ONLINE_TRANSFER,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 1002500,
                currency = Currency.LKR,
                balanceMinor = 659754,
                accountSuffix = "01902XXXXX33",
                merchantRaw = "MB:Sadah",
            ),
        ),
        ParseCase(
            label = "hnb_fee_alert_charge",
            sender = "HNB",
            body = "A Transaction for LKR 25.00 has been debit ed to Ac No:01902XXXXX33 on 05/09/25 05:31:18 .\nRemarks :HNB Alert Charges.Bal: LKR 6,572.54",
            expected = Expectation.Success(
                type = TransactionType.FEE,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 2500,
                currency = Currency.LKR,
                balanceMinor = 657254,
                accountSuffix = "01902XXXXX33",
                merchantRaw = "HNB Alert Charges",
            ),
        ),
        ParseCase(
            label = "hnb_ceft_credit",
            sender = "HNB",
            body = "LKR 6,000.00 credited to Ac No:01902XXXXX33 on 05/09/25 11:45:55 Reason:CEFT-[NAME] Bal:LKR 12,572.54 Protect from scams *DO NOT SHARE ACCOUNT DETAILS /OTP* Hotline 0112462462",
            expected = Expectation.Success(
                type = TransactionType.CEFT,
                flow = TransactionFlow.INCOME,
                amountMinor = 600000,
                currency = Currency.LKR,
                balanceMinor = 1257254,
                accountSuffix = "01902XXXXX33",
                merchantRaw = "CEFT-[NAME]",
            ),
        ),
        ParseCase(
            label = "hnb_card_alert_usd",
            sender = "HNB",
            body = "HNB SMS ALERT:INTERNET, Account:0190***4833,Location:SURFSHARK* SURFSHARK., US,Amount(Approx.):53.42 USD,Av.Bal:1791.36 LKR,Date:05.09.25,Time:11:51, Hot Line:0112462462",
            expected = Expectation.Success(
                type = TransactionType.POS,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 5342,
                currency = Currency.USD,
                // FX row → balance dropped (can't store LKR balance alongside USD amount).
                balanceMinor = null,
                accountSuffix = "0190***4833",
                merchantRaw = "SURFSHARK* SURFSHARK.",
            ),
        ),
        ParseCase(
            label = "hnb_card_alert_lkr",
            sender = "HNB",
            body = "HNB SMS ALERT:INTERNET, Account:0190***4554,Location:PH *DOMINOS PIZZA SRI, LK,Amount(Approx.):2427.00 LKR,Av.Bal:6.64 LKR,Date:23.04.26,Time:17:50, Hot Line:0112462462",
            expected = Expectation.Success(
                type = TransactionType.POS,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 242700,
                currency = Currency.LKR,
                balanceMinor = 664,
                accountSuffix = "0190***4554",
                merchantRaw = "PH *DOMINOS PIZZA SRI",
            ),
        ),
        ParseCase(
            label = "hnb_atm_withdrawal",
            sender = "HNB",
            body = "HNB ATM Withdrawal e-Receipt\nAmt(Approx.):  15000.00 LKR\nA/C: 0190***4833\nTxn Fee: 30.00LKR\nLocation: PEOPLE'S BANK         , LKA\nTerm ID: 0280AB01\nDate: 02.06.25 Time:17:05\nTxn No: 2503920984\nAvl Bal: 4592.86 LKR\nHotline:94112462462",
            expected = Expectation.Success(
                type = TransactionType.ATM,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 1500000,
                currency = Currency.LKR,
                balanceMinor = 459286,
                feeMinor = 3000,
                accountSuffix = "0190***4833",
                location = "PEOPLE'S BANK",
            ),
        ),
        ParseCase(
            label = "hnb_atm_withdrawal_hnb_crm",
            sender = "HNB",
            body = "HNB ATM Withdrawal e-Receipt\nAmt(Approx.):  2000.00 LKR\nA/C: 0190***4554\nTxn Fee: 5.00LKR\nLocation: HNB MALLAWAPITIYA CRM 01, LKA\nTerm ID: 888277\nDate: 24.04.26 Time:10:15\nTxn No: 3042522171\nAvl Bal: 1001.64 LKR\nHotline:94112462462",
            expected = Expectation.Success(
                type = TransactionType.ATM,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 200000,
                currency = Currency.LKR,
                balanceMinor = 100164,
                feeMinor = 500,
                accountSuffix = "0190***4554",
                location = "HNB MALLAWAPITIYA CRM 01",
            ),
        ),
    )
}
