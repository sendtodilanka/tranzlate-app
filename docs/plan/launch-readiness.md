# Launch readiness — Google Play (read-only audit)

status: draft (audit record — not an implementation plan; each bucket-A item still needs its own issue + plan-doc per Mandatory Rule 3)

Audited: 2026-07-31 · tree `25aede9` (branch `feat/issue-103-error-card-loading-ux`, working tree clean).
Method: repo read only. No code changed. Play requirements verified against Google's own
support pages (linked inline); everything else is file:line from this tree.

> ⚠️ `feature/text/**` was being edited by another agent during this audit. All
> `feature/text` line numbers are from the committed state at `25aede9` and must be
> re-checked before anyone edits there.

---

## 0. What the app actually is today (the honest one-liner)

A **working offline+online text translator** — Home → composer → translate (ML Kit
offline → unofficial GOT online), result actions (copy, speak, reverse, star, retry),
a real language picker, a real offline-model download manager, real History/Saved
(Room), and real Settings (theme, dynamic colour, mobile-data consent).

Bolted onto it: a **paywall that cannot sell anything**, an **ads stack that is
entirely NoOp**, a **Camera tab that opens a blank placeholder**, and **no signing
config at all**. Those four are what stands between this tree and a Play release.

---

## A. BLOCKERS I (Claude) CAN FIX IN HOURS

### A1 — [ ] No release signing config; the release artifact is unsigned  · ~1h (needs B1)
`app/build.gradle.kts:19-31` — the `release` block sets `isMinifyEnabled`/`isShrinkResources`
and then states `// NO signingConfig on purpose`. Corroborated by
`docs/plan/issue-5-r8-release.md:16-19` ("`assembleTranzlateProdRelease` output is unsigned").
Today's artifact: `app/build/outputs/apk/tranzlateProd/release/app-tranzlate-prod-release-unsigned.apk`
(71,098,695 bytes). Repo-wide `signingConfig` hits: exactly one — the comment above.
No `.jks` / `keystore.properties` exists anywhere (`.gitignore:14-15` already ignores both).

**Fix:** add `signingConfigs.create("release")` reading `storeFile/storePassword/keyAlias/keyPassword`
from a gitignored `keystore.properties`, and wire `signingConfig = signingConfigs.getByName("release")`
inside `buildTypes.release` **guarded by `if (keystorePropsFile.exists())`** so CI (which has no
keystore and runs `./gradlew build`) keeps building unsigned and the R8 gate stays green.
I can write and test this; I cannot create the keystore itself (see B1).

### A2 — [ ] Ship an AAB, not the 71 MB APK  · ~30min
`bundleTranzlateProdRelease` already exists (AGP default — confirmed in `:app:tasks --all`:
`bundleTranzlateProd`, `bundleRelease`). It is simply never built or configured, and would
be unsigned today for the same reason as A1.

Why it matters, measured from the current APK:

| Path | Uncompressed bytes |
|---|---|
| `lib/x86_64` | 18,716,304 |
| `lib/x86` | 18,634,624 |
| `lib/arm64-v8a` | 17,401,064 |
| `lib/armeabi-v7a` | 12,234,128 |
| everything else | ~6,965,000 |

`libtranslate_jni.so` (ML Kit) alone is 16.4 MB for arm64 and ships **four times**. An AAB's
default ABI split delivers one ABI per device: an arm64 phone downloads roughly `17.4 MB + 7 MB`
instead of all 67 MB of `lib/`. Google's own limits page states **"100MB: Maximum compressed
download size of APK for apps published with APKs (only applicable to apps created before
August 2021)"** and 200 MB for bundle-generated APKs
([support.google.com/googleplay/android-developer/answer/9859152]) — i.e. a *new* app is
effectively AAB-only, and the APK route is not available to us anyway.
`extractNativeLibs=false` is already set in the merged manifest, so the bundle is well-formed.

**Fix:** build `bundleTranzlateProdRelease` after A1 lands; verify with `bundletool
get-size total --dimensions=ABI`. No new Gradle config needed beyond the signing block.

### A3 — [ ] `versionName = "0.1.0"` reads as pre-release  · ~5min
`app/build.gradle.kts:13-14` — `versionCode = 1`, `versionName = "0.1.0"`. Confirmed in the
merged manifest (`android:versionCode="1" android:versionName="0.1.0"`).
`versionCode = 1` is correct for a first upload; the *name* is what users see on the store page.
**Fix:** `versionName = "1.0"` for a production listing (leave `0.1.0` if this is internal-testing only).

### A4 — [ ] Camera is a reachable hard dead end  · ~45min (app module only)
The single worst Play-quality item, because it is indistinguishable from a broken feature.

- Door: Home tool card **"Camera · Signs and menus"** — `feature/text/.../HomeScreen.kt:403-412`
  (`onClick = onOpenCamera`, `tt_home_tool_camera`)
- Wiring: `app/.../navigation/TranzlateApp.kt:107` → `TranzlateApp.kt:157` `entry<CameraNavKey> { CameraScreen() }`
- Destination: `feature/camera/.../CameraScreen.kt:19-32` — a bare `Box` with one centred
  `Text("Camera translation")`. No `Scaffold`, no top bar, **no back affordance** (system back only).
- `feature/camera/build.gradle.kts` has **no `dependencies` block at all** — no CameraX, no ML Kit
  text recognition. It is a scaffold stub, and issue #78 (the spec) is still open.

**Fix (cheapest honest, and it does not touch `feature/text`):** change `TranzlateApp.kt:157` to
render `ComingSoonScreen(title = …camera…, icon = …)` — the component already exists at
`app/.../navigation/ComingSoonScreen.kt:32-70` — and add a `TopAppBar` with a back arrow to
`ComingSoonScreen` (it currently has none either, so Chat has the same missing-back problem).
Add `camera_coming_soon_title` to `app/src/main/res/values/strings.xml`.
**Better, if `feature/text` frees up:** delete the Camera `ToolCard` outright.

### A5 — [ ] `FEATURES` white-label gating is dead code  · ~1h
`app/build.gradle.kts:71` declares `FEATURES = "text,dialog,camera,history,settings"`; it is
parsed correctly (`app/.../config/BuildConfigAppConfig.kt:24`, `core/config/.../AppConfig.kt:54`)
and injected — then **thrown away**: `app/.../navigation/TranzlateApp.kt:40` is literally
`@Suppress("UNUSED_PARAMETER") appConfig: AppConfig`. The toggle-aware registry
`app/.../navigation/TopLevelDestination.kt:65-74` is referenced by zero production code.

Consequence: **removing `camera` from the CSV changes nothing.** The documented white-label
escape hatch for A4 does not work. Also makes `nav_home`/`nav_chat`/`nav_camera`
(`app/src/main/res/values/strings.xml:8-10`) dead strings.
**Fix:** read `appConfig.featureToggles` in `TranzlateApp` and thread booleans into
`HomeScreen`'s `ToolsStack` (`feature/text/.../HomeScreen.kt:363-448`) — coordinate with the
`feature/text` agent, or land A4's app-module version first and do this after.

### A6 — [ ] A paywall that advertises prices and a free trial but cannot ever sell  · ~2h
This is the highest **policy** risk in the tree. Verified end to end:

- No billing SDK exists. `gradle/libs.versions.toml` has no Qonversion / Play Billing / RevenueCat
  entry; `lib/subscription/build.gradle.kts:12-14` depends only on `kotlinx-coroutines-core`.
- Prod binds the stub: `app/src/prod/.../di/TranslateModule.kt:122-124` →
  `NoOpSubscriptionGateway(config) // TODO(#4-brains)`.
- `lib/subscription/.../SubscriptionGateway.kt:58` pins entitlement to `Entitlement.Free`
  **forever**, so the Pro chip (`HomeScreen.kt:452-477`, gated on `!isPro`) is shown to **every**
  user, always.
- `:62-63` `purchase()` → `Result.failure(UnsupportedOperationException("Billing SDK not integrated yet"))`;
  `:65-66` `restore()` → the same.
- CTA path: `feature/paywall/.../PaywallScreen.kt:218-221` → `PaywallViewModel.kt:76-90` →
  `core/access/.../SubscriptionPurchaseFlow.kt:23-24` → the failure above → snackbar
  **"Billing isn't available yet — nothing was charged."** (`feature/paywall/src/main/res/values/strings.xml:26`).
  Same for Restore (`PaywallScreen.kt:244-247`).
- What the user reads before tapping: **US$1.99 / US$4.99 / US$29.99**, **"SAVE ~60%"**,
  **"7-day free trial"**, **"That's about US$0.08 a day"**, **"Start my 7-day free trial"**
  (`feature/paywall/src/main/res/values/strings.xml:13-20`) — all hardcoded string resources,
  duplicated verbatim in `values-fil/strings.xml:11-13` and `values-pt-rBR/strings.xml:11-13`.
  Nothing is fetched: `offeringIds = emptyList()` (`TranslateModule.kt:118-119`).
- This also contradicts our own accepted spec: `docs/specs/00-foundations/BUSINESS_MODEL.md:51`
  — "Prices are **never hardcoded**".

**Fix for a first release: remove the monetization surface entirely.** Concretely — hide the Pro
chip (`HomeScreen.kt:125` `showProChip`), drop the `PaywallNavKey` entry (`TranzlateApp.kt:183-188`),
and drop the limit-sheet upgrade CTA (`ComposerScreen.kt:1101-1106`). Then also relax the free-AI
quota fence so nothing is gated behind a purchase that cannot happen (GCT is already inert — see C3
— so in practice the quota fence should never fire, but verify). Ship v1 as a free, ad-free
translator; wire billing properly in the Qonversion batch.
*Do not* ship the paywall "just disabled-looking" — a visible plan card with a price is the
problem, not the button.

### A7 — [ ] Terms / Privacy links point at nothing, and there is no legal surface at all  · ~1h (needs B5)
- `feature/paywall/.../PaywallScreen.kt:248-249` — the Terms and Privacy `TextButton`s both call
  `onLinkNotice` (`:107`), which shows a snackbar reading
  **"Terms and Privacy pages arrive before release."** (`feature/paywall/src/main/res/values/strings.xml:29`).
- Repo-wide there is **no `http(s)://` URL in any `.kt` or user-facing `.xml`** — the only two hits
  are inside the stock comments of `app/src/main/res/xml/backup_rules.xml:4` and
  `data_extraction_rules.xml:4`. No `ACTION_VIEW`, no `LocalUriHandler`, no Custom Tabs, no WebView.
- Settings has no legal section: `feature/settings/src/main/res/values/strings.xml` is 16 keys
  (Appearance, Your data → History, Downloads → mobile data). No About, no Licenses, no
  privacy-options entry.
- Play requires this of everyone: *"Even developers with apps that do not collect any user data
  must complete this form and provide a link to their privacy policy."*
  ([support.google.com/googleplay/android-developer/answer/10787469])

**Fix:** add a "Legal" section to `SettingsScreen` with **Privacy policy** and **Terms** rows that
`ACTION_VIEW` the owner-supplied URLs (A7 is code-ready the moment B5 lands); point the paywall
links at the same URLs (moot if A6 removes the paywall).

### A8 — [ ] Room is on `fallbackToDestructiveMigration` — the first update wipes user history  · ~1h
`core/database/.../di/DatabaseModule.kt:25-30` — the comment says it outright:
*"⚠ PRE-LAUNCH ONLY … This line MUST be replaced by real Migration objects before the first
release — release-checklist item"*. `dropAllTables = true`. The DB is at version 2 and schemas
**are** exported (`core/database/schemas/…/1.json`, `2.json`), so migrations are writable.

Not a v1.0.0 *upload* blocker (no shipped users yet), but it is a **data-loss landmine for
v1.0.1**: any future schema bump silently deletes every user's translation history.
**Fix:** either write the `MIGRATION_1_2` object and remove the fallback now, or gate the
fallback to debug builds only. Cheap, and must not be forgotten after launch.

### A9 — [ ] Two hardcoded, factually false strings on Home  · ~30min
- `feature/text/src/main/res/values/strings.xml:38` — `home_tool_offline_sub` = **"6 languages ready"**,
  a static string rendered unconditionally at `HomeScreen.kt:384`.
- `feature/text/src/main/res/values/strings.xml:46` — `home_row_download_sub` =
  **"133 available · 2 updates ready"**, rendered at `HomeScreen.kt:427`.
- Reality: the bundled catalog has **20** languages
  (`core/data/.../BundledLanguageCatalog.kt:18-38`, wired at `LanguageRepositoryImpl.kt:30-34`),
  and "2 updates ready" is never computed by anything.

These are design-mock values that survived into shipping copy. A fresh install shows
"6 languages ready" with zero models downloaded.
**Fix (cheapest):** reword both to non-numeric subtitles ("Translate without a connection" /
"Add languages for offline use"). **Proper:** compute from `LanguageRepository`. Touches
`feature/text` — coordinate.

### A10 — [ ] `allowBackup=true` with stock, empty backup rules  · ~30min
Merged release manifest: `allowBackup=true`, `dataExtractionRules=@xml/data_extraction_rules`,
`fullBackupContent=@xml/backup_rules`. Both files are **unmodified Android Studio templates** with
every rule commented out (`app/src/main/res/xml/backup_rules.xml`,
`app/src/main/res/xml/data_extraction_rules.xml:9` still contains `<!-- TODO: Use <include> and
<exclude> … -->`).

Effect: the `translation` table — which stores `source_text` and `target_text`, i.e. **the user's
typed content** (`core/database/.../TranslationEntity.kt:29,31`) — is auto-backed-up to the user's
Google Drive and device-transferred. That is a real "data transferred off device" answer on the
Data safety form.
**Fix:** decide with the owner — either write real `<exclude>` rules for `tranzlate.db`, or keep
backup and declare it in B8. Either way, the stock template must not ship.

### A11 — [ ] App-shell strings have no `fil` / `pt-rBR`  · ~15min (native review = B9)
`app/src/main/res/values/strings.xml:4-6` suppresses `MissingTranslation` with a
`TODO(NEEDS-TRANSLATION, C-12)`. There is no `app/src/main/res/values-fil` or `values-pt-rBR`.
So "Conversation" / "Coming soon" render in English on Filipino and Brazilian-Portuguese devices,
while every surrounding feature screen is translated.
Same for `feature/camera/src/main/res/values/strings.xml` — that one *does* have fil/pt files.

### A12 — [ ] Open UX bug on tall phones (issue #99)  · unknown — decide, don't ignore
`#99` "Landscape typing area collapses on 832dp phones — minimal-IME gated on WIDTH, not height
(OnePlus 7 Pro)" is OPEN, with a live worktree at `.claude/worktrees/issue-99-height-gating`
(branch `fix/issue-99-height-gating`). A landscape-typing collapse on a common phone class is
1★-review material. **Decide before upload:** land the fix, or accept it knowingly for an
internal-testing build.

---

## B. BLOCKERS ONLY THE OWNER CAN CLEAR

### B1 — [ ] Upload keystore
No `.jks` exists in the repo (correctly — `.gitignore:14-15`). The owner must generate the upload
keystore (Android Studio → Build → Generate Signed Bundle/APK → Create new…), store it **outside**
the repo, and put the passwords in a gitignored `keystore.properties`. I must not create or hold
signing credentials. Lose this file and the app can never be updated (unless Play App Signing key
reset is used). **Back it up in two places before uploading anything.**

### B2 — [ ] Play Console account — and the 12-tester / 14-day rule ⚠️ **the most likely 12-hour killer**
$25 one-time, plus identity verification (which itself can take days). Critically, for
**personal developer accounts created after 13 November 2023**, Google requires:
*"you must run a closed test for your app with a minimum of 12 testers who have been opted-in for
at least the last 14 days continuously"* before production access is granted
([support.google.com/googleplay/android-developer/answer/14151465]).

**If the account is new and personal, a production release inside 12 hours is impossible** — the
14-day clock cannot be shortened. Internal testing has no such gate, which is why the 12-hour plan
below targets internal testing. Owner must confirm the account's age/type.

### B3 — [ ] Qonversion project key + Play subscription products
`app/build.gradle.kts:56` ships `QONVERSION_KEY = ""`. Needed: a Qonversion project, and in Play
Console the actual subscription with weekly/monthly/yearly base plans, per-country price templates,
the 7-day yearly trial offer, and product ids matching `PaywallViewModel.kt:30-36`
(`"weekly"|"monthly"|"yearly"`). **Not needed if we take A6's route and ship v1 without billing.**

### B4 — [ ] Real AdMob app id + ad-unit ids
`app/build.gradle.kts:53-55` ships the sample-format placeholder
`ca-app-pub-0000000000000000~0000000000`, and it reaches the **released manifest**
(`app/src/main/AndroidManifest.xml:21-23`; confirmed in the merged manifest meta-data
`com.google.android.gms.ads.APPLICATION_ID`). It is currently **inert** — no Mobile Ads SDK is on
the classpath — so it cannot crash, but a placeholder AdMob declaration should not ship.
The guard test `app/src/test/.../AppConfigMatrixTest.kt:31-36` only checks the *unit* ids against
Google's sample id; it does not check `ADMOB_APP_ID`.
**Cheapest v1 decision:** owner says "no ads in v1" → I delete the meta-data block. Otherwise the
owner supplies real ids.

### B5 — [ ] Privacy Policy URL + Terms URL, publicly hosted
Mandatory for every app (see A7). Must cover: on-device history storage, and the fact that text
typed for translation is sent to Google translation endpoints. Owner hosts them; I wire them (A7).

### B6 — [ ] Store listing assets
App name, short description (≤80 chars), full description (≤4000), 512×512 hi-res icon,
1024×500 feature graphic, ≥2 phone screenshots. Owner-authored (I can draft copy for review).

### B7 — [ ] Content rating questionnaire (IARC) — Play Console only, owner answers.

### B8 — [ ] Data safety form — **draft answers below, owner submits**
Grounded in what the code actually does:

| Question | Answer | Evidence |
|---|---|---|
| Does the app collect or share user data? | **Yes — collected, not shared** | see rows below |
| Data type | **"Other user-generated content"** — the text the user types for translation (confirm the exact category name in Play Console) | `core/translate/.../engine/GotEngine.kt:44-55` sends the full input text as the `q` query parameter to `https://translate.googleapis.com/translate_a/single` |
| Purpose | App functionality (translation) | `RealTranslator.kt:140-209` waterfall |
| Is it transferred off device? | **Yes**, for online translation; and **yes** via Android auto-backup of the history DB (until A10 is decided) | `GotEngine.kt:54`; `AndroidManifest.xml:10-12` + empty backup rules |
| Encrypted in transit? | **Yes** — HTTPS only (`.scheme("https")` on both engines) | `GotEngine.kt:47`, `GctEngine.kt:46` |
| Can users request deletion? | **Yes** — swipe-to-delete in History | `feature/history/.../HistoryScreen.kt` |
| Is it required or optional? | Optional — offline (ML Kit) translation sends nothing | `MlKitEngine.kt` is on-device |
| Device or advertising IDs? | **No** — zero hits for `AdvertisingIdClient` / `ANDROID_ID` / `Settings.Secure` / `UUID.randomUUID`; no `AD_ID` permission | repo-wide grep |
| Analytics / crash reporting? | **No SDK at all** — no Firebase, Crashlytics, Sentry, or `google-services.json` | `gradle/libs.versions.toml` |
| Personal info / contacts / location / photos / audio? | **No** — the only permissions are `INTERNET` and `ACCESS_NETWORK_STATE` | merged release manifest |
| Data stored on device | translation history (`source_text`, `target_text`, langs, engine, favourite, timestamp) + prefs/usage counters | `TranslationEntity.kt:23-37`; `TranzlatePreferencesDataSource.kt:102-107`; `UsageDataSource.kt:94-99` |

⚠️ Owner must consciously accept one thing: **the GOT tier sends user-typed text to an
undocumented, unofficial Google endpoint** (`GotEngine.kt:24` records *"D-E1: RISK ACCEPTED by
the product owner"*, and CLAUDE.md repeats it). Accepting that in a private build is different
from accepting it in a published app with a privacy policy that must describe it truthfully. The
kill switch exists (`RealTranslator.kt:186`, default on).

### B9 — [ ] Native review of the authored `fil` / `pt-rBR` strings
Actual counts in this tree — **169 keys per locale**, not 72:
text 94 · paywall 25 · languagepicker 20 · settings 16 · history 13 · camera 1.
Only the **first 19** of the text feature's are spec-catalogue-verbatim; the header says the rest
"were authored in-issue and are queued for native-speaker review before ship (DoD gate 12)"
(`feature/text/src/main/res/values-fil/strings.xml:2-4`). The paywall/settings/history/languagepicker
files carry no such header but were authored the same way.
A Filipino and a Brazilian-Portuguese speaker must read them before the store lists those locales.
**12-hour shortcut:** publish the listing English-only; the translations still ship but are not
advertised.

### B10 — [ ] The launcher icon is a placeholder
`app/src/tranzlate/res/values/ic_launcher_background.xml:3-5` — *"Placeholder scaffold icon; final
brand icon lands in the UI-design phase (D-P2)"*. Adaptive icon + all mipmap densities exist and
are valid, so it will *build* and *install* — it is simply the generic Android-Studio robot on brand
blue. Fine for internal testing; not fine for a production listing (it is also the 512×512 in B6).

### B11 — [ ] The product decision: does v1 ship with monetization at all?
A6 and B3 both hinge on this. Recommendation: **no**. Ship a free, ad-free, honest translator,
learn from real installs, add billing in its own issue with real Play products.

---

## C. NOT BLOCKING A FIRST RELEASE

- [ ] **C1 — Camera feature itself.** Spec is still in review (issue #78 OPEN). Deferring is
  correct; only the *dead entry point* (A4) blocks.
- [ ] **C2 — Voice / Phrasebook / Quotes / Natural phrasing.** These are already honest: each tap
  shows a snackbar saying the feature arrives later
  (`HomeScreen.kt:397,435,443,447`; strings `:53-55,59`). One nit worth 5 minutes: the
  "Natural phrasing" banner carries a **NEW** badge (`HomeScreen.kt:814`) for a feature that does
  not exist — that reads as a lie; drop the badge. And `home_guided_quotes` ("arrives with the
  history update", `strings.xml:54`) is stale — History/Saved already shipped.
- [ ] **C3 — Ads.** Entirely NoOp (`core/ads/.../RealAdsCoordinator.kt:25-27` is
  `onTranslationCompleted() = Unit`; `lib/ads/.../AdsGateway.kt:73-81`). Shipping ad-free is a
  perfectly good v1 and removes the UMP-consent obligation (`lib/consent/.../ConsentGateway.kt:46-55`
  is NoOp and **never called** — `RealAdsCoordinator.kt:23` injects it `@Suppress("unused")`).
- [ ] **C4 — GCT (paid tier) is inert.** `GCT_API_KEY = ""` (`app/build.gradle.kts:57`) and every
  call site is gated by `gctConfigured()` (`RealTranslator.kt:259`). Nothing to do; just know the
  "Advanced AI" badge can never appear.
- [ ] **C5 — `localeConfig`.** Not declared, so Android 13+ per-app language selection is
  unavailable. 20 minutes whenever; not a gate.
- [ ] **C6 — targetSdk.** We are at **targetSdk 36 / compileSdk 37 / minSdk 24**
  (`build-logic/.../ProjectExtensions.kt:22-26`, confirmed in the merged manifest). Google requires
  API 36+ for new apps and updates from **31 August 2026**
  ([support.google.com/googleplay/android-developer/answer/11926878]) — **we already comply**, with
  a month of margin. Nothing to do.
- [ ] **C7 — R8 keep breadth.** `app/proguard-rules.pro:22` keeps all of `com.google.mlkit.**`.
  It exists because the release smoke caught a real launch NPE inside
  `RemoteModelManager.getInstance()` (`docs/plan/issue-5-r8-release.md:24-35`). Narrowing it is
  recorded follow-up work — do it in the Qonversion batch, not now.
- [ ] **C8 — No crash reporting SDK.** Play Console's Android vitals gives crash/ANR data for free
  with no SDK, which is enough for internal testing. Adding Crashlytics needs `google-services.json`
  from the owner — worth doing before production, not before internal testing.
- [ ] **C9 — androidTest suite fails on API 35+/36** (issue #40 OPEN). A verification gap, not a
  user-facing defect. Unit tests + Konsist + detekt/spotless all run in CI (`.github/workflows/ci.yml`).
- [ ] **C10 — Dead code.** `TopLevelDestination.kt`, `core/ui/ComposerCard.kt`,
  `QuickActionButton.kt`, `PrimaryActionButton.kt`, `nav_*` strings, `FeatureToggle.VOICE`,
  and the unused `material3-adaptive-navigation-suite` dependency. R8 strips most of it; it is
  hygiene (FIX_QUEUE 🅑 B7 / ⚪ F).
- [ ] **C11 — Second white-label brand.** The mechanism works (`app/build.gradle.kts:44-71`);
  no second flavor is needed for launch.
- [ ] **C12 — Baseline profiles / macrobenchmark / screenshot harness** (issues #22, #20). Post-launch.

---

## 12-HOUR PLAN

### The honest reading first
**A production release in 12 hours is very likely impossible, and it is not our code's fault.**
If the Play developer account is personal and was created after 13 Nov 2023, Google requires a
14-day closed test with 12 testers before granting production access (B2). No amount of engineering
tonight shortens that. Owner must confirm the account's status *first* — it determines everything.

**Internal testing is achievable tonight** — no tester-day requirement, no content-rating wait,
and it produces a real Play-distributed build.

### Path to a Play **internal-testing** release (minimum honest scope)

| # | Who | Work | Est. |
|---|---|---|---|
| 1 | **Owner** | Confirm Play Console account exists + is verified (B2). *Everything downstream is blocked on this.* | — |
| 2 | **Owner** | Generate the upload keystore, keep it outside the repo, back it up twice (B1) | 15m |
| 3 | Claude | A1 signing config (properties-file driven, CI-safe) | 1h |
| 4 | Claude | A4 Camera → `ComingSoonScreen` + a back arrow on `ComingSoonScreen` | 45m |
| 5 | Claude | A6 remove the paywall surface — Pro chip, paywall destination, limit-sheet CTA | 2h |
| 6 | Claude | A9 reword the two false Home strings · C2 drop the NEW badge | 45m |
| 7 | Claude | A3 `versionName` · A10 backup rules decision · B4 delete the placeholder AdMob meta-data | 45m |
| 8 | Claude | A8 real Room migration (or debug-only fallback) | 1h |
| 9 | Claude | A2 build + sign `bundleTranzlateProdRelease`; verify with `bundletool` | 30m |
| 10 | Claude | Device smoke on `Tranzlate_Resizable`: cold start · offline translate · online translate · download a model · history · settings · every Home tap | 1h |
| 11 | **Owner** | Upload the AAB to the internal-testing track, add testers | 30m |

≈ **8h of engineering**, all of it in `app/`, `core/`, `feature/paywall` — one item (A9) reaches
into `feature/text` and must be coordinated with the agent working there.
Per Mandatory Rule 3 each of these needs an issue + a plan-doc + a PR with co-verify; at this pace
that means batching them into 2-3 issues, not 8.

**Deliberately deferred for internal testing:** A5 (feature toggles), A7 (legal links — internal
testers do not see a store listing), A11 (shell translations), B6/B7/B8 (store listing, rating,
data safety — internal testing does not require them), B9 (native string review), B10 (real icon).

### What a **PRODUCTION** release additionally requires

- **B2 — the 12-tester / 14-day closed test**, if the account is personal and post-Nov-2023. This is
  a two-week calendar item, not an engineering item. Start it *today*.
- **B5 + A7** — hosted Privacy Policy and Terms URLs, and in-app Settings rows that open them.
  Play will not let the listing complete without the privacy policy.
- **B8** — Data safety form submitted (draft answers above), truthfully declaring that translation
  text goes to Google endpoints, and matching whatever A10 decides about backup.
- **B7** — content rating questionnaire.
- **B6 + B10** — real store listing assets and a real launcher icon.
- **B9** — native review of the 169 fil + 169 pt-rBR strings, *or* an English-only listing.
- **A12 / issue #99** — the landscape-collapse fix landed and verified.
- **A5** — feature toggles made load-bearing, so the next brand flavor can actually drop Camera.
- **C8** — crash reporting (Crashlytics) before real user volume.
- **B11 + A6 reversal + B3** — if v1 is to monetize: real Play subscription products, the Qonversion
  SDK wired, prices read from the store (never resources), a working Restore, and the R8 rules
  re-verified for the new SDKs (`docs/plan/issue-5-r8-release.md:37-40` already scopes this batch).

### The three things most likely to stop a 12-hour launch
1. **B2** — Play account age/type and the 12-tester × 14-day production gate.
2. **B1** — no keystore exists; nothing can be uploaded until the owner creates one.
3. **A6** — a paywall showing US$29.99 and a 7-day trial that can only ever fail; shipping it
   invites a policy rejection *and* 1★ reviews.
