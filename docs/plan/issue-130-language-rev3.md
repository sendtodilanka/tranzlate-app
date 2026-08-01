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
| 7 | Manage packs empty state | ✅ **delivered** — rev4 frame **20f**, light+dark, accepted as drawn (brief §8 outcome). Builds in PR-23 |
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
| PR-0 | This plan + preserved ruling + designer-brief §8 (empty-state commission) | ✅ #131, 2026-08-01 |
| PR-0b | Spec of record → rev4 + corrected review (brief §9, 9 defects) + rev5 commission (§11) + ad verdict (§10 → #139) + §3 Detect correction | ✅ #140, 2026-08-01 |

### Phase 1 — shipped-truth stabilisation (no UI change)
| PR | Scope | Closes | Status |
|---|---|---|---|
| PR-1 | #123.3 delete/download ownership race in `RealOfflineModelManager` + #123.4 non-discriminating picker-VM test. HIGH-RISK concurrency; the new race test must be shown to FAIL on pre-fix code | #123.3 #123.4 | ✅ #133, 2026-08-01 |
| PR-2 | Screen B forever-Loading guard (`onStart emptyMap` on the VM combine) | — | ✅ #132, 2026-08-01 |
| PR-3 | Manager `states` stateIn + onSubscription conflated refresh + memoized `capableTags` | — | ⬜ |
| PR-4 | `LanguageTagResolver` lift (core:model) + `LanguageRole` + write-side canonicalisation + picker decoupled from `TextViewModel` | #119 #123.2 | ⬜ |
| PR-5 | Usage store: Room `language_usage(lang_id, role, last_used_at)` + translation-success stamper + per-role recents. HIGH-RISK data/migration | #122 | ✅ #134, 2026-08-01 |

### Phase 2 — the move (logic-edit zero)
| PR | Scope | Status |
|---|---|---|
| PR-6 | Create `:feature:language`; git-mv picker (4 prod files + 4 test suites + strings ×3) verbatim | ⬜ |
| PR-7 | git-mv packs screen; delete `:feature:languagepicker`; Screen B localized-names one-liner | ⬜ |

### Phase 3 — seams + primitives
| PR | Scope | Status |
|---|---|---|
| PR-8 | `TranzlateSheetScaffold` / `TranzlateListSheet` (designsystem, string-free) | ✅ #137, 2026-08-01 |
| PR-9 | Shared `DownloadGate` — deletes the duplicated consent logic ×2 | ⬜ |
| PR-10 | Offline-voice seam + `<queries>` TTS_SERVICE + experiment E-V1 | ⬜ |
| PR-11 | Storage walk (aggregate meter source) — seam only; **E-S1 re-ruled to PR-15**, see below | ✅ #138, 2026-08-01 |

### Phase 4 — 16a Translate-to
| PR | Scope | Status |
|---|---|---|
| PR-12 | 16a: voice marks (live day-1 → 19j) + per-role recents + `Selected(inner)` wrapper (ruling 1) | ⬜ |

### Phase 5 — adaptive
| PR | Scope | Status |
|---|---|---|
| PR-13 | Fold-posture WindowInfo extension + host-agnostic saveable contract + pendingConsent → SavedStateHandle | ⬜ |
| PR-14 | 17a landscape two-pane | ⬜ |
| PR-15 | 17b foldable two-leaf + aggregate meter — **E-S1 is a merge gate here** (re-ruled 2026-08-01) | ⬜ |
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
| PR-23 | 20b rewrite behind the SAME Home row + relabel "Language packs" (ruling 5) + **20f** empty state (ruling 7 — drawn in rev4) | ⬜ |
| PR-24 | 20c pack-actions sheet | ⬜ |
| PR-25 | 20e Free up space | ⬜ |
| PR-26 | 20d list-detail (camera card + pair-share line omitted) | ⬜ |
| PR-27 | Ruling 2 execution: remove the Detect "ONLINE ONLY" chip; 19i never built | ⬜ |
| PR-28 | 19n flavor-scoped copy (ruling 4) | ⬜ |

## Spec of record — rev 4 (2026-08-01)

`docs/design/language-screens/language-screens-spec.html` is **rev 4**, and it
stands — but the first review of it was too generous and an independent lens
took it apart. Corrected verdict, full evidence in DESIGNER-BRIEF §9:

- **20f delivered** (ruling 7) and accepted as drawn.
- **Nine defects to fix in rev 5**, three of them in the new sections. The worst:
  **21b offers a download on Azerbaijani and Basque**, neither of which is in
  `BundledLanguageCatalog.offlineCapableIds` — 15a and 16a correctly draw both
  `ONLINE ONLY`. Also: 21b re-writes the running state four ways, 19m is drawn on
  a source the document does not have, 20f and 18a disagree on French's
  suggestion signal, the `12 MB free` state makes the in-flight download
  impossible by sheet 19b's own rule, 19j's trigger cannot fire, the failed row
  drops its ISO avatar and its A–Z slot, the speaker mark appears on rows with no
  pack, and the Pro glyph still sits on the "packs are free" card.
- **Method lesson, recorded so it is not repeated:** `data-screen-label`
  enumerates 26 of some 60 drawings (every sheet and snackbar is unlabelled), and
  de-duplicating lines while scanning hides exactly the row-level contradiction
  being hunted. **DESIGNER-BRIEF §11 is the rev 5 commission** — 10 drawing
  fixes, 3 caption fixes, 3 ad-guide fixes, and a request to label every drawing.
- **Ruling 2 is unaffected:** rev 4 still draws the Detect chip and 19i because
  brief §3 told the designer that sheet had a real trigger. §3 is corrected;
  PR-27 is unchanged.

**Rev 4 also adds an ad layer (spec §7) — deliberately OUT of this epic.** Two
slots, a refusal list and a UMP-first build order, all good, but `:ads` and
`:consent` do not exist yet, step 1 is a startup privacy gate, and its own owner
decision 1 ("does Pro remove ads?") is unsettled while every frame assumes the
answer. Tracked as **#139**, which also carries the guide's three internal
contradictions (brief §10). **The one thing this epic adopts now:**
picker/Manage-packs list `contentPadding.bottom` and the A–Z rail's bottom stop
stay **parameterised** — today they are hardcoded (`LanguagePickerScreen.kt:370`
and the rail's `padding(vertical = spacing.lg24)`), so hosting a slot later would
be a re-layout.

## Per-PR gate (unchanged standing rules)

Issue-first · co-verify lens by a non-author agent (HIGH-RISK = adversarial +
cross-model) · full gradle gate · strings ×3 locales + #115 ledger line ·
`@PreviewLightDark` per meaningful state · emulator ritual on UI PRs ·
**tracker row updated in the merging change**.

## Device experiments

E-V1 voice enumeration reliability · E-W1 requireWifi observability (gates
19a's drawn actions + snackbar 20a-5) · E-D1 IME inside the tablet dialog ·
E-S1 models-dir walk. Results are recorded in research docs as they run.

### Re-ruling, 2026-08-01: E-S1 moves from PR-11 to PR-15 (owner-approved)

The ruling assigned E-S1 — download a pack, walk the ML Kit models directory,
pin the sum above zero, then simulate the directory being absent or renamed —
to PR-11. The co-verify lens correctly flagged shipping PR-11 without it as a
deviation, and under the owner's standing order a deviation stops and is
reported rather than improvised. The owner ruled it moves.

**Why it moves rather than gates PR-11.** PR-11 ships a SEAM and nothing else:
`packsBytes()` has no caller, no screen reads it, and no user-visible statement
depends on it. What E-S1 actually protects is the claim the METER makes — and
the meter is PR-15. If the models directory has been renamed since the issue-90
measurement, `packsBytes()` returns null on every device and the honest
degrade (free-space only, never zero-as-fact) is what a user would see; the
experiment is how we learn that before drawing a number, not before defining a
function.

**What this obliges.** E-S1 is now a PR-15 merge gate, listed in its tracker
row. PR-15 may not merge on a reasoned path — it needs the device run, its
result recorded in a research doc, and the outcome in the PR body. Risk R8 in
the ruling keeps E-S1 as its disconfirming experiment; only the PR it gates
changed.
