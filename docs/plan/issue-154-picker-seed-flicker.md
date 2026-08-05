---
issue: 154
title: "Language picker ticks a possibly-wrong row for one frame before the real selection is read"
severity: S2
status: accepted
basis:
  - "GitHub issue #154 (found by the adversarial co-verify lens on #141)"
  - "rev5 completion plan wave 1d (docs/plan/issue-130-rev5-completion.md) — user-visible rev5 defect"
owns:
  - feature/language/src/main/kotlin/com/codeboxlk/tranzlate/feature/language/LanguagePickerViewModel.kt
  - feature/language/src/test/kotlin/com/codeboxlk/tranzlate/feature/language/LanguagePickerViewModelTest.kt
  - docs/plan/issue-154-picker-seed-flicker.md
---

# #154 — the picker ticks a row for one frame that may not be the user's selection

## What the user sees (the harm)

Open the language picker as a `de → ja` user. For one frame the tick sits on
**English** (source side) or **French** (target side); then it jumps to the real
row. On a slow first DataStore read it is a visible flicker on the screen's
primary question — *which language am I on?* The `LanguagePickerViewModel` is
built fresh each time the picker's nav entry is pushed, so it happens on every
open, not just cold start.

## Root cause

`LanguagePickerViewModel` seeds its two selection flows with hardcoded language
defaults:

```kotlin
private const val FALLBACK_SOURCE_LANG = "en"
private const val FALLBACK_TARGET_LANG = "fr"
...
private val sourceSelection: StateFlow<String> =
    translatePrefs.sourceLang.map(LanguageTagResolver::canonicalOrSelf)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(...), FALLBACK_SOURCE_LANG)
```

`stateIn` emits the seed **synchronously** on first collect, before
`translatePrefs.sourceLang` delivers its first real value. So the very first
frame the radio group renders ticks `en`/`fr` regardless of the stored pair.

Two aggravating facts:
- Before the #130 PR-4 decouple the selection came from the hoisted
  `TextViewModel`, whose flows are `SharingStarted.Eagerly` and already warm by
  the time the picker opened — so the first frame was correct. The decouple
  traded that away.
- These constants are a **third copy** of the DECISIONS defaults table.
  `TranzlatePreferencesDataSource.DEFAULT_SOURCE_LANG` / `DEFAULT_TARGET_LANG`
  (`core/datastore`) is the single authority; `TextViewModel` holds a second
  copy; this ViewModel a third. A ViewModel restating the default is exactly the
  drift the conventions exist to prevent.

The tests do not catch the bug — they **encode** it:
`skipItems(1) // defaults-table frame` at four selection call sites tells Turbine
to look away from the exact frame that is wrong.

## Option chosen

**The issue's preferred option: serve a "nothing ticked yet" state until
DataStore's first real emission.** A control must never state a choice the app
has not read — *no row selected is honest; a wrong row is not.* This is also the
pattern the rest of this same file already uses: `library` seeds `null`,
`offlineStates` seeds `emptyMap()`, `packFailure` seeds `null`. The selection
flows are the outlier that seed a fake value, and the ViewModel's own KDoc
(the `library` block) states the principle: "A placeholder … that corrected
itself a moment later would have stated something false in between." The skill's
`coroutines-patterns.md` confirms the canonical form —
`stateIn(scope, WhileSubscribed, null)` for UI state where "nothing yet" is the
honest initial.

The rejected alternative from the issue — *seed from the data source's own
defaults rather than a private copy* — removes the third copy but still ticks a
possibly-wrong row for a frame, so it does not fix the flicker. Not chosen.

### Representation, and why it is a sentinel and not a nullable

The honest type would be `selection(): StateFlow<String?>` with a `null` seed.
Reading the consumers shows that ripples **outside this task's file ownership**:

| Consumer | File (owner) | `selectedId` type |
|---|---|---|
| `LanguagePickerContent(selectedId = …)` | `LanguagePickerScreen.kt` (not owned) | `String` |
| private row builder | `LanguagePickerScreen.kt:580` (not owned) | `String` |
| `buildPickerRows(selectedId = …)` | `LanguagePickerModel.kt` (not owned) | `String` |
| `selectedId == DETECT_LANGUAGE_ID` | `LanguagePickerScreen.kt:601` (not owned) | `String` |

A `String?` seed would force compile-breaking edits into `LanguagePickerScreen.kt`
and `LanguagePickerModel.kt`, which this task does not own.

So the fix keeps `selection(): StateFlow<String>` and seeds a **"no selection
yet" sentinel — the empty id `""`** (named `NO_SELECTION_YET`). It matches no
catalog row (`language.id == ""` is false for every real id) and not the Detect
sentinel (`"" == "auto"` is false), so the radio group ticks **nothing** until
the real read arrives. Same preferred semantics ("no row selected"), zero ripple
outside the two owned files. `""` can never be a real emission: no language id is
blank and `select()` only ever writes real ids or the `auto` sentinel.

This is the **preferred** option (nothing ticked), not the rejected alternative —
only its representation is adapted to the ownership boundary. The pure-nullable
form is recorded as a possible follow-up (widen `selection()` to `String?` when
the screen file is in scope), out of scope here.

Both `FALLBACK_SOURCE_LANG` and `FALLBACK_TARGET_LANG` are deleted — the third
copy of the defaults table is gone. `NO_SELECTION_YET` is not a default language,
so it introduces no new copy.

## Enumeration (rule 11)

`selection()` selection flows — the thing being changed:
- Production consumer: **1** — `LanguagePickerScreen.kt:162`
  (`viewModel.selection(target).collectAsStateWithLifecycle()`), passes through
  to `buildPickerRows`. Behaviour: a `""` frame ticks no row; unchanged type.
- Test consumers: `LanguagePickerViewModelTest.kt` lines 313, 318, 340, 365.

`FALLBACK_SOURCE_LANG` / `FALLBACK_TARGET_LANG` — the symbols being removed:
- `LanguagePickerViewModel.kt`: 4 occurrences (decl ×2, use ×2) — all removed.
- `TextViewModel.kt`: 4 occurrences — **out of scope / not owned.** This is the
  `SharingStarted.Eagerly` case that does not flicker (warm before the composer
  needs it); it is the *second* copy of the defaults table and a separate
  follow-up, noted for the report, untouched here.

## Test — mutation decided first (rules 11, 12)

**Mutation (decided before writing the test):** revert the seed to a concrete
language default — `stateIn(..., "en")` for source / `stateIn(..., "fr")` for
target. That is exactly #154.

**Test 1 (new, behaviour-faithful): `the picker ticks no row until the stored
choice is read`.** Real pair source = `de`; catalog contains `en` and `de`.
Collect `selection(SOURCE)`; build rows from the FIRST frame with the real
`buildPickerRows`; assert **no** row is `LanguageRowState.Selected`. Then the
real frame arrives (`de`) and exactly the `de` row is `Selected`. This asserts
the semantic (no wrong tick) and does not reference the sentinel literal, so it
cannot be gamed by mutating the constant's value.
- Under the mutation: frame 1 = `en` → the `en` row is `Selected` → "no row
  selected" assertion **RED**.

**Test 2 (existing tests de-encoded):** the four `skipItems(1) // defaults-table
frame` lines that *encode* the bug are replaced with an assertion that the first
frame is the honest empty sentinel, then the real value.
- Under the mutation: frame 1 = `en`/`fr` ≠ `""` → **RED**.

RED→GREEN output is pasted in the PR body and the final report.

## Not in scope / follow-ups (for the orchestrator to file)

1. `TextViewModel.kt` still holds the second copy of the defaults table
   (`FALLBACK_SOURCE_LANG`/`FALLBACK_TARGET_LANG`, `SharingStarted.Eagerly`). No
   flicker there, but the drift remains.
2. Widening `selection()` to `StateFlow<String?>` (true nullable "nothing")
   once `LanguagePickerScreen.kt` + `LanguagePickerModel.kt` are in the same
   change set.

## Rule 7 (previews)

No composable is added or changed — the fix is entirely ViewModel StateFlow
seeding. The "nothing selected" render is *all rows in their existing unselected
state*, which existing row previews already cover; and the preview file
(`LanguagePickerScreen.kt`) is outside this task's ownership. So no new
`@PreviewLightDark` is owed here.
