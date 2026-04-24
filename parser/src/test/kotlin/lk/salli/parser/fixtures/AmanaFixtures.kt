package lk.salli.parser.fixtures

import lk.salli.domain.Currency
import lk.salli.domain.TransactionFlow
import lk.salli.domain.TransactionType

/**
 * Redacted samples from Amana Bank PLC (sender `AMANABANK`).
 *
 * Four transaction shapes + one informational:
 *  - POS card purchase
 *  - Inward CEFTS transfer (incoming credit)
 *  - Outbound IB CEFTS transfer with explicit fee (outgoing debit)
 *  - Savings profit credit (Islamic-banking interest equivalent)
 *  - Onboarding notification (rejected)
 */
object AmanaFixtures {
    val cases: List<ParseCase> = listOf(
        ParseCase(
            label = "amana_pos_foreign",
            sender = "AMANABANK",
            body = "POS trx of LKR 1,647.70 at HIGGSFIELD INC. +14088370029 US authorised from your A/C ***0001. Avail. Bal. LKR 2,647.60. Enq 0117756756",
            expected = Expectation.Success(
                type = TransactionType.POS,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 164770,
                currency = Currency.LKR,
                balanceMinor = 264760,
                accountSuffix = "***0001",
                merchantRaw = "HIGGSFIELD INC. +14088370029 US",
            ),
        ),
        ParseCase(
            label = "amana_pos_daraz",
            sender = "AMANABANK",
            body = "POS trx of LKR 2,428.00 at DARAZ.LK COLOMBO 03 LK authorised from your A/C ***0001. Avail. Bal. LKR 9,079.35. Enq 0117756756",
            expected = Expectation.Success(
                type = TransactionType.POS,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 242800,
                currency = Currency.LKR,
                balanceMinor = 907935,
                accountSuffix = "***0001",
                merchantRaw = "DARAZ.LK COLOMBO 03 LK",
            ),
        ),
        ParseCase(
            label = "amana_ceft_inward",
            sender = "AMANABANK",
            body = "Inward CEFTS trf of LKR 2,000.00 ([NAME])  from: IBFT 115510546317 credited to your A/C ***0001. Avail Bal LKR 4,597.60. Enq 0117756756",
            expected = Expectation.Success(
                type = TransactionType.CEFT,
                flow = TransactionFlow.INCOME,
                amountMinor = 200000,
                currency = Currency.LKR,
                balanceMinor = 459760,
                accountSuffix = "***0001",
                merchantRaw = "IBFT 115510546317 ([NAME])",
            ),
        ),
        ParseCase(
            label = "amana_ceft_outbound_with_fee",
            sender = "AMANABANK",
            body = "IB CEFTS trf of LKR 4,800.00 & charge of LKR 25.00 ([NAME]) to: [NAME] HNB debited from your A/C ***0001. Avail. Bal. LKR  4,254.35. Enq 0117756756",
            expected = Expectation.Success(
                type = TransactionType.CEFT,
                flow = TransactionFlow.EXPENSE,
                amountMinor = 480000,
                currency = Currency.LKR,
                balanceMinor = 425435,
                feeMinor = 2500,
                accountSuffix = "***0001",
                merchantRaw = "[NAME] HNB ([NAME])",
            ),
        ),
        ParseCase(
            label = "amana_savings_profit",
            sender = "AMANABANK",
            body = "Profit of your Savings A/C ***0001 has been distributed to A/C ***0001. Profit Amount (Subject to Withholding Tax)LKR 11.57. Enq 0117756756",
            expected = Expectation.Success(
                type = TransactionType.OTHER,
                flow = TransactionFlow.INCOME,
                amountMinor = 1157,
                currency = Currency.LKR,
                accountSuffix = "***0001",
                merchantRaw = "Savings profit",
            ),
        ),
        ParseCase(
            label = "amana_onboarding_informational",
            sender = "AMANABANK",
            body = "Dear Customer, Your new LKR Statement Savings A/C No. 0100602780001 is ready to be funded. Visit bit.ly/AmanaOnline to sign-up for online banking & bit.ly/AmanaGTC for General T&/C",
            expected = Expectation.Informational(),
        ),
    )
}
