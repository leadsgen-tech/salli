package lk.salli.parser.fixtures

import lk.salli.domain.Currency
import lk.salli.domain.TransactionFlow
import lk.salli.domain.TransactionType

/** RECONSTRUCTED samples for NDB Bank cards — not real SMS. See SampathFixtures for why. */
object NdbFixtures {
    val cases: List<ParseCase> = listOf(
        ParseCase(
            label = "ndb_card_debit_reconstructed",
            sender = "NDB CARD",
            body = "Card 4512****7788 Debited LKR 3,450.00 KEELLS SUPER  123456 2026-09-14 18:42:10 Avl Bal 120,550.00",
            expected = Expectation.Success(
                type = TransactionType.POS, flow = TransactionFlow.EXPENSE,
                amountMinor = 345000, currency = Currency.LKR, balanceMinor = 12055000,
                accountSuffix = "7788", merchantRaw = "KEELLS SUPER",
            ),
        ),
        ParseCase(
            label = "ndb_statement_notice_is_informational",
            sender = "NDB ALERTS",
            body = "Your NDB credit card ending ****7788 outstanding as at 05-Sep-26 is Rs. 45,000.00, min payment of Rs. 2,250.00 is due by 25-Sep-26.",
            expected = Expectation.Informational(),
        ),
        ParseCase(
            label = "ndb_unmatched_goes_to_queue",
            sender = "NDB CARD",
            body = "Dear Customer, enjoy 20% off at selected restaurants with your NDB card this weekend.",
            expected = Expectation.Unknown,
        ),
    )
}
