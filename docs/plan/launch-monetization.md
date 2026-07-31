---
status: accepted   # LAUNCH TRACK C directive 2026-07-31 — make monetization real for a Play production update
issue: (launch batch — no single issue; tracks A/B/C run in parallel)
title: Firebase Remote Config · Qonversion billing · Play-required legal links
date: 2026-07-31
author: Claude (Opus 5) — Track C
---

# Plan — launch monetization (Track C)

Turns three of the four monetization seams from stand-ins into working integrations, and
says plainly which parts are still not real. The rule for this batch: **a shipped piece is
either honest or absent.** No fake success, no invented keys, no invented URLs.

| Seam | Before | After |
|---|---|---|
| `RemoteConfigSource` | `StaticRemoteConfigSource` (constants) | **`FirebaseRemoteConfigSource`** in prod; static stays in fake |
| `SubscriptionGateway` | `NoOpSubscriptionGateway` | **`QonversionSubscriptionGateway`** (SDK 9.7.0) |
| Paywall Terms/Privacy | "coming soon" snackbar | **remote URLs opened in the browser** |
| `AdsGateway` | `NoOpAdsGateway` | **unchanged — see §8** |
| `ConsentGateway` | `NoOpConsentGateway` | **unchanged — ads are off, so consent has nothing to gate** |

---

## 1. Is `google-services.json` a secret?

**No, and it is committed** (`app/google-services.json`).

It carries `project_id`, `mobilesdk_app_id`, `project_number` and an Android **API key**.
Google's own documentation treats these as *client* configuration: the file is packaged
into every APK the plugin builds, so anyone who can install the app already has it. The
API key is not a credential — access is controlled by Firebase Security Rules and by the
key's own package-name + SHA-1 restrictions in the Google Cloud console, not by secrecy.

The file is copied verbatim from the owner's live app because it is the **same Firebase
project** (`tranzlate-offline`) for the **same applicationId**
(`com.codeboxlk.tranzlate.offlinetranslator`) — which is exactly what makes the console
values already published there apply to this app on day one.

What is NOT in the repo and must never be: the Qonversion project key, the Cloud
Translation key. Those arrive through Remote Config (§3) or through a per-brand build
config the owner fills from a gitignored source.

---

## 2. ⚠️ TODO for TRACK A — the exact lines to add to `app/build.gradle.kts`

`app/build.gradle.kts` is Track A's file and this branch does not touch it. **One line is
missing and the Firebase integration is inert until it lands.**

Add to the existing `plugins { }` block, after the other aliases:

```kotlin
    alias(libs.plugins.google.services)
```

That is the whole change. Everything else is already in place:

* `libs.plugins.google-services` is declared in `gradle/libs.versions.toml` (4.5.0).
* The root `build.gradle.kts` already declares it `apply false`.
* `app/google-services.json` is committed.
* The `firebase-bom` + `firebase-config` dependencies live in `core/data/build.gradle.kts`,
  which `:app` already depends on for both engine flavors — **no dependency line is needed
  in `app/build.gradle.kts`.**
* No Qonversion line is needed either: the SDK sits in `lib/subscription/build.gradle.kts`,
  reached through `:core:access`'s existing `api(projects.lib.subscription)`, which `:app`
  already pulls in with `"prodImplementation"(projects.core.access)`.

**Why it cannot be applied anywhere else:** the plugin's own README and source restrict it
to `com.android.application` / `com.android.dynamic-feature`. Applied to a library module
it does not throw — it merely calls `logger.error(...)` and generates nothing, so the build
goes green while `google_app_id` never exists. That silent-failure mode is why this is a
TODO rather than a workaround.

**Until Track A adds it:** `FirebaseApp` is never initialised, `FirebaseRemoteConfig
.getInstance()` throws, `FirebaseRemoteConfigSource` catches it, logs a warning and serves
`RemoteConfigSnapshot.DEFAULTS`. The app builds, installs and runs — it is simply not yet
remotely configurable, and billing stays unconfigured because the key arrives by that route.

Optional, Track A's call: a `signingConfig` for release (still absent, as its own comment
in that file notes).

---

## 3. Remote Config — key mapping

`RemoteConfigSource` (in `:core:config`) grew five values: `qonversionKey`, `gctApiKey`,
`privacyPolicyUrl`, `termsUrl`, `contactEmail`, plus one `suspend fun awaitFirstFetch()`.
`RemoteConfigKeys` is the single home for the console key strings.

**Two families of keys, deliberately:**

| Our key (`snake_case`) | Serves | Console state |
|---|---|---|
| `limit_free_ai`, `limit_pro_fair_use`, `text_limit_free`, `text_limit_pro`, `ad_nth`, `ad_min_gap_s`, `ad_daily_cap`, `got_enabled`, `got_timeout_ms`, `gct_timeout_ms` | BUSINESS_MODEL §7 · D-2 rev.2 · D-4 | **do not exist yet** → our defaults serve |

| The live project's key (`PascalCase`) | Serves | Console state |
|---|---|---|
| `QonversionApiKey` | billing project key | **already published** |
| `CloudApiKey` | Cloud Translation v2 key | **already published** |
| `PrivacyPolicy`, `TermsAndCondition` | Play-required links | **already published** |
| `ContactEmail` | support address | **already published** |

**Deliberately NOT reused:** the live project's `FeatureLimitPerDay` (=20) and
`AdGapInMinute` (=15). Their semantics belong to the OLD product. Binding them to
`limitFreeAi` / `adMinGapSeconds` would let a console value written for a different app
silently overwrite our own confirmed decisions (5 / 90 s) the moment a fetch landed.
`RemoteConfigSnapshotTest` pins that as a regression test.

### Why a snapshot instead of reading the SDK per call

`RemoteConfigSource`'s getters are synchronous and are called from composition and from the
translation waterfall. Firebase's `getString`/`getLong` fall back to a **blocking disk read
with a 5 s timeout** when their in-memory container is cold, which the main thread may never
do. So `FirebaseRemoteConfigSource` resolves once on IO — settings → `setDefaultsAsync`
(awaited, so an absent console key resolves to OUR default rather than the SDK's static
`""`/`0`/`false`) → `fetchAndActivate` — and publishes one `@Volatile RemoteConfigSnapshot`.
Every getter afterwards is a field read.

`awaitFirstFetch()` settles on success, on failure, on a missing FirebaseApp, and on an 8 s
timeout. A paywall tap must never sit on a network.

### Credential precedence — one rule, one home

`CredentialResolution.kt`: **remote wins when non-blank; per-brand build config is the
floor.** Remote is the rotation channel (a revoked key must be replaceable without a Play
release); build config is the first-launch floor (a brand that hardcodes its key works cold
and offline). Both blank means *not configured*, and every caller degrades visibly.
`GctEngine` + `RealTranslator.gctConfigured()` now go through it, so a keyless brand can be
switched on from the console.

---

## 4. Qonversion billing

SDK `io.qonversion.android.sdk:sdk:9.7.0`, inside `:lib:subscription` only. The public API
(`SubscriptionGateway`, `Entitlement`) does not name it, so the module stays droppable into
another app. `implementation`, never `api` — Qonversion re-exports Play Billing 8.1.0 with
an `api` scope of its own, and that must not reach feature modules.

Verified in the built APKs: **`com/qonversion/android/sdk` and `com/android/billingclient`
appear in `app-tranzlate-prod-debug.apk` and are completely absent from
`app-tranzlate-fake-debug.apk`** — the classpath-absence guarantee holds.

Three design points:

1. **Lazy, once.** The project key comes from Remote Config, which has nothing on a cold
   first launch. Deciding "configured or not" when the DI graph is built would kill billing
   for that entire session, so the gateway takes a `suspend () -> SubscriptionConfig` and
   initialises the SDK at first use, after `awaitFirstFetch()`.
2. **A blank key is not an error.** Entitlement resolves to `Free` (a RESOLVED state, so
   `FeatureAccess.awaitResolved()` unblocks) and purchase/restore fail with
   `SubscriptionFailure.NotConfigured`. Behaviourally identical to the NoOp, which remains
   the library's SDK-free fallback for hosts that want no SDK at all.
3. **An Activity is required** — `Qonversion.purchase(activity, product, callback)` takes a
   plain `android.app.Activity`. Threading one down through a JVM-pure contract module would
   drag `android.app` into the contracts, so `:lib:subscription` gained
   `ActivityProvider` / `ForegroundActivityProvider` (weak reference to the resumed
   Activity, dropped on pause — the classic billing-wrapper leak, avoided).

**Failures are named, not lumped:** `NotConfigured`, `NoForegroundActivity`,
`ProductUnavailable(id)`, `Cancelled`, `Pending`, `StoreError`. `SubscriptionPurchaseFlow`
translates `Cancelled` into the provider-neutral `PurchaseCancelledException`, and the
paywall shows **nothing** for it — an error toast right after a user taps Back is what makes
a paywall feel broken.

**Entitlement mapping is unchanged and single-tier:** any active Qonversion entitlement →
`Tier.PRO`. Matching on specific entitlement ids would make a dashboard rename a silent
revenue outage.

---

## 5. Where each implementation lives, and the one compromise

| Class | Module | Why |
|---|---|---|
| `QonversionSubscriptionGateway` | `:lib:subscription` | Ring 1 rule — the SDK never leaves the reusable AAR |
| `FirebaseRemoteConfigSource` | `:core:data` | **compromise, see below** |
| `StaticRemoteConfigSource` | `:core:data` | now the fake variant's real binding, not a placeholder |
| prod binding | `:app/src/prod` `TranslateModule` | plan §6.1 — the one classpath position that sees every impl |
| fake binding | `:app/src/fake` `FakeConfigModule` (new) | plan §6.4 — a PRODUCTION `@InstallIn`, since `@TestInstallIn` never reaches an installed APK |

**The compromise:** architecturally `FirebaseRemoteConfigSource` wants its own prod-only
module (`:core:remoteconfig`). That would need a `"prodImplementation"` line in
`app/build.gradle.kts`, which this branch may not touch. Hosting it in `:core:data` — which
`:app` already depends on for both flavors — keeps the whole thing wired and working today
at the cost of `firebase-config` (~1 MB, verified present) being compiled into the fake APK
as well. It is never *bound* there: the fake flavor binds `StaticRemoteConfigSource`, so no
Maestro run can be changed by a console edit or reach a real credential. Moving it to its
own module later is a pure refactor.

`RemoteConfigSource` also left `DataModule`, because it now has two genuinely different
implementations per flavor. `app/src/androidTestProd`'s `@TestInstallIn` wrapper had to
re-supply `FakeRemoteConfig` for the same reason.

---

## 6. What the owner must do (nothing below can be done from code)

1. **Qonversion dashboard** — create products whose identifiers are exactly `weekly`,
   `monthly`, `yearly` (they are `PaywallPlan.offeringId`, fed straight to
   `Qonversion.products()`), link them to the Play Console subscriptions, and confirm the
   entitlement they grant. A plan with no matching product fails visibly with
   `ProductUnavailable` — nothing is faked.
2. **Firebase console** (`tranzlate-offline`) — confirm `QonversionApiKey`,
   `PrivacyPolicy`, `TermsAndCondition` hold current values, since this app now reads them.
   Optionally add the `snake_case` policy keys to tune limits without a release.
3. **Play Console** — the subscriptions themselves, and prices matching what the paywall
   displays (see §7, first item).
4. **AdMob** — see §8.

---

## 7. NOT WIRED — the honest list

1. **Paywall prices are still hardcoded strings** (`US$1.99` / `US$4.99` / `US$29.99` in
   `values/strings.xml`), not the store's localized prices. This is now a **Play policy
   risk**, because the CTA can complete a real purchase: displayed price and charged price
   must match, and they will not for any non-USD buyer. `QProduct.prettyPrice` is the fix
   and the gateway already fetches products — it needs a `prices: StateFlow<Map<String,
   String>>` on the gateway, plumbed through `PurchaseFlow` to `PaywallViewModel`. **Do this
   before the store listing goes live.**
2. **Terms/Privacy have no fallback URL.** `RemoteConfigDefaults` leaves them empty because
   inventing a URL is a policy liability. Before the first fetch lands, tapping either shows
   "Couldn't open that page…" rather than opening something wrong. Once the console values
   are confirmed (§6.2) the first launch resolves them within the fetch timeout.
3. **`contactEmail` is read but not displayed.** The value flows through the seam; no
   Settings row consumes it yet.
4. **Deferred purchases are not observed.** `Qonversion.setDeferredPurchasesListener` (cash
   payments that complete later) is not wired; such a purchase resolves on the next launch's
   entitlement check instead of live.
5. **No entitlement push updates.** Entitlement is read at init, after a purchase and after
   a restore. A subscription that expires mid-session is not noticed until the next launch.
6. **Ads and consent are untouched** — see §8.
7. **Instrumentation coverage.** The new paths have unit tests (`RemoteConfigSnapshotTest`,
   `CredentialResolutionTest`, the four new `PaywallViewModelTest` cases). Nothing exercises
   a real Qonversion sandbox purchase — that needs a device, a Play licence-tester account
   and published products.

---

## 8. AdMob — deliberately NOT done

Priority 4 was gated on 1–3, and it is also **blocked on a value that does not exist
anywhere we can reach**. The owner's live app ships Google's **test** AdMob app id
(`ca-app-pub-3940256099942544~3347511713`) in its production manifest — that IS the
revenue-zero bug this rebuild exists to fix, so the old repo is not a source for a real id.
Its ad unit ids are not in the repo either.

Current state, which is safe: the `tranzlate` flavor carries a sample-format, non-functional
placeholder app id, `AD_UNIT_BANNER` / `AD_UNIT_INTERSTITIAL` are empty, and `AdsGateway` is
the NoOp — **no ad SDK call is ever made**. An unconfigured ads layer earns nothing; a
test-id ads layer earns nothing *and* risks a policy strike.

To finish it the owner must supply, from the AdMob console: the real app id, the real banner
unit id, the real interstitial unit id. Then `admobAppId` + the two `buildConfigField`s in
the flavor block get real values (Track A's file), `:lib:ads` gets the SDK behind its
existing `AdsGateway` surface, and `:lib:consent` gets UMP — **consent must be obtained
before any personalized ad request**, and `ConsentGateway` already encodes that by starting
at `UNKNOWN` and never granting by itself.

---

## 9. Gate results (worktree, 2026-07-31)

| Gate | Result |
|---|---|
| `./gradlew test` | **All module tests pass.** 4 failures in `:app`'s `KonsistArchitectureTest`, **pre-existing and environmental** — see below |
| `./gradlew :app:assembleTranzlateProdDebug :app:assembleTranzlateFakeDebug` | **BUILD SUCCESSFUL** |
| `./gradlew :app:assembleTranzlateProdRelease` (extra — R8 with two new SDKs) | **BUILD SUCCESSFUL** |
| `./gradlew spotlessCheck detekt --rerun-tasks` | **BUILD SUCCESSFUL** |

**The Konsist failures:** `KonsistArchitectureTest` slices out `/.claude/worktrees/` so the
main checkout ignores worktree copies. This branch IS a worktree under that path, so the
slice removes every file and all four assertions fail with "expected not to be empty" /
size 0. Disconfirmed two ways: (a) `git stash` → the same four fail at the unmodified base
commit; (b) inverting the slice to keep only this worktree's files → **all four pass against
the new code**. They will pass normally once merged into the main checkout.

Also verified empirically, not assumed — dex-string counts in the built APKs:

| Probe | prod-debug | fake-debug |
|---|---|---|
| `com/qonversion/android/sdk` | 1651 | **0** |
| `com/android/billingclient` | 446 | **0** |
| `com/google/firebase/remoteconfig` | 253 | 253 (compiled in, never bound — §5) |
