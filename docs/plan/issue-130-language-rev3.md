# Plan — issue #130: Language screens rev3, full implementation

status: accepted
(accepted basis: owner-mandated design debate ran 2026-07-31 — 3 differently-primed
architects → adversarial judge → verifier who checked 74 load-bearing claims
against source/spec/platform, 71 held, 3 text errors corrected. Owner approved
ALL EIGHT rulings verbatim on 2026-08-01: "Oyage ewa okkoma hari." Full ruling:
`issue-130-language-rev3-ruling.md`. Evidence chain: code map, spec inventory,
platform audit, gap matrix — summarised in the ruling's §0.)

## ⛔ THE TRACKER RULE (owner, strict, 2026-08-01)

**This document is the progress tracker. The same change that merges a PR
updates its row below. A merge that leaves this table stale is an incomplete
merge.** Status legend: ⬜ pending · 🔨 building · 👁 in lens · ✅ merged (PR#,
date) · ⛔ blocked (by what).

## Owner rulings — ALL RESOLVED 2026-08-01

| # | Ruling | Decision |
|---|---|---|
| 1 | Selected row trailing (#123.1) | Marks + check — the selected row keeps its offline state |
| 2 | Detect | Stays ON-DEVICE; the false "ONLINE ONLY" chip is removed; sheet 19i is never built |
| 3 | 19g fallback target | Device language if catalog-capable, else `en` |
| 4 | 19n privacy copy | Flavor-scoped strings — each brand states only what is true for it |
| 5 | Home row label | **"Language packs"** (en/fil/pt-BR) |
| 6 | Folded cover screen | No separate design now — phone portrait covers it; revisit v2 |
| 7 | Manage packs empty state | Commissioned from Claude Design (brief §8; prompt given to owner) |
| 8 | 19a interim actions | Pre-approved: if experiment E-W1 fails, ship "Not now"/"Download now" |

## Architecture (the ruling, in one block)

One home — new **`:feature:language`** module: picker git-mv'd from
`feature:text` LOGIC-FROZEN (moved tests green without editing one assertion),
offline manager git-mv'd in, `:feature:languagepicker` deleted; then
packs/sheets/first-run/shared-rows grow inside it. Reliability substrate:
exactly **two** sanctioned screen-outliving scopes (download scope + event
observer — a third bounces at review); TTS = enumerate→cache→shutdown, never a
standing engine; sheet requests in SavedStateHandle (process-death survives);
usage dates are translation-success-stamped, NEVER selection-stamped
(test-pinned); tablet dialog carries a measured jank budget (gfxinfo/JankStats)
with a named escape hatch — no preemptive optimization. Full state-flow
contract, risk register (R1–R13, each with its disconfirming experiment) and
the do-not-relitigate REJECT list live in the ruling doc.

## Progress tracker

### Phase 0 — docs
| PR | Scope | Status |
|---|---|---|
| PR-0 | This plan + preserved ruling + designer-brief §8 (empty-state commission) | 🔨 this PR |

### Phase 1 — shipped-truth stabilisation (no UI change)
| PR | Scope | Closes | Status |
|---|---|---|---|
| PR-1 | #123.3 delete/download ownership race in `RealOfflineModelManager` + #123.4 non-discriminating picker-VM test. HIGH-RISK concurrency; the new race test must be shown to FAIL on pre-fix code | #123.3 #123.4 | ⬜ |
| PR-2 | Screen B forever-Loading guard (`onStart emptyMap` on the VM combine) | — | ⬜ |
| PR-3 | Manager `states` stateIn + onSubscription conflated refresh + memoized `capableTags` | — | ⬜ |
| PR-4 | `LanguageTagResolver` lift (core:model) + `LanguageRole` + write-side canonicalisation + picker decoupled from `TextViewModel` | #119 #123.2 | ⬜ |
| PR-5 | Usage store: Room `language_usage(lang_id, role, last_used_at)` + translation-success stamper + per-role recents. HIGH-RISK data/migration | #122 | ⬜ |

### Phase 2 — the move (logic-edit zero)
| PR | Scope | Status |
|---|---|---|
| PR-6 | Create `:feature:language`; git-mv picker (4 prod files + 4 test suites + strings ×3) verbatim | ⬜ |
| PR-7 | git-mv packs screen; delete `:feature:languagepicker`; Screen B localized-names one-liner | ⬜ |

### Phase 3 — seams + primitives
| PR | Scope | Status |
|---|---|---|
| PR-8 | `TranzlateSheetScaffold` / `TranzlateListSheet` (designsystem, string-free) | ⬜ |
| PR-9 | Shared `DownloadGate` — deletes the duplicated consent logic ×2 | ⬜ |
| PR-10 | Offline-voice seam + `<queries>` TTS_SERVICE + experiment E-V1 | ⬜ |
| PR-11 | Storage walk (aggregate meter source) + experiment E-S1 | ⬜ |

### Phase 4 — 16a Translate-to
| PR | Scope | Status |
|---|---|---|
| PR-12 | 16a: voice marks (live day-1 → 19j) + per-role recents + `Selected(inner)` wrapper (ruling 1) | ⬜ |

### Phase 5 — adaptive
| PR | Scope | Status |
|---|---|---|
| PR-13 | Fold-posture WindowInfo extension + host-agnostic saveable contract + pendingConsent → SavedStateHandle | ⬜ |
| PR-14 | 17a landscape two-pane | ⬜ |
| PR-15 | 17b foldable two-leaf + aggregate meter | ⬜ |
| PR-16 | 17c/17d dialog host + E-D1 + measured jank budget gate | ⬜ |

### Phase 6 — sheets, first-run, snackbars
| PR | Scope | Status |
|---|---|---|
| PR-17 | 19a mobile-data sheet (replaces dialogs ×2; E-W1-gated actions per ruling 8) | ⬜ |
| PR-18 | 19d + 19b + failure-cause map ×1 + 15a Retry-pill deviation fix | ⬜ |
| PR-19 | 19f + 19g (fallback per ruling 3) + saved-count query | ⬜ |
| PR-20 | 19h + 19m + app-shell sheet host | ⬜ |
| PR-21 | 18a/18b first run (LocaleList suggestions + E-K1) | ⬜ |
| PR-22 | PackEvents + app SnackbarHost + snackbars 20a | ⬜ |

### Phase 7 — Manage packs
| PR | Scope | Status |
|---|---|---|
| PR-23 | 20b rewrite behind the SAME Home row + relabel "Language packs" (ruling 5) + empty state (ruling 7 drawing) | ⬜ |
| PR-24 | 20c pack-actions sheet | ⬜ |
| PR-25 | 20e Free up space | ⬜ |
| PR-26 | 20d list-detail (camera card + pair-share line omitted) | ⬜ |
| PR-27 | Ruling 2 execution: remove the Detect "ONLINE ONLY" chip; 19i never built | ⬜ |
| PR-28 | 19n flavor-scoped copy (ruling 4) | ⬜ |

## Per-PR gate (unchanged standing rules)

Issue-first · co-verify lens by a non-author agent (HIGH-RISK = adversarial +
cross-model) · full gradle gate · strings ×3 locales + #115 ledger line ·
`@PreviewLightDark` per meaningful state · emulator ritual on UI PRs ·
**tracker row updated in the merging change**.

## Device experiments

E-V1 voice enumeration reliability · E-W1 requireWifi observability (gates
19a's drawn actions + snackbar 20a-5) · E-D1 IME inside the tablet dialog ·
E-S1 models-dir walk. Results are recorded in research docs as they run.
