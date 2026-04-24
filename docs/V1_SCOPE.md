# Salli v1 — Scope

Scope of the first release. If a feature isn't here, it's not v1.

## Thesis

> A complete Sri Lankan SMS expense tracker — on-device, open source, honest about privacy.

Salli reads bank SMS, turns them into a coherent financial picture, and never touches the network (except for one optional future AI-mode model download, parked behind a feature flag for v1).

## What v1 ships with

### The tabs

**Home (Snapshot)**
- Personalized "Hey [name], you spent Rs. X today"
- Balance hero (total across accounts) + month delta
- Week / month trend indicators with arrows (↗ / ↘) and % vs prior period
- Accounts strip — horizontal carousel of bank-branded cards
- **Top spenders** — "Where your money went this month" with top 2–3 categories
- Recent activity — last N transactions, tap to open detail

**Timeline**
- Search icon in the top bar (live filter)
- Income / Expenses pills at top — totals for the active date range
- Grouped by day with a **daily net total** beside each date header
- Transaction rows with merchant logos
- Transfer rows show `Source → Destination` and a `Bank fee: Rs X` secondary line
- Tap to open detail sheet (already shipped)

**Insights**
- Date range picker (prev/next month arrows, calendar for custom range)
- Donut chart of category spend with category icons around the ring
- Below chart: sorted category list with % of total and a horizontal progress bar
- All-accounts filter at top

**Budgets**
- Monthly cap per category
- Live progress bar; warning state when > 80%, over-budget state when ≥ 100%
- Create / edit / delete a budget (simple form)
- Add-budget FAB

**Settings** (already shipped)
- Parse mode section replaced by a "Coming soon" tile
- Export CSV, Delete all data, About

### Cross-cutting UI

- **Global date range picker** — `Aug 22 – Sep 21` header format with prev/next arrows. Applied on Timeline, Insights, Budgets. Home is always "this month" + "today".
- **Account filter dropdown** — "All accounts" default; scopes all views to one account or everything.
- **Unknown SMS review queue** — separate screen accessed from Settings. Shows raw SMS that didn't match any template with three actions: Ignore, Is transaction (opens manual-entry form pre-filled), Report template (logs for future parser expansion).

### Parser coverage

Banks in v1:
1. BOC — full (ATM, CDM, cheque, online transfer, CEFT, ACH)
2. PeoplesBank — full (POS, CDM, ATM, mobile pay, fund transfer, bill, QR)
3. COMBANK — **partial**: card purchase + declined only; transfers / ATM / CDM / bill payments / incoming credits still need samples
4. Sampath
5. HNB
6. NTB
7. DFCC

Each new template = samples collected (redacted), regex written, ≥3 unit tests passing.

### Categorization quality

- Expanded SL-specific keyword seed: Dialog, Mobitel, SLT, Hutch, CEB, Water Board, Uber, PickMe, Kapruka, Keells, Cargills, Arpico, Glomark, Laughs, Food City, Perera & Sons, Crescat, Apple.com/Bill, Google, Netflix, Spotify, reload providers, common petrol stations.
- "Teach Salli" flow on transaction detail — when user re-categorizes, we save a merchant→category alias so the same merchant lands correctly next time.

### Shipping polish

- Real app icon (not stock droid)
- Launcher name finalised (`Salli`)
- F-Droid metadata: `fastlane/metadata/android/en-US/` with title, short/long description, screenshots, changelog
- Reproducible build config
- Signing key outside the repo
- README with screenshots + install instructions + bank coverage table + contribute-a-bank-template link

## Non-goals for v1 (explicit)

These get a hard "no":

- **LLM / AI mode** — parked. `FeatureFlags.AI_ENABLED = false`. Code stays, UI doesn't surface it.
- **Chat / Ask Salli** — parked with AI.
- **Cloud / backend / sync / auth** — ever.
- **Play Store as primary** — F-Droid first; Play is a post-v1 consideration if/when permission stories allow it.
- **iOS** — v2+.
- **Subscription detection screen** — COMBANK-specific and killed earlier.
- **Generic "Explore" / feed tab** — no compelling content for v1; de-scope unless it proves essential later.
- **Multi-currency conversion** — we display USD if COMBANK surfaces it, no FX conversion.
- **Bill due reminders** — v1.1.

## Build order

One focused session per step. Each step is independently shippable (no half-finished features left in the user's face).

1. **Insights screen** — donut + category list + progress bars. Unblocks the shape of the app. Uses Vico (already in deps).
2. **Date range picker** — shared component consumed by Insights first, Timeline and Budgets later. Prev/next month + calendar picker.
3. **Timeline polish** — daily totals, income/expense pills at top, search bar in top app bar.
4. **Top spenders on Home** — categories mini-cards. Uses the same aggregation logic as Insights.
5. **Budgets** — screen + data flows. Monthly caps per category, progress bars.
6. **Trend indicators + personalized greeting** — on Home. Week/month deltas, "Hey [name]" (name from a new settings field, defaults to empty → no greeting).
7. **Unknown SMS queue** — review screen, triage actions.
8. **Account filter + transfer fee display + source→destination** — visible polish across views.
9. **Categorization keyword seed expansion** — lift quality across Insights and Budgets retroactively.
10. **Parser coverage expansion** — Sampath, HNB, NTB, DFCC (requires real samples; may parallelise with contribution outreach).
11. **Shipping polish** — icon, F-Droid metadata, README, screenshots.

## Definition of done

v1 ships when:

- A new user, first install → onboarding → SMS permission → historical import lands them on a Home screen that shows their real balance, their top spending categories this month, and a greeting that uses their name.
- They can open Insights and see a correct donut chart with the right categories and percentages.
- They can set a monthly food budget and see a live progress bar as they spend.
- They can scroll Timeline with a date range picker and see daily totals.
- Their inbox has SMS from BOC / PeoplesBank / COMBANK / Sampath / HNB / NTB / DFCC and Salli recognises all of them. Anything else lands in Unknown SMS for them to triage.
- They can uninstall at any time with zero data loss risk (export CSV before, SQLite dump optional).
- The APK is on F-Droid, reproducibly built, signed outside the repo.
- The repo has a README that a new contributor can read and understand the architecture, the bank-template contribution flow, and the privacy invariants in under 10 minutes.
