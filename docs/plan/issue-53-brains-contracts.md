---
status: accepted   # owner: "Ok" (2026-07-29) to "file A1–A9 issues and start the contract-fix batch" after reading the architecture review
issue: 53
title: Brains-phase contract fixes (A1–A9) — reshape the seams before building the engines
date: 2026-07-29
author: Claude (Opus 5) · evidence = 2026-07-29 architecture review (3 independent agent lenses + in-session spot-verification of every claim used here)
---

# Plan — issue #53

> Nine contract-shape defects stand between today's stubs and the brains phase. Writing the engines first would mean writing them against the wrong seams and rebuilding. This plan fixes the seams in five small PRs, then the engines land on solid contracts.

## 1. Why contracts first

The review's core insight: **three of the four brains are permissive stubs, but their *interfaces* are also the wrong shape** — a real implementation is impossible without widening them:

- `FeatureAccess.tier` is a synchronous `val`; billing entitlement is a `Loading → resolved` flow. A real impl must block or lie (default FREE while loading — the old app's exact production bug).
- `UsagePolicy.isOver()`/`remaining()` are sync over a DataStore that is async; there is no atomic check-and-spend, so the GCT budget gate (C-10 rev.2) cannot be built on it.
- `TranslationOutcome` cannot say *why per tier* — the owner's no-dead-end dialog ("MLKit: fr not downloaded · GOT: offline") has nowhere to carry its data.
- `TranslateTextUseCase` calls the engine before ever consulting the cache, so C-8/D-1's "no charge on a cache hit" is unimplementable at the current call order.

## 2. PR batches

### PR 1 — mechanics (A5 · A6 · A8)
- **A5**: `TextViewModel.startTranslation` — `withContext(dispatchers.default)` → `dispatchers.io`. The engine call becomes network IO in the brains phase; `Default` is the CPU pool. Tests unaffected (`TestDispatcherProvider` maps every lane to the same test dispatcher).
- **A6**: `core/ads/build.gradle.kts` + `implementation(projects.core.datastore)` — D-4's counters (`adsShownToday`, `adLastShown`, `translationsSinceAd`) live in `:core:datastore`; without this edge `RealAdsCoordinator` cannot be built at all.
- **A8**: `DatabaseModule` + `fallbackToDestructiveMigration(...)` **pre-launch only** — Room is at version 1 with no policy, so the first schema bump (PR 2 bumps it!) would crash every existing install with `IllegalStateException`. Before first release this flips to real `Migration` objects; a comment marks that gate. DATA_MODEL.md gets the policy note.

### PR 2 — cache-first + atomic dedupe (A2 · A9)
- **A9**: `TranslationEntity` cache index becomes `unique = true`; `TranslationDao.insert` → `OnConflictStrategy.IGNORE`; schema version 2 (safe under PR 1's pre-launch fallback). Read-then-insert races collapse into the DB constraint.
- **A2**: `TranslateTextUseCase` re-sequenced:
  ```
  normalize → cache read (engine-agnostic, per the owner's pipeline) 
      HIT  → Success(fromCache=true) — no gate, no engine, no quota, no duplicate write
      MISS → availability/quota gate → engine → on success: quota + history + ads
  ```
  Cache-hit ads question (D-4 "completions") — **owner decision recorded 2026-07-29: pending**; until decided, cache hits do NOT ask the Ads brain (conservative, revisit with the Ads work).
- The `srcLang == "auto"` skip stays (a resolved pair arrives only with the engines phase — Language ID lands there).

### PR 3 — Access contract (A1 · A7 · D-2 rev.2 alignment)
- `Tier { FREE, PLUS, PREMIUM }` → `{ FREE, PRO }`; `Entitlement.Paid(tier)` follows; `RemoteConfigDefaults.LIMIT_FREE=20 / LIMIT_PLUS=100` → `limit_free_ai=5 / limit_pro_fair_use` (BUSINESS_MODEL.md §7).
- `FeatureAccess` reshaped around the Loading rule (DATA_MODEL:50):
  ```kotlin
  interface FeatureAccess {
      val entitlement: Flow<Entitlement>          // Loading | Free | Paid(PRO)
      suspend fun awaitResolved(): Entitlement    // gate helper — never decides on Loading
      fun isEngineAllowed(mode: ModeId): Boolean  // kept for the fake-variant contract tests
  }
  ```
  (exact shape may be adjusted in-PR; the invariant that cannot move: **callers can always await a resolved value, and nothing defaults to FREE while loading**.)
- **A7**: `RealFeatureAccess` observes the same `SubscriptionGateway.entitlement` the purchase flow uses — one source of tier truth. ~~Unknown tier strings **fail closed to FREE with a log**, never silently to a paid tier.~~
  **rev.1 (PR #58, cross-model lens OPEN-1):** the unknown-string clause was written for the three-tier world, where a wrong guess could grant the *wrong paid tier*. D-2 rev.2 has one paid tier, which inverts the failure analysis: a provider-verified `Paid` is a real purchase, so mapping it to FREE would **strip a paying subscriber** — the worse billing failure. Shipped rule: the string→tier branch is **deleted**, any provider `Paid` → `Paid(PRO)`; there is no unknown-id path left to fail open *or* closed (`EntitlementMapping.kt`, table-tested incl. legacy `"plus"/"premium"` ids).
  **Engines-phase requirement (lens N1):** a real gateway can hold `Loading` at cold start — the metered gate's `awaitResolved()` needs a bounded wait + error outcome there (EDGE_CASES no-dead-end); safe today only because the NoOp gateway starts resolved.
- TEST_A11Y_CONTRACT §1.3's fake matrix + the superseded banners get their real rewrite here.

### PR 4 — Usage contract (A4)
```kotlin
interface UsagePolicy {
    val remaining: Flow<Int>                    // meter for the "{left}/5 today" UI
    suspend fun trySpend(): SpendResult         // ATOMIC check-and-spend — the only gate
}
```
One suspend that checks and spends under a single mutex/DataStore transaction kills the double-tap double-spend race. Midnight reset compares `AppClock.today()` against `usage.reset_epoch` inside the same transaction. `warningMessage()` retires (UI derives it from `remaining`).

**rev.1 (shipped in-PR adjustments):** (1) `trySpend(tier: Tier)` takes the RESOLVED tier — closes PR-58 lens N2: FREE spends the `limit_free_ai` pool, PRO the independent fair-use pool, decided per call from `awaitResolved()`'s return. (2) `refund(tier)` added — DECISIONS' success-only constant survives the atomic-upfront spend: spend at the gate, refund on failure *and* on cancellation (`NonCancellable`), so the net charge lands on success only. (3) Counters are in-process this batch (strictly better than the never-counting placeholder); the DataStore transaction lands with the brains implementation (TODO(#4-brains) in `RealUsagePolicy`).

### PR 5 — outcome taxonomy (A3)
```kotlin
sealed interface TranslationOutcome {
    data class Success(..., val fromCache: Boolean, val detectedSource: String?)
    data class Error(val attempts: List<EngineAttempt>)    // the waterfall trace
    data object NotEntitled                                // ≠ LimitReached
    data object LimitReached
}
data class EngineAttempt(val engine: Engine, val cause: AttemptCause)
enum class AttemptCause { MODEL_NOT_DOWNLOADED, OFFLINE, TIMEOUT, UNSUPPORTED_PAIR, ENGINE_ERROR, SKIPPED_NO_QUOTA, SKIPPED_SOURCE_UNKNOWN }
```
This is the type the owner's dialog reads ("MLKit: fr not downloaded · GOT: offline → [Download French]"). The UI mapping (per-cause copy + CTA per EDGE_CASES §4) rides in this PR for the causes that exist today; the engines phase fills the rest. `TextViewModel`'s `LimitReached → generic ENGINE error` masking dies here.

## 3. Explicitly out of scope
- The engines themselves (MLKit adapter, Language ID, GOT, GCT, timeouts, kill-switch) — next phase, on these contracts.
- Real `ConnectivityMonitor` + the Availability resolver wiring — engines phase (they gate the waterfall).
- The verified UX dead-ends (silent Paste, Swap-on-Detect visual, TalkBack over-limit regression) — separate small issue; not contract work.
- White-label config consumption gaps — separate issue.

## 4. Risks
| # | Risk | Mitigation |
|---|---|---|
| 1 | Contract reshapes ripple into the fake variant + contract tests | Each PR updates `core/translate-fake` + `core/testing` in the same commit; the golden `TranslateTextUseCaseTest` suite is the acceptance gate |
| 2 | Destructive migration (PR 1) later forgotten and ships | Loud comment + DATA_MODEL policy note: "flips to real Migrations before first release" — release checklist item |
| 3 | A3's causes speculative before engines exist | Only causes with a consumer today get UI mapping; the enum is additive |
| 4 | High-risk area (billing/usage — Rule 5) | PR 3 and PR 4 get adversarial co-verify + cross-model lens before merge |

## 5. Acceptance (per PR)
Unit: `TranslateTextUseCaseTest` (re-sequenced expectations: cache hit → zero engine calls, zero quota, zero ads ask) · new `UsagePolicy` race test (two concurrent `trySpend()` with 1 remaining → exactly one SPENT) · `FeatureAccess` Loading-gate test (no decision before resolve). Device: fake-variant flow unchanged end-to-end (golden "Good morning" → result). Gates: spotless · detekt · Konsist · fake+prod compile · CI.
