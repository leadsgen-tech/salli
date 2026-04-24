# Contributing bank SMS samples

Salli only works if its parser knows your bank's SMS format. Adding a new
bank means shipping a handful of redacted samples plus a few lines of
regex — the samples are the hard part, the rest takes 30 minutes for a
maintainer.

This guide walks you through producing samples that are safe to share.

## The low-effort path: just paste redacted SMS

If filling out a structured form feels like too much, **it is genuinely
fine to just paste a redacted SMS (or several) into an issue** with no
other context. Everything a maintainer needs — the bank, the transaction
type, the format — is right there in the body of the SMS itself. A
single line like this:

> `Online Transfer Debit Rs 5000.00 From A/C No XXX870. Balance available
> Rs 125000.00 - Thank you for banking with BOC`

…tells us: it's from BOC, it's an outgoing transfer, here's the shape of
the body. That's enough to extend or fix a template.

Submissions are anonymous by default. You don't have to tell us which
bank you use, which account it is, or what type of transaction it was —
we can see all of that in the SMS once the personal bits are redacted.

So the lightest-weight contribution is:

1. Redact per the table below (this part does matter).
2. Open an issue with "just SMS samples" in the title and paste.

That's it. A maintainer takes it from there.

## The structured path

If you have coverage for several transaction types at once and want to
help us organise, use the full form:

1. Open your SMS inbox, find messages from your bank.
2. For each [transaction type we care about](TRANSACTION_TYPES.md),
   find a few real SMS (more is better, but even one helps).
3. Redact following the rules below.
4. Open an issue using
   **[the Bank Support Request template](../../../issues/new?template=bank_support_request.yml)**.

Either path works. Both end with a maintainer writing regex + tests.

## What to redact

The parser's regex only cares about the **shape** of the message, not the
real values. So every bit of personal data can be replaced with a
placeholder without breaking anything.

| In the raw SMS | Replace with | Why |
|---|---|---|
| Account number (any length, any format) | Keep last **3 or 4 digits** only | Parser uses the last digits as the account key; full number is sensitive |
| Card number / card last 4 | Keep as-is if the SMS shows only last 4 | Banks already redact to last 4; that's public enough |
| Customer / sender / payee name | `[NAME]` | Never leave real names |
| Mobile number (local or international) | `[PHONE]` | Never leave real numbers |
| Transaction amount | Replace with a plausible **fake** amount that has the same magnitude (e.g. `Rs 1,287.00` → `Rs 1,250.00`) | Real amounts are personal; fake ones exercise the parser identically |
| Balance amount | Fake value with matching magnitude (e.g. `Rs 1,234,567.00` → `Rs X,XXX,XXX.00` or a plausible number) | Real balances are very personal |
| Reference / transaction ID | `[REF]` | Often traceable back to you |
| Location (e.g. `BAMBALAPITIYA`, `Mawanella`) | **Keep as-is** | Public place names, part of the parser signature |
| Merchant name (e.g. `KEELLS SUPER`, `DOMINOS PIZZA`) | **Keep as-is** | Business names are public; we need them for categorization |
| Date + time | **Keep as-is** | Part of the message shape |
| Bank name / sender | **Keep as-is** | It's the sender field |

## An example — before and after

**Before (don't commit this):**

```
Purchase at KEELLS SUPER COLOMBO for LKR 4287.53 on 15/04/26 04:32 PM
on your debit card ending #4273. Available balance LKR 127,843.22.
Ref 20260415A8F43B.
```

**After (safe to share):**

```
Purchase at KEELLS SUPER COLOMBO for LKR 4250.00 on 15/04/26 04:32 PM
on your debit card ending #4273. Available balance LKR 125,000.00.
Ref [REF].
```

Note what stayed the same: the sentence shape, the merchant, the
location, the card last-4, the timestamp, the currency. Note what
changed: the amount (close but fake), the balance (round fake), the
reference (placeholder).

## What we need per transaction type

For each [transaction type](TRANSACTION_TYPES.md) your bank sends,
please include at least **3 distinct samples**. More is better — the
variants that catch regex bugs are:

- Samples with and without a balance line at the end
- Samples with and without a location
- Samples from weekdays and weekends (some banks format dates differently)
- Samples in different currencies if your bank issues FX cards
- Samples with unusually small amounts (`Rs 50.00`) and unusually large
  (`Rs 1,000,000.00`)
- The **failed / declined** equivalent of each transaction type, if the
  bank sends one

## What we need per non-transaction type

OTPs, login notifications, promotional SMS — we reject these, but we
need samples to know what the rejection rule looks like. A single
redacted OTP per variant is enough.

**OTP samples are important.** Some banks send OTPs that embed a real
amount + merchant (e.g. "*OTP 425401 for your online payment LKR 1287.00
from Card 2462 at CARGILLS*") — if we don't catch these explicitly,
they'd be parsed as transactions. Please include any OTP formats your
bank uses.

## Getting samples off your phone

On Android, long-press any SMS → Share → Copy text. Paste into a
text editor on your computer, redact, then paste into the GitHub issue.

Do **not** screenshot + share the image — screenshots contain even more
metadata (your wallpaper, your name at the top of the Messages app)
and are harder to redact reliably.

## Review process

1. You open an issue with samples.
2. A maintainer reviews redaction. If anything leaked, we'll ask you
   to edit the issue. (Note: the raw text stays in GitHub's edit history,
   so redact before submitting.)
3. Maintainer writes a `<Bank>Template.kt` parser + `<Bank>Fixtures.kt`
   using your samples.
4. Unit tests pass → merged → shipped in the next release.
5. You're credited in `CONTRIBUTORS.md`.

## FAQ

**"I don't know what type of SMS this is."** — tick every box that looks
close and paste the samples anyway. Maintainers will classify them. It's
easier for us to sort 10 mixed samples than to decode vague
descriptions.

**"My bank is already supported — can I still contribute?"** — yes. Edge
cases we haven't seen (new templates the bank rolled out, regional
language variants) are the most useful additions for an existing
template.

**"Can I just share my entire SMS history?"** — please don't. Curated
samples of each transaction type are far more useful than bulk data, and
bulk data is harder to redact confidently.

**"Is Salli going to send my SMS to a server?"** — no, never. Salli runs
100% on-device. You're contributing samples so the *parser source code*
can learn your bank's format; the samples live in the open-source repo,
not on any server.
