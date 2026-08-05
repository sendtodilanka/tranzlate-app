# Plan — issue #224: the English/pivot row on the Offline-languages screen

status: accepted
Fixes: #224
Research: `docs/research/issue-224-en-row-delete.md` (Branch A, measured on emulator)

## The decision (owner, 2026-08-05)

The measurement settled which of #224's two branches is real: **Branch A — tapping
Delete on the English row is a no-op.** `deleteDownloadedModel("en")` removes
nothing from ML Kit's downloaded set; English stays reported as downloaded, offline
translation is unharmed, and the "Download" side of the row is never reachable
because `en` never leaves the set. Full evidence, with a French control experiment
that closes the Cancel/Remove confound, is in the research record.

Owner ruling on the fix: the English/pivot row becomes a **non-actionable
"included with every language" row — NOT hidden.**

- **Not hidden** keeps the counter honest. English is the 59th id in
  `BundledLanguageCatalog.offlineCapableIds`, and it IS the first-run downloaded
  count of 1. Removing it from the list would make the "59 languages available
  offline" counter (C-11) and that first-run count disagree with the list.
- **Non-actionable** removes the lie. No Download, no Delete on that row, because
  neither does anything real to the pivot.

Because Branch A is a no-op (not the destructive Branch B), **no anti-delete guard
and no recovery path are needed** — those were Branch-B costs. The fix is purely
presentational: stop offering controls that act on the pivot.

## The change

Scope owned for this PR: `OfflineLanguagesViewModel.kt`, `OfflineLanguagesScreen.kt`,
the three `strings.xml`, `STRINGS_language.md`, this plan, the research record, and
the new test. The pack-removal confirm sheet (#230) is deliberately untouched.

1. **`OfflineLanguagesViewModel.kt`** — the pivot identity lives here:
   `PIVOT_LANGUAGE_ID = "en"` and `isPivotLanguage(id)`, with a KDoc that records the
   measurement and the ruling. `TranslateLanguage.ENGLISH` is `"en"` and this catalog
   hands ML Kit tags through untranslated, so the pivot's id is `"en"` in every layer.

2. **`OfflineLanguagesScreen.kt`** — `OfflineRow` now:
   - draws a quiet sub-line `offline_included` ("Included with every language",
     `tt_offline_included`) for the pivot, in place of the Failed line; and
   - skips the trailing control entirely for the pivot. The trailing `when` moved
     into `OfflineRowTrailingControl`, called only when `!isPivotLanguage(row.id)`.
     Guarding by **id, not state** means even a pivot somehow reported
     `NotDownloaded` offers no ⬇ for a pack that does not exist.
   - A `@PreviewLightDark OfflinePivotRowPreview` shows the owner the new state on
     both themes (rule 7).

   **Why the identity is a shared predicate, not a flag on the row (ownership +
   evidence).** The screen renders `OfflinePackRow`, produced by `buildOfflineRows`
   in `OfflinePackRow.kt`, which this PR does not own. A `pivot` field added to
   `OfflineLanguageRow` could not reach the screen without editing that file, and a
   field the screen never reads would be production-dead code whose test would be a
   tautology. So both the row layer and the screen decide "is this the pivot?"
   through the one `isPivotLanguage` predicate — one identity, no drift.

3. **Strings** — `offline_included` added to `values/`, `values-fil/`,
   `values-pt-rBR/` (C-2), and to `STRINGS_language.md` §7 (C-3, or
   `verifyStringKeyDocs` fails). The fil/pt-BR wordings are best-effort pending the
   human native review tracked by #115, like every other string in those files.

## Enumeration (rule 11)

**Call sites: 1 found, 1 changed.** The offline Download/Delete controls render in
exactly one place, `OfflineLanguagesScreen.kt`. Two independent searches agree:

- `grep -rn "tt_offline_download\|tt_offline_delete" --include=*.kt feature/ core/`
  (excluding build/ and test/) → only `OfflineLanguagesScreen.kt`.
- `grep -rln "OfflineModelState.Downloaded\|OfflineModelState.NotDownloaded"` → five
  files, of which only `OfflineLanguagesScreen.kt` renders the CONTROLS (the others
  produce/merge/overlay state). The picker (`LanguagePickerScreen.kt`) shows offline
  status as badges, never a Download/Delete button — so it is not a second surface
  with this bug.

New pivot predicate (`isPivotLanguage` / `PIVOT_LANGUAGE_ID`) call sites after this
PR: defined in `OfflineLanguagesViewModel.kt`; used in `OfflineLanguagesScreen.kt`
(the sub-line guard and the trailing-control guard) and `OfflinePivotRowRenderTest.kt`.

## Tests (mutate-first, rule 11) — `OfflinePivotRowRenderTest.kt`

Render tests, because only a render can see what the guard draws:

- **pivot not actionable** — English `Downloaded` row shows `tt_offline_included`
  and the "Included with every language" text, and NO `tt_offline_delete` /
  `tt_offline_download`. Mutation (decided first): remove the
  `if (!isPivotLanguage(row.id))` guard → the pivot's Downloaded state renders
  `tt_offline_delete` → RED. That mutation is the #224 defect itself.
- **control, non-vacuous** — a non-pivot Spanish `Downloaded` row still shows
  `tt_offline_delete` and no included line. Guards against "fixing" it by stripping
  controls from every row.
- **guard is by id, not state** — a pivot reported `NotDownloaded` still offers no
  `tt_offline_download`. Mutation: guard only the `Downloaded` branch by state → RED.
- **predicate** — `isPivotLanguage` is true only for `en`.

## Gate

`./gradlew preflight` green (string-key catalogue, detekt, spotless, unit tests
incl. the Robolectric render tests, androidTest compilation, both debug APKs).

## Out of scope (from the research — file as issues if wanted, NOT fixed here)

- Offline error precedence: a missing pack + offline surfaces "You're offline"
  rather than anything about the model (the online tiers' NETWORK cause wins over
  ML Kit's `MODEL_NOT_DOWNLOADED`).
- Debug-only second launcher icon (LeakCanary) makes `monkey -c LAUNCHER` ambiguous.
