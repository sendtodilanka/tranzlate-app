# Issue draft — file with `gh issue create`

Register ref: R1-O5 (PR #109 findings register). Line numbers verified against
branch `feat/launch-monetization` during the round-4 fix pass.

## Title

Paywall: https-only allowlist for remote-config legal URLs before they reach openUri

## Body

### The gap

The paywall's Terms and Privacy links are **server-controlled data** handed
straight to the OS:

- The URLs come from Firebase Remote Config:
  `feature/paywall/src/main/kotlin/com/codeboxlk/tranzlate/feature/paywall/PaywallViewModel.kt:90`
  and `:103` build `LegalLinks(remoteConfig.termsUrl(), remoteConfig.privacyPolicyUrl())`;
  the raw values are read with only a `.trim()`
  (`core/config/src/main/kotlin/com/codeboxlk/tranzlate/core/config/RemoteConfigSnapshot.kt:128`).
- `openLink` in
  `feature/paywall/src/main/kotlin/com/codeboxlk/tranzlate/feature/paywall/PaywallScreen.kt:119–124`
  applies exactly one guard — `url.isNotBlank()` — then calls
  `uriHandler.openUri(url)` (`ACTION_VIEW`).

So whatever string the Remote Config console (or anyone who gains write access
to it, or a fat-fingered edit) serves is dispatched as an intent:
`intent://…#Intent;…end` URLs can address arbitrary exported components via
browsers' intent-scheme handling, and `market://`, `mailto:`, or a custom
scheme opens some unrelated app instead of the legal document Play requires to
be reachable from the purchase screen. The app's own trust model already treats
these keys as untrusted enough to `.trim()` — it should finish the thought.

### Proposed fix

- A small pure function, e.g. `isAllowedLegalUrl(url: String): Boolean`, that
  accepts only absolute `https://` URLs (decide explicitly whether plain
  `http://` is allowed; recommendation: no), with unit tests including
  `intent:`, `javascript:`, `file:`, scheme-relative `//host`, and
  uppercase-scheme (`HTTPS://`) cases.
- `openLink` routes anything rejected into the existing
  `viewModel.onLegalLinkUnavailable()` path (`PaywallScreen.kt:124`) — the
  `LINK_UNAVAILABLE` snackbar already exists, so the failure stays honest and
  dead-end-free rather than silently opening the wrong thing.
- Guard belongs at the point of use (the paywall), not inside `:core:config`,
  which is a dumb snapshot by design — but a note in `RemoteConfigSnapshot`'s
  KDoc that URL keys are consumed through an allowlist would keep the contract
  visible.

### Acceptance

- Test: `https://…` opens; every non-https scheme (and blank) lands in the
  `LINK_UNAVAILABLE` event, no `openUri` call.
- Mutation check: deleting the scheme check must turn a test red (the blank-only
  guard staying green is exactly how this gap survived review).
