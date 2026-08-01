plugins {
    alias(libs.plugins.tranzlate.android.application)
    alias(libs.plugins.tranzlate.android.application.compose)
    alias(libs.plugins.tranzlate.android.application.flavors)
    alias(libs.plugins.tranzlate.android.application.signing)
    alias(libs.plugins.tranzlate.hilt)
    alias(libs.plugins.kotlin.serialization)
    // Reads app/google-services.json into the resources Firebase Remote Config
    // needs at runtime. Without it `FirebaseRemoteConfig.getInstance()` throws,
    // the source falls back to its defaults, and every credential arrives blank
    // — a paywall that cannot take money and legal links that go nowhere. The
    // failure is silent, so this line is load-bearing, not boilerplate.
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.codeboxlk.tranzlate"

    defaultConfig {
        // PLAY UPDATE TRACK (docs/plan/launch-signing-aab.md). The live listing under
        // applicationId com.codeboxlk.tranzlate.offlinetranslator last shipped
        // versionCode 4 / versionName "1.0.4" from the OLD repo, so an update must be
        // strictly greater: 5. versionName "1.1.0" (minor bump, not 1.0.5) because this
        // is the ground-up rebuild, not a patch on the old binary.
        // White-label note: version lineage is per Play listing = per applicationId, so
        // when a second brand lands it overrides these inside its own flavor block.
        versionCode = 5
        versionName = "1.1.0"
        testInstrumentationRunner = "com.codeboxlk.tranzlate.HiltTestRunner"
    }

    buildTypes {
        release {
            // Issue #5 (debate-ruled): R8 on, default-optimize file + ONE
            // justified project rule (persisted enums). Library consumer rules
            // (MLKit/OkHttp/Hilt/Room/Compose) cover the rest — no hand keeps.
            // signingConfig is attached by the `tranzlate.android.application.signing`
            // convention plugin when a gitignored keystore.properties is present;
            // without it the release variant stays UNSIGNED so CI's `gradlew build`
            // (which assembles tranzlateProdRelease, gating R8 on every PR) stays green.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    // ---------------------------------------------------------------------------------
    // WHITE-LABEL BRAND CATALOG (plan §4 R1): brands are DECLARATIVE DATA in this
    // build script — never a .kt file. Adding an app = adding one create("<brand>")
    // block here (+ src/<brand>/res icon), zero code edits.
    //
    // The `engine` dimension (prod|fake) is mechanics and lives in the flavors
    // convention plugin; fake carries NO applicationIdSuffix (plan §6.5 — Maestro
    // appId match; documented escape hatch: add ".fake" suffix if co-install is
    // ever needed).
    // ---------------------------------------------------------------------------------
    productFlavors {
        create("tranzlate") {
            dimension = "brand"
            applicationId = "com.codeboxlk.tranzlate.offlinetranslator" // D-P1 = A (owner)
            resValue("string", "app_name", "Tranzlate") // the ONE app_name mechanism (§4 R4)
            // Placeholder AdMob app id (sample-format, non-functional). NEVER Google
            // test ids in prod (old app's revenue-zero bug); real id lands per-brand
            // in the ads integration phase.
            val admobAppId = "ca-app-pub-0000000000000000~0000000000"
            manifestPlaceholders["admobAppId"] = admobAppId
            buildConfigField("String", "ADMOB_APP_ID", "\"$admobAppId\"")
            buildConfigField("String", "QONVERSION_KEY", "\"\"")
            buildConfigField("String", "GCT_API_KEY", "\"\"")
            buildConfigField("String", "AD_UNIT_BANNER", "\"\"")
            buildConfigField("String", "AD_UNIT_INTERSTITIAL", "\"\"")
            buildConfigField("String", "DEFAULT_SOURCE_LANG", "\"en\"")
            buildConfigField("String", "DEFAULT_TARGET_LANG", "\"fr\"")
            // Issue #90: wifi-only model downloads by default for this brand.
            buildConfigField("boolean", "DEFAULT_ALLOW_MOBILE_DATA", "false")
            buildConfigField("String", "FEATURES", "\"text,dialog,camera,history,settings\"")
        }
        // Brand 2 (future, plan §4 — owner's live "French Translator - English" app):
        // create("frenchtranslator") { dimension = "brand";
        //   applicationId = "com.french.translator.free.english.traduction.offline"; … }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// ---------------------------------------------------------------------------------
// R4-O7: the Fake variant must NOT initialise the LIVE Firebase project.
//
// The google-services plugin generates a `google_app_id` string resource for
// EVERY variant, and Firebase's `FirebaseInitProvider` (a ContentProvider merged
// into the manifest by the Firebase SDK) initialises the default FirebaseApp at
// process start from exactly that resource — before any code of ours runs, DI
// bindings notwithstanding. So the fake/Maestro APK, which binds
// `FakeConfigModule` and never touches `RemoteConfigSource`, was still
// auto-initialising the PRODUCTION Firebase project on every test launch.
//
// Disabling the per-variant `process<Variant>GoogleServices` task keeps the
// generated resource out of the fake variant's resource merge. Without a
// `google_app_id` resource, `FirebaseInitProvider` skips initialisation cleanly
// (FirebaseApp.initializeApp returns null and logs a warning — documented,
// non-fatal). Prod variants keep the task, and the credentials it feeds.
//
// Task names as of AGP/google-services in this tree (`:app:tasks --all`):
//   processTranzlateFakeDebugGoogleServices   ← disabled here
//   processTranzlateProdDebugGoogleServices   ← kept
//   processTranzlateProdReleaseGoogleServices ← kept
// Matched by pattern so a future brand flavor's Fake variants are covered too.
// ---------------------------------------------------------------------------------
tasks.configureEach {
    if (name.contains("Fake") && name.endsWith("GoogleServices")) {
        enabled = false
        // A machine that built a Fake variant BEFORE this guard existed still has
        // the generated resource on disk (build/generated/res/<taskName>/), a
        // disabled task never runs to replace it, and the resource merger — whose
        // input dir is then unchanged — stays UP-TO-DATE and keeps packaging the
        // live google_app_id silently. Deleting the stale output here makes
        // incremental builds converge without a manual clean. (Verified: the dir
        // is named after the task; deleting it invalidates the merge task.)
        project.layout.buildDirectory
            .dir("generated/res/$name")
            .get()
            .asFile
            .takeIf(File::exists)
            ?.deleteRecursively()
    }
}

dependencies {
    // Features (Ring 4)
    implementation(projects.feature.text)
    implementation(projects.feature.language)
    implementation(projects.feature.languagepicker)
    implementation(projects.feature.camera)
    implementation(projects.feature.history)
    implementation(projects.feature.settings)
    implementation(projects.feature.paywall)

    // Shared cores (engine-agnostic — both variants)
    implementation(projects.core.common)
    implementation(projects.core.config)
    implementation(projects.core.model)
    implementation(projects.core.domain)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(projects.core.data)
    implementation(projects.core.database)
    implementation(projects.core.datastore)

    // Engine dimension (plan §6): prod brains vs the fake binding module.
    // Classpath-absence guarantee — fake APKs carry no brain/:lib:* classes at all.
    "prodImplementation"(projects.core.translate)
    "prodImplementation"(libs.okhttp)
    "prodImplementation"(projects.core.access)
    "prodImplementation"(projects.core.usage)
    "prodImplementation"(projects.core.ads)
    "fakeImplementation"(projects.core.translateFake)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.kotlinx.serialization.core)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.konsist)
    testImplementation(projects.core.testing)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.hilt.android.testing)
    "kspAndroidTest"(libs.hilt.compiler)
    // Contract §1.6 wrapper (src/androidTestProd) binds the golden fakes.
    "androidTestProdImplementation"(projects.core.testing)
}
