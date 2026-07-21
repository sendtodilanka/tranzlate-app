---
status: accepted   # owner-accepted 2026-07-21 — evidence: docs/plan/review/issue-3-comments.json #5 (plan-final approve, 20:55) + #4 ("9.1 A hodai")
issue: 3
title: Scaffold multi-module project (version catalog, Compose/Hilt, tokens, adaptive nav shell, white-label flavor)
date: 2026-07-21
author: Claude (Fable 5) · design-debate + version-verify workflow `wf_7ca4d35c-99f` (agents 29 · 3 proposals × 3 judges × 3 adversarial audits · versions two-source-verified)
---

# Plan — Issue #3: Multi-module scaffold

> **Scope (BUILD_ROADMAP §Phase C step 1):** Gradle project එක බිංදුවේ සිට — version catalog · build-logic convention plugins · module graph · DESIGN_SYSTEM tokens · adaptive nav shell (`NavigationSuiteScaffold`) · white-label flavor scaffold (brand 1) · deterministic **fake-engine** variant · CI. **Feature logic නෑ, real engine නෑ** — ඒවා phase 2 (brains) + phase 3 (Text vertical).
> **Non-goals:** real MLKit/GOT/GCT engines, Qonversion/AdMob SDK integration, Text feature UI, Room migrations. Prod brain modules compile වෙන placeholder impls (explicit `TODO`) සමඟ; **demo APK = fake variant** (golden-table translator, nav shell + theme පෙන්වයි).

## 1. Verified toolchain & versions (2026-07-21 · two-source-verified)

සියල්ල **stable** — pre-release කිසිවක් නෑ. එක correction එකක් cross-check එකෙන් හමු වුණා (Kotlin 2.4.0→**2.4.10**).

| Item | Version | Notes / source |
|---|---|---|
| AGP | **9.3.0** | Gradle ≥9.5.0 · Build Tools 36.0.0 · max compileSdk 37 · JDK 21 (JBR) OK — developer.android.com/build/releases/gradle-plugin + dl.google.com maven-metadata |
| Gradle | **9.5+** (wrapper latest 9.x stable at scaffold time) | AGP 9.3 requirement |
| Kotlin / Compose compiler | **2.4.10** (`org.jetbrains.kotlin.android`, `org.jetbrains.kotlin.plugin.compose` — Kotlin එකේම ships) | Maven Central metadata (2.4.20 = Beta1 පමණයි) |
| KSP | **2.3.10** (independent versioning; Kotlin 2.4 fix included) | github.com/google/ksp releases + KSP quickstart |
| Compose BOM | **2026.06.01** → material3 **1.4.0** | bom-mapping + dl.google.com |
| material3-adaptive | **1.2.0** (`adaptive`, `adaptive-layout` (ListDetailPaneScaffold), `adaptive-navigation`) | releases/compose-material3-adaptive + group-index.xml |
| NavigationSuiteScaffold | `material3-adaptive-navigation-suite` **1.4.0** (BOM included) | dl.google.com group-index |
| Hilt | **2.60.1** + androidx.hilt **1.4.0** (`hilt-lifecycle-viewmodel-compose` — hiltViewModel() 1.3.0 සිට මෙතන) | github dagger releases + releases/hilt |
| Room / DataStore / WorkManager | **2.8.4 / 1.2.1 / 2.11.2** | releases pages + group-index |
| Lifecycle / Activity | **2.11.0 / 1.13.0** | releases pages |
| Navigation | **Navigation 3 = 1.1.4 stable** (`navigation3-runtime`, `navigation3-ui`) — Google's recommendation for NEW Compose apps (verified) | releases/navigation3 · §7 decision |
| Coroutines / Serialization | **1.11.0 / 1.11.0** (`-play-services` included — MLKit `Task.await()`) | github releases |
| MLKit translate | **17.0.3** (phase 2 එකේ use; catalog-pinned now) | ml-kit release notes |
| compileSdk / targetSdk / minSdk | **36 / 36 / 24** | Android 16 stable; Play new-app target-36 deadline 2026-08-31; minSdk 24 ← DESIGN_SYSTEM "static palette = fallback API 24–30" |
| Test/quality | turbine **1.2.1** · truth **1.4.5** · coroutines-test **1.11.0** · ui-test-junit4 (BOM) · hilt-android-testing **2.60.1** · Detekt **1.23.8** · Spotless **8.8.0** | maven central / plugin portal |
| coreLibraryDesugaring | **enabled (mandatory)** | minSdk 24 + `AppClock`/`java.time` (TEST contract :152) — audit finding |

## 2. Module graph — 26 modules, rings 4 (winning design: library-first, judges 3/3)

**Ring 1 — reusable AARs (වෙන app වලටත් — CLAUDE.md "Advanced required"):**
- `:lib:subscription` — ns `com.codeboxlk.subscription`. Public API: `SubscriptionGateway`, `Entitlement=Loading|Free|Paid(tier)`, `SubscriptionConfig(projectKey, offeringIds)` (host app @Provides). Qonversion SDK `internal`. Tranzlate ගැන දන්නේ ශුන්‍යයි.
- `:lib:ads` — ns `com.codeboxlk.ads`. `AdsGateway` (load/show), `AdsConfig`, `AdPolicyConfig(nth,minGap,dailyCap)` + frequency **mechanics only**; consent `setConsent()` හරහා. **Show/no-show decision එක මෙතන නෑ** (one-home rule → `:core:ads`).
- `:lib:consent` — ns `com.codeboxlk.consent`. UMP wrapper: `ConsentGateway(requestConsent, consentStatus: Flow, privacyOptionsRequired)` — පරණ app එකේ auto-granted consent bug එක structural ලෙස fix.

**Ring 2 — pure-JVM contracts (Android import ශුන්‍ය — Konsist gate §8):**
- `:core:common` — `AppResult` sealed seam, `DispatcherProvider`, `AppClock` **interface** (⚠ `SystemAppClock` binding මෙතන **නෑ** — §6.2), coroutine utils (hilt-core).
- `:core:model` — `Translation`, `Language`, **`ModeId{AUTO,ML2_MINI,ML2_ONLINE,NLP35}` / `Engine{OFFLINE_MLKIT,ONLINE_GOOGLE,ONLINE_CLOUD_NLP}` (C-9 verbatim)**, `Tier`, `Entitlement`, `TranslationOutcome`, `FailureReason`, `Availability` (EDGE_CASES §1).
- `:core:domain` — ask-only contracts: **`Translator` @ `com.codeboxlk.tranzlate.domain.translate` (FROZEN — TEST contract :26)**, `FeatureAccess`, `UsagePolicy`, `AdsCoordinator`, `OfflineModelManager`, repo interfaces + use cases. **`TranslateTextUseCase` = single ask-flow encoding** (Access check → translate → Usage +1 on-success-only → Ads ask) — APP_STRUCTURE flow එක feature එකකින් කවදාවත් re-sequence නොවෙන ලෙස. Brain-per-sub-package (`translate/access/usage/ads`) + boundary check — future api-split fault line.
- `:core:config` — typed `AppConfig` interface (admob ids, qonversion key, default langs, feature toggles) + `RemoteConfigSource` interface (`limit_free`, `ad_nth`, `text_limit`…). Implementation-free.
- `:core:testing` — `FakeTranslator` + **golden table §1.2 exact**, `FakeFeatureAccess`, `FakeUsagePolicy`, `FakeClock`, `TestDispatcherRule` — unit/androidTest/fake-variant තුනම single-source.

**Ring 3 — Android infra + brains 4 (one home each):**
- `:core:designsystem` — DESIGN_SYSTEM §1 static light/dark palette · dynamic color API 31+ (static fallback 24–30) · Typography/Shapes · `LocalSpacing`/`LocalGradientColors`/`Motion`/`Dimensions`/alpha tokens. Leaf.
- `:core:ui` — shared stateless composables (`ErrorView`/`LoadingView` — no-dead-end), `rememberWindowInfo()` (C-13 wrap), a11y helpers (48dp, liveRegion).
- `:core:database` — Room `tranzlate.db`, C-8 cache index, DAOs.
- `:core:datastore` — `prefs.*`/`usage.*` typed accessors (DATA_MODEL).
- `:core:data` — repo impls (dependency inversion): `TranslationRepository` (cache-first C-8), `LanguageRepository` (bundled 180+ ∩ MLKit runtime), `UsageStore`, `ConnectivityMonitor`, `FirebaseRemoteConfigSource`.
- `:core:translate` — **TRANSLATION BRAIN.** `RealTranslator`, engine adapters (MlKit/GOT/GCT — scaffold එකේ TODO placeholders), AUTO free-only resolver (C-10), fallback chain, `OfflineModelManager` impl (6-state). ⚠ Hilt `TranslateModule` මෙතන **නෑ** — §6.1.
- `:core:access` — **ACCESS BRAIN.** `FeatureAccess` + purchase-flow impl ← `:lib:subscription` adapt; Loading-gate rule (DATA_MODEL :48).
- `:core:usage` — **USAGE BRAIN.** `UsagePolicy`: daily counter, device-local-midnight reset via `AppClock`, D-2 limits, success-only increment.
- `:core:ads` — **ADS BRAIN.** `AdsCoordinator` = **එකම** show/no-show decision point (D-4), `ConsentGateway`→`AdsGateway` wiring.
- `:core:translate-fake` — **fake variant binding module** (§6). `FakeTranslateModule` (production `@InstallIn`): seams 4 + **`FakeOfflineModelManager` (6-state) + NoOp purchase-flow + NoOp `AdsCoordinator`** — excluded prod brains 4ේම **සම්පූර්ණ binding surface** cover කරයි (audit mandatory fix).

**Ring 4 — features + shell (feature deps: interfaces + data/ui/designsystem පමණයි — brain impl කිසිදා නෑ):**
- `:feature:text` · `:feature:languagepicker` · `:feature:camera` · `:feature:history` · `:feature:settings` · `:feature:paywall` — scaffold එකේ placeholder screens (tt_ tags ready).
- `:app` — thin shell: `MainActivity`, `NavigationSuiteScaffold` (bar→rail→drawer), Nav3 NavDisplay mediator, Hilt app, flavors (§4), **සියලු prod Hilt wiring** (§6.1), `AppConfig` binding + `AppConfig`→`SubscriptionConfig`/`AdsConfig`/`ConsentConfig` mapping.
- `build-logic/convention` — included build (§5).

**Dependency rules (Konsist/Gradle-enforced):** features → brains impl ✗ · `:lib:*` → project deps ✗ (zero) · Ring 2 → Android ✗ · brains ↔ brains ✗ (composition `:core:domain` use cases වලින්).

## 3. Nav shell + tokens

- **Token wiring** — DESIGN_SYSTEM §10 verbatim: static schemes → `TranzlateTheme(dynamic if SDK≥31 && enabled else static)`; non-M3 tokens CompositionLocals; feature code raw hex/dp/sp **තහනම්** (module boundary + Detekt rule). C-13 dims (`360/400dp`, `40/60`, `480dp` max-width) `Dimensions` tokens ලෙස.
- **Adaptive nav** — `NavigationSuiteScaffold` bar (Compact) → rail (Medium) → permanent drawer (Expanded, `layoutType` override). Destinations scaffold එකේ: Text · Camera · History · Settings (MVP set; toggle-aware — §4 R2).
- **⚖ DESIGN_TOKENS.md conflict flag:** DESIGN_TOKENS (seed `#1E88A8`, "PROPOSED") vs DESIGN_SYSTEM (full palette `#1C7A97`, WCAG-checked, "designer confirms buildable"). C-13 + BUILD_ROADMAP DESIGN_SYSTEM ම cite කරන නිසා **DESIGN_SYSTEM = canonical** ලෙස implement කරයි; DESIGN_TOKENS superseded-note docs follow-up PR.
- **🎨 D-P2 owner resolution (2026-07-21, comments #2/#4):** palette = **provisional baseline** — DESIGN_SYSTEM tokens implement වෙයි (token seam එක නිසා hex swap = එක file එකක්), නමුත් **final colours තීරණය වෙන්නේ UI-design phase එකේදී** (Claude Design / claude.ai-design mockups, scaffold merge ට පසු, Text feature ට කලින් — වෙනම issue). Owner preference recorded: **light/white, Google-Translate-style** look (note: DESIGN_SYSTEM light theme දැනටමත් white-surfaced — `background #FCFCFD` — teal = accent පමණයි).

## 4. White-label flavors (dimension "brand") — "app අලුතක් = flavor එකක්, code ✗"

- `flavorDimensions += listOf("brand", "engine")` — **`:app` එකේ පමණයි** (libraries dimension-free).
- Brand flavor එකක් = `applicationId` · `signingConfig` · `resValue app_name` (**මේ එක mechanism එකම** — R4 audit: `src/<brand>/res` strings duplicate ✗) · `manifestPlaceholders[admobAppId]` · `buildConfigField` (QONVERSION_KEY, AD_UNIT_*, DEFAULT_SOURCE/TARGET_LANG, FEATURE_* toggles) · `src/<brand>/res` icon · (brand needs Firebase →) `src/<brand>/google-services.json`.
- **Config flow:** BuildConfig කියවන්නේ `:app` විතරයි → `@Provides AppConfig` → libs වලට ඒවායේම config types. FEATURE_* csv → typed set **once** + per-flavor matrix unit test (generic assertions — R3: per-brand golden .kt ✗).
- **R1 (audit, binding):** brand list එක **`.kt` file එකක ✗** — `:app/build.gradle.kts` flavor DSL block / data file (`brands.toml`) එකක. NIA enum pattern verbatim ගන්නේ නෑ (open-ended white-label ට වැරදි hosting).
- **R2 (audit, binding):** nav shell day-1 toggle-aware — camera entry `:app` nav registry එකේ පමණයි, `AppConfig.featureToggles` filter.
- Scaffold ships **brand 1** (§10 D-P1) — 2වෙනි brand simulation audit passed (`.kt` edit zero — evidence: workflow refute #3).
- **Brand 2 (future, owner-named 2026-07-21 comment #4):** `com.french.translator.free.english.traduction.offline` — owner ගේ **live Play app එකක්** ("French Translator - English", listing existence web-verified 2026-07-21). Flavor එක add කරන issue එකේදී listing එකෙන් details (name/icon direction/description) capture කරයි. Scaffold obligation: brand list + config seams ඒකට ready වීම පමණයි.

## 5. build-logic convention plugins (included build)

> **Owner directive (2026-07-21, chat):** project bootstrap එක **Android Studio new-project wizard conventions** වලටම — Kotlin DSL + `gradle/libs.versions.toml` catalog, AS-shape `settings.gradle.kts` (pluginManagement/dependencyResolutionManagement repos), AS-standard `gradle.properties` flags + `.gitignore`, committed Gradle wrapper, AS-style `:app` res scaffolding (adaptive launcher, themes, manifest shape) — repo එක Android Studio එකේ cleanly open වෙන ලෙස.

`tranzlate.android.application` (SDK 36/24, desugaring) · `.application.compose` · `.application.flavors` (brand+engine, declarative list) · `tranzlate.android.library` (+ per-module ns `com.codeboxlk.tranzlate.<ring>.<name>`) · `.library.compose` · `tranzlate.android.feature` (library+compose+hilt+standard deps — **අලුත් feature = 1 line**) · `tranzlate.android.room` · `tranzlate.jvm.library` · `tranzlate.hilt`. Versions: `gradle/libs.versions.toml` පමණයි. (Official patterns page + NIA build-logic README cited in workflow.)

## 6. Deterministic fake-engine variant (dimension "engine": `prod` | `fake`) — TEST contract §1.6/§1.10

**ඇයි flavor dimension (build type ✗, @TestInstallIn-only ✗):** `@TestInstallIn` installed APK එකට කවදාවත් compile වෙන්නේ නෑ (hilt-testing docs — verified) → Maestro (real APK) ට insufficient. NIA `demo` flavor = official precedent. Fake APK එකේ **real SDK කිසිවක් classpath එකේම නෑ** (classpath-absence guarantee): `:app` → `prodImplementation(:core:translate, :core:access, :core:usage, :core:ads)` vs `fakeImplementation(:core:translate-fake)` — `:lib:*` AARs transitively drop.

**Audit-mandated placements (compile-verified-by-trace):**
1. **`TranslateModule` (seams 4ම @Provides) = `:app/src/prod`** — real impls 4ම එකට පේන එකම classpath position. (`:core:translate` ownership claim contradiction — audit #2 resolution.)
2. **`SystemAppClock` + default dispatcher bindings = prod-side wiring** (`:core:common` main ✗) — fake variant duplicate-binding fail fix.
3. Contract §1.6 `@TestInstallIn(replaces=[TranslateModule::class])` wrapper = **`:app/src/androidTestProd`**; fake variants: `enableAndroidTest=false`.
4. `:core:translate-fake` = full surface (§2 Ring 3 last item).
5. Fake flavor brand applicationId **un-suffixed** (contract :305 Maestro appId match) — trade-off: real+fake co-install ✗; escape hatch `.fake` suffix documented, දැන් ✗.
6. **Guards:** `beforeVariants` → සියලු `fake`+`release` kill · CI classpath assertion (prod/release ⊅ `:core:testing`/`:core:translate-fake`) · Play publish tasks `*Prod*` variants only.
7. Maestro §1.10 steps 4–5 (at-limit/forced-failure) — contract-internal gap: fake seeding mechanism (launch extra / instrumentation arg) **Text-feature phase** එකේ resolve; scaffold obligation ✗ (flag only).

## 7. Navigation decision — Navigation 3

Nav3 **1.1.4 stable** + Google's stated recommendation for new Compose apps (verified 2026-07-21, releases/navigation3) → greenfield app එකට Nav3. `hiltViewModel()` ← `hilt-lifecycle-viewmodel-compose` 1.4.0 (nav2-coupled `hilt-navigation-compose` අවශ්‍ය නෑ). ListDetail pane navigation = stable `adaptive-navigation` 1.2.0 scaffold navigator (feature-internal) — pre-release `adaptive-navigation3` **අවශ්‍ය නෑ**. Risk: ecosystem younger than nav2 — mitigation: nav logic `:app` mediator එකේ isolated.

## 8. Architecture gates (scaffold PR එකේම ships)

- **Konsist tests:** Ring-2 JVM purity (hilt-core/javax.inject/coroutines-core only) · feature→brain-impl dep ban · `:lib:*` zero-project-deps · FROZEN package `com.codeboxlk.tranzlate.domain.translate`.
- **FROZEN markers:** ඉහත package + `tt_text_*` tags 17 (TEST contract :203) — rename = breaking-change PR + doc update.
- **Detekt + Spotless + CI** (GitHub Actions): build (JBR 21) + unit tests + Konsist + guards §6.6.
- **Rent-test triggers (plan-recorded, decision-gate reviewed):** `:core:usage` post-MVP <10 files → `:core:access` merge candidate · `:core:domain` recompile pain measurable → per-brain api split (named path) · `:feature:settings` Screen B growth → `:feature:offlinemodels` split.

## 9. Risks (workflow + audit)

| Risk | Mitigation |
|---|---|
| Variant matrix growth (brands × engine × build types) | `beforeVariants` filtering; CI fake = brand 1 only |
| GOT unofficial endpoint (D-E1 risk-accepted) break/ban | one internal class `:core:translate`; AUTO degrade path |
| Fake mis-publish look-alike (un-suffixed appId) | §6.6 triple guard |
| `:lib:ads` boundary erosion (D-4 leak → AAR) | one-home rule + review checklist item |
| TEST contract §1.1 `Engine{AUTO,ML2_MINI…}` vs C-9 naming drift | `:core:model` C-9 verbatim; contract doc fix = follow-up docs PR |
| `:core:translate-fake` JVM+hilt-core aggregation | verified pattern; fake needs Android API → Android-library convert (trivial) |
| Nav3 ecosystem maturity | isolated in `:app` mediator |

## 10. Owner decisions — RESOLVED (2026-07-21 · docs/plan/review/issue-3-comments.json)

- **D-P1 ✅ = A (comment #4):** applicationId **`com.codeboxlk.tranzlate.offlinetranslator`** (TEST contract :305 match; පරණ Play listing update-path open). Brand 2 future = French app (§4).
- **D-P2 ✅ provisional (comments #2/#4):** DESIGN_SYSTEM tokens = implementation baseline; **final palette → UI-design phase** (Claude Design), owner preference light/white GT-style (§3).
- **D-P3 ✅ accepted (comment #5):** "මුළු scaffold සැලැස්මම අනුමතයි — වැඩේ පටන් ගන්න".
- **C-2 owner re-confirmation (comment #1):** live-translate = free engines (MLKit/GOT/AUTO-free) පමණයි; **metered GCT (Advanced AI) = explicit Translate affordance** — API cost concern structural ලෙස already covered (C-2 + C-10: AUTO කිසිදා GCT ට නොවැටේ, keystroke එකකින් quota burn ✗). "Remove live entirely" alternative rejected — D-0 north star (GT-equal UX) supersedes (v1 button-only design explicitly superseded by DECISIONS).

### Follow-ups spawned by review
1. **UI-design phase issue (new, scaffold merge ට පසු):** Claude Design project එකක් create කර (DesignSync tool verified available), MVP screens mockups + palette finalize — owner reviews at claude.ai/design. Text feature implementation ඒ designs වලට පසුව.
2. DESIGN_TOKENS.md superseded-note + TEST contract §1.1 C-9 naming fix — docs PR.

## 11. Acceptance criteria (issue #3) + evidence plan

Issue #3 list per se + මෙහි §6 guards. **Definition of done:** `./gradlew build` green (JBR 21) · fake-variant APK installs + nav shell/theme demo · Konsist/Detekt/Spotless/CI green · co-verify lens ≠ author (PR gate — Rule 5; scaffold = medium-risk, cross-model optional).

**Evidence:** workflow `wf_7ca4d35c-99f` — 29 agents, 3 proposals (scores 78–91, winner unanimous 3/3), 3 adversarial audits (refuted=false ×3; mandatory amendments §4/§6 adopted), versions two-source (1 correction caught). Journal: session transcript dir.
