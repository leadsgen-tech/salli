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
    )
}
