package lk.salli.parser.fixtures

import lk.salli.domain.Currency
import lk.salli.domain.TransactionFlow
import lk.salli.domain.TransactionType

/** RECONSTRUCTED samples for Pan Asia Bank cards — not real SMS. See SampathFixtures for why. */
object PanAsiaFixtures {
    val cases: List<ParseCase> = listOf(
        ParseCase(
            label = "panasia_cc_auth_reconstructed",
            sender = "PanAsiaBank",
            body = "Cr Crd no..**4521 Auth Pmt LKR 3,450.00 at KEELLS SUPER on SEP-14 Avl Bal LKR 120,550.00",
            expected = Expectation.Success(
                type = TransactionType.POS, flow = TransactionFlow.EXPENSE,
                amountMinor = 345000, currency = Currency.LKR, balanceMinor = 12055000,
                accountSuffix = "4521", merchantRaw = "KEELLS SUPER",
            ),
        ),
        ParseCase(
            label = "panasia_unmatched_goes_to_queue",
            sender = "PanAsiaBank",
            body = "Dear Customer, your card statement is now available on our mobile app.",
            expected = Expectation.Unknown,
        ),
    )
}
