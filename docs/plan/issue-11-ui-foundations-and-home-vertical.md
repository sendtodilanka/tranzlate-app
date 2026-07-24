---
status: accepted   # owner-accepted 2026-07-22 (chat: "හරි, පටන් ගන්න")
issues: [9, 10, 11]
title: UI foundations (P8 palette + Translate-button spec) and the Home/Composer/Drawer/Result vertical
date: 2026-07-22
author: Claude (Opus 4.8) · design rounds 1-7 with owner; approved design contract = docs/design/UI_SPEC.md
---

# Plan — issues #9 + #10 + #11

> **One PR-chain, three issues.** Docs-truth first (so specs and code can't disagree), then the approved UI, runnable on the deterministic **fake** variant. No real engines in scope — the Text screen talks to the `Translator` interface, which the fake variant already satisfies with the golden table.

> **⚠ Superseded IA (2026-07-24, issue #26 — D-5 rev.2):** this plan's Compact information architecture — **canvas quick-action tiles (Conversation · Camera) instead of a bottom nav bar** — is **reversed**. The app now uses a **persistent bottom `NavigationBar` (Home / Chat / Camera)** via `NavigationSuiteScaffold`; the quick-action tiles are removed and the drawer is secondary-only. The composer / result-screen / drawer / motion work described below stays reusable and correct — only the top-level nav shell and the canvas-tiles decision change. See DECISIONS **D-5 rev.2** + `docs/design/UI_SPEC.md` §2.1/§2.3.

## 1. Stage A — foundations & spec truth (issues #9, #10)

**A1 · C-2 amendment (#9) — behaviour change.** `DECISIONS.md` C-2 currently mandates live-translate for free engines and a button only for metered. Owner decision (2026-07-22): **explicit Translate action for every engine; the result opens on its own screen.** Reconcile in the same commit:
- `DECISIONS.md` C-2 rewrite + D-0 note (intentional deviation from GT's live-translate, recorded with the reason: consistent action across engines, a large comfortable editor, zero per-keystroke quota risk).
- `01-text-translation.md` §0.2/§2/§4 state machine (`TYPING —debounce→` becomes `TYPING —tapTranslate→`), US-1 acceptance.
- `TEST_A11Y_CONTRACT_text.md` conformance override: free-engine happy path asserts a **button tap**; `tt_text_translate_btn` applies to all modes (the metered-only `tt_text_translate_action` note retires).

**A2 · P8 palette (#10).** `DESIGN_SYSTEM.md` §0/§1.1/§1.2/§2 → P8 tables + ambient teal→blue wash (coral/gold gone); `DESIGN_TOKENS.md` superseded note. Then `:core:designsystem`: `Color.kt` light/dark schemes = P8 verbatim, `surfaceTint = primary`, new `LocalAmbientGradient` token, motion tokens for the drawer/morph. Grep gate: no coral/gold/violet hex anywhere.

**A3 · UI_SPEC.md** (already written) referenced from `DESIGN_SYSTEM.md` as the component-level contract.

## 2. Stage B — shared UI components (`:core:ui`)
Each ships with `@PreviewLightDark` (project convention) and fake data from `:core:testing` so previews render without DI:
`TranzlateTopBar` (☰ · mode chip · action) · `ModeChip` · `QuickActionTile` (compact, grid-scalable) · **`ComposerCard`** (the signature component — text area + full-width control row, mic↔Translate swap, grow-to-max + internal scroll, IME-aware, char counter) · `LanguageChip` + `SwapButton` · `DottedRingIconButton` · `EngineBadge` · `FollowUpChipRow` · `ResultBlock` (label + text + phonetic/transliteration + action row) · `ShimmerResult` · `InlineErrorRetry` · existing `ErrorView`/`LoadingView` restyled.

## 3. Stage C — feature + shell (`:feature:text`, `:app`)
- `TextUiState` (sealed: Idle · Typing · Translating · Result · Error · LimitSheet) + `TextViewModel` injecting `TranslateTextUseCase` (interfaces only; brains stay placeholders).
- `HomeScreen` = ambient background + canvas (greeting/tiles, vertically centred, hides on first character) + `ComposerCard`.
- `ResultScreen` = source block · divider · target block (engine badge, result in `primary`, transliteration, actions) · follow-up chips. No composer.
- `:app`: drawer content + **push/scale/dim motion** (predictive-back aware), Nav3 destinations, Home→Result container transform.
- Strings: all copy in `strings.xml` (en; fil/pt-rBR flagged NEEDS-TRANSLATION per C-12). testTags per C-1.

## 4. Tests & gates
`TextViewModel` unit tests against the golden table (empty→disabled action · tap→Translating→Result "Bonjour (fake)" · error→Retry replays · canvas-hides rule) · Compose UI tests on `tt_text_*` (type → tap `tt_text_translate_btn` → assert result) · existing Konsist/Detekt/Spotless/CI unchanged and green · manual: `assembleTranzlateFakeDebug` on an emulator, screenshots light + dark for the PR.

## 5. Sequencing & PRs
1. **PR-A** (#9 + #10): docs truth + `:core:designsystem` P8. Small, quickly reviewable.
2. **PR-B** (#11): `:core:ui` components + previews.
3. **PR-C** (#11): feature screens + drawer/nav + tests + emulator screenshots.
Each PR: co-verify lens ≠ author (Rule 5) with Review-evidence; merge only on green.

## 6. Non-goals (later issues)
Real engines / the 4 brains · Advanced-AI composer variant + `15/20 today` counter · mode picker · language picker · History & Saved · Settings + offline languages · paywall · camera · LLM enhancement (#8) · theme presets (#7) · R8 (#5).

## 7. Risks
- Compose shared-element (Home→Result) is stable-API but fiddly — fallback = a plain container-transform-styled animation; will not block the PR.
- Predictive-back drawer motion needs API 34+ behaviour checks; degrade gracefully below.
- `:feature:text` must not gain a `:core:config` dep for the tiles' feature toggles — the nav registry in `:app` filters them (scaffold plan §4 R2).

## 8. Owner decision needed
Accept this plan (→ `status: accepted`) so implementation can start with PR-A.
