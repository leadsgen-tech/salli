package lk.salli.parser.fixtures

import lk.salli.domain.Currency
import lk.salli.domain.TransactionFlow
import lk.salli.domain.TransactionType

/**
 * Redacted samples from Commercial Bank (sender `COMBANK`).
 *
 * Three templates:
 *  - Successful purchase (has `authorised on your debit card ending #<last4>`)
 *  - Declined (has `declined due to insufficient funds`, money did NOT move)
 *  - OTP for online transaction (caught by global OtpGuard)
 *
 * Unique traits:
 *  - Multi-currency. Observed LKR and USD; pattern should tolerate any ISO code.
 *  - Date format `DD/MM/YY`, time `HH:MM AM/PM` (12-hour).
 *  - No `Balance available` — we never get a balance from ComBank.
 *  - Card-level (`#4273`), not account-level. Store as `accountSuffix = "#<last4>"` or similar.
 *
 * Declined attempts are **not** deductions but still worth storing (flagged [isDeclined]) —
 * grouping by merchant reveals the user's recurring subscriptions that keep failing.
 */
object CombankFixtures {
    val cases: List<ParseCase> = listOf(
        ParseCase(
            label = "combank_purchase_usd",
            sender = "COMBANK",
            body = "Dear Cardholder, Purchase at APPLE.COM/BILL SINGAPORE SG for USD 15.99 on 08/03/26 05:09 PM has been authorised on your debit card ending #4273.",
            expected = Expectation.Success(
                type = TransactionType.POS,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 1599,
                currency = Currency.USD,
                merchantRaw = "APPLE.COM/BILL SINGAPORE SG",
                accountSuffix = "#4273",
            ),
        ),
        ParseCase(
            label = "combank_purchase_lkr",
            sender = "COMBANK",
            body = "Dear Cardholder, Purchase at KEELLS SUPER COLOMBO 04 for LKR 3,450.00 on 15/04/26 10:24 AM has been authorised on your debit card ending #4273.",
            expected = Expectation.Success(
                type = TransactionType.POS,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 345000,
                currency = Currency.LKR,
                merchantRaw = "KEELLS SUPER COLOMBO 04",
                accountSuffix = "#4273",
            ),
        ),
        ParseCase(
            label = "combank_decline_usd",
            sender = "COMBANK",
            body = "Dear Cardholder, your card was declined due to insufficient funds. The attempted transaction amount was USD 15.99 at APPLE.COM/BILL SINGAPORE SG on 08/03/26 04:43 PM. Please check your balance and try again.",
            expected = Expectation.Success(
                type = TransactionType.DECLINED,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 1599,
                currency = Currency.USD,
                merchantRaw = "APPLE.COM/BILL SINGAPORE SG",
                isDeclined = true,
            ),
        ),
        ParseCase(
            label = "combank_decline_lkr",
            sender = "COMBANK",
            body = "Dear Cardholder, your card was declined due to insufficient funds. The attempted transaction amount was LKR 275.00 at Google CapCut Video E 650-2530000 US on 26/02/26 11:43 PM. Please check your balance and try again.",
            expected = Expectation.Success(
                type = TransactionType.DECLINED,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 27500,
                currency = Currency.LKR,
                merchantRaw = "Google CapCut Video E 650-2530000 US",
                isDeclined = true,
            ),
        ),
        ParseCase(
            label = "combank_decline_large_lkr",
            sender = "COMBANK",
            body = "Dear Cardholder, your card was declined due to insufficient funds. The attempted transaction amount was LKR 8,236.21 at DONELY, INC. [PHONE] US on 22/04/26 01:29 PM. Please check your balance and try again.",
            expected = Expectation.Success(
                type = TransactionType.DECLINED,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 823621,
                currency = Currency.LKR,
                merchantRaw = "DONELY, INC. [PHONE] US",
                isDeclined = true,
            ),
        ),
        ParseCase(
            label = "combank_otp_online_with_amount",
            sender = "COMBANK",
            body = "Dear Cardholder, please verify the merchant name and transaction amount before entering the One Time Password (OTP). Keep your OTP confidential and do not share it with anyone. Your OTP at Merchant 'SOURCEGRAPH AMPCODE' for USD 0.00 is 125610",
            expected = Expectation.Otp,
        ),
        ParseCase(
            label = "combank_card_withdrawal",
            sender = "COMBANK",
            body = "Withdrawal at POLGAHAWELA-1 BR POLGAHAWELA KGLK for LKR 25,000.00 on 01/03/26 10:12 AM from card ending #5308. Click link to view the Digital Receipt for Withdrawal performed at ComBank ATMs  https://vas.combank.net/rec/abcdef",
            expected = Expectation.Success(
                type = TransactionType.ATM,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 2500000,
                currency = Currency.LKR,
                accountSuffix = "#5308",
                location = "POLGAHAWELA-1 BR POLGAHAWELA KGLK",
            ),
        ),
        ParseCase(
            label = "combank_q_account_credit",
            sender = "ComBank_Q+",
            body = "Credit for Rs. 6,000.00 to 8025675326 at 13:52 at DIGITAL BANKING DIVISION",
            expected = Expectation.Success(
                type = TransactionType.ONLINE_TRANSFER,
                flow = TransactionFlow.INCOME,
                amountMinor = 600000,
                currency = Currency.LKR,
                accountSuffix = "8025675326",
                merchantRaw = "DIGITAL BANKING DIVISION",
            ),
        ),
        ParseCase(
            label = "combank_q_account_credit_large",
            sender = "ComBank_Q+",
            body = "Credit for Rs. 247,330.00 to 8025675326 at 22:43 at DIGITAL BANKING DIVISION",
            expected = Expectation.Success(
                type = TransactionType.ONLINE_TRANSFER,
                flow = TransactionFlow.INCOME,
                amountMinor = 24733000,
                currency = Currency.LKR,
                accountSuffix = "8025675326",
                merchantRaw = "DIGITAL BANKING DIVISION",
            ),
        ),
        ParseCase(
            label = "combank_q_crm_deposit",
            sender = "ComBank_Q+",
            body = "We wish to confirm a CRM Deposit at 16:57 for Rs. 150,000.00 through POLGAHAW-CRM1 BR to your account 8*****5326",
            expected = Expectation.Success(
                type = TransactionType.CDM,
                flow = TransactionFlow.INCOME,
                amountMinor = 15000000,
                currency = Currency.LKR,
                accountSuffix = "8*****5326",
                location = "POLGAHAW-CRM1 BR",
            ),
        ),
        ParseCase(
            // Real-world: ComBank appends a receipt URL to the CRM deposit body. The original
            // regex's trailing `\s*$` after the account token rejected anything after it,
            // so 2 of these landed in Unknown despite being a known shape.
            label = "combank_q_crm_deposit_with_receipt_url",
            sender = "ComBank_Q+",
            body = "We wish to confirm a CRM Deposit at 16:59 for Rs. 11,000.00 through KURUN'MA-CRM3 BR to your account 8*****5326 https://vas.combank.net/rec/abcdef",
            expected = Expectation.Success(
                type = TransactionType.CDM,
                flow = TransactionFlow.INCOME,
                amountMinor = 1100000,
                currency = Currency.LKR,
                accountSuffix = "8*****5326",
                location = "KURUN'MA-CRM3 BR",
            ),
        ),
        ParseCase(
            label = "combank_q_dormant_reminder",
            sender = "ComBank_Q+",
            body = "Dear Customer , We noticed that you have not logged into your ComBank  Digital account for the past 30 days. You can conveniently access your Bank accounts by logging in to ComBank Digital online banking service from wherever you are, 24 hours a day.",
            expected = Expectation.Informational(),
        ),
        ParseCase(
            label = "combank_fund_transfer_to_named_recipient",
            sender = "COMBANK",
            body = "Fund transfer request [REF] P 2026-04-16 at 11:47:41 via Combank Online to [NAME] for LKR 15000.00 Inquiries+[PHONE].",
            expected = Expectation.Success(
                type = TransactionType.ONLINE_TRANSFER,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 1500000,
                currency = Currency.LKR,
                merchantRaw = "[NAME]",
            ),
        ),
        ParseCase(
            label = "combank_fund_transfer_small_amount",
            sender = "COMBANK",
            body = "Fund transfer request [REF] P 2026-03-28 at 10:36:04 via Combank Online to [NAME] for LKR 400.00 Inquiries+[PHONE].",
            expected = Expectation.Success(
                type = TransactionType.ONLINE_TRANSFER,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 40000,
                currency = Currency.LKR,
                merchantRaw = "[NAME]",
            ),
        ),
        ParseCase(
            label = "combank_bill_payment_received",
            sender = "COMBANK",
            body = "Bill Payment in the amount of LKR 547.00 was received on 31/03/2026 from the ComBank Digital.",
            expected = Expectation.Success(
                type = TransactionType.MOBILE_PAYMENT,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 54700,
                currency = Currency.LKR,
                merchantRaw = "ComBank Digital",
            ),
        ),
        ParseCase(
            label = "combank_bill_payment_effected_no_amount",
            sender = "COMBANK",
            body = "Dialog Axiata PLC - Mobile  bill payment effected through Combank Digital.  Status - Completed. Reference  [REF]    For inquiries +[PHONE]",
            expected = Expectation.Informational(),
        ),
        ParseCase(
            label = "combank_login_failed",
            sender = "COMBANK",
            body = "Dear Customer, your attempt to login to ComBank Digital failed. For inquiries +[PHONE]",
            expected = Expectation.Informational(),
        ),
        ParseCase(
            label = "combank_scheduled_maintenance",
            sender = "COMBANK",
            body = "Dear Customer, a scheduled system maintenance will take place on Sunday, 18th May 1:00AM to 5:30AM SL time. During this time, you may experience interruptions on all Credit and Debit Card transactions, Digital Banking & ATM services. We regret any inconvenience caused.",
            expected = Expectation.Informational(),
        ),
        ParseCase(
            label = "combank_domestic_payment_rejected",
            sender = "COMBANK",
            body = "Your Domestic Payment of Rs 300.00 to account [REF] has been rejected due to technical failure. Inquiries +[PHONE]",
            expected = Expectation.Informational(),
        ),
        ParseCase(
            label = "combank_fund_transfer_rejected",
            sender = "COMBANK",
            body = "Your Fund Transfer reference [REF] has been rejected due to invalid credit account. Inquiries +[PHONE]",
            expected = Expectation.Informational(),
        ),
        // ComBank_Q+ outgoing — Q+ app initiates a top-up / merchant payment.
        ParseCase(
            label = "combank_q_plus_transaction",
            sender = "ComBank_Q+",
            body = "Your Q+ transaction at MOBITEL DATA PLANS for LKR 80.00 has been successful. Reference Number [REF]  (Mobile Number - [PHONE].)",
            expected = Expectation.Success(
                type = TransactionType.MOBILE_PAYMENT,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 8000,
                currency = Currency.LKR,
                merchantRaw = "MOBITEL DATA PLANS",
            ),
        ),
        // ComBank_Q+ outgoing — fund transfer to another account.
        ParseCase(
            label = "combank_q_plus_fund_transfer",
            sender = "ComBank_Q+",
            body = "Dear Customer,Your Fund Transfer of Rs. 1,900.00 to Account Number XXXXXXXXXXX8871 is successful. Reference Number is [REF].",
            expected = Expectation.Success(
                type = TransactionType.ONLINE_TRANSFER,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 190000,
                currency = Currency.LKR,
                merchantRaw = "Account XXXXXXXXXXX8871",
            ),
        ),
    )
}
