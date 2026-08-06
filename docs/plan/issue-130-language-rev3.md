# Plan — issue #130: Language screens rev3, full implementation

> **issue #130 has three plan files.** **This one = the per-PR tracker + implementation plan** — the PR-N checklist (the ✅ rows). The other two: the order-of-work plan → [`issue-130-rev5-completion.md`](issue-130-rev5-completion.md) · the design authority → [`issue-130-language-rev3-ruling.md`](issue-130-language-rev3-ruling.md).
> ⚠️ **"rev3" in this filename is the 2026-07-31 architecture-planning round, NOT a design revision.** What these PRs build is the **rev5** design (spec of record: `docs/design/language-screens/language-screens-spec.html`).

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

> **The level above this file is [`ROADMAP.md`](ROADMAP.md)** — the epic's
> remaining PRs sequenced together with the issues that surfaced while building
> it (#148–#154) and the owner decisions that gate them. This table stays the
> per-PR truth; the roadmap is the order.

## Progress tracker

### Phase 0 — docs
| PR | Scope | Status |
|---|---|---|
| PR-0 | This plan + preserved ruling + designer-brief §8 (empty-state commission) | ✅ #131, 2026-08-01 |
| PR-0b | Spec of record → rev4 + corrected review (brief §9, 9 defects) + rev5 commission (§11) + ad verdict (§10 → #139) + §3 Detect correction | ✅ #140, 2026-08-01 |
| PR-0c | Spec of record → **rev5**: 16/16 corrections verified, scripted row/capability check, PR-3's row repaired | ✅ #144, 2026-08-01 |

### Phase 1 — shipped-truth stabilisation (no UI change)
| PR | Scope | Closes | Status |
|---|---|---|---|
| PR-1 | #123.3 delete/download ownership race in `RealOfflineModelManager` + #123.4 non-discriminating picker-VM test. HIGH-RISK concurrency; the new race test must be shown to FAIL on pre-fix code | #123.3 #123.4 | ✅ #133, 2026-08-01 |
| PR-2 | Screen B forever-Loading guard (`onStart emptyMap` on the VM combine) | — | ✅ #132, 2026-08-01 |
| PR-3 | Manager `states` stateIn + onSubscription conflated refresh + memoized `capableTags`. Lens found 3 defects, 2 of them regressions this PR introduced (count gate starved late subscribers; worker died permanently on a cancelled Task) — fixed before merge | — | ✅ #142, 2026-08-01 |
| PR-4 | `LanguageTagResolver` lift (core:model) + `LanguageRole` + write-side canonicalisation + picker decoupled from `TextViewModel` | #119 #123.2 | ✅ #141, 2026-08-01 |
| PR-5 | Usage store: Room `language_usage(lang_id, role, last_used_at)` + translation-success stamper + per-role recents. HIGH-RISK data/migration | #122 | ✅ #134, 2026-08-01 |

### Phase 2 — the move (logic-edit zero)
| PR | Scope | Status |
|---|---|---|
| PR-6 | Create `:feature:language`; git-mv picker (4 prod + 4 test suites + strings ×3) verbatim; `LanguagePickerTarget` alias retired — the composer speaks `LanguageRole`. **Two corrections to the ruling's file list, both verified:** `LanguageNames.kt` is NOT picker-only (`languageLabel` is the composer's chip label), so `:feature:text` now depends on `:feature:language` — language presentation keeps ONE home; and the cross-VM coherence test cannot live in either feature after the split, so it moved to `:app` | ✅ #145, 2026-08-01 |
| PR-7 | git-mv packs screen; delete `:feature:languagepicker`; Screen B localized-names one-liner | ✅ #146, 2026-08-01 |

### Phase 3 — seams + primitives
| PR | Scope | Status |
|---|---|---|
| PR-8 | `TranzlateSheetScaffold` / `TranzlateListSheet` (designsystem, string-free) | ✅ #137, 2026-08-01 |
| PR-9 | Shared `DownloadGate` — deletes the duplicated consent logic ×2 | ✅ #166, 2026-08-01 |
| PR-10 | Offline-voice seam + `<queries>` TTS_SERVICE + experiment E-V1 | ✅ #147, 2026-08-01 |
| PR-11 | Storage walk (aggregate meter source) — seam only; **E-S1 re-ruled to PR-15**, see below | ✅ #138, 2026-08-01 |

### Phase 4 — 16a Translate-to
| PR | Scope | Status |
|---|---|---|
| PR-12 | 16a: voice marks **drawn only where the device has an offline voice** (rev 5 — no 19j; see §rev-5 below and #180) + per-role recents + `Selected(inner)` wrapper (ruling 1) | ✅ #185, 2026-08-02 |

### Phase 5 — adaptive
| PR | Scope | Status |
|---|---|---|
| PR-13 | Fold-posture `WindowInfo` extension + host-agnostic saveable contract (query + list position out of `rememberSaveable`, into the picker VM's `SavedStateHandle`) + `pendingConsent` → `SavedStateHandle` behind a storage seam + LeakCanary debug-only rider. **`LanguageSheetRequest` is NOT built here** — it has no members until PR-17; see below | ✅ #192, 2026-08-02 |
| PR-14 | 17a landscape two-pane: a `pickerArrangement()` window gate + a side pane beside the catalog + the catalog on a grid so ONE list position means the same language in both arrangements + the Detect-key settle. **Deviations below** | ✅ #198, 2026-08-02 |
| PR-15 | 17b foldable two-leaf (24dp crease gutter, 296dp leaf) + the offline-library meter (U-5) with its two honest degrades. **E-S1 ran and passed** — `docs/research/issue-130-e-s1-storage-walk.md`. **Deviations below** | ✅ #200, 2026-08-02 |
| PR-16 | 17c/17d dialog host + docked "Manage packs" ordering + **E-D1 ran and PASSED at both sizes** + the jank budget **measured and reported unsatisfiable on an emulator** (see below) | ✅ #205, 2026-08-02 |

### Phase 6 — sheets, first-run, snackbars
| PR | Scope | Status |
|---|---|---|
| PR-17 | 19a mobile-data sheet: `MeteredConsentDialog` + Screen B's inline dialog both deleted, their two string sets retired, one `MobileDataSheet` raised by both screens, the standing "Always ask" preference changeable in the sheet. **E-W1 has never been run, so the drawn "Wait for Wi-Fi" is not shipped** — the owner's pre-approved interim "Not now" is (ruling 8). **Deviations below** | ✅ #209, 2026-08-02 |
| PR-18 | 19d + 19b + failure-cause map ×1 + 15a Retry-pill deviation fix. **Closes #175.** Deviations below | ✅ #216, 2026-08-02 |
| PR-19 | 19f + 19g + U-10 saved-count query + the 🗑 becomes a confirmed remove. **No fallback is built: the drawn 19g and ruling 3 both describe a target switch this app does not have** — deviations below | ✅ #227, 2026-08-02 |
| PR-20 | 19h + 19m + app-shell sheet host | ✅ #299 |
| PR-21 | 18a/18b first run (LocaleList suggestions + E-K1) | ✅ #302 — locale path ships; **E-K1 deferred** (rule 4, InputMethod path unproven, additive) |
| PR-22 | PackEvents + app SnackbarHost + snackbars 20a | ✅ #304 — 20a-1..4; **20a-5 deferred** on E-W1/#208. Co-verify caught + fixed a real `DownloadStarted` registration race (`putIfAbsent`, real-threads test); residual device check #306 |

### Phase 7 — Manage packs
| PR | Scope | Status |
|---|---|---|
| PR-23 | 20b rewrite behind the SAME Home row + relabel "Language packs" (ruling 5) + **20f** empty state (ruling 7 — drawn in rev4) | ✅ #310 — co-verify BLOCK (STORAGE dead-end) fixed: honest Retry + refusal snackbar; 428 tests. Fast-follows filed (onRetry discard, two-SnackbarHost overlap, repeatOnLifecycle) |
| PR-24 | 20c pack-actions sheet | ✅ #317 — inline `TranzlateListSheet` off the downloaded-pack overflow: "Use as target now" (appScope write through the picker's `TranslatePrefsRepository` path) · voice line IFF `hasOfflineVoice` (plumbed `Language`→`OfflineLanguageRow`→`PackRow`) · Remove → existing 19f/19g. 4 mutation-proven tests; preflight + build green |
| PR-25 | 20e Free up space | ✅ #321 — 20e batch-cleanup sheet (`TranzlateListSheet`): stale-only checklist (`stalePacks` — real-dated past-90d only; `NoRecord`/pivot/fresh excluded, ruling ⑧), pre-checked `rememberSaveable` boxes (process-death restore, `StateRestorationTester`-pinned), reused `StorageCardView` breakdown (one vocabulary), batch remove via the existing per-pack delete on `appScope`. Nudge "Review N packs" wired end-to-end. 19b second action `Free up space` built as an optional callback (tested + previewed); the picker→20e wiring needs an app-module nav arg (`LanguagesNavKey` is a `data object`) → **#320**. 6 mutation-proven tests (2 selector + rememberSaveable + batch + 2 19b); preflight + build green |
| PR-26 | 20d list-detail (camera card + pair-share line omitted) | ✅ #330 — plain `Row` two-pane at EXPANDED width, gated on the C-13 `WindowInfo.isExpanded` (no `adaptive-layout` dep, ruling :90/:238): the existing Manage-packs list on the left with now-selectable rows (`selectable` + `secondaryContainer` tint), the SELECTED pack's detail on the right — identity · a per-role "source" line fed by the #122 store (`As source` / `As target`, honest `NoRecord` where a role has no stamp, ruling ⑧) · the reused `StorageCardView` used-against-free bar (one vocabulary). Camera card + pair-share line omitted (ruling :238/:249). VM splits usage into `RoleUsage(asSource, asTarget)`, merged for the list rows; pure `packRoleUsage` for the detail. **1280×800 emulator pass, light + dark** (`pr26-emulator/`), selection + per-role dates visible. 3 mutation-proven render tests (width gate · source line · selection) + 2 model + 1 VM; preflight + build green |
| PR-27 | Ruling 2 execution: remove the Detect "ONLINE ONLY" chip; 19i never built | ✅ #309 — chip stripped from both faces (visible chip + TalkBack cd); real online-only langs keep theirs |
| PR-28 | 19n flavor-scoped copy (ruling 4) | ✅ #308 — 19n NOT built (no trigger); became the privacy-copy fix: `lang_first_run_privacy` was a lie (app uploads on AUTO fallback), rewritten honest ×3 locales |

### PR-13 deviations from the ruling's PR-13 row (2026-08-02)

Three, each verified against the code rather than against the ruling text
(mandatory rule 11, fourth cause).

1. **`LanguageSheetRequest` is not created.** The ruling lists it in PR-13's
   saveable contract, and the ruling's own §1 puts its twelve sheet members in
   PR-17 to PR-22. It does not exist in the tree today (`grep`: three hits, all
   in the ruling doc). An empty sealed interface would be a type with nothing to
   serialize and nothing to test. The one sheet request the app actually ships is
   the metered-consent question, and that is exactly what item 3 makes durable —
   through the same `SavedStateHandle`, so PR-17 inherits a working pattern
   rather than an empty placeholder.
2. **The consent question moves behind a storage seam, not into the gate.**
   `DownloadGate` lives in `:core:domain`, which the Konsist gate keeps JVM-pure,
   so it cannot hold a `SavedStateHandle`. It now takes a `ConsentQuestionStore`;
   the durable implementation and its Hilt binding live in `:app`, the
   composition root. Neither ViewModel changed for this — one seam fixed both
   screens.
3. **The ruling's test plan ("instrumented restore host×2 harness") is not
   what shipped.** CI runs `test` and `assembleAndroidTest` and never runs an
   instrumented test (#40 open); there is no Compose unit-test runtime anywhere
   either (#186). An instrumented-only test here would be a test nobody ever sees
   fail. What shipped instead: JVM tests over a real `SavedStateHandle` for the
   behaviour (`LanguagePickerViewModelTest`, `SavedStateConsentQuestionStoreTest`,
   `DownloadGateTest`), a pure `foldPosture()` function with its own table
   (`FoldPostureTest`), and one named-file source rule for the part no JVM test
   can reach — that the screen keeps no host-scoped saveable state
   (`PickerHostAgnosticTest`, honest about being a source rule).

### PR-14 deviations from the ruling's PR-14 row (2026-08-02)

Five, each checked against the export's markup or the code rather than against
the ruling text (mandatory rule 11, fourth cause).

1. **17a is ONE picker in one role, not two pickers side by side.** Each
   landscape frame carries a single title — `from · landscape` says "Translate
   from", `to · landscape` says "Translate to" — so the two panes are a shortcut
   pane and a catalog pane serving the same `LanguageRole`. The landscape
   treatment is therefore a branch inside `LanguagePickerContent`, not a wrapper
   that composes the picker twice: two pickers would draw a screen the design
   does not have, and would hand two panes the ONE search query and ONE scroll
   position `LanguagePickerViewModel` holds. It also keeps the layout inside the
   file `PickerHostAgnosticTest` reads by path — a separate landscape file would
   have been outside PR-13's guard.
2. **The catalog moves from `LazyColumn` to `LazyVerticalGrid` in BOTH
   arrangements.** A grid indexes ITEMS rather than layout rows, so the column
   count does not change the numbering; the obvious alternative — a two-column
   list of paired rows — would have indexed PAIRS and landed every restored
   position at twice its language. One state type, one emission body, one
   contract.

   **This deviation originally claimed more than that, and the extra claim was
   false.** It said item 40 is the 41st language "whether it is drawn in one
   column or two", and therefore that PR-13's saved position survives the
   rotation. The first half is true of the COLUMN count and the second half does
   not follow, because the two arrangements do not emit the same items: the
   single-pane grid starts `[detect?] [recent header + rows?] [All languages]`,
   17a moves the first two to the side pane and starts `[All languages]`. The
   prefix differs by 1 to 7 items, so the same number named a different language
   after a rotation — reproduced on `emulator-5554` (#198 co-verify F1):
   browsing at English in portrait, landscape opened at Finnish. **The stored
   position is now the LANGUAGE at the top of the catalog**
   (`PickerListPosition.anchorId`), which is arrangement-independent by
   construction rather than by an arithmetic nobody had checked.
3. **`PickerHostAgnosticTest`'s banned list grows by two.** The grid and the
   scrolling side pane cut two new doors to host-scoped state
   (`rememberLazyGridState`, `rememberScrollState`) that did not exist when
   PR-13 wrote the rule over `rememberSaveable` / `rememberLazyListState`. A
   rule that names symbols has to be extended whenever a new one appears; that
   cost is now written down in the test rather than left to be rediscovered.
   A new case also pins that the picker builds exactly ONE lazy state — seeding
   correctly in two branches is still two scroll positions.
4. **The landscape row keeps its portrait anatomy.** The export compresses each
   landscape row to a single 48dp line with no supporting text. Adopting that
   would delete the failure reason ("No connection. Reconnect and try again.")
   and the progress line from the screen, which EDGE_CASES' no-dead-end rule
   forbids — and the landscape frames never draw a failed row, so the export is
   silent on the case rather than deciding it. Only the ARRANGEMENT changes.
   For the same reason the search field stays at `touchTargetMin` where the
   export draws 40dp: C-14 makes 48dp the authoritative a11y floor.
5. **An empty side pane is not drawn.** The export only draws the resting state;
   a search on the source side clears the recents and filters the Detect row
   out, which would leave 272dp of empty surface beside a "no results" message.
   The pane goes and the catalog takes the width back — `pickerListPlan`
   decides it, and a unit test pins it.

**Two things the device run found that the code review did not.** First, the
landscape bar is a plain `Row`, and a plain `Row` applies none of the insets an
M3 `TopAppBar` applies for you: the first run drew the title through the
status-bar clock and the counter through the signal icons. It now carries
`TopAppBarDefaults.windowInsets` — systemBars top AND horizontal, which is the
right pair in landscape, where a display cutout eats the leading edge. Second,
the "Detect language" row ellipsises to "Detect la…" inside the 272dp side pane,
because it still carries the shipped `ONLINE ONLY` chip — the chip rev 5 removed
from every frame and **PR-27** removes from the app under owner ruling 2. The
narrow pane makes that already-agreed removal visible rather than merely
untrue; it is not fixed here, because a third place touching the Detect chip is
what REJECT §7.8 bounces.

**Three more the co-verify lens found on PR #198, all of them only visible on a
device.**

1. **F1 — rotating while browsing landed on the wrong language.** The saved
   position was a raw grid index; see deviation 2 above for what replaced it and
   why. The PR's own device ritual missed it for a sharper reason than
   carelessness: it typed `ar` into the search box first, and that query
   suppresses the detect row and empties the recents section in BOTH
   arrangements, collapsing the prefix difference to exactly zero. The ritual
   selected the one case where the defect cannot appear. `PickerListPositionTest`
   now covers the round trip with recents present and absent, in both roles, and
   **never in search mode**.
2. **F2 — the picker could draw the portrait layout in a landscape window.**
   Recorded in the PR body as "unexplained, not resolved". Diagnosed by
   instrumenting the screen and hammering rotations on `emulator-5554`: **24 of
   539 picker compositions**, every one with the same fingerprint —
   `BoxWithConstraints` measuring 914.29×411.43dp (landscape) while Compose's
   window snapshot still reported 1080×2400px / `orient=PORTRAIT`, with a live
   `WindowMetricsCalculator` call in the same frame already answering 2400×1080.
   The gate took its width from the constraints and its height from
   `WindowInfo.heightCompact`, which reads that snapshot; a wide window reporting
   itself tall fails the height condition. `pickerArrangement` now takes **both**
   sizes as `Dp` from the same `BoxWithConstraints`, so the two cannot be half a
   rotation apart. Every one of the 24 was corrected by the next composition
   (0.12–0.34s); the PERSISTENT form the lens described — 5+ seconds, surviving
   eight Activity recreations — was **not** reproduced, and no claim is made that
   it had the same cause.
3. **The 420dp cap on the landscape search field never bound.**
   `Modifier.weight(1f)` measures its child at a FIXED width, and `widthIn(max =)`
   can only narrow within the incoming range, which at a fixed width is a single
   point. Measured 1544px ≈ 588dp against a documented 420dp. The field now sits
   in a `Box` that takes the weighted share, and measures 1103px = 420.2dp.

**What the export actually draws for 17a, read per row** (sliced between one
language name and the next, never off a flattened token list — the #183 trap):
`from · landscape` has 17 rows and **zero** speaker marks, confirming the mark is
target-only. `to · landscape` has 17 rows and **four** marks — English, Arabic,
Bengali, Bulgarian — and does **not** mark Afrikaans or Spanish. The one stray
`volume_up` outside any row is the voice legend at the foot of the side pane,
which sits immediately after the Afrikaans row in document order: that adjacency
is exactly how a flattened scan mis-reads Afrikaans as marked.

**On #183:** measured device data (`docs/research/issue-130-e-v1-voice-enumeration.md`,
68 offline voice ids) has `en`, `ar`, `bn`, `bg`, `es`, `sq` all present and `af`
absent. So `to · landscape` agrees with the device on all four rows it marks and
on Afrikaans; where it differs from 16a portrait is Spanish, which 16a marks and
the landscape frame draws with the tick alone — a single-line row has no
supporting line to carry the mark, and the selected row keeps only its check.
The app reads the device either way, so nothing here changes behaviour, and 16a
is not "fixed" to match.

### PR-15 deviations from the ruling's PR-15 row (2026-08-02)

Each checked against the export's markup or the running app rather than against
the ruling text (mandatory rule 11, fourth cause). The frames were parsed per row
container, walking the row `div`s — never on a flattened token list, which is
what produced the wrong finding in #183 that had to be retracted.

1. **17b is a THIRD arrangement, not a wider 17a, and the height gate is why.**
   The export's foldable frames are 760 × 812. That is comfortably wider than
   17a's 568dp floor and 332dp TALLER than its 480dp short-window ceiling, so
   `pickerArrangement` would have returned single-pane on the exact window the
   design was drawn for. The BOOK branch is therefore decided first and does not
   ask about height at all: the split is not being done to use up spare width,
   it is being done because the device is already in two halves.
2. **`PickerArrangement` grew three fields rather than the screen growing an
   `if`.** `gutter`, `sidePaneWidth` and `twoLeaf` all come from the gate that
   already established which window this is. The alternative — deciding the
   gutter at the draw site — is a second place that has to be kept in step, and
   PR-14's co-verify found exactly that shape of defect when width and height
   were measured from two sources. `PickerListPositionTest` pins that the draw
   site spells neither constant.
3. **The leaf is 296dp where 17a's pane is 272dp**, measured off the frames. It
   is not a taste choice: the leaf also carries the meter card, whose "of 59
   packs · 110 MB used" line sets the floor.
4. **`hinged` finally decides something, and it is not the arrangement.** PR-13
   shipped `WindowInfo.hinged` as deliberately NOT the same question as posture,
   and until now nothing in the picker depended on the difference. A fully-open
   dual screen is `hinged` and FLAT: it gets 17a's layout, because that is what
   it has room for, but its gutter is drawn at the crease width because there is
   a physical seam down it. That is the reading PR-13's own KDoc asks for
   ("handled where content is placed rather than by refusing the arrangement").
   Two tests fail in opposite directions if the two fields are swapped.
5. **TABLETOP gets no split at all.** Its crease is horizontal, so two
   side-by-side panes would each be cut across the middle by it. One scroller the
   user can push past the fold is better than two that both straddle it.
6. **The meter is gated on `twoLeaf`, not on `twoPane`.** The export draws no
   meter in either landscape frame, and the reason is visible in the geometry:
   272 × 412 has no room for the card without pushing the recents section — the
   reason the pane exists — off the bottom. Those two booleans are equal in every
   other case, which is exactly why the distinction had to be written down and
   tested.
7. **The meter has THREE sentences, and the one the export never drew is the
   commonest.** The export draws two — "110 MB used" and "nothing downloaded".
   The third, "8.6 GB free", was written as the R8 degrade the ruling's risk
   register asks for, on the reasoning that a designer cannot draw a state that
   only exists when ML Kit renames a directory. Co-verify's `pm clear` (E-S1b)
   showed it is also the FIRST card a new user sees, so the export's
   `from · foldable first run` frame does not match the device. It is drawn from
   the same card with an empty bar.
8. **A count of zero outranks the byte answer** — a store that outlived the packs
   deleted from it can still be walked and can still sum above zero, and a size
   printed beside a count of nought is a size for something the user does not
   have. The count also needs no disk.
   **Corrected by co-verify (E-S1b).** This item first justified the order as
   protecting the commonest state in the app — the first run — from being
   degraded to a free-space number. A real `pm clear` disproved that: ML Kit
   reports the English pivot as on device from the first launch, so `downloaded`
   is **1, not 0**, and the first card a new user sees is
   "1 of 59 packs · 8.6 GB free" whichever way the branches are ordered. The
   order stands on the reason above; it is not a first-run protection.
   `OfflineLibraryMeter.Empty` is close to unreachable on Play-Services hardware
   — kept because the alternative for a zero count is a size line under a zero,
   and because a device that reports no models at all still needs a truthful
   card. See `docs/research/issue-130-e-s1-storage-walk.md` §E-S1b.
8b. **`temp/` debris does not count as pack bytes (co-verify F3 / E-S1c).** The
   walk summed every regular file under the store, and an interrupted download
   leaves partial models in ML Kit's store-root scratch directory that nothing
   is documented to remove. One real 14,779,264-byte file dropped into
   `temp/af_en/` moved the card from 44 MB to 59 MB — a 34% overstatement that
   survives forever — while the catalogue still correctly read "2 of 59 packs".
   `packsBytesOf` now refuses to descend into the store-root `temp` only; a
   `temp` folder inside a pack, and a plain file of that name at the root, are
   real bytes.
9. **The meter waits for the catalogue.** Found by a test, not by review: the
   picker's `languages` flow starts at `emptyList()`, so the meter published
   "0 of 0 packs · nothing downloaded" for a frame and then corrected itself.
   Same class as the first frame labelling 194 rows "Online only". The empty
   catalogue is now filtered out, which is also how the picker itself reads it
   (`PickerSections.catalogEmpty` → the loading placeholder).
10. **Row density is left alone.** The foldable frames draw 48dp catalog rows
    with a 14.5px name, denser than the shipped 56/60dp. Adopting that is the
    same change PR-14 declined for the same reason (deviation 4 there): the
    compressed row has no supporting line, which would delete the failure reason
    and the progress line from the screen. Only the ARRANGEMENT changes.

**On #183, from the foldable frames.** Parsed the same way, per row container:

| language | 16a portrait | `to · landscape` | `to · foldable` | device (E-V1) |
|---|---|---|---|---|
| Spanish `es` | mark | **none** (selected row) | **mark** | has a voice |
| Arabic `ar` | **none** | mark | **mark** | has a voice |
| English `en` | mark | mark | mark | has a voice |
| Bengali `bn` | — | mark | mark | has a voice |
| Bulgarian `bg` | — | mark | mark | has a voice |
| Albanian `sq` | none | none | **none** | **has a voice** |
| Afrikaans `af` | none | none | none | has none |

The foldable pair settles both of #183's disagreements in the same direction —
**it marks Spanish AND Arabic** — and agrees with the measured device data on six
of the seven rows. `to · foldable` draws Spanish's mark on the selected row's
supporting line, which is the space `to · landscape`'s single-line row did not
have; that is the whole of the Spanish disagreement, and it is a consequence of
row height rather than a claim about Spanish. The one row no frame marks is
Albanian, which the device says has a voice. **Nothing is copied into code
either way** — `showsVoiceMark` reads `Language.hasOfflineVoice`, which the
device answers.

### PR-17 deviations from the ruling's PR-17 row (2026-08-02)

Each checked against the code or the export's markup rather than against the
ruling text (mandatory rule 11, fourth cause).

1. **E-W1 has never been run, so the sheet's second action is "Not now".** The
   ruling makes the DRAWN actions conditional on it and the tracker lists it as
   an outstanding device experiment. There is no research record for it, no
   issue, no commit; `docs/plan/issue-90-download-lifecycle.md`
   parks the same probe as *"X6 (v2 only): requireWifi mid-download drop probe —
   future tail"*. So the owner's pre-approved interim (ruling 8) ships, and no
   E-W1 result is claimed. **What this PR also removes is a promise that was
   already live:** both deleted dialogs said *"Wait for Wi-Fi"* while
   `RealOfflineModelManager` passed a bare `DownloadConditions.Builder().build()`
   and nothing anywhere queued a download — the exact string the ruling's REJECT
   §7.8 refuses pre-E-W1, shipping since #90.
2. **The sheet is not told which language it is about, and the title names
   none.** The export draws *"Download over mobile data?"* where both dialogs
   drew *"Download %1$s over mobile data?"*, and the reason is the checkbox
   underneath: unticking "Always ask" answers for EVERY language, so a title
   naming one would misdescribe what the user just did. Consequence in code —
   the sheet takes a `Boolean`, not an id, which is why one composable can serve
   two screens without either of them handing it a row list to look a name up in.
3. **`LanguageSheetRequest` is still not created**, and PR-13's deviation 1
   predicted why. 19a is the app's ONE sheet request, and it is already durable
   and already typed: `ConsentQuestionStore` holds it in the `SavedStateHandle`.
   A sealed interface with one member, wrapping a `String` that survives process
   death today, would be a type to serialize instead of the one that works. The
   ruling's saveable contract is met by the thing PR-13 built.
4. **The standing preference is written by the two ViewModels, not by the
   gate.** `DownloadGate`'s surface is deliberately untouched: #192's lens
   compiled a bypass out of one extra reachable member, and `consentOnce()` is
   still the only producer of a `ConsentedDownload` (`DownloadGateTest` reddens
   if either half is dropped). The checkbox therefore writes
   `DownloadPrefsRepository` — the same repository Settings writes and the gate
   reads — on the APPLICATION scope, because a consent preference dropped by a
   cancelled screen can fail in the direction that keeps a permission the user
   just revoked.
5. **The two consent previews are deleted rather than ported.** A
   `ModalBottomSheet` opens a window and the tooling renders nothing for a
   window, so `LanguagePickerConsentDialogPreview` would have drawn the resting
   picker while claiming to show the consent state. The sheet's two meaningful
   states are previewed where the sheet lives, through a new
   `TranzlateSheetPreviewFrame` in `:core:designsystem` — PR-8 built the anatomy
   but left the host-free path `internal`, so `:feature:language` was the first
   module unable to satisfy rule 7 for a sheet.
6. **`PickerHostAgnosticTest`'s banned list grows by one.**
   `rememberModalBottomSheetState` is `rememberSaveable` underneath, and
   `TranzlateSheetScaffold`'s KDoc invites the caller to hoist one "to drive a
   graceful `hide()`". Taking that invitation would tie the sheet to whichever
   `SaveableStateHolder` draws the picker — the defect PR-13 removed — in
   exchange for an exit animation neither deleted dialog had. The scaffold's
   default is used, and the ban is written down rather than left as a habit.

**What a mutation found that no test in this PR can close.** Drawing the
checkbox inverted — `Checkbox(checked = !alwaysAsk)` — survives the whole suite,
and would survive any rewrite of it. The row carries the `toggleable` and so
carries the semantics; the box inside is read-only by design (one node for
TalkBack, not two), so nothing in a semantics tree reads the glyph. It is
checked on the device screenshot instead, and named in `MobileDataSheetTest`'s
KDoc so a green suite is not mistaken for a claim about the tick.

**One measurement the copy does not match.** The drawn body says a pack is
"usually 20–45 MB". The two packs this project has actually measured are
44,169,505 bytes (E-S1, af↔en) and 45.7 MB (#90 E3, de↔en) — the second is over
the drawn ceiling. Shipped as drawn, because "usually" carries it and because
the alternative is inventing copy; recorded because the app now states a pack
size in three places and the other two (`offline_subtitle`,
`settings_mobile_data_supporting`) both say `~30 MB`, which the measurements
contradict by more.

> **SUPERSEDED by #219 / PR #262 (wave 1c).** The paragraph above is PR-18's
> record and stays as written; the figure it describes no longer ships. All four
> strings now say **`40–65 MB`** — the on-disk range of all 58 ML Kit translate
> models, read from `res/raw/translate_models_metadata.json` in
> `translate-17.0.3.aar` rather than extrapolated from two samples. The
> "alternative is inventing copy" reasoning was right at the time and stopped
> being right the moment the manifest was opened: `af_en`'s declared size is
> 44,169,505 bytes, which is E-S1's measurement to the byte, so the manifest is
> the measurement for all 58. Derivation: `docs/plan/issue-219-copy-sweep.md` §1.

### PR-18 mutation register — decided BEFORE the tests (2026-08-02)

Rule 11, third cause: a mutation chosen after reading the code gets shaped by it.
Written down before the first line of production or test code, run afterwards,
and every one of them reddened the test it was aimed at.

| # | Mutation (one edit to production code) | Test that went RED |
|---|---|---|
| M1 | un-fold `WIFI_REQUIRED` from `NETWORK` | `DownloadFailureTest` ×2 — the fold, and the shared sheet |
| M2 | `STORAGE` opens 19d instead of 19b | `DownloadFailureTest` + `PackFailureSheetRaisingTest` ×2 |
| M3 | an unexplained failure reuses the connection copy | `DownloadFailureTest` + `PackFailureSheetsTest` |
| M4 | raise the sheet for ANY observed failure, not only a requested one | `PackFailureSheetRaisingTest` — the un-asked failure |
| M5 | invert the consent guard (`!=`) | `PackFailureSheetRaisingTest` ×3 |
| M6 | swap free/total on the no-space request | `PackFailureSheetRaisingTest` — the no-space figures |
| M7 | put `Icons.Filled.Refresh` back in place of the pill | `PackFailureCopyTest` — the labelled Retry |
| M8 | sort failed rows out of A–Z order | `LanguagePickerRowStateTest` ×2 |
| M9 | a second `when (cause)` in the offline manager | `DownloadFailureSourceTest` ×2 |
| M10 | 19b's one action only dismisses | `PackFailureSheetsTest` — the action leads somewhere |

M7 is the one worth reading twice: the tag did not change with the control, so
every tag-based assertion in the repository stays green under it. The assertion
had to be on the LABEL.

### PR-18 deviations from the ruling's PR-18 row (2026-08-02)

Each checked against the export's markup, the repo's own record or the code
rather than against the ruling text (mandatory rule 11, fourth cause). The two
sheets were read out of the export's `__bundler/template` island per frame
container, never off a flattened token list — the #183 trap.

1. **19d's drawn body contradicts 19d's own caption, and the BODY ships.** The
   caption says *"The important reassurance — progress is kept — leads the
   copy."* The body says *"…so nothing is on the device yet."* This project
   settled it before the frame was drawn: `DESIGNER-BRIEF.md:73` — *"No resume
   control. The system may or may not resume internally; we cannot observe it,
   so we must not promise it. A failed download offers retry, not resume. **Do
   not claim kept progress.**"* — and `README.md:73`, which lists resume among
   the things replaced by "Nothing". `RemoteModelManager.download()` returns
   `Task<Void>`; there is no handle on a partial transfer, and this app's Retry
   starts a fresh download. **The caption is the half that is wrong**, and
   `PackFailureSheetsTest.19d promises no kept progress` is where that ruling is
   now enforced rather than merely written down.
   *One precision, because the project has measured the exception:* an
   interrupted download DOES leave bytes behind, in ML Kit's store-root scratch
   directory (E-S1c — one real 14,779,264-byte file survived indefinitely). Those
   bytes are not a pack, cannot be translated with, and are excluded from the
   library meter. The sentence is about the pack, and "yet" is doing its work.

2. **19d's body and cause line are per cause; the drawn frame has one cause.**
   The export writes 19d for a dropped connection ("The connection dropped…",
   "Cause: connection lost."). ML Kit's other failures report no reason at all —
   `Exception.toFailure()` maps `IOException` to `NETWORK` and **everything else
   to `UNKNOWN`** — so reusing the drawn sentences for them would state a reason
   the app does not have, which is the same class of invention as a percentage on
   an indeterminate download. Two undrawn strings ship
   (`lang_sheet_failed_{body,cause}_generic`), flagged as undrawn in
   `STRINGS_language.md` §5.2.

3. **19b ships ONE action, not the two that are drawn.** `Free up space` opens
   20e, which is **PR-25**. The ruling pre-decided this exact case (PR-18 row:
   *""Free up space" button 20e එනකම් omit (single action "Manage packs" — no
   dead end)"*), and EDGE_CASES §7 is why: a button that opens nothing is the
   dead end, and one that silently did something else would be worse. `Manage
   packs` therefore becomes the sole action and is FILLED rather than the text
   action the frame draws it as — a lone action is by definition the likely
   intent (sheet anatomy §5). It leads to the offline manager, which is where
   space is actually freed, so the sheet stays inside the no-dead-end rule with
   one action instead of two. `PackFailureSheetsTest` asserts both halves: that
   the action navigates rather than dismisses, and that "Free up space" is
   absent.

4. **19b's bar is used-against-free on the whole volume, and its numbers are read
   at the moment of refusal.** Not from the offline-library meter, which answers
   a different question (how much of the disk THIS APP's packs occupy) and
   degrades to `null` when ML Kit's store cannot be found. Both figures come from
   one `StorageProbe` call pair on IO, so the fill and the legend cannot describe
   two moments. The rule the export states five times over
   (`docs/design/language-screens/README.md:15`) is that at 110 MB the library
   cannot be plotted against a whole device without misstating one of the two
   numbers; a `deviceUsedFraction` unit table pins the clamp, the swap and the
   unmeasurable-volume degrade.

5. **The failed row's Retry is a labelled pill, and defect G's OTHER half needed
   no change.** The ruling names this deviation as *"icon → spec filled pill"*
   and that is what shipped: `Icons.Filled.Refresh` in an `IconButton` became a
   `Button` reading **Retry**, error-filled as drawn, at
   `Dimensions.touchTargetMin` where the frame draws 40dp (C-14's floor, the same
   collision PR-14 settled the same way for the landscape search field).
   **Defect G's first half — "no ISO-code avatar, and out of A–Z order" — was
   already right in the code and is left alone**: every row's avatar is
   `LanguageAvatar.Code` regardless of state, and `buildPickerRows` sorts the
   whole list through one `Collator`. Nothing NAMED that rule, though, so nothing
   would have gone red the day someone opened the export, saw `cloud_off` after
   Azerbaijani, and "fixed" the app to match the picture. Two tests now name it
   (`LanguagePickerRowStateTest`), which is the deliverable defect G actually
   had.

6. **`cd_text_lang_retry` changed VALUE, and the label is why.** It read *"Try
   downloading %1$s again"* while the control was a bare glyph. WCAG 2.5.3
   (*Label in Name*) requires a control's accessible name to CONTAIN the words
   drawn on it — otherwise a voice-control user says "tap Retry" and nothing
   happens — so the moment the word appears on the button, that description stops
   containing its own label. Now *"Retry download for %1$s"*, in three locales.
   The key survives rather than being retired: a screen-reader user still needs
   the language name that the sighted user reads a few dp to the left.

7. **The surviving failure copy is the picker's on all three messages, and
   #175's own recommendation is deviated from twice.** The issue recommends
   keeping the `offline_*` NAMES and taking the offline manager's wording for
   `generic`. Neither was followed. *Names:* the key is no longer either
   screen's — it is read by two screens and two sheets — and `offline_*` is
   Screen B's namespace on a screen PR-23 rewrites; `lang_*` has been this
   module's shared prefix since PR-17's sheet-19a set. *Wording:* each of the
   picker's three sentences states the fact and then the way out, which is
   EDGE_CASES §7 in one line, while `Something went wrong — retry` states
   neither.

8. **The sheets are raised by the PICKER only, and only for a download that
   screen asked for.** Two consequences, both deliberate. *Only the picker,*
   because 19b's action navigates to the offline manager and raising it FROM
   that manager would be a button to where the user already is; Screen B keeps
   its row cause line — now the shared sentence — and PR-23 rewrites that screen
   anyway. *Only for a requested download,* because the manager's state map is
   shared by every screen and outlives the screen that caused a failure, so a
   picker opened after a failure elsewhere would otherwise be ambushed by a sheet
   about something the user did not just do.

9. **A watcher, not an event — and the honest limit that comes with it.** The
   Translation brain has no outcome API by design (`download()` hands the
   transfer to a process-lifetime scope and returns, so leaving the screen cannot
   strand it), and U-1 `PackEvents` — the sanctioned outcome channel — is
   **PR-22**. Building a second one here is what REJECT §7.8 bounces, so
   `LanguagePickerViewModel` watches the shared state map for the one attempt it
   started. **The case it cannot report:** a Retry refused for the SAME reason
   already on the row writes the identical value, so the map never changes and no
   second sheet opens. That is left as the better behaviour rather than papered
   over — re-opening a modal sheet to say exactly what the user read and
   dismissed a second ago is worse than the row's own line, which is still there,
   still names the cause and still offers Retry. Pinned by
   `PackFailureSheetRaisingTest.a retry refused for the same reason does not
   interrupt twice`, with the reasoning in the ViewModel so a change of mind has
   to be a decision. PR-22 can delete the whole watcher.

   > **REVERSED, 2026-08-03 — issue #234** (`docs/plan/issue-234-failure-sheet-state.md`).
   > The change of mind this paragraph asked to be a decision is now one. Two
   > things were wrong with it. The smaller: "no second sheet opens" understated
   > the cost — the row's **enabled, labelled, 48 dp Retry produced nothing at
   > all**, no spinner and no sheet, and a watcher was left suspended on a shared
   > flow until the picker closed. Under `EDGE_CASES.md` §7 an enabled control
   > with no observable effect IS the dead end, and "the row still offers Retry"
   > establishes that the row still RENDERS one, not that it DOES anything. The
   > larger: the argument was *"the sheet they dismissed a second ago"* — and a
   > second ago they were told 12 MB free, and have since had the chance to
   > change it, which is exactly what #235 found them doing. The refusal is now
   > reported on every attempt with figures read at raise time, and the pinning
   > test above is inverted (`…raises the sheet again`). **The watcher stays and
   > PR-22 can still delete it**: only the SYNCHRONOUS pre-flight answer moved,
   > onto the return of the call the caller already awaits (`DownloadAttempt`),
   > which adds no flow, no scope and no second channel for REJECT §7.8 to bounce.

10. **A second divergent pair was found and deliberately NOT folded in.**
    `cd_text_lang_retry` ("Try downloading %1$s again") against
    `offline_cd_retry` ("Retry downloading %1$s") — the same #175 shape, on the
    control that answers the failure #175 was about, in the accessibility layer
    where nobody looks. `STRINGS_language.md` §9 never listed it. It is recorded
    for its own issue rather than folded in, because #175's brief enumerates six
    keys and a PR that quietly does eight is a PR whose `Call sites:` line stops
    meaning anything.

**What the mutation register found that the tests did not** — nothing, which is
worth stating rather than leaving as silence. All ten mutations were written down
before the first test (`Reproduced:` in the PR body carries the run), all ten
reddened the test they were aimed at, and none of them reddened a test that had
no business failing. The one thing no JVM test in this repo can reach is the
same thing `MobileDataSheetTest` names: a PIXEL. The sheets' colours — 19d's
error-container icon slot and cause card against 19b's primary-container one, the
error-filled Retry pill — are checked on the device screenshots in the PR body
and nowhere else.

### PR-19 deviations from the ruling's PR-19 row (2026-08-03)

Each checked against the code or the export's markup rather than against the
ruling text (mandatory rule 11, fourth cause).

1. **The drawn 19g is false about this app, and it is not shipped.** The export
   draws *"It is your target language. Removing it switches the target to
   English."* **Nothing switches.** Enumerated two ways: `grep -rn` for
   `setTargetLang|setSourceLang|setLanguagePair` across every module, and
   separately every writer of the DataStore keys themselves inside
   `TranzlatePreferencesDataSource`. The language selection has exactly four
   production writers — `LanguagePickerViewModel.select` (the user picking a
   language) and `TextViewModel`'s three `setLanguagePair` calls (swap, restoring
   the last request, reopening a history row). The remove path is none of them:
   `OfflineLanguagesViewModel` reaches `OfflineModelManager.delete`, and
   `RealOfflineModelManager` is constructed from a `ModelStore` and a
   `StorageProbe` — it cannot reach a preference at all. Removing a pack takes
   away OFFLINE capability and nothing else; `mergeModelStates` returns the row
   to `NotDownloaded` and `RealTranslator.waterfall` keeps translating the
   language through GOT and GCT, naming that exact case in its own trace
   ("MLKit: fr not downloaded · GOT: offline"). **Verified on the device**: after
   removing the in-use French pack the composer still reads
   `Target language, French`.

   The owner said the same thing when the drawn sentence was put to him: *"If you
   delete a language pack in the app, nothing happens. It just stops working
   offline. You can download it again and make it offline. Otherwise it can work
   online."*

2. **Ruling 3 is therefore moot, and no fallback is implemented.** The ruling
   asks which language becomes the target when an in-use pack is removed, and
   answers *the device language if catalog-capable, else `en`*. It was approved
   by the owner on 2026-08-01 along with the other seven — it is a real decision,
   not an open recommendation — but it answers a question the app never asks.
   Building it would have required a device-locale seam, a fallback rule and a
   preference write, all in service of a behaviour nothing else in the app has.
   **The ruling is not overturned; it is unreachable.** If the owner ever wants
   removing a pack to change the selection, ruling 3 already says what to change
   it to.

3. **The saved line's second sentence is corrected the same way.** The export
   draws *"3 saved phrases use Spanish. They stay saved and will need a
   connection to reopen."* The first sentence is true and is the useful half —
   saved rows live in Room's `translation` table and nothing on the delete path
   can reach it. The second is false: `TextViewModel.onHistoryPick` puts
   `translation.targetText`, the STORED answer, straight into the result state
   with no engine call, and even Retry short-circuits on
   `TranslationRepository.cachedAny`, a database read. Shipped as *"They stay
   saved and still open without a connection."*

4. **19g survives as its own sheet, and this is the owner's to confirm.** With
   the switch gone, what 19g adds over 19f is immediacy — this is not a
   capability the user might miss one day, it is the next translation they make —
   plus the saved-phrases reassurance, which a user removing "Spanish" may
   reasonably want. That is a real difference and both frames are drawn, so both
   are built. But the case for a second sheet is now thinner than the export
   assumed, and **whether to keep 19g or fold its saved line into 19f is a design
   call, not an engineering one.** Recorded rather than decided. Its `warning`
   glyph and `Remove anyway` verb are kept and re-justified: the sheet still
   states a reason to hesitate, so "anyway" still reads correctly.

5. **The ⏹ on a downloading row is NOT confirm-sheeted.** The ruling asks for the
   unconfirmed 🗑 to become confirmed. 19f's body describes removing a pack the
   user HAS; a download still in flight is not that, nothing is being taken away
   that they had a moment ago, and the ⏹ has always been the way out of a
   download they no longer want. A confirmation in front of an abort turns an
   escape hatch into a second decision. `stopDownload` is therefore split from
   `requestRemove` and stays immediate, with a test that reddens if it is routed
   through the sheet.

6. **The saved-count query is not the query it looks like it should be.** The
   obvious spelling —
   `WHERE favourite = 1 AND (source_lang = ? OR target_lang = ?)` — does **not**
   use the two indices this PR adds, because SQLite's OR optimization needs
   `sqlite_stat1` to prefer two index lookups over one and **Room never runs
   `ANALYZE`**. Measured three ways (empty table, 4000 rows without `ANALYZE`,
   4000 rows after `ANALYZE`); only the third reaches them. The shipped query is
   a `UNION` of two single-column lookups, which plans as two COVERING index
   searches with no statistics at all, and `SavedCountQueryTest` asserts that
   plan against the SQL Room actually issues (captured through `setQueryCallback`,
   not retyped). Had the obvious form shipped, both indices would have cost every
   write and served nothing.

7. **`core:database` gains Robolectric, and `tranzlate.compose-test` gains one
   line.** The DAO's SQL had no test that ran it — two Kotlin doubles
   re-implement the count, and neither can be wrong the way the SQL can. Applying
   the project's one Robolectric wiring to a non-Compose module surfaced a latent
   bug in it: `ui-test-manifest` was added to `debugImplementation` without the
   Compose BOM, which resolved only because every previous consumer was also a
   Compose module. Fixed where it lives rather than worked around.

## Spec of record — rev 5 (2026-08-01)

`docs/design/language-screens/language-screens-spec.html` is **rev 5**, and the
sixteen corrections commissioned after the rev 4 review are **all in** — item by
item in DESIGNER-BRIEF §11, verified against the drawings. Highlights that
change what gets built:

- **21b** no longer offers a download on Azerbaijani or Basque (no pack exists
  for either), **19j and 19i are cut** (neither has a trigger that can fire) —
  *this paragraph said so from the day rev 5 landed, while the PR-12 scope row
  above went on ordering 19j built until #180 (**PR #181**); the row is
  corrected, and the ruling's three matching lines with it*,
  the **Detect `ONLINE ONLY` chip is gone from every frame** — so **PR-27's
  scope shrinks to the shipped app only**, since the spec no longer disagrees
  with ruling 2.
- The **speaker mark's meaning is settled**: it reports the *device voice*,
  installed separately from the translate pack. **PR-10 and PR-12 build to
  that** — a row with no pack may still carry the mark.
- The **failed row keeps its alphabetical slot** in the app; its position in the
  frames is a drawing convenience, stated in the caption.
- The running state's free space is **1.4 GB**, so the in-flight download the
  frames show is possible; `12 MB` survives only inside 19b, where it is the
  trigger.

**Verification is now scripted, not eyeballed:** every drawn row's ISO code is
cross-checked against `BundledLanguageCatalog.offlineCapableIds` across 54 of the
58 frames — zero contradictions (the true structural count is **58**; `20c`/`20e`
were never labelled, corrected in #184 / PR #301). Rev 4 shipped that exact defect past a manual
scan, so the check is written down in the brief.

**Ads (spec §7) remain OUT of this epic — tracked as #139.** One of its four
owner decisions is now settled: **Pro removes ads**, and always did —
`BUSINESS_MODEL.md` says so in four places, including the paywall's first
selling point. Three decisions left. **The one thing this epic adopts now:**
picker/Manage-packs list `contentPadding.bottom` and the A–Z rail's bottom stop
stay **parameterised** — today they are hardcoded (`LanguagePickerScreen.kt:370`
and the rail's `padding(vertical = spacing.lg24)`).

## Per-PR gate (unchanged standing rules)

Issue-first · co-verify lens by a non-author agent (HIGH-RISK = adversarial +
cross-model) · full gradle gate · strings ×3 locales + #115 ledger line ·
`@PreviewLightDark` per meaningful state · emulator ritual on UI PRs ·
**tracker row updated in the merging change**.

## Device experiments

E-V1 voice enumeration reliability · E-W1 requireWifi observability (gates
19a's drawn actions + snackbar 20a-5) · E-D1 IME inside the tablet dialog ·
E-S1 models-dir walk (**RAN 2026-08-02, passed** —
`docs/research/issue-130-e-s1-storage-walk.md`). Results are recorded in
research docs as they run.

**E-W1 status, stated because PR-17 needed it and could not find it: NOT RUN.**
No research record, no issue, no commit mentions it; the nearest thing in the
tree is `docs/plan/issue-90-download-lifecycle.md`, which parks the
same probe as "X6 (v2 only)". PR-17 therefore shipped the owner's pre-approved
interim (ruling 8) and claims no E-W1 result. **It still gates snackbar 20a-5
in PR-22**, and the follow-up issue the ruling asks for is the owner's to file.

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

**Discharged 2026-08-02.** The run is in
`docs/research/issue-130-e-s1-storage-walk.md`: the E3 path still holds (30
files, 44,169,505 bytes for one af↔en pack on `emulator-5554`), the renamed
store degrades to `null` rather than to zero, and a fresh install has no model
store at all, so a null byte answer there is ordinary rather than a fault.

**Re-run and corrected the same day (E-S1b, E-S1c, co-verify).** The first run
was reasoned about rather than measured — the original run never did a
`pm clear`. Doing one showed the pack COUNT is 1 on a fresh install, not 0,
because ML Kit reports the English pivot as on device, so the free-space line is
the first card a new user sees rather than a rare degrade (item 8 above).
E-S1c separately showed the walk counts an interrupted download's leftovers
forever, not "for a few seconds" as first recorded (item 8b).

### PR-16 deviations from the ruling's PR-16 row (2026-08-02)

Each checked against the export's markup, the androidx source or the running app
rather than against the ruling text (mandatory rule 11, fourth cause).

1. **The card's ViewModel is scoped to the card, and the ruling does not say
   how.** The ruling puts the dialog outside `NavDisplay` (§2, and R13: this
   composition is Nav-external), which is where `hiltViewModel()` stops resolving
   to a nav entry and starts resolving to the **Activity** — a third
   screen-outliving scope, which the ruling's own §2 inventory bounces at review.
   The seam PR-13 left is that `LanguagePickerScreen` takes its ViewModel as a
   parameter, and what fills it is `rememberViewModelStoreProvider` /
   `rememberViewModelStoreOwner` (lifecycle-viewmodel-compose 2.11.0, public
   API). Those build a child `ViewModelStore` INSIDE the Activity's store, and
   clear it when the composable leaves the composition **unless the parent
   lifecycle is already destroyed** — cleared when the card closes, kept across a
   rotation. Verified on the device: typing `ger`, resizing 800×1280 → 1280×800
   and finding `ger` still in the field; closing and reopening and finding it
   empty.
2. **The card is not a fourth ARRANGEMENT, but it is a fourth branch of the
   gate.** 17c reuses single-pane exactly. 17d does not: the export draws the
   catalog in **two columns** at 720 × 624 and one column at 560 × 998, and the
   discriminator cannot be width, because 560dp already clears two
   `pickerColumnMin` columns with 80dp to spare. What separates them is that the
   landscape card is wider than it is tall. `PickerArrangement` therefore grows
   one field (`rail`) and `pickerArrangement` one parameter (`host`), decided
   before any question of size — the card's constraints are the CARD's, and
   measuring them as if they were a window's would have offered 17d 17a's side
   pane, which the export draws in none of the four tablet frames.
3. **No A–Z rail in the card, counted rather than assumed.** 15a and 17a each
   draw all twenty-six letters; the four tablet frames draw none. The reason is
   the geometry: the rail is a drag target pinned to the trailing edge, and in a
   card that edge has the scrim a few dp beyond it.
4. **"All languages" and its counter are NOT the rail**, and the device run is
   what separated them. The first build tied the header to the same boolean as
   the rail, because until this host existed the two were always equal — and
   `5 of 59 packs on device`, which the export draws in all four tablet frames,
   silently disappeared. Two booleans now: `wholeCatalog` (header, counter,
   `catalogOffset`) and `arrangement.rail`.
5. **`imePadding()` on the layer the card is measured in — E-D1's actual
   finding.** The soft keyboard was reported as refusing to appear in phone
   LANDSCAPE on this AVD. It does **not** at tablet sizes: `mInputShown=true`,
   `mImeWindowVis=3` at both 800×1280 and 1280×800. What the experiment DID find
   is that the dialog window does not resize for the IME, so the keyboard covered
   the bottom of a card that still thought it was 998dp tall, putting the docked
   "Manage packs" and "Cancel" out of reach until the keyboard was dismissed.
   Taking the IME inset where the card is MEASURED fixes it in both orientations.
6. **The jank budget is measured and reported UNSATISFIABLE on an emulator**,
   which is a finding rather than a skipped gate. The ruling asks for "fling
   frame-drop clusters zero; fail = PR fail". `dumpsys gfxinfo` on
   `emulator-5554` (Resizable_Experimental, API 37), same window, same input
   protocol, same session:

   | surface | frames | janky |
   |---|---|---|
   | the new card, 1280×800, two columns, over a composed Home | 61 | 78.7% |
   | the **shipped, merged** offline list, full screen, same window | 51 | 86.3% |

   Repeats put the card at 75–79% across five runs. **Already-merged code does
   not clear the budget either**, so a failure here cannot distinguish this PR's
   work from the harness — `input swipe` is a synthetic three-point gesture and
   not a fling, and the absolute percentage moved by a factor of two purely with
   the swipe protocol. The comparative result is the part that means something:
   **the card is not worse than a list this project has already accepted.** The
   named escape hatch (`produceState(Default)` + Collator memoize) is therefore
   NOT taken — the ruling refuses preemptive optimization without measured need,
   and there is no measured need, only an unusable instrument. A real budget
   needs a physical device and Macrobenchmark; this repo has neither (#40 — CI
   compiles instrumented tests and never runs them).
7. **Two labels for one destination, left un-unified deliberately.** The card's
   docked action reads "Manage packs", as the export draws it; owner ruling 5
   relabels the Home row that opens the SAME destination to "Language packs" in
   PR-23. Unifying here would make that relabel two PRs' work in three locales.
   Flagged rather than silently changed.
