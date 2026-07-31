# Issue draft — file with `gh issue create`

Register ref: R3-O6 residual (PR #109 findings register). Line numbers verified
against branch `feat/launch-monetization` during the round-4 fix pass.

## Title

White-label: a brand with no Qonversion key still surfaces the paywall — including a retry that can never succeed

## Body

### The situation

A brand can legitimately ship without subscriptions: `QONVERSION_KEY` is an
empty `buildConfigField` in the flavor block (`app/build.gradle.kts:71`), and
the effective key falls back remote-config → BuildConfig
(`core/config/src/main/kotlin/com/codeboxlk/tranzlate/core/config/CredentialResolution.kt:24–25`).
When both are blank, the gateway deliberately treats it as "no billing", not an
error: `createSdk()` returns null on a blank key
(`lib/subscription/src/main/kotlin/com/codeboxlk/subscription/QonversionSubscriptionGateway.kt:252`),
entitlement resolves `Free`, and `refreshPrices()` settles
`StorePrices.Unavailable` (`QonversionSubscriptionGateway.kt:148`).

That is correct at the gateway layer. The residual (established in review round
3, kept open through round 4) is that **the UI still advertises the paywall**:

- The PRO chip renders whenever the user is not paid —
  `showProChip = !isPro`
  (`feature/text/src/main/kotlin/com/codeboxlk/tranzlate/feature/text/HomeScreen.kt:131`,
  rendered at `:205` and `:292`). On a keyless brand the user is *always*
  `!isPro`, so the chip *always* shows.
- The limit sheet's upgrade CTA
  (`feature/text/src/main/kotlin/com/codeboxlk/tranzlate/feature/text/ComposerScreen.kt:233`)
  and the nav entry (`entry<PaywallNavKey>`,
  `app/src/main/kotlin/com/codeboxlk/tranzlate/navigation/TranzlateApp.kt:196`)
  both lead there too.
- Once on the paywall, `StorePrices.Unavailable` renders as the
  store-unreachable state with a Retry — and for a keyless brand that retry is a
  **structural** no-op: there is no key, so there is nothing to reach, ever.
  (Round 4's `priceHintFor` split fixed the *transient* mislabelings; this
  permanent one is a different animal — the message "couldn't reach Google
  Play" is simply false here.)

That is a textbook EDGE_CASES no-dead-end violation, and a white-label
correctness gap: CLAUDE.md's white-label requirement says adding a brand is
config only, and "this brand sells nothing" is a config statement the UI
currently cannot express.

### Proposed fix

- Derive a single `billingConfigured` signal from the resolved credentials
  (BuildConfig key non-blank OR remote-config key non-blank — note the
  remote-config half arrives async, so the signal is a flow, defaulting to the
  BuildConfig answer).
- When false: hide the PRO chip, the paywall nav entry, and swap the limit
  sheet's upgrade CTA for its keyless copy (the free-quota message without an
  upsell). No paywall surface means the impossible-retry screen is unreachable.
- Natural homes: the flavor's `FEATURES` toggle list (`app/build.gradle.kts:79`)
  for the static half, `FeatureAccess`/`AppConfig` for the runtime signal —
  screens must keep asking a brain rather than reading BuildConfig directly
  (one-home rule).
- Follow the existing preview discipline: chip-absent and keyless-limit-sheet
  states need `@PreviewLightDark` previews (CLAUDE.md rule 7).

### Acceptance

- Test: keyless config → no PRO chip, no paywall route, limit sheet shows
  keyless copy; keyed config → all present exactly as today.
- Test: remote-config key arriving mid-session flips the signal without a
  restart.
- Mutation check: hardcoding `billingConfigured = true` must turn a test red.

## Library-robustness note from review round 5 (same issue family)

A host whose `configProvider` THROWS crashes the process through the gateway's
`init` launch — demonstrated with a scratch probe. Tranzlate's own provider
cannot throw (verified: `withTimeoutOrNull` + a `CompletableDeferred` that is
never completed exceptionally), so this is a library-hardening item for the
white-label story, not a Tranzlate defect: either document "the provider must
not throw" as the Ring-1 contract, or `runCatching` in `createSession` and
settle Free/Unavailable.
