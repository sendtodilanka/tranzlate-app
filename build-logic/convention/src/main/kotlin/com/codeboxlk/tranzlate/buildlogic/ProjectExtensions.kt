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

/** SDK levels — plan §1 (compileSdk 36 / targetSdk 36 / minSdk 24). */
object TranzlateSdk {
    const val COMPILE_SDK = 36
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
        compileOptions {
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
