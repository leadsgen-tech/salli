# Salli — Project Context

**Salli** (සල්ලි, "money" in Sinhala) is an **open-source, Android-native, 100% on-device** expense tracker for Sri Lanka. Parses bank SMS locally, categorizes locally, stores locally. No backend, no account, no telemetry.

**License:** Apache 2.0. Chosen over AGPL to stay permissive and compatible with any future commercial SL ecosystem reuse. Notably this means we must **not** copy code from AGPL-licensed projects like [PennyWise AI](https://github.com/sarim2000/pennywiseai-tracker) — study architecture, write clean-room.

## Core Thesis

Sri Lankan banks have no public APIs, but every card swipe and transfer triggers an SMS alert. Some existing apps ship SMS content to cloud backends to parse — contradicting their "privacy-first" marketing. Salli does it all on-device so the privacy claim is actually true.

## Non-Goals (keep scope honest)

- **No LLM, no AI mode, ever.** Regex templates only. An on-device model (MediaPipe + Qwen) was prototyped and removed in Sep 2026: it needed `INTERNET` for the model download and prebuilt binaries F-Droid can't build. Do not re-propose it.
- **No iOS in v1.** iOS is a later phase. Code is structured so the parser module can be converted to Kotlin Multiplatform without rewriting.
- **No backend.** Ever. No auth, no sync server, no analytics. Export-only for user-driven portability.
- **No Play Store as primary distribution.** F-Droid first — Play Store's SMS permission policies make OSS distribution painful. We may revisit with a workaround later.

## Stack

| Layer | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 (Expressive) |
| DB | Room (SQLite) |
| Background | WorkManager + BroadcastReceiver for `RECEIVE_SMS` |
| DI | Hilt (standard Android choice) |
| Navigation | Compose Navigation |
| Charts | Vico |
| Images | Coil |
| Tests | JUnit5 + Turbine (Flow) + Robolectric for parser |

## Planned Module Structure

```
salli/
├── app/                   # Android app (Compose UI, DI, screens)
├── parser/                # Pure Kotlin — SMS → Transaction (KMP-ready)
│   └── templates/         # Per-bank regex templates as data classes
├── domain/                # Pure Kotlin — Transaction, Merchant, Category, enums
├── data/                  # Room DAOs, migrations, repositories
├── design/                # Material3 theme, tokens, typography, color
└── testing/               # Shared test fixtures (sample SMS, redacted)
```

The `parser/` and `domain/` modules are pure Kotlin with zero Android deps — enables Kotlin Multiplatform conversion later without refactor.

## Architectural Decisions (locked in)

1. **`RECEIVE_SMS` BroadcastReceiver**, not `READ_SMS` polling — fires the instant an SMS lands.
2. **One small Kotlin object per bank** implementing `BankTemplate`; the regexes are the data. Verified templates anchor whole sentences from real samples. *Provisional* templates (no real sample yet) use field-anchored extraction via `parser/util/Fields.kt`, fixtures labelled `_reconstructed`, and a "provisional" label in the README; promote them when redacted real samples arrive.
3. **DB schema shape:**
   - `transactions` with `enum_flow_id` (0=expense, 1=income), `enum_method_id` (0=manual, 1=sms), `is_hidden`
   - `merchants`, `categories`, `sub_categories`
   - `keywords` table for fast local re-categorization
   - 48h debit/credit pairing for internal transfer detection
   - Optional `sms_account_balance` column for bank-reported balance reconciliation
4. **Zero network permissions in manifest.** Can't accidentally leak data if we literally can't reach the network. CI greps the manifest and the version catalog to keep it that way.
5. **User-driven export.** CSV, JSON, maybe SQLite file dump. No auto-sync.

## Attribution / Ethics

- **Bank logos** in `assets/banks/` are trademarks of their respective banks. Used for identification only. Equivalent to downloading from each bank's own website.
- **Merchant logos** in `assets/merchants/` similarly belong to the merchants. Identification use only.
- All code, regex patterns, UI, icons, and category artwork are original work.

## Priority Banks for v1

Target 80%+ coverage with 7 banks:
1. **Commercial Bank** (`COMBANK`)
2. **Sampath** (`SAMPATH`)
3. **HNB** (Hatton National)
4. **BOC** (Bank of Ceylon)
5. **NTB** (Nations Trust)
6. **Seylan** (`SEYLAN` / `SEYLANBANK`)
7. **DFCC** (`DFCCINFO`)

Later: NSB, NDB, HSBC, StanChart, Amana, Cargills, Union, plus wallets (Frimi, Genie, eZ Cash).

**Status (Sep 2026):** verified on real samples — BOC, PeoplesBank, COMBANK (+Q+), HNB, Seylan, Amana. Provisional (reconstructed formats) — Sampath, DFCC, NDB, NTB, Pan Asia, PeoplesCard. Sender-only (queued to Unknown) — NSB, HSBC, StanChart, Cargills, CDB, Union. Additional real samples arrive via `docs/BANK_SAMPLES_GUIDE.md` and the GitHub Issue template.

## SMS Sample Collection Protocol

Samples live in `samples/raw/` (gitignored — real inbox data, never committed) and `samples/redacted/` (sanitized, committed). Redaction rules:
- Account numbers → keep last 4 digits only
- Names → replace with `[NAME]`
- Phone numbers → `[PHONE]`
- Balances → keep currency + magnitude order, zero the digits (e.g. `Rs 1,234,567.00` → `Rs X,XXX,XXX.00`)
- Transaction amounts → plausible fake values (same magnitude, different digits)

Full guide for contributors in `docs/BANK_SAMPLES_GUIDE.md`.

## How Codex Should Work In This Repo

- **Prefer editing existing files.** No gratuitous new docs.
- **Tests for every parser template.** A template without a sample test is not merged.
- **Don't add backend/network dependencies.** If you find yourself reaching for Retrofit/Ktor, stop and ask.
- **When adding a bank template**: (1) add raw sample to `samples/raw/` locally, (2) redact and commit to `samples/redacted/`, (3) write template, (4) write test using the redacted sample.
- **Anti-drift**: skim this file at the start of any non-trivial session to avoid re-proposing discarded paths (AI mode, iOS-parallel, Flutter/RN, Play-Store-primary).
- **Competitors are format evidence, not source.** KoSalli (closed-source) and Prabhava Labs' Flutter "Salli" (MIT) may be studied for sender IDs and field wording; never paste their regexes or code. Same clean-room rule as PennyWise.
- **Coverage check against a real inbox**: dump with `adb shell content query --uri content://sms/inbox --projection _id:address:date:body > samples/raw/inbox.txt` and run `SALLI_RAW_INBOX=$PWD/samples/raw/inbox.txt ./gradlew :parser:test --tests '*RawInboxRunner*'` (the runner reads that raw output directly). Dumps and reports stay in the gitignored `samples/raw/`.
