package lk.salli.parser.fixtures

import lk.salli.domain.Currency
import lk.salli.domain.TransactionFlow
import lk.salli.domain.TransactionType

/**
 * Redacted samples from BOC (Bank of Ceylon, sender `BOC`).
 *
 * Format observed:
 *   <TYPE> Rs <AMOUNT> <From|To> A/C No <X…NNN>. Balance available Rs <BALANCE> - Thank you for banking with BOC
 *
 * TYPE values seen: ATM Withdrawal, ATM Cash Deposit, Cheque Deposit,
 *   Online Transfer Debit, Online Transfer Credit, CEFT Transfer Debit,
 *   CEFT Transfer Credit, Transfer Credit, Transfer Debit.
 */
object BocFixtures {
    val cases: List<ParseCase> = listOf(
        ParseCase(
            label = "boc_online_transfer_debit",
            sender = "BOC",
            body = "Online Transfer Debit Rs 1,234.56 From A/C No XXXXXXXXXX999. Balance available Rs 9,876.54 - Thank you for banking with BOC",
            expected = Expectation.Success(
                type = TransactionType.ONLINE_TRANSFER,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 123456,
                currency = Currency.LKR,
                balanceMinor = 987654,
                accountSuffix = "999",
            ),
        ),
        ParseCase(
            label = "boc_online_transfer_credit",
            sender = "BOC",
            body = "Online Transfer Credit Rs 5,000.00 To A/C No XXXXXXXXXX999. Balance available Rs 14,876.54 - Thank you for banking with BOC",
            expected = Expectation.Success(
                type = TransactionType.ONLINE_TRANSFER,
                flow = TransactionFlow.INCOME,
                amountMinor = 500000,
                currency = Currency.LKR,
                balanceMinor = 1487654,
                accountSuffix = "999",
            ),
        ),
        ParseCase(
            label = "boc_ceft_transfer_debit",
            sender = "BOC",
            body = "CEFT Transfer Debit Rs 500.25 From A/C No XXXXXXXXXX999. Balance available Rs 14,376.29 - Thank you for banking with BOC",
            expected = Expectation.Success(
                type = TransactionType.CEFT,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 50025,
                currency = Currency.LKR,
                balanceMinor = 1437629,
                accountSuffix = "999",
            ),
        ),
        ParseCase(
            label = "boc_ceft_transfer_credit",
            sender = "BOC",
            body = "CEFT Transfer Credit Rs 3,250.00 To A/C No XXXXXXXXXX999. Balance available Rs 17,626.29 - Thank you for banking with BOC",
            expected = Expectation.Success(
                type = TransactionType.CEFT,
                flow = TransactionFlow.INCOME,
                amountMinor = 325000,
                currency = Currency.LKR,
                balanceMinor = 1762629,
                accountSuffix = "999",
            ),
        ),
        ParseCase(
            label = "boc_transfer_credit_bare",
            sender = "BOC",
            body = "Transfer Credit Rs 2,500.00 To A/C No XXXXXXXXXX999. Balance available Rs 20,126.29 - Thank you for banking with BOC",
            expected = Expectation.Success(
                type = TransactionType.ONLINE_TRANSFER,
                flow = TransactionFlow.INCOME,
                amountMinor = 250000,
                currency = Currency.LKR,
                balanceMinor = 2012629,
                accountSuffix = "999",
            ),
        ),
        ParseCase(
            label = "boc_atm_withdrawal",
            sender = "BOC",
            body = "ATM Withdrawal Rs 4,000.00 From A/C No XXXXXXXXXX999. Balance available Rs 16,126.29 - Thank you for banking with BOC",
            expected = Expectation.Success(
                type = TransactionType.ATM,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 400000,
                currency = Currency.LKR,
                balanceMinor = 1612629,
                accountSuffix = "999",
            ),
        ),
        ParseCase(
            label = "boc_atm_cash_deposit",
            sender = "BOC",
            body = "ATM Cash Deposit Rs 10,000.00 To A/C No XXXXXXXXXX999. Balance available Rs 26,126.29 - Thank you for banking with BOC",
            expected = Expectation.Success(
                type = TransactionType.ATM,
                flow = TransactionFlow.INCOME,
                amountMinor = 1000000,
                currency = Currency.LKR,
                balanceMinor = 2612629,
                accountSuffix = "999",
            ),
        ),
        ParseCase(
            label = "boc_cheque_deposit",
            sender = "BOC",
            body = "Cheque Deposit Rs 25,000.00 To A/C No XXXXXXXXXX999. Balance available Rs 51,126.29 - Thank you for banking with BOC",
            expected = Expectation.Success(
                type = TransactionType.CHEQUE,
                flow = TransactionFlow.INCOME,
                amountMinor = 2500000,
                currency = Currency.LKR,
                balanceMinor = 5112629,
                accountSuffix = "999",
            ),
        ),
        // Small amount, ensures our amount regex handles <1000 cases.
        ParseCase(
            label = "boc_small_amount",
            sender = "BOC",
            body = "Online Transfer Debit Rs 58.00 From A/C No XXXXXXXXXX999. Balance available Rs 51,068.29 - Thank you for banking with BOC",
            expected = Expectation.Success(
                type = TransactionType.ONLINE_TRANSFER,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 5800,
                currency = Currency.LKR,
                balanceMinor = 5106829,
                accountSuffix = "999",
            ),
        ),
        // Very large amount, tests triple-comma handling.
        ParseCase(
            label = "boc_large_amount",
            sender = "BOC",
            body = "CEFT Transfer Debit Rs 1,250,000.00 From A/C No XXXXXXXXXX999. Balance available Rs 250.10 - Thank you for banking with BOC",
            expected = Expectation.Success(
                type = TransactionType.CEFT,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 125000000,
                currency = Currency.LKR,
                balanceMinor = 25010,
                accountSuffix = "999",
            ),
        ),
        // Non-transaction — must be rejected.
        ParseCase(
            label = "boc_tax_notice_nontx",
            sender = "BOC",
            body = "Dear Customer, Self-declaration forms for YA 2025/26 valid up to 31 March 2026. Submit new declaration forms to the bank to avoid WHT deductions.",
            expected = Expectation.Informational("non-transaction BOC notice"),
        ),
    )
}
