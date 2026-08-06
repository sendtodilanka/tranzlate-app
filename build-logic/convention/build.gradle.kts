plugins {
    `kotlin-dsl`
}

group = "com.codeboxlk.tranzlate.buildlogic"

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.compiler.gradle.plugin)
    compileOnly(libs.ksp.gradle.plugin)
    compileOnly(libs.room.gradle.plugin)
    compileOnly(libs.hilt.android.gradle.plugin)
    // Roborazzi is `implementation`, not `compileOnly` like the plugins above — a
    // deliberate difference (#333). Those plugins are declared `apply false` in the root
    // `build.gradle.kts`, so they reach every module's buildscript classpath from there
    // and a convention plugin only needs to COMPILE against them. Roborazzi is instead
    // applied programmatically inside `ComposeTestConventionPlugin`, which also configures
    // its `RoborazziExtension`; both need the plugin on build-logic's RUNTIME classpath.
    // `implementation` keeps the whole screenshot wiring inside `build-logic/**` and off
    // the shared root build file, rather than adding a root `apply false` line for a
    // plugin no module applies by alias.
    implementation(libs.roborazzi.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "tranzlate.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidApplicationCompose") {
            id = "tranzlate.android.application.compose"
            implementationClass = "AndroidApplicationComposeConventionPlugin"
        }
        register("androidApplicationFlavors") {
            id = "tranzlate.android.application.flavors"
            implementationClass = "AndroidApplicationFlavorsConventionPlugin"
        }
        register("androidApplicationSigning") {
            id = "tranzlate.android.application.signing"
            implementationClass = "AndroidApplicationSigningConventionPlugin"
        }
        register("androidLibrary") {
            id = "tranzlate.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "tranzlate.android.library.compose"
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "tranzlate.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("androidRoom") {
            id = "tranzlate.android.room"
            implementationClass = "AndroidRoomConventionPlugin"
        }
        register("jvmLibrary") {
            id = "tranzlate.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("hilt") {
            id = "tranzlate.hilt"
            implementationClass = "HiltConventionPlugin"
        }
        register("stringKeyDocs") {
            id = "tranzlate.string-key-docs"
            implementationClass = "StringKeyDocsConventionPlugin"
        }
        register("robolectric") {
            id = "tranzlate.robolectric"
            implementationClass = "RobolectricConventionPlugin"
        }
        register("composeTest") {
            id = "tranzlate.compose-test"
            implementationClass = "ComposeTestConventionPlugin"
        }
        register("preflight") {
            id = "tranzlate.preflight"
            implementationClass = "PreflightConventionPlugin"
        }
    }
}
