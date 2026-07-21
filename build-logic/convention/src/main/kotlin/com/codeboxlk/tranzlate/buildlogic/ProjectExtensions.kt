package com.codeboxlk.tranzlate.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/** Version catalog accessor for convention plugins. */
val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/**
 * SDK levels — plan §1 pinned targetSdk 36 / minSdk 24.
 * compileSdk DEVIATION (recorded): plan pinned 36, but the pinned androidx.hilt
 * 1.4.0 and lifecycle 2.11.0 AAR metadata REQUIRE compileSdk >= 37 (AGP
 * checkAarMetadata, verified in-build). AGP 9.3 supports max API 37 (official
 * release notes), so compileSdk = 37 rather than downgrading two pinned
 * libraries. targetSdk stays 36 (Play new-app target-36 deadline 2026-08-31).
 */
object TranzlateSdk {
    const val COMPILE_SDK = 37
    const val TARGET_SDK = 36
    const val MIN_SDK = 24
}

/**
 * Common Android configuration shared by application + library convention plugins.
 * coreLibraryDesugaring is mandatory (plan §1 last row): minSdk 24 + `AppClock`/java.time.
 */
internal fun Project.configureAndroidCommon(extension: CommonExtension) {
    extension.apply {
        compileSdk = TranzlateSdk.COMPILE_SDK
        defaultConfig.minSdk = TranzlateSdk.MIN_SDK
        with(compileOptions) {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
            // Built-in Kotlin (AGP 9) derives jvmTarget from targetCompatibility.
            isCoreLibraryDesugaringEnabled = true
        }
    }
    dependencies.add("coreLibraryDesugaring", libs.findLibrary("desugar-jdk-libs").get())
}

/**
 * Default per-module namespace `com.codeboxlk.tranzlate.<ring>.<name>` (plan §5).
 * `:lib:*` modules override this in their own build files (own AAR namespaces).
 */
internal fun Project.defaultTranzlateNamespace(): String {
    val segments = path.split(":")
        .filter { it.isNotBlank() }
        .map { it.replace("-", "").lowercase() }
    return "com.codeboxlk.tranzlate." + segments.joinToString(".")
}
