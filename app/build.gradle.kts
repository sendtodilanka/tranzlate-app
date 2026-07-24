plugins {
    alias(libs.plugins.tranzlate.android.application)
    alias(libs.plugins.tranzlate.android.application.compose)
    alias(libs.plugins.tranzlate.android.application.flavors)
    alias(libs.plugins.tranzlate.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.codeboxlk.tranzlate"

    defaultConfig {
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "com.codeboxlk.tranzlate.HiltTestRunner"
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
            buildConfigField("String", "AD_UNIT_BANNER", "\"\"")
            buildConfigField("String", "AD_UNIT_INTERSTITIAL", "\"\"")
            buildConfigField("String", "DEFAULT_SOURCE_LANG", "\"en\"")
            buildConfigField("String", "DEFAULT_TARGET_LANG", "\"fr\"")
            buildConfigField("String", "FEATURES", "\"text,camera,history,settings\"")
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

dependencies {
    // Features (Ring 4)
    implementation(projects.feature.text)
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

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.hilt.android.testing)
    "kspAndroidTest"(libs.hilt.compiler)
    // Contract §1.6 wrapper (src/androidTestProd) binds the golden fakes.
    "androidTestProdImplementation"(projects.core.testing)
}
