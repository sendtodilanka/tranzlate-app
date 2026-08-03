import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.HasDeviceTests
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.configure
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * `tranzlate.preflight` — ONE task name for the gate every brief repeats (#253).
 *
 * ## The problem it exists for
 *
 * The gate this project hands out is a five-command line copied into
 * `.claude/skills/land-pr/SKILL.md`, `.claude/agents/co-verify-lens.md` and every agent
 * brief:
 *
 * ```
 * ./gradlew test :app:assembleTranzlateProdDebug :app:assembleTranzlateFakeDebug spotlessCheck detekt
 * ```
 *
 * **None of those five compiles an `androidTest` source set.** Measured on
 * `origin/main` with a deliberate `Unresolved reference` planted in
 * `app/src/androidTestProd/`: BUILD SUCCESSFUL in 20s, exit 0. It bit for real in #246,
 * where the author only knew their change compiled because they remembered a second
 * command. And `:core:database`'s androidTest is worse — `./gradlew build`,
 * `./gradlew :core:database:build` and `:app:assembleAndroidTest` (the #148 CI guard,
 * which is `:app`-scoped) are **all three green** over a broken file there (#241).
 *
 * A list that lives in prose is a list every brief has to copy correctly, and #246
 * proves what happens when one does not. This task moves it into the build.
 *
 * ## Why `preflight` and not `verifyAll`
 *
 * #253 suggested `verifyAll`. That name asserts totality and would have been false on
 * the day it shipped — it does not RUN instrumentation tests (#40; no emulator in CI)
 * and does not analyse `build-logic`'s own Kotlin (#210). Naming a gate for coverage it
 * does not have is the exact failure this task exists to fix, one level out.
 * `preflight` says *when* to run it, which cannot go stale, and [DESCRIPTION] states
 * both exclusions by issue number so a green run is not read as more than it is.
 *
 * ## Everything it depends on is DERIVED
 *
 * The #173/#201 lesson, one level out: a gate whose scope is a hardcoded list is a gate
 * that silently misses whatever the list forgot.
 *
 *  - **Unit tests** — every subproject that has a build file. A Gradle project without
 *    one has no tasks; the three container projects `:core`, `:feature` and `:lib` are
 *    exactly those.
 *  - **androidTest compilation** — every device-test component AGP reports through
 *    [HasDeviceTests], on every Android module. Not a source-directory check: a module
 *    that gains `src/androidTest/` tomorrow, or a second white-label brand flavour that
 *    brings its own androidTest variant, is covered without anyone editing this file.
 *  - **App packaging** — every debug variant of every application module, from the same
 *    variant callback, replacing the two hand-named `:app:assembleTranzlate*Debug`
 *    entries in the old prose list.
 *
 * ## Why it COMPILES androidTest instead of assembling it
 *
 * Measured, not preferred. `./gradlew assembleAndroidTest` across all 19 Android
 * modules **fails**: `:core:ui:mergeExtDexDebugAndroidTest` →
 * `D8: java.lang.OutOfMemoryError: Java heap space`, at this repo's `-Xmx4g`, after 57s.
 * Dexing and packaging nineteen test APKs is unaffordable and beside the point — what
 * the gate must catch is that androidTest sources still COMPILE, which is the whole
 * class #148 was filed for (a missing binding, a retired signature, a fake that no
 * longer implements its interface).
 *
 * `compile<Component>Sources` is AGP's per-component anchor and it pulls the Kotlin
 * compilation in — verified from the task graph rather than assumed:
 *
 * ```
 * $ ./gradlew :core:database:compileDebugAndroidTestSources --dry-run
 * :core:database:kspDebugAndroidTestKotlin SKIPPED
 * :core:database:compileDebugAndroidTestKotlin SKIPPED
 * :core:database:compileDebugAndroidTestJavaWithJavac SKIPPED
 * :core:database:compileDebugAndroidTestSources SKIPPED
 * ```
 *
 * `:app`'s androidTest APK is still ASSEMBLED in CI by the untouched #148 step, so
 * nothing that ran before this task stops running.
 *
 * ## Task paths as strings, deliberately
 *
 * `dependsOn(":core:database:test")` is resolved when Gradle builds the execution
 * graph — after every project is evaluated — so this plugin does not have to reach into
 * an unevaluated project's task container. A module that somehow lacks one of these
 * tasks fails loudly with the task path in the message, which is the right direction
 * for a gate to fail in.
 */
class PreflightConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        if (target != target.rootProject) {
            throw GradleException(
                "tranzlate.preflight is a whole-repo gate and belongs on the root project; " +
                    "it was applied to ${target.path}.",
            )
        }

        val preflight =
            target.tasks.register(TASK_NAME) {
                group = LifecycleBasePlugin.VERIFICATION_GROUP
                description = DESCRIPTION
                // The three whole-repo gates, all registered on the root project:
                // the C-3 string-key authority check (#152/#173) and the two linters
                // whose scope was derived from `subprojects` by #201.
                dependsOn("verifyStringKeyDocs", "detekt", "spotlessCheck")
            }

        // A subproject with no build file is a container Gradle created from a path in
        // settings.gradle.kts (`:core`, `:feature`, `:lib`) — no plugins, no tasks.
        val modules = target.subprojects.filter { it.buildFile.exists() }
        preflight.configure { dependsOn(modules.map { "${it.path}:$UNIT_TEST_TASK" }) }

        modules.forEach { module ->
            module.plugins.withId("com.android.application") {
                module.extensions.configure<ApplicationAndroidComponentsExtension> {
                    onVariants { variant ->
                        preflight.dependOnDeviceTestCompilation(module, variant)
                        // Both white-label brands must still package. `buildType`
                        // rather than a variant-name pattern: a brand added to the
                        // flavor catalog is picked up with no edit here, and the
                        // release variants stay out (they are `./gradlew build`'s
                        // job, and R8 is too slow for a pre-PR gate).
                        if (variant.buildType == DEBUG_BUILD_TYPE) {
                            preflight.configure {
                                dependsOn("${module.path}:assemble${variant.name.taskSuffix()}")
                            }
                        }
                    }
                }
            }
            module.plugins.withId("com.android.library") {
                module.extensions.configure<LibraryAndroidComponentsExtension> {
                    onVariants { variant ->
                        preflight.dependOnDeviceTestCompilation(module, variant)
                    }
                }
            }
        }
    }

    /**
     * Adds `compile<Component>Sources` for every device-test component [variant]
     * declares. [HasDeviceTests.deviceTests] is a map because AGP allows more than one
     * device-test suite per variant; taking all of them is what keeps this derived
     * rather than a guess that `androidTest` is the only one.
     */
    private fun TaskProvider<*>.dependOnDeviceTestCompilation(module: Project, variant: Any) {
        val deviceTests = (variant as? HasDeviceTests)?.deviceTests?.values.orEmpty()
        deviceTests.forEach { deviceTest ->
            configure { dependsOn("${module.path}:compile${deviceTest.name.taskSuffix()}Sources") }
        }
    }

    private companion object {
        const val TASK_NAME = "preflight"
        const val UNIT_TEST_TASK = "test"
        const val DEBUG_BUILD_TYPE = "debug"

        const val DESCRIPTION =
            "The gate to run before opening a PR: string-key catalogue, detekt, spotless, " +
                "every module's unit tests, every androidTest source set's COMPILATION, and " +
                "both debug APKs. Does NOT run instrumentation tests (#40) and does NOT " +
                "analyse build-logic's own Kotlin (#210)."

        /** `debugAndroidTest` -> `DebugAndroidTest`, for the AGP task-name convention. */
        fun String.taskSuffix(): String = replaceFirstChar(Char::uppercaseChar)
    }
}
