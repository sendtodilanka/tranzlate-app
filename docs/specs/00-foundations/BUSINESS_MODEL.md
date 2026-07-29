# Business Model — Free + Pro (single source of truth)

> Status: **accepted** — owner-decided in session 2026-07-29 ("Oyage recommendation decisions use karanna" + "Lifetime membership ekak nam oni na"), issue #50.
> Consumers: the Access brain (`FeatureAccess`), the Usage brain (`UsagePolicy`), the Ads brain (`AdsCoordinator`), `:feature:paywall`, and the C-11 at-limit sheet. Every tier/quota/trigger in code cites this file.
> Related decisions: D-2 rev.2 · D-4 · C-10 rev.2 · C-11 (DECISIONS.md).

## 1. The principle: value fence = cost fence

The free tier runs entirely on engines with **zero marginal cost**; Pro pays for the engines that cost money. A million free users must never grow the API bill.

| Engine | Our cost | Placement |
|---|---|---|
| MLKit offline | 0 | Free, unlimited — **and strategic**: every offline user is an API bill of zero |
| GOT (free online) | 0 (unofficial; kill-switch — see spec 02) | Free, unlimited |
| GCT / future LLM | paid per character | **The fence.** Free = 5/day taste · Pro = unlimited* |

\* "Unlimited" carries a hidden fair-use cap (`limit_pro_fair_use`, RemoteConfig) purely as abuse protection. It is never marketed and set high enough that no honest user meets it.

## 2. Tiers — exactly two

**`FREE` + `PRO`.** The former PLUS/PREMIUM split (D-2 rev.1) is dropped: tier proliferation adds choice overload where it hurts conversion. Choice lives in **billing options** instead, where anchoring does useful work.

**No lifetime purchase** (owner, 2026-07-29): a lifetime buyer caps LTV while the API-cost liability recurs forever. Subscription-only also keeps `PurchaseFlow` single-path — no one-time-product branch in billing code.

| | FREE | 💎 PRO |
|---|---|---|
| Offline translate (MLKit) | ✅ unlimited | ✅ |
| Online translate (GOT) | ✅ unlimited | ✅ |
| Interstitial ads (D-4) | shown | **none** |
| AI-quality translations (GCT, later LLM) | **5/day** (`limit_free_ai`) | unlimited (fair-use cap) |
| Character limit per translation | **500** (`text_limit_free`) | **5,000** (`text_limit_pro` — the design export's own "0 / 5000") |
| Camera translate | ✅ (with ads) | ✅ (no ads) |
| Natural phrasing (future LLM) | 🔒 locked (trigger #2) | ✅ |
| Conversation mode (v2) | limited | ✅ |
| Offline language packs | ✅ **free** | ✅ |
| History / Saved | ✅ | ✅ |

Offline packs stay free deliberately: the flagship brand *is* "Offline Translator", and pushing free users to the offline engine is itself the cost-control strategy.

## 3. Billing options — one tier, three periods

| Option | Role | Notes |
|---|---|---|
| Weekly | **honest anchor** + traveler fit | A two-week trip doesn't need a year. Priced so the per-day rate makes Yearly obviously better |
| Monthly | middle option | |
| **Yearly** | **pre-selected · 7-day free trial · "SAVE ~60%" badge** | The intended choice; annual pre-selection is the single highest-LTV lever (industry data: 60–70% pick annual when pre-selected) |

- **Trial: 7 days, yearly only.** Endowment + loss aversion do the converting; a trial-reminder notification on day 5 keeps trust (fewer refunds and 1★ reviews).
- **Intro offer** (honest scarcity): Play intro pricing for new installs — real, per-user, time-boxed. No fake countdowns.
- **Win-back**: Play win-back offers for lapsed subscribers.
- Prices are **never hardcoded**: Play Console per-country price templates (global audience, purchasing-power aware) + Play price experiments. Per-brand product ids live in flavor config (white-label rule).

## 4. Paywall screen (`:feature:paywall` — currently orphaned; this is its spec)

```
[✕]                                  ← Play policy: dismissible, always
hero: "Translate like a Pro"
✓ No ads   ✓ Unlimited AI quality   ✓ 5,000 characters   ✓ Natural phrasing
┌──────────┬──────────┬───────────────────────┐
│ Weekly   │ Monthly  │ ★ YEARLY              │
│          │          │ 7-day free trial      │
│          │          │ SAVE ~60% · pre-selected │
└──────────┴──────────┴───────────────────────┘
per-day price framing · "Cancel anytime"
[ Start free trial ]                 ← CTA text follows the selected option
Restore purchases · Terms · Privacy
```

Copy rules: benefit-led bullets (what the user gets), never feature-led (what we built). Price-per-day framing on Yearly. "Cancel anytime" visible — reassurance raises conversion and Play requires easy cancellation anyway.

## 5. Trigger map (ranked by conversion intent)

| # | Moment | Surface |
|---|---|---|
| 1 | **AI daily quota hit** | C-11 bottom sheet — *in place*, free engines keep working underneath; never a navigated block |
| 2 | Locked feature tap (Natural phrasing) | paywall |
| 3 | Post-interstitial "Remove ads?" link | paywall |
| 4 | Onboarding end | **skippable** paywall — warm intent, never forced |
| 5 | Settings row + Home "Pro" chip | paywall — passive, always available |

Goal-gradient support: the composer shows the remaining AI quota (e.g. "AI translations: 3/5 left today") whenever an AI-quality result is on screen — awareness of the meter is what makes trigger #1 land as expected rather than as a surprise.

## 6. What we deliberately do NOT do

No fake countdowns · no hidden auto-renew · no cancellation mazes · no silent-charge trials · no pre-checked add-ons. These earn Play policy strikes, refunds and 1★ reviews — and this product lives on organic ASO + ratings. Persuasion here means **showing real value at the moment it is felt**, not deception. (This section is normative: a PR that adds a dark pattern contradicts an accepted spec.)

## 7. Config keys (the contract the brains implement)

| Key | Default | Consumer |
|---|---|---|
| `limit_free_ai` | 5 / day | Usage brain — free tier's GCT/LLM pool (D-2 rev.2) |
| `limit_pro_fair_use` | high (unpublished) | Usage brain — abuse guard |
| `text_limit_free` / `text_limit_pro` | 500 / 5000 | composer counter + Translate gate |
| `trial_days` | 7 | paywall copy (billing source of truth = Play) |
| `ad_nth` / `ad_min_gap_s` / `ad_daily_cap` | 2 / 90 / 12 | Ads brain (D-4, unchanged) — **suppressed entirely when `isPaid()`** |
| per-brand product ids | flavor config | `PurchaseFlow` offerings (white-label) |

Quota reset: per-day at local midnight, `AppClock`-driven (D-2 mechanics unchanged).

## 8. Decision log

| Date | Decision | By |
|---|---|---|
| 2026-07-29 | Tiers 3→2 (FREE+PRO) · free AI 5/day · trial 7-day yearly-only · paywall layout B (Weekly/Monthly/Yearly) | owner ("Oyage recommendation decisions use karanna") |
| 2026-07-29 | No lifetime purchase — subscription-only | owner ("Lifetime membership ekak nam oni na") |
