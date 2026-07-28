---
status: accepted   # owner: "ඔයාම හදන්න" (2026-07-28), after seeing the repro on the merged build
issue: 48
title: 5a keeps its translation across process death — persist the result, never replay it
date: 2026-07-28
author: Claude (Opus 5) · evidence = device repro + fix verified on emulator-5554 (API 36)
---

# Plan — issue #48

> Translate something, leave the app, come back after Android has reclaimed the process: the typed text returns but **the translation is gone** — no result card, no actions, no explanation.

## 1. What was actually wrong

`TextViewModel._uiState` was a plain `MutableStateFlow`, so it restarted at `Idle`. `isEditing` in `ComposerScreen` **is** `rememberSaveable` and correctly restored `false` — the read face. A read face with `Idle` underneath falls into `ComposerReadBody`'s `TextUiState.Idle -> Unit` branch, whose comment claimed it was "unreachable: Idle always shows the edit face". Process death made it reachable, and it rendered nothing.

Measured on the merged build before the fix:

```
after Translate ....................... result present ✅
after rotation (activity recreated) .... result present ✅   <- ViewModels survive config changes
after process death ................... result GONE    ❌
```

Not a crash and not a hard dead end — the user could tap the source line or `✕` and start again — but a result vanished silently, which is what the EDGE_CASES no-dead-end rule exists to prevent.

## 2. Why not the function that was already there

`restoreResultIfNeeded()` replayed the last request whenever the state was `Idle`. Wiring it to composer entry would have been **worse than the bug**: on a fresh open `Idle` is the normal state, so it would have resurrected the *previous* translation — exactly the stale text requirement D exists to prevent. It also re-ran the translation, spending an API call on a paid engine to recover text the user already had.

So the fix had to distinguish "restored after a kill, with a result showing" from "opened fresh" — and the cheapest way to know that is to persist the result itself rather than the intent to rebuild it.

## 3. The fix

- `SavedStateHandle` keys hold **which face was showing** plus whatever that face cannot recompute: the translated text and engine for a result, the failure reason for an error. The request is already persisted for Retry, and `transliteration` is always null.
- An interrupted `Translating` is **resumed**, not reported as a failure that never happened — the same single call the user already asked for, not an extra one.
- **All state changes go through one private `state` setter.** Setting a `Result` records it; setting *anything else* erases the record. That is the whole safety property: `onComposerDismissed()` and `onClearAll()` both set `Idle`, so after leaving 5a there is nothing left to restore and no later composer can resurrect anything. Persistence cannot drift from the state because there is no second way to change it.
- `init` rebuilds the `Result` when a record survives; otherwise the ViewModel starts `Idle` as before.
- A record that cannot be rebuilt (an `Engine` or `FailureReason` constant renamed by an app update) is cleared rather than left to linger.
- `restoreResultIfNeeded()` is **deleted** — the result is restored eagerly now, so replay has no caller and no reason to exist.

Input is capped at `TEXT_CHAR_LIMIT` (500), so the saved-state Bundle stays far below the binder transaction limit.

## 4. Risks

| # | Risk | Mitigation |
|---|---|---|
| 1 | A stale result resurfaces on a fresh composer — the exact trap that made the obvious fix wrong | Every non-`Result` state erases the record, enforced by the single setter; covered by two unit tests and re-checked on device |
| 2 | Persisting on every state change bloats saved state | Only two short strings, only while a result exists; capped at 500 chars |
| 3 | A restored result disagrees with the restored request | Both come from the same `SavedStateHandle`; `restoreResult()` returns null unless text, engine **and** request are all present |

## 5. Acceptance

Unit (`:feature:text`, 17 tests, all green — 7 new):
- `the result survives process death` — a second ViewModel over the same handle restores the same text, engine and request, and the fake translator was called **once** (restored, not replayed).
- `a dismissed composer leaves nothing to restore` — after `onComposerDismissed()`, a fresh ViewModel is `Idle` with empty input.
- `clearing leaves nothing to restore` — same for `onClearAll()`.
- `an error survives process death` — the error card and its Retry come back with the right `FailureReason`.
- `an interrupted translation resumes and stays cancellable` · `dismissing mid-translation cannot push a result back` — the cancellation race the whole design rests on.
- `an unreadable record is cleared instead of lingering` — the enum-rename path.

Device (emulator-5554, `am kill` = what Android does under memory pressure):

| Step | Before | After |
|---|---|---|
| after Translate | result ✅ | result ✅ |
| **after process death (success face)** | **result ✗, actions ✗** | **result ✅, actions ✅** |
| **after process death (error face)** | **message ✗, Retry ✗** | **message ✅, Retry ✅** |
| back to Home | cleared | cleared |
| reopen 5a | empty | empty |
| kill + relaunch after dismissing | — | still empty (no ghost) ✅ |

Gates: `spotlessCheck` · `detekt` · unit tests incl. Konsist · fake + prod compile · `androidTest` sources compile.

## 6. Co-verify findings and what was done

A lens by an agent other than the author returned **OPEN:3, no blocker** — it independently confirmed the resurrection property holds, verifying `withContext`'s prompt-cancellation guarantee against the kotlinx.coroutines reference rather than assuming it.

| Finding | Severity | Action |
|---|---|---|
| Only `Result` was persisted — `Translating` and `Error` reproduced the very bug this issue is about | major | **Fixed.** Every non-Idle face is now recorded; reproduced the error case on device first, then re-verified both |
| No regression test for the cancellation race the design rests on | minor-moderate | **Added**, two of them |
| A record that cannot be rebuilt lingers until some later transition overwrites it | nit | **Fixed** + test |

While fixing the first item I placed the `init` block above `translateJob` and talked myself into an ordering bug — that a `= null` initializer would wipe an assignment made from `init`. The disconfirmation test did not fail, and the constructor bytecode shows **no `putfield` for that field at all**: Kotlin elides the initializer for a null default, so there was never a wipe. The ordering is kept anyway (an assignment from `init` should sit below the field it writes), but the comment now says what is actually true rather than what I assumed.
