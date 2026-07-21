// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
}

// ---- Quality gates (whole-repo, single home) ----------------------------------------------------

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    source.setFrom(
        files(
            fileTree(rootDir) {
                include("app/src/**/*.kt", "core/**/src/**/*.kt", "feature/**/src/**/*.kt", "lib/**/src/**/*.kt")
                exclude("**/build/**")
            },
        ),
    )
    parallel = true
}

spotless {
    kotlin {
        target("app/src/**/*.kt", "core/**/src/**/*.kt", "feature/**/src/**/*.kt", "lib/**/src/**/*.kt")
        targetExclude("**/build/**")
        ktlint()
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts", "core/**/*.gradle.kts", "feature/**/*.gradle.kts", "lib/**/*.gradle.kts", "build-logic/**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint()
    }
}
