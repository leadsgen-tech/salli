# Contributing to Salli

Thanks for helping. The single most valuable contribution right now is **bank template expansion** — samples from banks we don't support yet.

## Adding a new bank template

1. **Collect 3–5 real SMS** from your inbox for the bank. Include a mix of transaction types — a purchase (POS), an ATM withdrawal, an online transfer, an incoming credit, and maybe a declined attempt. More variety = better regex.

2. **Redact before sharing.** Open each SMS in a plain text editor and sanitise:

    | Field | Rule | Example |
    |---|---|---|
    | Account numbers | Keep last 4 digits, mask the rest | `123456789012` → `XXXXXXXX9012` |
    | Names | Replace with `[NAME]` | `Mr. K. Pereira` → `Mr. [NAME]` |
    | Phone numbers | Replace with `[PHONE]` | `0771234567` → `[PHONE]` |
    | Reference / tracking IDs | Zero the digits, keep magnitude | `Ref 98765432` → `Ref 00000000` |
    | Balances | Zero the digits, keep currency + magnitude | `Rs 1,234,567.00` → `Rs X,XXX,XXX.00` |
    | Transaction amounts | **Keep as-is** (not sensitive, needed for regex tests) | |
    | Dates / times | **Keep as-is** | |
    | Merchant names | **Keep as-is** (factual, needed for keyword matching) | |

3. **Open a GitHub issue** using the `Bank support` template. Paste the redacted samples, name the bank, and confirm the redaction checklist.

4. A maintainer will add the samples to `samples/redacted/<sender>.json`, author a `<Bank>Template.kt` regex, and write parameterised tests from the fixture. You'll be credited in `CONTRIBUTORS.md` once the template merges.

## Categorization keywords

If a merchant in your SMS keeps landing as "Uncategorised", open a PR against `SeedKeywords.kt` with the merchant name mapped to its category. Keep entries short (substring matches case-insensitively) and prefer generic forms — `keells` over `keells super colombo 04`.

## Code style

- Prefer editing existing files to adding new ones.
- No gratuitous comments — code should read by itself. Comments explain _why_, not _what_.
- Tests live alongside the module they test (`parser/src/test/...`, `data/src/test/...`).
- Kotlin's standard style. Run `./gradlew check` before pushing.

## Setup

```bash
./gradlew :app:installDebug
```

Android Studio Ladybug or later. JDK 17.

## What we won't merge

- Anything that adds network calls outside the existing `LocalModel` download URL.
- Analytics / crash reporting / telemetry SDKs.
- Firebase, RevenueCat, Mixpanel, or similar hosted services.
- Direct copies of code from AGPL'd competitors (we ship Apache 2.0 — see `CLAUDE.md` for the rationale).
- Copied images or icons — only self-made or public-domain assets.

## Getting help

- GitHub Discussions for design questions
- GitHub Issues for bugs and feature requests
- WhatsApp / Discord (links in README once the community forms)
