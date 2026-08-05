# Plan — #242: three tests that cannot fail, made able to fail

status: accepted
(accepted basis: rev5 completion plan wave 1b (accepted); owner directed continuing wave 1b,
2026-08-05. Refs: #242. Test-only — the production guards already exist and are correct; the
tests just don't go red when a guard is removed.)

Each fix carries its own mutate-first: remove the guard the test is supposed to protect, prove
the (new/fixed) test goes RED, revert → GREEN.

## 1. Tautological size assertion → a real exhaustiveness guard
`DownloadFailureTest.every cause the platform can report has a line` asserts
`lines.hasSize(OfflineModelFailure.entries.size)` — `entries.map{}` is size-preserving, so it
CANNOT fail. The real hole is one file over: `RealOfflineModelManager.toFailure()`
(`core/translate/.../RealOfflineModelManager.kt:606-610`) has an `else`, so a 5th `OfflineModelFailure`
constant lands silently as `UNKNOWN`. `downloadFailureCopy` is `else`-less (compile-guarded);
`toFailure()` is not. **Fix (delivered scope, corrected per co-verify):** two tests pinning
**both edges of the current `else`** via the public `download()` path — an `IOException`
surfaces as `NETWORK` (not UNKNOWN-folded), and an unrecognised failure surfaces as `UNKNOWN`.
**Honest limit:** because `Exception` is an **open** hierarchy (no compiler exhaustiveness,
unlike the else-less `downloadFailureCopy`), this pins the *current* mapping — it is **not** a
future-5th-cause guard. The originally-suggested `DownloadFailureSourceTest` ban does **not**
fit: that test polices copy-duplication branches, not cause-classification (its own KDoc scopes
it out).
**Mutate-first:** add a fake 5th-cause path (or point the check at the else) → RED.

## 2. A guard the fake conflates away → a non-conflating fixture
`OfflineLanguagesViewModel.kt:194` `.distinctUntilChanged()` on target. The test fake
`RecordingTranslatePrefsRepository.targetLang` is a `MutableStateFlow`, which conflates equal
consecutive values by contract — so deleting `.distinctUntilChanged()` changes nothing in any
test. Production has NO conflation in the chain (DataStore re-emits on any key write), so the
guard is load-bearing: without it, ticking 19a re-runs `savedCountUsing` with a 19g sheet open.
**Fix:** make the fixture non-conflating (`MutableSharedFlow` for `targetLang`) + a call counter
on `FakeTranslationRepository.savedCountUsing` (the `beforeSavedCount` hook exists). Assert a
repeat same-value target does NOT re-run the count. **Mutate-first:** delete
`.distinctUntilChanged()` → the new test RED.

## 3. A rethrow arm nothing enters → a cancellation test
`OfflineLanguagesViewModel.kt:215-216` — the `CancellationException` rethrow in `savedCountOf`
is untested (the generic-exception branch IS tested). Deleting the rethrow makes a cancelled
`flatMapLatest` (every `dismissRemove()`) swallow the cancellation and return 0. **Fix:** a test
that cancels the collection while `savedCountUsing` is in flight and asserts the
`CancellationException` propagates (structured concurrency preserved). **Mutate-first:** delete
the rethrow → RED.

## Verify
`./gradlew :feature:language:test :core:translate:test` green; each of the 3 mutate-first RED→GREEN
recorded in the PR body; `preflight` at land.

## Landed
The three fixes landed in **PR #277** (`Fixes: #242`) — cross-model co-verify APPROVE, all three
mutations reproduced independently; the co-verify even built a probe to confirm fix-3's
manual-throw is the only observable technique for that guard.
