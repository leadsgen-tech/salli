# Redacted SMS Samples

The canonical fixtures used by the parser tests live in Kotlin at
`parser/src/test/kotlin/lk/salli/parser/fixtures/*Fixtures.kt`. Each sample is
a `ParseCase` with the redacted body and the expected `ParseResult`.

## Redaction rules

Before committing any SMS to this repository, the following must be replaced:

| In raw SMS                    | Replace with              |
| ----------------------------- | ------------------------- |
| Account numbers (any format)  | keep last 3–4 digits only |
| Names (customer, sender)      | `[NAME]`                  |
| Mobile numbers (international or local) | `[PHONE]`        |
| Card last 4                   | keep as-is (part of template) |
| Transaction amounts           | plausible fake value      |
| Balance amounts               | plausible fake value      |
| Reference / transaction IDs   | `[REF]`                   |
| Locations (Mawanella, etc.)   | keep as-is (not sensitive) |
| Merchant names                | keep as-is (business names) |
| Dates                         | keep as-is                |

## Why fake amounts

The parser's regex only cares about the *shape* of the amount (`[\d,]+\.\d{2}`),
not its value. Using invented amounts for fixtures means no real financial
information ever enters the repo, while still exercising the parser correctly.

## Contributing a new bank

1. Redact 5+ real SMS following the table above.
2. Open an issue using the "Bank support request" template.
3. A maintainer will review and add a `<Bank>Fixtures.kt` + `<Bank>Template.kt`.
