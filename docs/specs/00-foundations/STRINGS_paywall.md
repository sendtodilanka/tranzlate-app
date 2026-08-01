# String Catalogue Foundation — Paywall (`:feature:paywall`)

> Tranzlate · feature: **Paywall / upgrade screen** (D-2 rev.2 FREE + PRO, BUSINESS_MODEL.md §5)
> Locales: `en` (default), `fil` (Filipino), `pt-rBR` (Brazilian Portuguese)
> Resources: `feature/paywall/src/main/res/values/strings.xml` (+ `values-fil/`, `values-pt-rBR/`)

C-3 makes this file the key authority for every string `:feature:paywall` ships. Written after the
resources rather than before — the catalogue was the piece issue #152 found missing. Every `en`
value is transcribed **verbatim from the shipped resource**.

This is money copy, so the rule that governs it is stricter than "sounds good": **the screen may
never state a number or an outcome the store has not confirmed.** Several keys below exist only
because of that rule, and the notes say which fact each one is standing in for.

---

## 1. Value proposition

| Key | Type | `en` | Args |
|-----|------|------|------|
| `paywall_hero` | string | `Translate like a Pro` | — |
| `paywall_benefit_no_ads` | string | `No ads, ever` | — |
| `paywall_benefit_unlimited_ai` | string | `Unlimited AI-quality translations` | — |
| `paywall_benefit_characters` | string | `Translate up to 5,000 characters at once` | — |
| `paywall_benefit_phrasing` | string | `Natural phrasing that sounds like a local` | — |

> ⚠ `paywall_benefit_characters` names **5,000** and `paywall_benefit_unlimited_ai` promises
> **unlimited**. Both are claims the Access + Usage brains have to make true; if either limit
> lands at a different number, this copy is a false statement on a purchase screen, not a stale
> string. Re-check both when D-2's PRO entitlements ship.

## 2. Plan selection

| Key | Type | `en` | Args | Notes |
|-----|------|------|------|-------|
| `paywall_plan_weekly` | string | `Weekly` | — | |
| `paywall_plan_monthly` | string | `Monthly` | — | |
| `paywall_plan_yearly` | string | `★ Yearly` | — | the star is part of the resource, marking the recommended plan |
| `paywall_cta_continue` | string | `Continue` | — | primary action when no trial applies |
| `paywall_cancel_anytime` | string | `Cancel anytime in Google Play` | — | names where cancelling actually happens |

## 3. Prices and trials — the store is the only source

Prices are never hardcoded. A literal `US$1.99` shows every buyer outside that currency a false
number at the moment money changes hands, so the resource file carries only the states where the
store has **not** answered yet, plus the two trial shapes the store can report.

| Key | Type | `en` | Args | Notes |
|-----|------|------|------|-------|
| `paywall_price_pending` | string | `—` (em dash) | — | placeholder while the query is in flight — an empty row would look broken |
| `paywall_price_loading` | string | `Getting prices from Google Play…` | — | first load |
| `paywall_price_unavailable` | string | `Couldn\u2019t reach Google Play. Tap to try again.` | — | the query failed — retry is on the row itself, no dead end |
| `paywall_plan_unavailable` | string | `This plan isn\u2019t available on Google Play right now. Tap to check again.` | — | the store **answered** and this plan was absent; saying "couldn't reach Play" here would be false |
| `paywall_trial_days` | plurals | `%1$d-day free trial` | day count | store reported a trial expressed in days |
| `paywall_trial_generic` | string | `Free trial included` | — | store reported a trial in months or years, where a day count would be an invention |
| `paywall_cta_trial_days` | plurals | `Start my %1$d-day free trial` | day count | primary action, day-count form |
| `paywall_cta_trial_generic` | string | `Start my free trial` | — | primary action, unquantified form |

## 4. Purchase and restore outcomes (EDGE_CASES, no dead ends)

| Key | Type | `en` | Args | Notes |
|-----|------|------|------|-------|
| `paywall_purchase_unavailable` | string | `Couldn't complete the purchase. Nothing was charged.` | — | terminal failure — the "nothing was charged" half is the part that matters |
| `paywall_purchase_pending` | string | `Payment is being processed. When it clears, reopen the app or tap Restore purchases to unlock Pro.` | — | Google `PENDING`: the buyer **may still be charged**, so this must never say "nothing was charged" |
| `paywall_restore` | string | `Restore purchases` | — | action |
| `paywall_restore_failed` | string | `Couldn't reach the store to restore. Try again later.` | — | transport failure |
| `paywall_restore_nothing` | string | `No purchases to restore on this account.` | — | the store answered, and the answer was empty — a different fact from the row above |

> The three-way split (`paywall_purchase_unavailable` / `paywall_purchase_pending` /
> `paywall_restore_nothing`) exists because each says something different about the user's money.
> Collapsing them into one "something went wrong" is a regression even though it reads tidier.

## 5. Legal and chrome

| Key | Type | `en` | Args | Notes |
|-----|------|------|------|-------|
| `paywall_terms` | string | `Terms` | — | link |
| `paywall_privacy` | string | `Privacy` | — | link |
| `paywall_link_unavailable` | string | `Couldn't open that page. Check your connection and try again.` | — | no browser, or no connection — the link is not allowed to be a dead tap |
| `paywall_cd_close` | string | `Close` | — | contentDescription (C-4) for the icon-only dismiss |

## 6. Translation status (C-12)

Every key above has a `fil` and a `pt-rBR` value in the module's locale files today. Those
wordings were authored in-project, not by a native speaker. **Purchase-screen copy is the highest
priority in the `NEEDS-TRANSLATION` queue** — an inaccurate translation of
`paywall_purchase_pending` or `paywall_purchase_unavailable` misinforms a buyer about a charge.
