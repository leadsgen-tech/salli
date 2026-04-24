package lk.salli.parser.fixtures

import lk.salli.domain.Currency
import lk.salli.domain.TransactionFlow
import lk.salli.domain.TransactionType

/**
 * Redacted samples from People's Bank (sender `PeoplesBank`).
 *
 * Three main shapes:
 *  1. Primary debit/credit: "Dear Sir/Madam, Your A/C <mask> has been <debited|credited> by
 *     Rs. <amount> (<TYPE> @<time> <date>[ at <location>]).[Av_Bal: Rs. <bal> <suffix>]"
 *     — case of "debited/credited/Debited/Credited" varies across samples.
 *     — Av_Bal is optional (some CDM samples omit it entirely).
 *     — "at <location>" is present for POS / ATM / CDM, absent for LPAY / PeoPAY.
 *     — sometimes no space before the opening paren, e.g. "Rs. 100.00(Cash payment …)".
 *
 *  2. Mobile Payment (separate confirmation of a completed reload/bill):
 *     "Mobile Payment Successful, LKR <amount> to <merchant> Ref No <ref> on <datetime>. Call 1961"
 *
 *  3. Fund Transfer (separate confirmation of a P2P/inter-bank transfer):
 *     "Fund transfer  Successful. LKR <amount> to <recipient> Account <acctnum> on <datetime>. Call 1961"
 *     (note the double space after "transfer")
 *
 * Plus pure-informational variants: login alerts, failed-login alerts, multilingual OTPs.
 */
object PeoplesBankFixtures {
    val cases: List<ParseCase> = listOf(
        // --- Primary debit/credit template ---
        ParseCase(
            label = "peoples_lpay_debit_with_balance",
            sender = "PeoplesBank",
            body = "Dear Sir/Madam, Your A/C 280-2001****68 has been debited by Rs. 500.25 (LPAY Tfr @08:58 22/04/2026).[Av_Bal: Rs. 1,046.27 at the time of SMS generated]",
            expected = Expectation.Success(
                type = TransactionType.MOBILE_PAYMENT,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 50025,
                currency = Currency.LKR,
                balanceMinor = 104627,
                accountSuffix = "280-2001..68",
            ),
        ),
        // Older People's Bank mask format — same physical account, different asterisk count.
        // The parser normalises both to `280-2001..68` so they collapse to one account.
        ParseCase(
            label = "peoples_lpay_debit_older_mask",
            sender = "PeoplesBank",
            body = "Dear Sir/Madam, Your A/C 280-2001******68 has been debited by Rs. 100.00 (LPAY Tfr @10:00 01/01/2025).[Av_Bal: Rs. 5,000.00 at the time of SMS generated]",
            expected = Expectation.Success(
                type = TransactionType.MOBILE_PAYMENT,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 10000,
                currency = Currency.LKR,
                balanceMinor = 500000,
                accountSuffix = "280-2001..68",
            ),
        ),
        ParseCase(
            label = "peoples_lpay_credit",
            sender = "PeoplesBank",
            body = "Dear Sir/Madam, Your A/C 280-2001****68 has been credited by Rs. 700.50 (LPAY Tfr @13:18 05/04/2026).[Av_Bal: Rs. 9,486.15 at the time of SMS generated]",
            expected = Expectation.Success(
                type = TransactionType.MOBILE_PAYMENT,
                flow = TransactionFlow.INCOME,
                amountMinor = 70050,
                currency = Currency.LKR,
                balanceMinor = 948615,
                accountSuffix = "280-2001..68",
            ),
        ),
        ParseCase(
            label = "peoples_pos_with_location",
            sender = "PeoplesBank",
            body = "Dear Sir/Madam, Your A/C 280-2001****68 has been debited by Rs. 190.00 (POS @20:30 13/04/2026 at Dissanayaka Super Center).[Av_Bal: Rs. 5,852.68 as of SMS]",
            expected = Expectation.Success(
                type = TransactionType.POS,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 19000,
                currency = Currency.LKR,
                balanceMinor = 585268,
                accountSuffix = "280-2001..68",
                merchantRaw = "Dissanayaka Super Center",
                location = "Dissanayaka Super Center",
            ),
        ),
        ParseCase(
            label = "peoples_atm_with_location",
            sender = "PeoplesBank",
            body = "Dear Sir/Madam, Your A/C 280-2001****68 has been debited by Rs. 6,005.00 (ATM @13:34 07/04/2026 at Pothuhera).[Av_Bal: Rs. 5,428.68 as of SMS]",
            expected = Expectation.Success(
                type = TransactionType.ATM,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 600500,
                currency = Currency.LKR,
                balanceMinor = 542868,
                accountSuffix = "280-2001..68",
                location = "Pothuhera",
            ),
        ),
        ParseCase(
            label = "peoples_cdm_credit_with_location",
            sender = "PeoplesBank",
            body = "Dear Sir/Madam, Your A/C 280-2001****68 has been Credited by Rs. 5,000.00 (CDM @13:01 12/04/2026 at Gampola).[Av_Bal: Rs. 6,042.68 as of SMS]",
            expected = Expectation.Success(
                type = TransactionType.CDM,
                flow = TransactionFlow.INCOME,
                amountMinor = 500000,
                currency = Currency.LKR,
                balanceMinor = 604268,
                accountSuffix = "280-2001..68",
                location = "Gampola",
            ),
        ),
        ParseCase(
            label = "peoples_cdm_credit_no_balance",
            sender = "PeoplesBank",
            body = "Dear Sir/Madam, Your A/C 280-2001****68 has been Credited by Rs. 10,000.00 (CDM @22:14 13/04/2026 at Mawanella).",
            expected = Expectation.Success(
                type = TransactionType.CDM,
                flow = TransactionFlow.INCOME,
                amountMinor = 1000000,
                currency = Currency.LKR,
                balanceMinor = null,
                accountSuffix = "280-2001..68",
                location = "Mawanella",
            ),
        ),
        ParseCase(
            label = "peoples_cash_payment_no_space_paren",
            sender = "PeoplesBank",
            body = "Dear Sir/Madam, Your A/C 280-2001****68 has been Credited by Rs. 12,087.03(Cash payment @21:23 05/04/2026).[Av_Bal: Rs. 21,494.68 at SMS generated]",
            expected = Expectation.Success(
                // "Cash payment" phrase maps to CDM (same semantic bucket as a cash deposit
                // at a machine) — see PeoplesBankTemplate.classifyType.
                type = TransactionType.CDM,
                flow = TransactionFlow.INCOME,
                amountMinor = 1208703,
                currency = Currency.LKR,
                balanceMinor = 2149468,
                accountSuffix = "280-2001..68",
            ),
        ),
        ParseCase(
            label = "peoples_peopay_credit",
            sender = "PeoplesBank",
            body = "Dear Sir/Madam, Your A/C 280-2001****68 has been credited by Rs. 500.00 (PeoPAY Tfr @08:55 02/04/2026).[Av_Bal: Rs. 636.15 at the time of SMS generated]",
            expected = Expectation.Success(
                type = TransactionType.MOBILE_PAYMENT,
                flow = TransactionFlow.INCOME,
                amountMinor = 50000,
                currency = Currency.LKR,
                balanceMinor = 63615,
                accountSuffix = "280-2001..68",
            ),
        ),
        // --- Mobile Payment confirms ---
        ParseCase(
            label = "peoples_mobile_payment_mobitel",
            sender = "PeoplesBank",
            body = "Mobile Payment Successful, LKR 100.00 to Mobitel Ref No [PHONE] on 2026-04-15 21:53:17. Call 1961",
            expected = Expectation.Success(
                type = TransactionType.MOBILE_PAYMENT,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 10000,
                currency = Currency.LKR,
                merchantRaw = "Mobitel",
            ),
        ),
        ParseCase(
            label = "peoples_mobile_payment_dialog",
            sender = "PeoplesBank",
            body = "Mobile Payment Successful, LKR 385.00 to Dialog  Ref No [PHONE] on 2026-04-06 20:47:21. Call 1961",
            expected = Expectation.Success(
                type = TransactionType.MOBILE_PAYMENT,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 38500,
                currency = Currency.LKR,
                merchantRaw = "Dialog",
            ),
        ),
        // --- Fund Transfer confirms ---
        ParseCase(
            label = "peoples_fund_transfer_other_bank",
            sender = "PeoplesBank",
            body = "Fund transfer  Successful. LKR 50,000.00 to LOLC Finance PLC Account 20810007495 on 2026-04-22 08:58:39. Call 1961",
            expected = Expectation.Success(
                type = TransactionType.ONLINE_TRANSFER,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 5000000,
                currency = Currency.LKR,
                merchantRaw = "LOLC Finance PLC",
            ),
        ),
        ParseCase(
            label = "peoples_fund_transfer_boc",
            sender = "PeoplesBank",
            body = "Fund transfer  Successful. LKR 5,000.00 to Bank Of Ceylon - BOC Account 93637870 on 2026-03-30 18:12:09. Call 1961",
            expected = Expectation.Success(
                type = TransactionType.ONLINE_TRANSFER,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 500000,
                currency = Currency.LKR,
                merchantRaw = "Bank Of Ceylon - BOC",
            ),
        ),
        // --- Informational (must be rejected) ---
        ParseCase(
            label = "peoples_login_alert",
            sender = "PeoplesBank",
            body = "Dear MR [NAME], You have logged in to People's Pay App on 2026-04-22 08:57:43. Not you? Call us on 1961.",
            expected = Expectation.Informational("people's pay login alert"),
        ),
        ParseCase(
            label = "peoples_login_failed",
            sender = "PeoplesBank",
            body = "Dear MR [NAME], Your attempt to log in to People's Pay App failed. For inquiries, Call us on 1961.",
            expected = Expectation.Informational("people's pay login failed"),
        ),
        ParseCase(
            label = "peoples_otp_multilingual",
            sender = "PeoplesBank",
            body = "Please don't share this with anyone. මෙය කිසිවෙකු වෙත ලබා නොදෙන්න. இதை யாருடனும் பகிர வேண்டாம். use 508926 as OTP. [for People's Pay transaction] Didn't request? Call 1961.",
            expected = Expectation.Otp,
        ),
    )
}
