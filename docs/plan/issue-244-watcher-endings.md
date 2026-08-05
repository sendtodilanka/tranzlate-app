# Plan — #244: the watcher's untested endings

status: accepted
(accepted basis: rev5 completion plan wave 1d, `issue-130-rev5-completion.md` (accepted),
which classifies #244 as a wave-1d test gap on the shipped language screen, scope
`feature/language/**/kotlin`. Refs: #244.)

## The gap
`LanguagePickerViewModel.awaitFailure` (the download-outcome watcher) collects the shared
model-state map for one tag and decides whether the download the user just asked for FAILED —
raising sheet 19d ("did not download") only when it did. Its `transformWhile` has three
outcomes, and its own KDoc names three NON-failure endings: `Downloaded`, `Deleting` after the
user hit Stop, and `NotDownloaded` again. All three fall through the single `else -> false`.

`pr-review-toolkit:pr-test-analyzer` found that **only `Downloaded` has a test**
(`PackFailureSheetRaisingTest.a download that finishes opens no sheet`). The other two endings —
the two a user reaches by tapping ⬇ then ✕ (cancel) — were unpinned. A future edit that made
the `else` raise would tell a user who deliberately cancelled that their own action failed, and
no test would go red.

## The question settled first (measure, don't assume — rule 12)
**When the user taps ⬇ then ✕, does the row land as `Deleting` (safe) or `Failed` (bad)?**

It lands as `Deleting`, then `NotDownloaded` — **never `Failed`.** Traced in
`RealOfflineModelManager`:
- `delete()` (the Stop) does `activeDownloads.remove(tag)?.cancel()` then `setTransient(tag,
  Deleting)` (`:422`, `:465`). The state written by the Stop itself is `Deleting`.
- The cancelled download job catches `CancellationException` **first** and rethrows it without
  writing anything (`:401-403`); it never reaches the `Exception` branch that writes
  `Failed(...)`, and even if it did, `owns()` is already false because the job was removed from
  `activeDownloads` at `:422`.
- After the platform delete resolves, `refreshDownloaded()` + `clearTransient()` land the row on
  `NotDownloaded` (nothing on disk) or `Downloaded` (a partial that actually completed).

Proven already at the manager level by
`RealOfflineModelManagerTest.stop mid-download cancels the manager's job and the row never
ghosts back` (`:147-162`), which asserts the row is `NotDownloaded` with the explicit comment
"never Downloaded, never Failed", and `store.downloadCancelled == true`.

**Conclusion: there is NO code bug.** The watcher's `else -> false` already treats both
`Deleting` and `NotDownloaded` as non-failures. The fix is purely to ADD the two missing
watcher-level tests so a regression that made those endings raise a sheet goes red.

## The fix — two tests, mutate-first (rule 11)
Add to `PackFailureSheetRaisingTest` (the suite that owns the watcher's raise decision), driving
the fake `PickerModelManager` through the exact states the real manager produces above:
1. `a stopped download lands on Deleting and opens no sheet` — `NotDownloaded` → `download` →
   `Downloading` → (user Stop) `Deleting`. Assert `packFailure.value == null`, and that
   `stopAndRemove` recorded the delete.
2. `a stopped download that resolves to NotDownloaded opens no sheet` — `NotDownloaded` →
   `download` → `Downloading` → `NotDownloaded`. Assert `packFailure.value == null`.

**Mutation decided before writing (both tests):** change the watcher's `else -> { false }` to
`else -> { emit(OfflineModelFailure.UNKNOWN); false }` — i.e. treat every non-failure ending as a
failure, which is the issue's own harm. Under it each new test must go RED (a sheet appears where
none should); with the real `else -> false` each must go GREEN. This same mutation also reddens
the existing `Downloaded` test, which is the point: the `else` must never raise. Recorded
RED→GREEN in the PR body.

The fake already supports the needed states via `put(tag, state)` — no fake change needed. (The
brief said the fake lives in `core/testing`; the fake the watcher tests actually drive is
`PickerModelManager` in `feature/language/.../PickerViewModelFakes.kt`. Noted, not touched
beyond adding tests that call its existing `put`.)

## Enumerate (rule 11)
- Watcher endings (`awaitFailure` `transformWhile`): 3 — `Downloading` (continue), `Failed`
  (emit → raise), `else` (Downloaded / Deleting / NotDownloaded / OnlineOnly → conclude, no
  raise). The `else` is the one under test.
- Raise paths: `reportOutcome` → `Refused → raise`, `Started → awaitFailure()?.let { raise }`
  (2 call sites of `raise`); `raise()` itself sets `raisedFailure` (1). Entry points that reach
  the watcher: `download`, `downloadAnyway` (2).
- **Call sites: watcher endings 3 found; production changed 0** (no bug → no production edit).

## Verify
`./gradlew :feature:language:test` green; mutate-first RED proof recorded; `./gradlew preflight`.
