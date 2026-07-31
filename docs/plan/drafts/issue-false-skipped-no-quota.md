# Issue draft — file with `gh issue create`

Register refs: R1-O4 / R2-O3 / R3-O4 (PR #109 findings register). Line numbers
verified against branch `feat/launch-monetization` during the round-4 fix pass.

## Title

Translate: unresolved entitlement is reported to the user as "out of free quota" (false SKIPPED_NO_QUOTA)

## Body

### The lie

`RealTranslator.gctTail()` waits at most `ENTITLEMENT_WAIT_MS = 3_000L`
(`core/translate/src/main/kotlin/com/codeboxlk/tranzlate/core/translate/RealTranslator.kt:36`)
for the entitlement to resolve:

- line 225: `withTimeoutOrNull(ENTITLEMENT_WAIT_MS) { featureAccess.awaitResolved() }`
- line 227: on timeout it records
  `EngineAttempt(tier3Paid.engine, AttemptCause.SKIPPED_NO_QUOTA)` —
  **the same cause as a genuinely exhausted quota** (line 232).

But first resolution can legitimately take longer than 3s: the gateway's config
provider first awaits the Remote Config fetch
(`app/src/prod/kotlin/com/codeboxlk/tranzlate/di/TranslateModule.kt:173`,
`remoteConfig.awaitFirstFetch()`), then Qonversion's first entitlement check is
bounded at `FIRST_RESOLUTION_TIMEOUT_MS = 8_000L`
(`lib/subscription/src/main/kotlin/com/codeboxlk/subscription/QonversionSubscriptionGateway.kt:31`).
So in roughly the first 3–10+ seconds of a cold session on a slow network, a
**paid** user's GCT tier is skipped and the trace says "no quota".

The cause is user-visible, not internal: `AttemptCause`'s own KDoc
(`core/model/src/main/kotlin/com/codeboxlk/tranzlate/core/model/TranslationOutcome.kt:5–8`)
says the owner's error dialog reads exactly this ("GCT: skipped — out of free
quota"), and `TextViewModel` surfaces `outcome.primaryCause` into
`TextUiState.Error`
(`feature/text/src/main/kotlin/com/codeboxlk/tranzlate/feature/text/TextViewModel.kt:389`).
A paying user with quota remaining is told they are out of quota. That violates
the EDGE_CASES no-dead-end rule twice: the message is false, and the guidance it
implies (upgrade / wait for the daily reset) cannot fix the actual problem
(entitlement not yet resolved).

### The related fragile bound

`TranslateTextUseCase` line 97
(`core/domain/src/main/kotlin/com/codeboxlk/tranzlate/domain/translate/TranslateTextUseCase.kt:97`)
calls `featureAccess.awaitResolved()` with **no timeout at all**. It terminates
today only because the gateway guarantees resolution within its own 8s ceiling —
a cross-module invariant that is load-bearing and undocumented at the call
site. Any future gateway that can stay `Loading` forever hangs the metered path.

### Proposed fix

- Add a distinct cause, e.g. `SKIPPED_ENTITLEMENT_UNRESOLVED`, recorded when the
  3s wait expires. The `isSkip` `when` (`TranslationOutcome.kt:20–32`) is
  deliberately exhaustive, so the compiler forces every consumer to decide.
- Honest strings for it in en/fil/pt-rBR ("Couldn't confirm your subscription
  yet — check your connection and try again"), wired through the error dialog.
- Keep the 3s bound (it protects the translate path); do NOT silently lengthen
  it.
- Document (or bound) the `TranslateTextUseCase:97` wait so the gateway's 8s
  ceiling stops being an unstated dependency.

### Acceptance

- Test: entitlement still `Loading` after the wait → attempt cause is the new
  value, never `SKIPPED_NO_QUOTA`; UI shows the new copy.
- Test: genuinely exhausted quota still yields `SKIPPED_NO_QUOTA`.
- Mutation check: mapping the timeout branch back to `SKIPPED_NO_QUOTA` must
  turn a test red.
