package lk.salli.parser.fixtures

import lk.salli.domain.Currency
import lk.salli.domain.TransactionFlow
import lk.salli.domain.TransactionType

/**
 * RECONSTRUCTED samples for Sampath Bank — not real SMS. Bodies were built from publicly
 * observable format evidence so the provisional template has a regression net. Replace with
 * redacted real samples as soon as a contributor supplies them (see docs/BANK_SAMPLES_GUIDE.md).
 */
object SampathFixtures {
    val cases: List<ParseCase> = listOf(
        ParseCase(
            label = "sampath_cc_auth_lkr_reconstructed",
            sender = "SAMPCCTXN",
            body = "Cr Crd no..**4821 Auth Pmt LKR 4,321.00 at KEELLS SUPER KANDY Avl Bal LKR 95,679.00",
            expected = Expectation.Success(
                type = TransactionType.POS, flow = TransactionFlow.EXPENSE,
                amountMinor = 432100, currency = Currency.LKR, balanceMinor = 9567900,
                accountSuffix = "4821", merchantRaw = "KEELLS SUPER KANDY",
            ),
        ),
        ParseCase(
            label = "sampath_cc_auth_usd_reconstructed",
            sender = "SAMPCCTXN",
            body = "Cr Crd no..**4821 Auth Pmt USD 12.99 at APPLE.COM/BILL Avl Bal LKR 91,779.00",
            expected = Expectation.Success(
                type = TransactionType.POS, flow = TransactionFlow.EXPENSE,
                amountMinor = 1299, currency = Currency.USD, balanceMinor = null,
                accountSuffix = "4821", merchantRaw = "APPLE.COM/BILL",
            ),
        ),
        ParseCase(
            label = "sampath_account_pos_reconstructed",
            sender = "SAMPATHTXN",
            body = "LKR 2,500.00 debited from AC **1234 at KEELLS SUPER 123456 on 14/09/2026 18:42:10 Avl Bal LKR 45,300.00",
            expected = Expectation.Success(
                type = TransactionType.POS, flow = TransactionFlow.EXPENSE,
                amountMinor = 250000, currency = Currency.LKR, balanceMinor = 4530000,
                accountSuffix = "1234", merchantRaw = "KEELLS SUPER",
            ),
        ),
        ParseCase(
            label = "sampath_vishwa_transfer_reconstructed",
            sender = "SAMPATHTXN",
            body = "LKR 5,000.00 debited from AC **1234 for [NAME] -123456 on 14/09/2026 10:15:00",
            expected = Expectation.Success(
                type = TransactionType.ONLINE_TRANSFER, flow = TransactionFlow.EXPENSE,
                amountMinor = 500000, currency = Currency.LKR, balanceMinor = null,
                accountSuffix = "1234", merchantRaw = "[NAME]",
            ),
        ),
        ParseCase(
            label = "sampath_account_credit_reconstructed",
            sender = "SAMPATHTXN",
            body = "LKR 25,000.00 credited to AC **1234 for SALARY AUG -778899 on 25/08/2026 09:00:00 Avl Bal LKR 70,300.00",
            expected = Expectation.Success(
                type = TransactionType.ONLINE_TRANSFER, flow = TransactionFlow.INCOME,
                amountMinor = 2500000, currency = Currency.LKR, balanceMinor = 7030000,
                accountSuffix = "1234", merchantRaw = "SALARY AUG",
            ),
        ),
        ParseCase(
            // "TREATMENT" contains "ATM": must stay a transfer credit, not a cash withdrawal.
            label = "sampath_credit_with_atm_substring_in_payee",
            sender = "SAMPATHTXN",
            body = "LKR 25,000.00 credited to AC **1234 for MEDICAL TREATMENT REFUND -778899 on 25/08/2026 09:00:00",
            expected = Expectation.Success(
                type = TransactionType.ONLINE_TRANSFER, flow = TransactionFlow.INCOME,
                amountMinor = 2500000, currency = Currency.LKR, balanceMinor = null,
                accountSuffix = "1234", merchantRaw = "MEDICAL TREATMENT REFUND",
            ),
        ),
        ParseCase(
            label = "sampath_atm_withdrawal_reconstructed",
            sender = "SAMPATHTXN",
            body = "LKR 10,000.00 debited from AC **1234 via ATM at SAMPATH KANDY 123456 on 14/09/2026 08:00:00 Avl Bal LKR 35,300.00",
            expected = Expectation.Success(
                type = TransactionType.ATM, flow = TransactionFlow.EXPENSE,
                amountMinor = 1000000, currency = Currency.LKR, balanceMinor = 3530000,
                accountSuffix = "1234", merchantRaw = "SAMPATH KANDY",
            ),
        ),
        ParseCase(
            label = "sampath_unmatched_notice_goes_to_queue",
            sender = "SAMPATHTXN",
            body = "Dear Customer, Sampath Vishwa will be under maintenance tonight from 11 PM to 2 AM.",
            expected = Expectation.Unknown,
        ),
    )
}
