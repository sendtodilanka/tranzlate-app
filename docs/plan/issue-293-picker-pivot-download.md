# Plan — issue #293: the English/pivot row on the language PICKER offers Download

status: accepted
Fixes: #293
Research: `docs/research/issue-293-picker-pivot-download.md`
Basis for acceptance: the owner's rev5-completion objective (CLAUDE.md rule 13 —
#293 is a group-A user-visible rev5 defect, classified in
`docs/plan/issue-130-rev5-completion.md`), the landed **#224 precedent** (PR #292,
commit `96a131b`), and the issue itself. This is the sibling of #224 on a second
screen; the design decision it needs was already ruled on by the owner for #224.

## The defect

The **language picker** draws a real, tappable **Download** button on the English
row. `LanguagePickerModel.rowStateOf` (`LanguagePickerModel.kt:643-658`) computes
`LanguageRowState.Downloadable` for English exactly as it does for any other
offline-capable language whenever `offlineDownloaded == false`, and
`LanguagePickerScreen`'s `RowTrailing` (`:1676-1688`) renders that state as an
unguarded `IconButton` (`tt_lang_download_en`). Tapping it runs
`onDownload("en")` → `LanguagePickerViewModel.download("en")` →
`DownloadGate.requestDownload("en")` → (once consented)
`RealOfflineModelManager.download("en")` →
`manager.download(TranslateRemoteModel.Builder("en").build(), …)`.

English is the ML Kit **pivot**: every one of the 58 downloadable models is an
`X↔English` pair and **there is no standalone `en` model** (AAR proof in
`.claude/memory/mlkit-english-is-in-every-model.md`; re-verified for #224). So a
Download control on the English row offers a pack that does not exist — the same
class of misleading control #224 found on the offline manager's Delete side.

**When the button is actually drawn (the difference from #224, rule 12).** The
offline manager showed English as *Downloaded* on first run (Delete icon), because
ML Kit reports the pivot as on-device before any pack (#224, measured: first-run
downloaded count = 1). The picker is different: `LanguageRepositoryImpl.languages()`
combines the model-state flow with `onStart { emit(emptyMap()) }`
(`LanguageRepositoryImpl.kt:75`), so on the **first frame the model-state map is
empty**, `offlineDownloaded` is false for every language, and English —
offline-capable — resolves to `Downloadable` and draws the Download button until
the real `getDownloadedModels()` result arrives. It also stays `Downloadable`
permanently on any device/state where `getDownloadedModels()` does not report `en`
(the memory's still-open consequence). Full analysis and severity in the research
record. Severity: **S3/P3** — a control that misleads but, per the measured
`en`-is-permanent evidence, does no damage.

## The decision (inherited from the owner's #224 ruling, 2026-08-05)

The pivot row is **kept and made non-actionable**, not hidden — the same ruling
#224 applied, for the same reason: English is the 59th id in
`BundledLanguageCatalog.offlineCapableIds`, so hiding it would make the "59
languages" counter (C-11) lie. On the picker specifically:

- **Keep the row SELECTABLE.** A picker row's whole job is to pick a language, and
  the user must still be able to choose English as source or target. The
  `.selectable` modifier is keyed on selection, not on pack state, so it is
  untouched; a selected pivot still shows its tick.
- **Strip the pack affordance.** No Download (and, by guarding the whole trailing
  control, no Stop / Retry / Online-only chip / on-device icon either) on the
  pivot — none of them acts on a pack that exists.
- **Tell the SAME story as the offline manager.** The pivot row carries the quiet
  sub-line **"Included with every language"** — the exact `offline_included` string
  #224 introduced, reused here (one sentence, one source of truth), tagged
  `tt_lang_included` for the picker. So a user who opens the offline manager and the
  picker sees English described the same way in both, instead of a Download here and
  an "included" there.
- **Guard by id, not state.** `isPivotLanguage(row.id)` is checked in the render
  layer, so even a pivot the model layer somehow reports as `Downloadable`,
  `Downloading` or `Failed` offers no control. A per-row boolean flag was rejected
  for #224's reason: a flag the screen never reads would be dead code and its test a
  tautology.

## The change

Scope owned for this PR: `LanguagePickerScreen.kt`, `STRINGS_language.md`, this
plan, the research record, and the new test. **No `strings.xml` change** — the
pivot line reuses the existing `offline_included` key (already in all three
locales and already catalogued), so `verifyStringKeyDocs` needs no new row.
`LanguagePickerModel.rowStateOf` is deliberately **left alone**: special-casing the
pivot in the state mapping would put the guard behind a state a mutation could
reshape, and the model's six-state matrix (`LanguagePickerRowStateTest`) is
unit-pinned as it is. The guard lives where the control is drawn.

`isPivotLanguage` / `PIVOT_LANGUAGE_ID` are `internal` in
`OfflineLanguagesViewModel.kt` (`:66`, `:69`) in the same `:feature:language`
module, so the picker calls the predicate directly — no new identity, no
duplication, no drift.

1. **`RowTrailing`** (`LanguagePickerScreen.kt`) — one guard line before the state
   `when`: for the pivot, render nothing except the `Selected` tick. Selection is
   handled first so a chosen English keeps its tick; every pack-control branch is
   then skipped by id.
2. **`LanguageRow` + `RowSupportingLine`** — for the pivot the supporting line is
   the `offline_included` sentence (tagged `tt_lang_included`), forced to the static
   (non-progress) layout, with the offline-voice speaker preserved if the device has
   an English voice. Non-pivot rows are unchanged.
3. **`@PreviewLightDark`** — `LanguageRowPivotPreview` (unselected) and
   `LanguageRowPivotSelectedPreview` (selected, tick + included line) so the owner
   reviews the new state on both themes (rule 7).
4. **`STRINGS_language.md`** — the `offline_included` row's note is extended to
   record that the picker now renders it too (as `tt_lang_included`); no key added.

## Enumeration (rule 11) — two independent searches

**Call sites: 2 found, 2 changed** — the two render sites that draw an actionable
pack control keyed off English's row state:

- `RowTrailing`'s `LanguageRowState.Downloadable` arm (the Download `IconButton`,
  `tt_lang_download_${id}`), and the supporting-line path feeding the row.

Two independent searches, neither able to miss what the other finds, named in the
PR body:

- by testTag: `grep -rEn "tt_lang_(download|stop)_" feature/language/src/main` — the
  actionable trailing controls, all inside `RowTrailing` in `LanguagePickerScreen.kt`.
- by state usage: `grep -rEn "LanguageRowState\.Downloadable|rowStateOf\(" feature core`
  — every producer/consumer of the `Downloadable` state; only `RowTrailing` turns it
  into a tappable control (the rest compute or describe it).

New predicate call sites after this PR: `isPivotLanguage` — defined in
`OfflineLanguagesViewModel.kt`; used in `OfflineLanguagesScreen.kt` (#224), now also
`LanguagePickerScreen.kt` (this PR), and the two render tests.

## Tests (mutate-first, rule 11) — `LanguagePickerPivotRowRenderTest.kt`

Render tests, because only a render can see what the guard draws. Each mutation was
decided before the test and is proven RED-then-GREEN in the PR body:

- **pivot not actionable** — English driven to `Downloadable` (the bug path:
  `offlineDownloaded=false`, empty offline states) shows `tt_lang_included` +
  "Included with every language" and **no** `tt_lang_download_en`. Mutation: delete
  the `isPivotLanguage` guard in `RowTrailing` → the pivot renders
  `tt_lang_download_en` again → RED. That mutation is the #293 defect itself.
- **control, non-vacuous** — a non-pivot Spanish `Downloadable` row still shows
  `tt_lang_download_es` and no included line. Mutation: widen the guard to strip the
  control from every row → Spanish loses its Download → RED. Stops a "fix" that
  hides the button everywhere from passing.
- **guard is by id, not state** — English driven to `Downloading` offers neither
  `tt_lang_download_en` nor `tt_lang_stop_en`, and still shows the included line.
  Mutation: guard only the `Downloadable` state (by state) → a `Downloading` pivot
  leaks `tt_lang_stop_en` → RED. Proves the guard is by id, not by one state.
- **predicate** — `isPivotLanguage` is true only for `en` (the shared identity this
  screen now depends on).

## Gate

`./gradlew preflight` green (string-key catalogue, detekt, spotless, every module's
unit tests incl. the Robolectric render tests, every androidTest source set's
compilation, both debug APKs).

## Not measured on a device — and why (rule 4 · rule 12)

The runtime effect of `download("en")` on the pivot is characterised from the code
path + #224's on-device measurements + the AAR proof, and is **not** independently
re-measured here. An emulator (`emulator-5554`) was up, but two other agent
worktrees were active this session and the device-claim file was absent — which is
exactly the state in which two agents have shared one emulator before (rule 12,
fourth shape; the #213 hook that "had never fired"). Per the brief's "if unsure, do
NOT", I did not claim or drive it. The reasoning that stands in for the measurement
is in the research record.

## Landed — PR #297

Merged via `/land-pr` (co-verify APPROVE-WITH-NOTES, cross-model). One non-blocking
follow-up the lens found and I filed separately: the pivot row's
`stateContentDescription` is not yet `pivot`-guarded, so a TalkBack user still hears
"available for offline use" on English even though the visual Download affordance is
gone — a visual/a11y mismatch this PR introduces. Tracked as its own issue for a
picker-a11y pass; the visual fix here is a net a11y improvement (one fewer reachable
misleading action) and stands on its own.
