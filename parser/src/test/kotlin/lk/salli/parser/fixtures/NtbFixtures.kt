package lk.salli.parser.fixtures

import lk.salli.domain.Currency
import lk.salli.domain.TransactionFlow
import lk.salli.domain.TransactionType

/** RECONSTRUCTED samples for Nations Trust Bank — not real SMS. See SampathFixtures for why. */
object NtbFixtures {
    val cases: List<ParseCase> = listOf(
        ParseCase(
            label = "ntb_card_purchase_reconstructed",
            sender = "NationsSMS",
            body = "Transaction approved on your Card 4512****7788 for LKR 3,450.00 at KEELLS SUPER Available Bal: LKR 120,550.00",
            expected = Expectation.Success(
                type = TransactionType.POS, flow = TransactionFlow.EXPENSE,
                amountMinor = 345000, currency = Currency.LKR, balanceMinor = 12055000,
                accountSuffix = "7788", merchantRaw = "KEELLS SUPER",
            ),
        ),
        ParseCase(
            label = "ntb_cefts_transfer_reconstructed",
            sender = "NationsSMS",
            body = "Other Bank Transfer (CEFTS) was performed from account ...1234 for LKR 5,000.00 to [NAME]",
            expected = Expectation.Success(
                type = TransactionType.CEFT, flow = TransactionFlow.EXPENSE,
                amountMinor = 500000, currency = Currency.LKR, balanceMinor = null,
                accountSuffix = "1234",
            ),
        ),
        ParseCase(
            label = "ntb_declined_is_informational",
            sender = "NationsSMS",
            body = "Transaction on your Card 4512****7788 for LKR 3,450.00 at KEELLS SUPER was declined. Please contact us.",
            expected = Expectation.Informational(),
        ),
        ParseCase(
            label = "ntb_unmatched_goes_to_queue",
            sender = "NationsSMS",
            body = "Dear Customer, Nations Trust Bank wishes you a happy new year!",
            expected = Expectation.Unknown,
        ),
    )
}
