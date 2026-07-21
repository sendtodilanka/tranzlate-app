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

### Phase C — Code build (clean-room greenfield **with salvage**)
New module structure (per cruxes / CLAUDE Phase 7 split). Build order:
1. `:core` — modes orchestrator, FeatureAccess, UsagePolicy, Result types, DI
2. `:data` — Room (salvage entities + 180-lang seed + migrations), clients (salvage MLKit/Retrofit logic), DataStore
3. Feature by feature (spec-driven, tests alongside — contracts exist)
4. Integrations (Qonversion, AdMob, Firebase) behind the gateways
5. Adaptive/nav shell (NavigationSuiteScaffold)

**Salvage (clean + carry):** language seed data · Room entities/migrations · MLKit/Google/Cloud client logic · string resources (many already fil/pt-rBR) · brand assets.
**Rebuild:** all ViewModels · navigation/IA · subscription/ads/usage layers · onboarding · paywall · every screen.

### Phase H — Hardening & launch
a11y gate · NEEDS-TRANSLATION completion (fil/pt-rBR) · adaptive/foldable testing · CI (Detekt/Spotless/tests) · staged release.

## 3. Recommended strategy — two tracks

**Track 1 — protect production (days):** verify whether main (shipped) is hit by the revenue/consent findings (sample AdMob ids, paywall-Idle, consent auto-grant likely affect main too — the crash does NOT). If so, small targeted fixes to main via PR. Keeps revenue/trust intact while the rewrite proceeds.

**Track 2 — clean-room MVP first (then expand):** don't spec+build all 12 at once. Ship a **correct MVP subset**:
> **MVP = Text + Camera + Language picker + History/Saved + Subscription + core engines (Offline/Standard/Advanced) + adaptive nav shell.**
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
