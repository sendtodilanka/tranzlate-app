# Plan — LAUNCH TRACK A: release signing + the real AAB path

status: accepted
(accepted basis: owner standing rules — launch track A is an owner-directed
autonomous task. Scope was fixed by the launch brief: signing plumbing only,
no feature/** or core/** edits.)

## Why this exists

`applicationId` = `com.codeboxlk.tranzlate.offlinetranslator` is the **same id as
the owner's LIVE Play listing.** The old repo (`~/StudioProjects/Tranzlate`) last
shipped that listing at `versionCode = 4` / `versionName = "1.0.4"`.

So this is **not a new app — it is a PRODUCTION UPDATE.** Two hard Play
constraints follow, and both were previously unmet:

1. `versionCode` must be **strictly greater than 4**. We were at `1`.
2. The artifact must be signed with the **same certificate** as the live app, or
   Play rejects the upload outright. We had `// NO signingConfig on purpose`
   (issue #5 / PR #96) — every release artifact was unsigned.

## The ruling

### 1. Where the secrets live — repo-root `keystore.properties`, key OUTSIDE the repo

Two layers, so no single mistake can leak the key:

| Thing | Location | Committed? |
| --- | --- | --- |
| Pointer file (`storePassword`/`keyPassword`/`keyAlias`/`storeFile`) | `<rootDir>/keystore.properties` | **No** — gitignored |
| The private key (`.jks`) | `~/.android-keystores/tranzlate/` — **outside the repo tree** | **No** — not reachable from the repo at all |

**Why `keystore.properties` and not `~/.gradle/gradle.properties`:**

- It is the pattern Android's own "Sign your app" documentation uses, so it is
  what any Android dev (or future contributor) expects to find.
- It is **per-project**. A global `~/.gradle/gradle.properties` would leak
  Tranzlate's key names into every other Gradle project on the machine, and
  collide the moment a second app wants a `storePassword` key.
- It scales to **white-label**: brand 2 gets brand-scoped keys in the same file
  rather than a second global-file convention.
- Its one real risk — accidental `git add` — is fully mitigated below, and the
  `.jks` itself is not even in the tree.

`.gitignore` was hardened **before** any key material was copied in
(`*.jks`, `*.keystore`, `*.p12`, `*.pepk`, `keystore*.properties`). Note the
pre-existing `*-keystore.properties` rule does **not** match a bare
`keystore.properties` — that was the actual gap, and it is now covered
explicitly. Verified with `git check-ignore -v` and
`git status --ignored` (`!! keystore.properties`).

Override for a non-default path: `-PkeystorePropertiesFile=<path>`.

### 2. Mechanics live in build-logic, not in the app script

New convention plugin `tranzlate.android.application.signing`
(`build-logic/convention/src/main/kotlin/AndroidApplicationSigningConventionPlugin.kt`),
matching the existing split — **mechanics in build-logic, brand data in
`app/build.gradle.kts`** (plan §4 R1).

The config is attached to the **`release` build type**, so it covers every brand
today. When brand 2 ships under its own Play listing with its own key, it
overrides `signingConfig` inside its own flavor block (flavor beats build type).

### 3. CI safety — the load-bearing behaviour

CI runs `./gradlew build`, which assembles `tranzlateProdRelease` (that is what
keeps R8 gated on every PR, per issue #5). CI has no keystore. So:

- **Pointer file absent** → log a clear lifecycle warning, create no
  signingConfig, build the release variant **UNSIGNED**. Build stays green.
- **Pointer file present but broken** (missing key, or `storeFile` points at a
  file that isn't there) → **fail the build loudly.** Someone who wrote that file
  intended to sign; silently handing them an unsigned artifact they might upload
  is the worse failure.

### 4. Version

`versionCode = 5`, `versionName = "1.1.0"` in `defaultConfig`.

`5` because it is the smallest legal value above the live `4`. `"1.1.0"` rather
than `"1.0.5"` because this is the ground-up rebuild, not a patch on the old
binary — a minor bump is the honest signal to users. Version lineage is per Play
listing = per `applicationId`, so brand 2 overrides these in its own flavor block.

## Verification (Rule 6)

| Check | Evidence |
| --- | --- |
| Signed AAB builds | `./gradlew :app:bundleTranzlateProdRelease` → BUILD SUCCESSFUL, `:app:signTranzlateProdReleaseBundle` ran |
| Artifact | `app/build/outputs/bundle/tranzlateProdRelease/app-tranzlate-prod-release.aab` — 36,537,714 bytes (36.5 MB) |
| Merged manifest | `versionCode="5"`, `versionName="1.1.0"` |
| **Certificate matches the live app** | `keytool -printcert -jarfile` on the old live AAB and on ours both yield SHA-256 `EB3648C0…`, `CN=YPD LAKSIRI` — full 64-char strings compared programmatically, **MATCH** |
| CI path (no keystore) | keystore moved aside → BUILD SUCCESSFUL, warning logged, and `keytool` on the output reports **"Not a signed jar file"** — i.e. genuinely unsigned, not accidentally debug-signed |
| Configuration-cache correctness | Disconfirmation experiment (Rule 4): the plugin reads the file with raw I/O at configuration time, so a stale cached signing state was the obvious hypothesis. Gradle 9.6.1 reports `configuration cache cannot be reused because the file system entry 'keystore.properties' has been removed` / `…has been created` — it tracks the read as a configuration input and invalidates **in both directions**. Hypothesis disconfirmed. |
| Quality gates | `./gradlew spotlessCheck detekt --rerun-tasks` |

### Size — the 4-ABI problem the AAB actually solves

The universal APK is ~71 MB because all four ABIs of the MLKit `.so` payload ship
in one artifact. Per-ABI native payload inside our AAB (compressed):

| ABI | Native payload |
| --- | --- |
| arm64-v8a | 7.2 MB |
| armeabi-v7a | 6.4 MB |
| x86 | 7.8 MB |
| x86_64 | 7.7 MB |

**Estimated arm64-v8a device download: ~10.8 MB** — everything under `base/`
except the three non-arm64 ABIs. This is a deliberate **upper bound**: it still
counts every density and every language, which real Play splits trim further.

Method note: `bundletool` is not installed and the launch brief forbids pulling in
heavyweight tooling, so this is computed from compressed AAB zip entries, **not**
a `bundletool get-size total` measurement. Treat it as an estimate with the right
order of magnitude, not a precise figure.

For reference the old live AAB is 48.8 MB on disk vs our 36.5 MB.

## Play-update hygiene audit (read-only unless a blocker)

Checked against the **merged release manifest**, not the source manifest.

| Item | State | Verdict |
| --- | --- | --- |
| `android:debuggable` | absent | Correct — never set on release |
| `usesCleartextTraffic` | absent | Correct — `targetSdk` default blocks cleartext |
| `testOnly` | absent | Correct — a `testOnly` artifact is rejected by Play |
| `extractNativeLibs` | `false` | Correct for modern AGP |
| Permissions | `INTERNET`, `ACCESS_NETWORK_STATE` (+ AndroidX's auto-injected `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`) | Minimal; no `AD_ID` yet, consistent with ads still being inert |
| `android:allowBackup="true"` | **stock template rules** | **Reported, deliberately NOT changed** — see below |

### On `allowBackup` — why this was left alone

`backup_rules.xml` (`<full-backup-content>`, Android 11-) and
`data_extraction_rules.xml` (`<cloud-backup>`, Android 12+) are both still the
**Android Studio template stubs**, empty with `TODO` comments. Empty means
"back everything up", so the Room translation-history DB and DataStore prefs go
to the user's Drive.

It was left alone on purpose:

- It is the framework default and not a Play policy violation — the data goes to
  the **user's own** Drive, which is ordinary Android behaviour.
- This ships as an **update to an existing install base.** Changing backup
  semantics changes restore behaviour for users who already have backup sets.
  That is a product decision, not release plumbing, and not something to slip
  into a signing PR hours before an upload.
- It is therefore **not "unambiguously a release blocker"**, which was the bar
  the launch brief set for fixing things.

Worth a follow-up issue: excluding Room's `-wal`/`-shm` sidecar files from backup
is the usual hardening, since restoring a DB without its matching sidecars is a
known corruption vector.

## What still blocks a *good* update (outside this PR's file scope)

1. **Camera is a functional regression.** The live v1.0.4 ships a working camera
   translator (`CAMERA` permission + CameraX in the old repo). Our
   `feature/camera` is a 32-line placeholder with no CameraX dependency and no
   `CAMERA` permission. Play will accept the upload; **existing users will
   experience a removed feature.** This is the single biggest launch risk and it
   is a product call, not a build-config one.
2. **Monetization is inert by design right now.** The AdMob app id is a
   non-functional sample-format placeholder and `QONVERSION_KEY` / `GCT_API_KEY`
   are empty strings. Shipping like this means zero ad revenue and a paywall that
   cannot transact — the exact failure the rebuild was meant to fix.
3. Voice/Dialog absence is **not** a regression — the live app has no
   `RECORD_AUDIO` permission, so it never had those features.
