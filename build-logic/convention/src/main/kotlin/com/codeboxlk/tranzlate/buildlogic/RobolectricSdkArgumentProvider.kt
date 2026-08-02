package com.codeboxlk.tranzlate.buildlogic

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.Classpath
import org.gradle.process.CommandLineArgumentProvider

/**
 * Hands the unit-test JVM Robolectric's Android runtime image as a jar Gradle
 * already resolved, and switches Robolectric's own downloader off.
 *
 * Robolectric normally fetches `android-all-instrumented` from Maven Central the
 * first time a test runs, into a cache of its own that has nothing to do with
 * Gradle. That is 213MB per cold machine, and — the part that matters here — it
 * is a dependency resolved OUTSIDE the build. A warm developer machine has it
 * and a CI runner does not, which is the exact shape of #163: local green that
 * was not evidence of anything. Declaring the jar in the version catalog puts it
 * in `modules-2`, where the repo's existing `gradle/actions/setup-gradle` cache
 * already restores it, and `robolectric.offline=true` removes the fallback so a
 * wrong pin fails loudly rather than downloading its way out of trouble.
 *
 * A [CommandLineArgumentProvider] rather than `Test.systemProperty(...)`, for two
 * reasons that are both correctness rather than style:
 *  - `systemProperty` takes a plain `Object` and stringifies it, so a lazy
 *    `Provider` would arrive as `provider(?)` instead of a path. Resolving the
 *    configuration eagerly at configuration time to avoid that is what the
 *    configuration cache (`org.gradle.configuration-cache=true`, on in this
 *    repo) exists to forbid.
 *  - [sdkJars] is a declared task input, so changing the pinned image re-runs the
 *    tests instead of silently reusing an up-to-date result computed against a
 *    different Android.
 *
 * [Classpath] normalization, not `@InputFiles`: only the jar's contents decide
 * anything here, and its absolute path differs between every machine and CI
 * runner — path sensitivity would make the build cache useless for exactly the
 * runners it is meant to help.
 */
abstract class RobolectricSdkArgumentProvider : CommandLineArgumentProvider {
    /** The single resolved `org.robolectric:android-all-instrumented` artifact. */
    @get:Classpath
    abstract val sdkJars: ConfigurableFileCollection

    override fun asArguments(): Iterable<String> {
        // `single`, deliberately, and with a message that names the cause. In
        // offline mode Robolectric reads ONE directory and builds the file name
        // itself (`LocalDependencyResolver.getLocalArtifactUrl`:
        // `<artifactId>-<version>.jar`), so two jars in that directory means the
        // pin resolved to something unintended, and zero means the configuration
        // resolved empty. Both are silent in a `firstOrNull`.
        val jars = sdkJars.files
        require(jars.size == 1) {
            "Expected exactly one android-all-instrumented jar for Robolectric, found ${jars.size}: " +
                jars.joinToString { it.name }
        }
        return listOf(
            "-Drobolectric.offline=true",
            "-Drobolectric.dependency.dir=${jars.single().parentFile.absolutePath}",
        )
    }
}
