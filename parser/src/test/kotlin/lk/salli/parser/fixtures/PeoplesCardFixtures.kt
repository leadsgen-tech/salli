package lk.salli.parser.fixtures

import lk.salli.domain.Currency
import lk.salli.domain.TransactionFlow
import lk.salli.domain.TransactionType

/** RECONSTRUCTED samples for People's Bank credit cards — not real SMS. See SampathFixtures for why. */
object PeoplesCardFixtures {
    val cases: List<ParseCase> = listOf(
        ParseCase(
            label = "peoplescard_pos_reconstructed",
            sender = "PeoplesCard",
            body = "Peoples Card X-X-X-1234 trxn LKR 3,450.00 @ KEELLS SUPER. [Av.Bal: LKR 120,550.00]",
            expected = Expectation.Success(
                type = TransactionType.POS, flow = TransactionFlow.EXPENSE,
                amountMinor = 345000, currency = Currency.LKR, balanceMinor = 12055000,
                accountSuffix = "1234", merchantRaw = "KEELLS SUPER",
            ),
        ),
        ParseCase(
            label = "peoplescard_unmatched_goes_to_queue",
            sender = "PeoplesCard",
            body = "Dear Customer, your card is due for renewal. Visit your branch.",
            expected = Expectation.Unknown,
        ),
    )
}
