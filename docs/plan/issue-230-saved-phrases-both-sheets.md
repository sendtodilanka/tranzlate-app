# Plan — the saved-phrases reassurance belongs to both removal sheets, not just the in-use one

status: accepted
(accepted basis: issue **#230** (S3/P2), the owner's ruling of 2026-08-05 relayed
by the coordinator, and the rev5 completion plan which lists #230 in wave **1c**
— `docs/plan/issue-130-rev5-completion.md:69,97`. Scope fixed below and not
widened past it.)

Issue: **#230**. Unblocked by **#224** (PR #292, merged — the two files this fix
must edit are now off `origin/main` and no longer another agent's).

## 1. The decision

#230 poses two coherent answers and asks for one. The owner chose option 1
(2026-08-05):

> The saved-phrases reassurance line shows on **both** pack-removal confirm sheets.
> 19f (removing any other pack) gains it too, not just 19g (the in-use pack).
> Shown only when that language has saved phrases (`savedCount > 0`), exactly as
> 19g already does; omitted when 0.

The reasoning #230 records: whether the pack is the live target has **no bearing**
on what happens to saved work — saved rows live in Room's `translation` table,
which nothing on the delete path can reach, in either case. So the reassurance was
attached to the wrong axis. Both sheets now carry it, gated identically.

## 2. Why this is three hunks, not one — the data must flow end to end

The sheet composable alone cannot make the line appear. The count is a database
read owned by the ViewModel; it travels `ViewModel → PendingPackRemoval → Screen →
sheet`. Before this change, **two** points on that path deliberately zeroed it for
19f, so adding a line to the sheet in isolation would draw nothing in the wired
app (and a composable-only test would pass while the user saw no line — the #242
shape).

Enumeration (rule 11), two independent searches (rule 12) — `grep -rn` for the
call form `RemovePackSheet(` and, separately, for the data carriers
`SavedPhrasesLine(` / `savedCountOf(` / `savedCount =` across `src/main`:

| # | File | Site (by content) | Change |
|---|---|---|---|
| 1 | `OfflineLanguagesViewModel.kt` | `savedCount = if (inUse) savedCountOf(id) else 0` | → `savedCount = savedCountOf(id)` (compute for every removal) |
| 2 | `OfflineLanguagesScreen.kt` | the `RemovePackSheet(...)` caller in `OfflineLanguagesContent` | add `savedCount = pendingRemoval?.savedCount ?: 0` (19g one line below already passes it) |
| 3 | `RemovePackSheets.kt` | `RemovePackSheet` composable + `SavedPhrasesLine` | add `savedCount` param + `supportingContent = { SavedPhrasesLine(...) }`; give `SavedPhrasesLine` a `testTag` param |

**Call sites of `RemovePackSheet(`: 1 production (`OfflineLanguagesScreen.kt`), 1
declaration + 1 preview (`RemovePackSheets.kt`). All 3 hunks are in files this
task owns after #224.** The `savedCount` field on `PendingPackRemoval` already
exists and is already carried to the screen — no new plumbing type.

## 3. The mutations, decided BEFORE the tests (rule 11)

Each hunk gets one mutation and one test that reddens under it. All three are
behavioural (they compile), so RED is a test failure, not a build break.

- **M1 (VM hunk).** Restore `savedCount = if (inUse) savedCountOf(id) else 0`.
  Reddens `OfflineLanguagesViewModelTest.an ordinary removal reports the saved
  count too`: `de` is not the target in that fixture, so the guard forces 0 and
  the assertion `isEqualTo(2)` fails.
- **M2 (Screen wiring hunk).** Change the caller to `savedCount = 0` (severing the
  thread while still compiling). Reddens `OfflineRemoveFlowRenderTest.19f draws its
  saved line when the pack has saved phrases`: the `PendingPackRemoval` carries
  `savedCount = 2` but the sheet receives 0, so the line is absent.
- **M3 (sheet composable hunk).** Drop `supportingContent = { SavedPhrasesLine(...) }`
  from `RemovePackSheet`. Reddens `RemovePackSheetsTest.19f reads the saved count
  in the plural`: the sheet draws no line at any count.

The **hides-at-0** half is proven by the same tests' zero-count siblings
(`19f draws no saved line when nothing is saved`), which rely on `SavedPhrasesLine`'s
existing `if (savedCount <= 0) return` guard — untouched by this change.

## 4. The string key is reused; the testTag is new — each follows its own rule

- **String (owner's instruction, and correct):** reuse `R.plurals.lang_sheet_remove_inuse_saved`
  verbatim. Its text is generic about saved phrases — *"N saved phrases use X. They
  stay saved and still open without a connection."* — with nothing "in-use" specific
  in the copy, so it reads true on 19f unchanged. Present with `one`/`other` in
  `values/`, `values-fil/`, `values-pt-rBR/` (verified). **No new string key, no
  `res/` edit.** The key *name* keeps its `_inuse_` segment; renaming it would touch
  three locale files and 19g for no user-visible gain, and the owner said reuse.
- **testTag (convention C-1, mine to set):** 19f's line gets its own tag
  `TT_SHEET_REMOVE_SAVED = "tt_lang_sheet_remove_saved"`, matching the existing 19f
  namespace (`tt_lang_sheet_remove`, `_confirm`, `_cancel`). 19g keeps
  `tt_lang_sheet_remove_inuse_saved`. Reusing 19g's tag on 19f would make the tag
  lie about which sheet it is on, and C-1 wants one tag per control. `SavedPhrasesLine`
  therefore takes a `testTag` parameter and each caller passes its own.

## 5. Would 19f and 19g merge into one sheet with a variant? — No, keep them separate

#230 raises this as a follow-up ("once both carry the line they differ only by the
in-use warning"). My call from the code: **keep two sheets.** After the line is
added they still differ on **four** axes, not one —

- title (`lang_sheet_remove_title` vs `lang_sheet_remove_inuse_title`),
- body (the in-use body adds *"It is your target language, and it stays your target."*),
- icon (`Delete` glyph vs `Warning` glyph — kept distinct on purpose, `RemovePackSheets.kt` KDoc),
- primary label ("Remove" vs "Remove anyway"),

plus separate testTag namespaces that carry a live mutation guard
(`OfflineRemoveFlowRenderTest` mutation **D5**: drawing 19f for the in-use case must
lose the whole warning — it asserts `TT_SHEET_REMOVE` and `TT_SHEET_REMOVE_IN_USE`
are mutually exclusive). A parameterized merge would rework those tags and their
tests for no user-visible change and widen the diff well past the owner's decision.
Recorded as a possible later cleanup, deliberately not done here.

## 6. One existing test is rewritten because its premise was reversed, not because it was weak

`OfflineLanguagesViewModelTest.an ordinary removal reports no saved count`
(pre-change) seeds a saved phrase using `de` and asserts `savedCount == 0` — it
encoded the very optimization the owner just reversed ("19f draws no saved line, so
the ordinary removal must not pay for the query"). It is replaced by `an ordinary
removal reports the saved count too`, which asserts the count IS reported for a
not-in-use pack, over an asymmetric fixture (either side of the pair counts). This
is the owner changing what correct behaviour is, not a test being loosened to pass.

Two nearby VM tests are untouched: the broken-DB / cancelled-query cases
(`a broken saved-count query still lets the sheet open`, `a saved-count query that
fails to link still lets the sheet open`, `a cancelled saved-count query is not
folded to a zero-count sheet`) all use `fr` (the in-use pack), so their behaviour is
unchanged. One now-stale sentence in the `fails to link` KDoc — "savedCount is only
queried when inUseAsTarget, so … any other pack is safe" — is corrected, since after
#230 every removal queries.

## 7. Previews (rule 7)

19f gains a second meaningful state, so it gets a second `@PreviewLightDark`:
with-line (`savedCount > 0`) and without-line (`savedCount = 0`), same house style
as 19g's three, wrapped in the sheet preview frame. `SavedPhrasesLine`'s own
item-level previews already cover the line's wrap and are unchanged.

## 8. Verification

- Module unit tests: `:feature:language:testTranzlateDebugUnitTest` green, including
  the three new/rewritten tests above; each mutation M1–M3 shown RED then GREEN in
  the PR body (`Reproduced:`).
- Gate: `./gradlew preflight` green.
