# Tranzlate Clean-Room — Build Roadmap

> Realistic plan built on **evidence** from this session: main works but architecture is broken (76 code findings, crash-on-launch on develop, 58/58 core UI patterns non-KEEP); the clean-room spec methodology is proven (Text template converged 55%→~80% via spec→review→reconcile).
> Status: 2026-07-21 · uncommitted · solo developer + AI assist (Fable 5 for build).

---

## 0. The one hard truth first
A full rewrite is **months of work**, and the #1 rewrite-killer is starting code before the specs+architecture are locked. **main already works and ships** — so this is not an emergency rewrite; it is a deliberate re-architecture. Therefore: **keep main shippable throughout**, and build the clean-room in parallel, spec-first.

## 1. Proven production method (per feature)
```
capture (dirty-room, main app)  →  spec (GT-behaviour lead + cite foundations)
        →  adversarial review (4 roles: eng/design/qa/a11y)  →  reconcile to canonical conventions
        →  re-review  →  LOCK "buildable"
```
Cost shape: **foundations = one-time** (shared by all features); **per-feature ≈ spec + its strings + its test-contract + one reconcile pass** (cheap now that conventions exist).

## 2. Phases

### Phase F — Foundations (one-time · ~60% done)
| Item | Status |
|------|--------|
| DECISIONS + canonical conventions (C-1..C-13) | ✅ |
| DATA_MODEL (typed entities, engine map) | ✅ |
| DESIGN_SYSTEM (tokens, adaptive dims) | ✅ (designer confirms buildable) |
| Architecture cruxes: modes · FeatureAccess · UsagePolicy · Ads-pipeline | ⏳ design docs to finalize |
| Per-feature string catalogue + test contract templates | ⏳ (Text done; template proven) |
| **Screenshot-test harness (JVM)** — the gate for dark/light · contrast · RTL · 200% text | ⏳ [#20](https://github.com/sendtodilanka/tranzlate-app/issues/20) — none exists; TEST_A11Y gates 11-12 unreachable without it |
| **Performance foundation** — detectors (LeakCanary · StrictMode · Compose metrics) → R8 → Macrobenchmark → Baseline Profiles | ⏳ [#22](https://github.com/sendtodilanka/tranzlate-app/issues/22) — sequenced on purpose: we cannot measure until R8 (#5) gives us a build we actually ship |
| Dirty-room capture completion (dark/locales/secondary/error states) | ⏳ |
| GT gold-reference capture (optional, strengthens every behaviour spec) | ⏳ optional |

### Phase S — Spec production (per feature, proven loop)
Order (core value first):
1. **Text** ✅ (template, near-locked) · **Camera/OCR** · **Voice** · **Dialog**
2. Language picker · History/Saved · Download/offline-models
3. Settings (+ themes/speech/about) · Onboarding · Paywall/Subscription · Collections
→ ~12 feature specs. Each: ~1 focused spec + 1 review loop.

### Phase D — Design production
Figma per feature from DESIGN_SYSTEM tokens (designer role already unblocked). Runs parallel with late Phase S.

### Phase C — Code build (greenfield — write everything fresh, **no code copied from Tranzlate**)
New module structure (per cruxes / CLAUDE Phase 7 split). Build order:
1. `:core` — modes orchestrator, FeatureAccess, UsagePolicy, Result types, DI
2. `:data` — Room (re-derive entities + 180-lang catalog + migrations fresh), clients (write to current MLKit/Retrofit API docs), DataStore
3. Feature by feature (spec-driven, tests alongside — contracts exist)
4. Integrations (Qonversion, AdMob, Firebase) behind the gateways
5. ~~Adaptive/nav shell (NavigationSuiteScaffold)~~ → **nav shell = a bare Nav3 `NavDisplay`; Home's card stack carries the IA** (DECISIONS **D-5 rev.3**, 2026-07-26 · issue #42). Adaptive (Medium/Expanded) layout is a **separate, undesigned** piece of work — do not assume `NavigationSuiteScaffold`.

**Re-derive fresh, verify vs standards (NEVER copy Tranzlate code — Rule 1):** language catalog (verify BCP-47 + capabilities) · entity shapes (design clean) · client integrations (write to current API docs) · strings/copy (rewrite per Material UX writing) · brand direction (redesign per DESIGN_SYSTEM). Tranzlate = suspect reference for behaviour only.
**Rebuild:** all ViewModels · navigation/IA · subscription/ads/usage layers · onboarding · paywall · every screen.

### Phase H — Hardening & launch
a11y gate · NEEDS-TRANSLATION completion (fil/pt-rBR) · adaptive/foldable testing · CI (Detekt/Spotless/tests) · staged release.
> The a11y gate here **depends on the Phase F screenshot harness ([#20](https://github.com/sendtodilanka/tranzlate-app/issues/20))** — contrast, RTL and 200%-text are gates 11-12 of the TEST_A11Y contract and cannot be asserted without it. Landing it late means hardening becomes a manual sweep of every screen.
> Same shape for performance ([#22](https://github.com/sendtodilanka/tranzlate-app/issues/22)): startup and jank numbers are only meaningful on a minified release build, so R8 (#5) gates the measurement, and the measurement gates any optimisation worth keeping. Android Vitals covers the half no local benchmark can — real devices, real users.

## 3. Recommended strategy — two tracks

**Track 1 — protect production (days):** verify whether main (shipped) is hit by the revenue/consent findings (sample AdMob ids, paywall-Idle, consent auto-grant likely affect main too — the crash does NOT). If so, small targeted fixes to main via PR. Keeps revenue/trust intact while the rewrite proceeds.

**Track 2 — clean-room MVP first (then expand):** don't spec+build all 12 at once. Ship a **correct MVP subset**:
> **MVP = Text + Camera + Language picker + History/Saved + Subscription + core engines (Offline/Standard/Advanced) + the nav shell.**
> *(2026-07-26 · D-5 rev.3: "adaptive nav shell" now means the `NavDisplay` + Home card stack. **History/Saved have no entry point** since the drawer was removed — that has to be part of their build, not an afterthought.)*
Defer Voice, Dialog, Collections, Download-manager polish to v2. This gets a *correct, shippable* app fastest, proves the new architecture end-to-end, then scales.

## 4. Effort realism (honest)
- Foundations + architecture cruxes: **~1–2 weeks** to finalize (mostly done).
- Spec production (MVP ~6 features): **~1–2 weeks** with the proven loop.
- Design: **~1–2 weeks** (parallel).
- Code build (MVP): **the bulk — many weeks**, solo + AI.
- This is a real project, not a weekend. The specs de-risk it; they do not shrink the code.

## 5. Decision gates (STOP-and-decide points)
1. After foundations lock → commit to MVP scope (this doc's §3) or full.
2. After MVP specs lock → build vs. re-evaluate (is the rewrite still worth it vs. targeted refactor of main?).
3. After MVP build → ship MVP, measure, then expand.

> **Anti-goal:** do NOT delete or stop shipping main until the clean-room MVP is at parity and shipped.
