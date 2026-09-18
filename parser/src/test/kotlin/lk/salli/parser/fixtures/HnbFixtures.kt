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
        ParseCase(
            label = "hnb_payment_notice_is_not_a_transaction",
            sender = "HNB",
            body = "You received LKR 10,000 from [NAME]\nOTP අංකය හෝ රහස්‍ය තොරතුරු කිසිවෙකුටවත් ලබා නොදෙන්න.\nOTP மற்றும் முக்கியமான தகவல்களை எவருடனும் பகிர வேண்டாம்.\nDo not share OTP & sensitive information with anyone.",
            expected = Expectation.Informational(),
        ),
        ParseCase(
            // A notice, not an OTP: a four-digit amount near the word OTP must not trip the guard.
            label = "hnb_payment_notice_four_digit_amount_not_otp",
            sender = "HNB",
            body = "You received LKR 5000 from [NAME]\nමෙම අංකය කිසිවෙකුට නොකියන්න.\nDo not share OTP & sensitive information with anyone.",
            expected = Expectation.Informational(),
        ),
        ParseCase(
            label = "hnb_credit_card_alert_reconstructed",
            sender = "HNB",
            body = "HNB Credit Card **1234 :KEELLS SUPER (Apprx) LKR 3,450.00 (14-Sep-2026 06:42:10 PM) Av.Bal : LKR 120,550.00",
            expected = Expectation.Success(
                type = TransactionType.POS,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 345000,
                currency = Currency.LKR,
                balanceMinor = 12055000,
                accountSuffix = "1234",
                merchantRaw = "KEELLS SUPER",
            ),
        ),
        ParseCase(
            label = "hnb_card_declined_lkr",
            sender = "HNB",
            body = "HNB SMS ALERT:Transaction for Amt(Approx.):  560.00 LKR declined due to insufficient funds.Please attempt when sufficient funds are available. Account: 0190***5845,Location: PickMe Ride, LK,Avl Bal: 276.02 LKR,Date: 24.07.26, Time:06:54,Hotline:[PHONE]",
            expected = Expectation.Success(
                type = TransactionType.DECLINED,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 56_000,
                currency = Currency.LKR,
                balanceMinor = 27_602,
                accountSuffix = "0190***5845",
                merchantRaw = "PickMe Ride",
                isDeclined = true,
            ),
        ),
        ParseCase(
            label = "hnb_card_declined_usd_negative_balance",
            sender = "HNB",
            body = "HNB SMS ALERT:Transaction for Amt(Approx.):  1.00 USD declined due to insufficient funds.Please attempt when sufficient funds are available. Account: 0190***5845,Location: PAYPAL, LU,Avl Bal: -23.98 LKR,Date: 14.06.26, Time:20:42,Hotline:[PHONE]",
            expected = Expectation.Success(
                type = TransactionType.DECLINED,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 100,
                currency = Currency.USD,
                accountSuffix = "0190***5845",
                merchantRaw = "PAYPAL",
                isDeclined = true,
            ),
        ),
        ParseCase(
            label = "hnb_card_purchase_debit_account_spelling",
            sender = "HNB",
            body = "HNB SMS ALERT: PURCHASE, Debit account:0190***5845,Location:LANKA FILLING STATION, LK,Amount(Approx.):520.00 LKR,Av.Bal:945.00 LKR,Date:28.04.26,Time:00:37, Hot Line:[PHONE]",
            expected = Expectation.Success(
                type = TransactionType.POS,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 52_000,
                currency = Currency.LKR,
                balanceMinor = 94_500,
                accountSuffix = "0190***5845",
                merchantRaw = "LANKA FILLING STATION",
            ),
        ),
        ParseCase(
            label = "hnb_fx_markup_refund_is_credited",
            sender = "HNB",
            body = "A Transaction for LKR 410.90 has been credit ed to Ac No:01902XXXXX45 on 24/06/26 13:54:03 . Remarks :FX MARKUP_37213.91 06/14.Bal: LKR 624.91",
            expected = Expectation.Success(
                type = TransactionType.FEE,
                flow = TransactionFlow.INCOME,
                amountMinor = 41_090,
                currency = Currency.LKR,
                balanceMinor = 62_491,
                accountSuffix = "01902XXXXX45",
                merchantRaw = "FX MARKUP_37213.91 06/14",
            ),
        ),
        ParseCase(
            label = "hnb_charge_with_zero_balance",
            sender = "HNB",
            body = "A Transaction for LKR 1.02 has been debit ed to Ac No:01902XXXXX45 on 04/07/26 07:37:54 . Remarks :Finacle Alert Charges.Bal: LKR .00",
            expected = Expectation.Success(
                type = TransactionType.FEE,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 102,
                currency = Currency.LKR,
                balanceMinor = 0,
                accountSuffix = "01902XXXXX45",
                merchantRaw = "Finacle Alert Charges",
            ),
        ),
        ParseCase(
            label = "hnb_interest_under_a_rupee",
            sender = "HNB",
            body = "LKR .41 credited to Ac No:01902XXXXX45 on 29/07/26 22:20:19 Reason:01902XXXXX45:Int.Pd:XX-XX-2026 to XX-XX-2026 Bal:LKR 276.43 Protect from scams *DO NOT SHARE ACCOUNT DETAILS /OTP* Hotline [PHONE]",
            expected = Expectation.Success(
                type = TransactionType.OTHER,
                flow = TransactionFlow.INCOME,
                amountMinor = 41,
                currency = Currency.LKR,
                balanceMinor = 27_643,
                accountSuffix = "01902XXXXX45",
                merchantRaw = "01902XXXXX45:Int.Pd:XX-XX-2026 to XX-XX-2026",
            ),
        ),
        ParseCase(
            label = "hnb_transaction_reversal_is_income",
            sender = "HNB",
            body = "TRANSACTION REVERSAL, Credit account:0190***5845,Location:PickMe Ride, LK,Amount:110.00 LKR,Av.Bal:276.02 LKR,Date:09.07.26,Time:14:31, Hot Line:[PHONE]",
            expected = Expectation.Success(
                type = TransactionType.OTHER,
                flow = TransactionFlow.INCOME,
                amountMinor = 11_000,
                currency = Currency.LKR,
                balanceMinor = 27_602,
                accountSuffix = "0190***5845",
                merchantRaw = "PickMe Ride",
            ),
        ),
        ParseCase(
            label = "hnb_charge_alert_cut_short",
            sender = "HNB",
            body = "A Transaction for LKR 1.02 has been debit ed to Ac No:01902XXXXX45 on 04/07/26 07:37:54 .",
            expected = Expectation.Success(
                type = TransactionType.FEE,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 102,
                currency = Currency.LKR,
                accountSuffix = "01902XXXXX45",
            ),
        ),
    )
}
