package lk.salli.parser.fixtures

import lk.salli.domain.Currency
import lk.salli.domain.TransactionFlow
import lk.salli.domain.TransactionType

/**
 * Redacted samples from Seylan Bank (sender `SEYLAN` — alt `SEYLANBANK`).
 *
 * Card-level, one canonical debit shape. Variants we need to survive:
 *  - Merchant with location + country code (`KEELLS … KURUNEGALA   LK`)
 *  - Merchant only, no location (`DEBIT CARD ANNUAL FEES.`)
 *  - ATM-at-other-bank (merchant = "Peoples Bank", etc.) — classified as ATM
 *  - Login-notification informational SMS (rejected)
 */
object SeylanFixtures {
    val cases: List<ParseCase> = listOf(
        ParseCase(
            label = "seylan_pos_normal",
            sender = "SEYLAN",
            body = "Seylan Card ...3687 debit Txn 9566324039 of LKR 5,000.00 done on 19/12/2025 04:18:36 PM at Nippon Bag City          KURUNEGALA   LK. Avl bal 2,505.03",
            expected = Expectation.Success(
                type = TransactionType.POS,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 500000,
                currency = Currency.LKR,
                balanceMinor = 250503,
                accountSuffix = "#3687",
                merchantRaw = "Nippon Bag City",
                location = "KURUNEGALA",
            ),
        ),
        ParseCase(
            label = "seylan_pos_cargills",
            sender = "SEYLAN",
            body = "Seylan Card ...3687 debit Txn 9560025721 of LKR 2,870.00 done on 17/12/2025 01:53:31 PM at CARGILLS EXPRESS-YANTHAM KURUNEGALA   LK. Avl bal 7,565.03",
            expected = Expectation.Success(
                type = TransactionType.POS,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 287000,
                currency = Currency.LKR,
                balanceMinor = 756503,
                accountSuffix = "#3687",
                merchantRaw = "CARGILLS EXPRESS-YANTHAM",
                location = "KURUNEGALA",
            ),
        ),
        ParseCase(
            label = "seylan_atm_peoples_bank",
            sender = "SEYLAN",
            body = "Seylan Card ...3687 debit Txn 9566119213 of LKR 12,000.00 done on 19/12/2025 03:02:03 PM at Peoples Bank           Kurunegala Ra  LK. Avl bal 505.03",
            expected = Expectation.Success(
                type = TransactionType.ATM,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 1200000,
                currency = Currency.LKR,
                balanceMinor = 50503,
                accountSuffix = "#3687",
            ),
        ),
        ParseCase(
            label = "seylan_atm_crm",
            sender = "SEYLAN",
            body = "Seylan Card ...3687 debit Txn 9334531157 of LKR 20,000.00 done on 25/09/2025 10:28:32 AM at POLGAHAW-CRM1 BR       POLGAHAWELA    LK. Avl bal 74,975.23",
            expected = Expectation.Success(
                type = TransactionType.ATM,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 2000000,
                currency = Currency.LKR,
                balanceMinor = 7497523,
                accountSuffix = "#3687",
            ),
        ),
        ParseCase(
            label = "seylan_atm_icbs",
            sender = "SEYLAN",
            body = "Seylan Card ...3687 debit Txn 9318206370 of LKR 8,000.00 done on 19/09/2025 07:07:53 AM at ICBS                   POTHUHERA      LK. Avl bal 153,585.23",
            expected = Expectation.Success(
                type = TransactionType.ATM,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 800000,
                currency = Currency.LKR,
                balanceMinor = 15358523,
                accountSuffix = "#3687",
            ),
        ),
        ParseCase(
            label = "seylan_card_annual_fees",
            sender = "SEYLAN",
            body = "Seylan Card ...3687 debit Txn 9445628494 of LKR 500.00 done on 06/11/2025 12:08:29 AM at DEBIT CARD ANNUAL FEES. Avl bal 507.43",
            expected = Expectation.Success(
                type = TransactionType.FEE,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 50000,
                currency = Currency.LKR,
                balanceMinor = 50743,
                accountSuffix = "#3687",
            ),
        ),
        ParseCase(
            label = "seylan_login_informational",
            sender = "SEYLAN",
            body = "Dear MR [NAME], You have logged in to Seylan Online Banking/Mobile Banking at 09:45 Hrs. (+5.30 GMT) on 2025-09-25 Not you? Call us on +94112008888",
            expected = Expectation.Informational(),
        ),
    )
}
