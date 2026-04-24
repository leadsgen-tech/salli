# Smoke test · fresh install · 2026-04-23

Walked the app end-to-end after a clean `adb uninstall` + `adb install`, real inbox (1,082 SMS, 382 parsed, BOC + PeoplesBank accounts detected). Documenting what works and what doesn't so the fix pass has a punch list.

## ✅ Works end-to-end

- Onboarding: Ayubowan greeting, privacy promise, mode picker, permission ask, live import with progress + account discovery, success page → Home lands correctly
- Home: balance hero, greeting row ("Good evening" / "You've spent … today" variants), trend tiles (WEEK + MONTH), accounts strip, MonthSummaryCard, Top Spenders, Recent Activity
- Timeline: date-range selector, income/expense pills, daily totals, per-row transfer source→destination, inline fee ("Fee Rs 25.00"), search top-bar toggle
- Insights: donut animates in, category rows with colored icon chips and progress bars, date-range prev/next steps correctly (March shows different totals)
- Budgets: empty state on fresh install
- Settings: YOU / REVIEW / WHAT'S NEXT / YOUR DATA / ABOUT sections all render; Unknown badge updates live (6 count)
- Unknown SMS screen: lists all 6 pending with sender, body, timestamp, ignore/report actions
- Transaction detail sheet: date, amount, account, balance, category chips, note field, raw SMS
- SMS auto-logging (verified in step 7 via DEBUG_INJECT_SMS) still works unchanged

## 🐛 Bugs found

### 1. Onboarding still offers AI mode despite feature flag off
**Severity:** medium (misleads users into choosing a parked feature)
**Location:** `OnboardingScreen.kt` · `ModePage`
**Repro:** fresh install, advance past privacy page
**Symptom:** Page 3 shows both Standard and AI mode cards. Selecting AI does nothing meaningful afterward because AI surface is parked behind `FeatureFlags.AI_ENABLED = false` in Settings.
**Fix:** gate the AI card on `FeatureFlags.AI_ENABLED`, or skip Page 3 entirely when the flag is off.

### 2. PeoplesBank parser drops to TransactionType.OTHER on some messages
**Severity:** high (hides large transactions behind "Uncategorised")
**Location:** `PeoplesBankTemplate.kt`
**Evidence:** 5 April rows totaling Rs 158,540 land in Uncategorised because `type_id = 99 (OTHER)`, which TypeCategorizer's `when` branch returns `null` for. DB query:

```
merchant_raw  amount    sender        type  flow
              19000000  PeoplesBank   99    1  (INCOME)
              12008703  PeoplesBank   99    2  (TRANSFER)
              1000000   PeoplesBank   99    1
              500000    PeoplesBank   99    1
              360000    PeoplesBank   99    1
```

Empty merchant + type=OTHER means our template matched amount/direction but couldn't identify the sub-type. Either (a) the template's type-dispatch misses patterns, or (b) the merge pipeline produced a placeholder.

### 3. PeoplesBank parser misses 6 valid transaction SMS
**Severity:** high (missed transactions = wrong balance math)
**Location:** `PeoplesBankTemplate.kt`
**Evidence:** all 6 Unknown SMS rows are valid People's Pay transactions in three sub-patterns:

- **6-asterisk account masks** (`280-2001******68` instead of `280-2001****68`) — our regex likely expects 4 exactly
- **"Just Pay Transaction"** transaction type — not in our template's recognised TYPE set (we know LPAY Tfr, PeoPAY Tfr, CDM, POS, ATM, Cash payment)
- **"(Reversal)"** suffixed credits — `Credited (Reversal) by Rs. 0.99 (Just Pay Transaction…)` has no Av_Bal and unusual wording

Real examples from the user's inbox:

```
A/C 280-2001******68 has been credited by Rs. 114631.79 (LPAY Tfr @23:37 11/01/2026)
A/C 280-2001****68 has been Credited (Reversal) by Rs. 0.99 (Just Pay Transaction @07:10 20/02/2026)
```

### 4. UX · Timeline search is date-range-scoped
**Severity:** low (not wrong, but surprising)
**Location:** `TimelineViewModel.kt`
**Repro:** search "Keells" in April 2026 when Keells purchases live in March → "No matches"
**Symptom:** the empty state text (`Try a different search term or widen the date range`) hints at the issue, but a user typing a merchant name almost certainly wants to see every instance across all time.
**Options:**
- Make search span all history by default (widen the range to the whole DB while a query is active)
- Or add a "Widen to all time" chip in the empty state
- Or add a time-range toggle within the search UI

### 5. Uncategorised also holds ~14 POS merchants we don't seed
**Severity:** low (expected; user can teach via detail sheet)
**Location:** `SeedKeywords.kt`
**Evidence:** DISSANAYAKE SUPER CENTER, DINEMORE, INFINITE TRADING, S T T AUTO, M M M ILHAM, Fresh Hot, SISIRI ENTERPRISES, WEBXPAY — real SL merchants not in our seed list. Expected long-tail; the fix is a "teach Salli" merchant-alias flow in the transaction detail sheet (already queued as post-v1 polish).

## Fix priority

1. **Bug #3 + #2** together — PeoplesBank parser gaps. Biggest impact (surfaces ~11 hidden transactions for this user). Needs new regex variants + fixture tests.
2. **Bug #1** — onboarding AI gate. One-liner.
3. **Bug #4** — search scope. Small UX PR.
4. **Bug #5** — merchant seed expansion. Low ROI compared to the parser fixes.
