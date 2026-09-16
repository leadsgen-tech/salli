package lk.salli.parser.fixtures

import lk.salli.domain.Currency
import lk.salli.domain.TransactionFlow
import lk.salli.domain.TransactionType

/** RECONSTRUCTED samples for DFCC Bank — not real SMS. See SampathFixtures for why. */
object DfccFixtures {
    val cases: List<ParseCase> = listOf(
        ParseCase(
            label = "dfcc_card_debit_reconstructed",
            sender = "DFCC Info",
            body = "DEBITED LKR 3,450.00 ON (14/Sep/2026 18:42) CARD: KEELLS SUPER CARD**4521 AVBAL LKR 120,550.00",
            expected = Expectation.Success(
                type = TransactionType.POS, flow = TransactionFlow.EXPENSE,
                amountMinor = 345000, currency = Currency.LKR, balanceMinor = 12055000,
                accountSuffix = "4521", merchantRaw = "KEELLS SUPER",
            ),
        ),
        ParseCase(
            label = "dfcc_card_credit_reconstructed",
            sender = "DFCC Info",
            body = "CREDITED LKR 1,200.00 ON (15/Sep/2026 09:10) CARD: REFUND KEELLS CARD**4521 AVBAL LKR 121,750.00",
            expected = Expectation.Success(
                type = TransactionType.POS, flow = TransactionFlow.INCOME,
                amountMinor = 120000, currency = Currency.LKR, balanceMinor = 12175000,
                accountSuffix = "4521", merchantRaw = "REFUND KEELLS",
            ),
        ),
        ParseCase(
            label = "dfcc_account_debit_reconstructed",
            sender = "DFCC Alerts",
            body = "Your A/C No: ****1234 has been debited with LKR3,450.00 on 14 Sep 2026 ref: POS KEELLS SUPER. Available bal is LKR12,345.00",
            expected = Expectation.Success(
                type = TransactionType.POS, flow = TransactionFlow.EXPENSE,
                amountMinor = 345000, currency = Currency.LKR, balanceMinor = 1234500,
                accountSuffix = "1234", merchantRaw = "POS KEELLS SUPER",
            ),
        ),
        ParseCase(
            label = "dfcc_account_credit_reconstructed",
            sender = "DFCC Alerts",
            body = "Your A/C No: ****1234 has been credited with LKR25,000.00 on 25 Aug 2026 ref: SALARY AUG. Available bal is LKR37,345.00",
            expected = Expectation.Success(
                type = TransactionType.OTHER, flow = TransactionFlow.INCOME,
                amountMinor = 2500000, currency = Currency.LKR, balanceMinor = 3734500,
                accountSuffix = "1234", merchantRaw = "SALARY AUG",
            ),
        ),
        ParseCase(
            // "GREATMART" contains "ATM"; the ref field says POS and must win.
            label = "dfcc_account_pos_with_atm_substring",
            sender = "DFCC Alerts",
            body = "Your A/C No: ****1234 has been debited with LKR1,200.00 on 15 Sep 2026 ref: POS GREATMART. Available bal is LKR11,145.00",
            expected = Expectation.Success(
                type = TransactionType.POS, flow = TransactionFlow.EXPENSE,
                amountMinor = 120000, currency = Currency.LKR, balanceMinor = 1114500,
                accountSuffix = "1234", merchantRaw = "POS GREATMART",
            ),
        ),
        ParseCase(
            label = "dfcc_unmatched_goes_to_queue",
            sender = "DFCC Alerts",
            body = "Dear Customer, your DFCC e-statement for August is ready on DFCC Virtual Wallet.",
            expected = Expectation.Unknown,
        ),
    )
}
