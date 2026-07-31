# Issue draft — file with `gh issue create`

Register refs: R1-O2 + R1-O7 (PR #109 findings register). Line numbers verified
against branch `feat/launch-monetization` during the round-4 fix pass; symbols
are the stable anchor if lines drift.

## Title

Access: entitlement never re-resolves after a failed first attempt, and the purchase Activity is captured before a 15s store call

## Body

Two related gaps in `:lib:subscription`'s `QonversionSubscriptionGateway`, both
about state resolved once and then trusted forever.

### 1. Entitlement resolves once, fail-closed, and nothing ever retries (R1-O2)

`refresh()` runs exactly once, from the gateway's `init`
(`lib/subscription/src/main/kotlin/com/codeboxlk/subscription/QonversionSubscriptionGateway.kt:116`,
`scope.launch { refresh() }`). It is bounded by
`FIRST_RESOLUTION_TIMEOUT_MS = 8_000L` (line 31), and every failure path —
timeout, SDK error, no network — fail-closes to `Entitlement.Free`
(`refresh()`, lines 225–236). Fail-closed is the right default; the bug is that
it is also **final**: no code path ever calls `refresh()` again. Only
user-initiated `restore()` re-publishes entitlement.

Consequence: a **paying subscriber** who cold-launches offline (airplane mode,
subway) and regains network ten seconds later stays `Free` for the entire
process lifetime — their NLP3.5 usage meters against the FREE 5/day pool
(`TranslateTextUseCase` spends by tier), the PRO chip renders, and the app
offers them a paywall for something they already bought. Nothing looks broken;
it looks like the user's subscription vanished.

Proposed fix:
- Track internally whether the current resolved value came from a real store
  answer or from the fail-closed decay.
- Re-run the resolution when it was decay-resolved and a retry trigger fires:
  network regain (`ConnectivityMonitor.online`,
  `core/common/src/main/kotlin/com/codeboxlk/tranzlate/core/common/ConnectivityMonitor.kt:15–27`;
  prod impl `app/src/prod/kotlin/com/codeboxlk/tranzlate/di/AndroidConnectivityMonitor.kt`)
  and/or process `ON_START`.
- **Library-purity constraint:** `:lib:subscription` is Ring-1 with zero project
  dependencies (enforced by `KonsistArchitectureTest > lib AARs are
  project-independent`), so the gateway cannot import `ConnectivityMonitor`.
  The trigger must arrive through the library's own surface — e.g. a public
  `refreshEntitlement()` the host wires up in
  `app/src/prod/kotlin/com/codeboxlk/tranzlate/di/TranslateModule.kt` (which
  already owns the gateway's DI seam, lines 159–179).
- Reuse the in-flight dedupe pattern the round-4 pass added for
  `refreshPrices()` (register R4-O2/M14) so concurrent triggers cannot race.

### 2. The store-launch Activity is captured before a 15-second lookup (R1-O7)

`purchase()` resolves `activityProvider.current()` at lines 180–182 — and then
performs a product lookup bounded by `STORE_CALL_TIMEOUT_MS = 15_000L`
(line 39) before finally launching the sheet with that same reference
(`session.purchase(activity, offeringId)`, line 192). A rotation or
background-and-return during those 15 seconds destroys the captured Activity;
the purchase sheet is then launched against a dead Activity.

Proposed fix: re-resolve `activityProvider.current()` immediately before
`session.purchase(...)` and fail with `SubscriptionFailure.NoForegroundActivity`
if it is gone — the paywall already renders that failure honestly.

### Acceptance

- Test: decay-resolved `Free` + network-regain trigger → gateway re-queries and
  publishes the store's answer; answer-resolved values are NOT re-queried in a
  loop.
- Test: activity destroyed between purchase entry and sheet launch →
  `NoForegroundActivity`, no launch against the stale reference.
- Mutation check (register discipline): removing the retry wiring or reverting
  to the captured-early activity must turn a test red.

## Additional shapes proven by review round 5 (fold into this issue)

1. **The deliver-the-pending-purchase listener.** The gateway's entitlement
   state has exactly six writers, all one-shot; `javap` on `sdk-9.7.0.aar`
   confirms `setEntitlementsUpdateListener(QEntitlementsUpdateListener)` and
   `setDeferredPurchasesListener` exist unused. Wiring them into the gateway's
   state flow is what lets a cleared deferred payment unlock Pro DURING the
   session — the pending snackbar copy was softened (round 5 B1) precisely
   because this listener is not wired yet. Wire it here, then the copy can
   promise more again.
2. **Cold-launch entitlement miss.** If `awaitFirstFetch` (8s) times out just
   before the RC bootstrap publishes, the init `refresh()` sees a blank key and
   settles Free. A later paywall open builds a session (the factory never
   caches null — verified) but nothing re-runs `checkEntitlements()`: a paying
   subscriber stays Free until they tap Restore. Same family, same fix surface.
3. **Post-sheet failure copy (R5-O1).** "Nothing was charged" is provably true
   for every PRE-flight failure but not for post-sheet ones (network drop
   between Play charging and Qonversion confirming; a product mapped to no
   dashboard entitlement). Split the copy: pre-flight keeps the sentence,
   post-sheet says "We couldn't confirm the purchase — check Google Play."
