# Plan — #240: a render test for PackFailureSheetHost

status: accepted
(accepted basis: rev5 completion plan wave 1b, `issue-130-rev5-completion.md` (accepted);
owner directed continuing wave 1b, 2026-08-05. Refs: #240.)

## The gap
`PackFailureSheetHost` (`feature/language/.../LanguagePickerScreen.kt:~415-422`) is
under-tested. **CORRECTION (build finding, rule 12):** the original claim "nothing passes a
non-null `packFailure` into `LanguagePickerContent`" was **stale** — `PackFailureSheetsTest:257-316`
already drives it non-null for two cases (19b dismiss, 19d dismiss). So dismiss-order was
covered; what escapes (worst first) and is genuinely new here:
1. **Retry wiring** (`:419-422`, `onRetry = { onDismiss(); onRetry(request.id) }`) — drop the
   `onDismiss()` and a stale "did not download" sits over a re-running download; wrong id →
   wrong language retried. Nothing goes red.
2. **Two silent no-draw branches** (`:415`, `if (name != null && sheet is …Interrupted)`) — an
   `Interrupted` carrying `STORAGE` draws nothing at all.
3. **Routing** `NoSpace → 19b`, `Interrupted → 19d` is unasserted.

## The fix — mirror the sibling that already exists
`OfflineRemoveFlowRenderTest` (PR-19) is six render tests over this exact layer; the tooling
(`tranzlate.compose-test`, Robolectric, `createComposeRule`) and pattern exist. Write
`PackFailureSheetHostRenderTest` (or add to the language render tests) that renders
`LanguagePickerContent` with a non-null `packFailure` and asserts:
- **Retry** fires BOTH `onDismiss()` AND `onRetry(request.id)` with the CORRECT id (record via
  test lambdas; assert order/ids). Mutate-first: dropping `onDismiss()` (or passing a wrong id)
  must turn a test RED.
- **Routing**: `NoSpace` renders the 19b sheet; `Interrupted` renders 19d.
- **The no-draw branch**: an `Interrupted` whose cause makes `name == null`/`STORAGE` — assert
  the CURRENT behaviour explicitly (so a future change to it is caught), and if it draws
  nothing, that is a finding to surface, not silently encode.

## Verify
`./gradlew :feature:language:test` green; mutate-first RED proof (drop `onDismiss()` or wrong id)
recorded in the PR body; `./gradlew preflight`.
