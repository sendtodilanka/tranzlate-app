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
| PR-13 | Fold-posture `WindowInfo` extension + host-agnostic saveable contract (query + list position out of `rememberSaveable`, into the picker VM's `SavedStateHandle`) + `pendingConsent` → `SavedStateHandle` behind a storage seam + LeakCanary debug-only rider. **`LanguageSheetRequest` is NOT built here** — it has no members until PR-17; see below | 👁 **PR #192** |
| PR-14 | 17a landscape two-pane: a `pickerArrangement()` window gate + a side pane beside the catalog + the catalog on a grid so ONE list position means the same language in both arrangements + the Detect-key settle. **Deviations below** | ✅ #198, 2026-08-02 |
| PR-15 | 17b foldable two-leaf (24dp crease gutter, 296dp leaf) + the offline-library meter (U-5) with its two honest degrades. **E-S1 ran and passed** — `docs/research/issue-130-e-s1-storage-walk.md`. **Deviations below** | ✅ #200, 2026-08-02 |
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
cross-checked against `BundledLanguageCatalog.offlineCapableIds` across all 54
frames — zero contradictions. Rev 4 shipped that exact defect past a manual
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
