# Transaction types the parser recognises

Salli's parser treats every bank SMS as one of these shapes. When
contributing samples for a new bank, skim this list and find the labels
that match what your bank sends — then submit at least 3 redacted samples
per matching type.

## Money-moving messages (land in your Timeline)

| Type | What it is | Example body fragment |
|---|---|---|
| **Card purchase — POS** | You swiped / tapped your card at a shop, the charge went through. Usually LKR, sometimes foreign currency for COMBANK / HNB cards at Apple / Google / online merchants. | `Purchase at KEELLS SUPER for LKR 1250.00 ... card ending #1234` |
| **Card purchase — FX** | Same as above but in foreign currency. The parser stores the amount with its currency — no auto-conversion. | `Purchase at APPLE.COM for USD 15.99 ...` |
| **Card purchase — declined** | Carries an amount + merchant but **nothing actually left your account.** These are surfaced on a "Declined attempts" screen (useful for catching expiring subscriptions). | `your card was declined due to insufficient funds. The attempted transaction amount was LKR 5000.00 at NETFLIX.COM ...` |
| **ATM cash withdrawal** | You pulled cash out of an ATM. | `Withdrawal Rs 10000.00 at ATM BAMBALAPITIYA ...` |
| **ATM / CDM cash deposit** | You dropped cash into an ATM / Cash Deposit Machine. | `Your A/C ...68 has been Credited by Rs 10000.00 (CDM @22:14 ... at Mawanella)` |
| **Cheque deposit (credit)** | A cheque cleared **in your favour**. | `Cheque Deposit Rs 247250.00 To A/C No XXX870. Balance available Rs X,XXX.XX` |
| **Cheque cleared (debit)** | A cheque **you wrote** cleared against your account. | `Cheque No 123456 for Rs 50000.00 debited ...` |
| **Online transfer — outgoing** | You sent money to another account (same bank or interbank). | `Online Transfer Debit Rs 85000.00 From A/C No XXX870 ...` |
| **Online transfer — incoming** | Someone sent money to your account. | `Transfer Credit Rs 50000.00 To A/C No XXX870 ...` |
| **CEFT / SLIPS interbank transfer** | Real-time interbank transfer. Some banks label this distinctly from "Online Transfer". | `CEFT Transfer Debit Rs 50025.00 From A/C No XXX870 ...` |
| **Fund transfer confirmation** | The **follow-up** SMS banks send a minute after a transfer, typically with the payee name. These often need to be *merged* with the initial debit alert — write both in comments if so. | `Fund transfer Successful. LKR 50000.00 to LOLC Finance PLC Account 20810007495 ...` |
| **Mobile / app payment** | You paid via the bank's mobile app (LPay, Peo Pay, Sampath PayApp, Frimi, Genie, etc.). | `Mobile Payment Successful, LKR 4950.00 to Dialog Axiata ...` |
| **Bill payment** | Utility or credit-card bill paid directly from your account. | `Bill payment Rs 8500.00 to CEB Ref [REF] ...` |
| **Mobile reload / top-up** | A telco reload initiated from the banking app. May overlap with "Mobile payment" on some banks. | `Reload Rs 500.00 to [PHONE] ...` |
| **Standing order / recurring** | Bank-executed recurring transfer (rent, insurance premium). | `Standing Order Executed: Rs 25000.00 debited ...` |
| **Refund / reversal credit** | A previously-debited charge was reversed. Banks flag these distinctly. Salli excludes them from income totals. | `Reversal credited Rs 1500.00 for transaction on 15/04/26 ...` |
| **Salary / payroll credit** | Payroll deposit. Many banks tag these `(SALARY)` or `(PAYROLL)` in the narration. | `Credited by Rs 150000.00 (SALARY @08:00 30/04/2026)` |

## Messages we need to **recognise and ignore**

These aren't transactions — but without explicit rejection rules they'd
pollute your Timeline. Submit samples of these too.

| Type | Why we reject |
|---|---|
| **OTP** | Six-digit codes for login / transaction confirmation. Some OTPs embed a real amount + merchant (e.g. "*OTP 425401 for your payment LKR 1287.00 at CARGILLS*") — a naive parser would treat this as a transaction. We filter any SMS containing `OTP` or `one-time password`. |
| **Balance alert** | Just a balance, no transaction attached. We could optionally use these to refresh the cached account balance, but they're not timeline rows. |
| **Login notification** | `You logged in to [App]`. Ignored. |
| **Failed login / security alert** | Useful for the user but not a financial event. Ignored. |
| **Card activation / expiry** | Informational. Ignored. |
| **Promotional / marketing** | "Get a personal loan at 8%!" Ignored. |
| **Compliance / tax notice** | e.g. BOC's *"Self-declaration forms for YA 2025/26..."*. Ignored. |

## What we don't track (yet)

- **Foreign exchange transactions between your own accounts** in different currencies — we don't convert, and we don't pair them as internal transfers if the currencies differ.
- **Credit card statement alerts** (the monthly "your bill is Rs X, due on Y") — treated as informational for now. The actual bill payment goes through as an Online transfer or Bill payment when you pay it.
- **Investment / fixed deposit maturity notices** — out of scope for v1.
