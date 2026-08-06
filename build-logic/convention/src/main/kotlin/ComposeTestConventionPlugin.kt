import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.codeboxlk.tranzlate.buildlogic.libs
import io.github.takahirom.roborazzi.RoborazziExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * `tranzlate.compose-test` — a Compose test rule that runs in the ordinary unit-test
 * task, so a decision inside a `@Composable` stops being a decision no test can reach
 * (#186) — and, on top of it, a JVM screenshot-diff harness that LOCKS what those
 * composables draw (#333).
 *
 * ## Why this exists
 *
 * Before this plugin, the only `createComposeRule` in the repo lived in
 * `app/src/androidTestProd/`, CI compiled that source set without running it (#148),
 * and running it at all is blocked by #40. So for anything a composable DECIDES, the
 * strongest tool an author had was a source-shape assertion over the function's text —
 * which verifies that code was written and never what it does. #186 lists what that
 * cost: a co-verify lens deleted half a row-height condition and
 * `:feature:language:testDebugUnitTest` stayed BUILD SUCCESSFUL with zero failures;
 * #193 lists three separate gates defeated by a rename in a single day.
 *
 * Robolectric closes that gap at unit speed and without a device, which is the whole
 * point of not waiting for #40 — #186 names that dependency as the thing that kept the
 * issue open. This plugin deliberately does NOT make the instrumented suite run; that
 * is still #40's job.
 *
 * ## What it is now that [RobolectricConventionPlugin] exists (#231)
 *
 * **The Compose half, and only the Compose half.** The JVM Android runtime underneath —
 * the pinned offline SDK image, `unitTests.isIncludeAndroidResources`, the Robolectric
 * dependency itself — moved to `tranzlate.robolectric`, which this applies. Nothing a
 * module got from this plugin before has gone away; the difference is that a module
 * with **no Compose in it** can now take the runtime alone instead of inheriting a
 * Compose dependency graph it has no use for. `:core:database` was the module that made
 * that visible, and it is the reason #231 was filed.
 *
 * ## What a module gets on top of the runtime
 *
 * One line — `alias(libs.plugins.tranzlate.compose.test)` — and then
 * `createComposeRule()` works in `src/test`. Same shape as `tranzlate.string-key-docs`
 * (#152): a named plugin applied where wanted, never a dependency block copied between
 * feature modules.
 *
 *  - `ui-test-junit4` on the test classpath, `ui-test-manifest` on `debugImplementation`
 *    — the second is not optional and not decorative: `createComposeRule()` launches an
 *    empty `ComponentActivity` that ONLY that artifact's manifest declares, and its
 *    absence surfaces as an activity-not-found at run time rather than as a compile
 *    error.
 *
 * ## Screenshot diff (Roborazzi, #333)
 *
 * The same `createComposeRule` a module already has becomes a screenshot recorder:
 * `compose.onRoot().captureRoboImage("src/test/screenshots/<name>.png")` renders the
 * composable under Robolectric Native Graphics and, in verify mode, compares it to the
 * committed golden. This plugin supplies the three pieces a module needs and nothing it
 * does not:
 *
 *  - **The plugin + capture deps.** `io.github.takahirom.roborazzi` is applied here, and
 *    `roborazzi` / `roborazzi-compose` / `roborazzi-junit-rule` go on `testImplementation`
 *    — the same opt-in-by-one-line shape the rest of this plugin keeps. A compose-test
 *    module with no screenshot test pays nothing at run time: `captureRoboImage()`
 *    early-returns when no record/verify run is active (`Roborazzi.kt`), so the plain
 *    `test`/`preflight` task is a no-op for it.
 *  - **A stable, committed golden dir.** [RoborazziExtension.outputDir] is pinned to the
 *    module's `src/test/screenshots`, so goldens live in source control next to the test
 *    and survive `clean` — never under `build/`, which is `.gitignore`d. Record and
 *    verify both resolve the SAME path from the test's explicit argument, so the two can
 *    never read and write different files.
 *  - **`./gradlew build` fails on a mismatch.** [wireDebugVerifyIntoCheck] makes `check`
 *    depend on `verifyRoborazzi<DebugVariant>`, whose presence in the task graph flips
 *    `testDebugUnitTest` into verify mode (Roborazzi's own wiring), so a pixel change to
 *    a locked screen fails `build` — the enforceable, server-side regression lock CI
 *    already runs (`./gradlew build`). A client hook could not enforce this and is not
 *    added. `preflight` (plain `test`) stays a no-op, by design: it is not the
 *    screenshot gate.
 */
class ComposeTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("tranzlate.robolectric")
            pluginManager.apply("io.github.takahirom.roborazzi")

            dependencies {
                val bom = libs.findLibrary("androidx-compose-bom").get()
                "testImplementation"(platform(bom))
                "testImplementation"(libs.findLibrary("androidx-compose-ui-test-junit4").get())
                // The empty ComponentActivity `createComposeRule()` launches lives in
                // this artifact's manifest and nowhere else. `debugImplementation`
                // because unit tests build against the debug variant, and because a
                // test-only activity has no business in a release manifest.
                //
                // The BOM goes on this configuration too, and it is not redundant.
                // Every module that applied this plugin until #130 PR-19 was also a
                // Compose module, so the Compose convention plugin had already put
                // the BOM on `implementation` and the version resolved by accident.
                // The first module without Compose failed at dependency resolution
                // with "Could not find androidx.compose.ui:ui-test-manifest:" — an
                // unversioned coordinate, which is what this line supplies. Applying
                // it twice is a no-op for the modules that already had it.
                "debugImplementation"(platform(bom))
                "debugImplementation"(libs.findLibrary("androidx-compose-ui-test-manifest").get())

                // Roborazzi screenshot-diff (#333) — the capture half on top of the
                // Compose test rule. `roborazzi` carries the
                // `SemanticsNodeInteraction.captureRoboImage()` used with
                // `createComposeRule`; the other two are the composable-content form and
                // the optional `RoborazziRule`.
                "testImplementation"(libs.findLibrary("roborazzi").get())
                "testImplementation"(libs.findLibrary("roborazzi-compose").get())
                "testImplementation"(libs.findLibrary("roborazzi-junit-rule").get())
            }

            // Goldens live in source control, next to the test, and survive `clean`.
            extensions.configure<RoborazziExtension> {
                outputDir.set(layout.projectDirectory.dir(ROBORAZZI_GOLDEN_DIR))
            }

            wireDebugVerifyIntoCheck()
        }
    }

    /**
     * Make `check` (hence `./gradlew build`, hence CI) depend on the DEBUG variant's
     * `verifyRoborazzi<Variant>` task, so a golden mismatch fails the build.
     *
     * Derived from the variant graph and filtered to `buildType == "debug"`, the same
     * brand-agnostic filter [PreflightConventionPlugin] applies to `assemble` and
     * `lint`: a white-label brand added to the flavor catalog is covered with no edit
     * here, and the release variants stay out — Roborazzi never runs both a debug and a
     * release verify in one invocation, which is what would otherwise share one output
     * directory and hit the Gradle-9 race the project warns about (roborazzi #830).
     *
     * Debug-only is also why `preflight` stays green regardless of goldens: it runs the
     * plain `test` task, whose graph contains no `verifyRoborazzi*`, so
     * `captureRoboImage()` early-returns. The lock lives on `build`, deliberately.
     */
    private fun Project.wireDebugVerifyIntoCheck() {
        plugins.withId("com.android.library") {
            extensions.configure<LibraryAndroidComponentsExtension> {
                onVariants { variant -> dependCheckOnDebugVerify(variant.name, variant.buildType) }
            }
        }
        plugins.withId("com.android.application") {
            extensions.configure<ApplicationAndroidComponentsExtension> {
                onVariants { variant -> dependCheckOnDebugVerify(variant.name, variant.buildType) }
            }
        }
    }

    private fun Project.dependCheckOnDebugVerify(variantName: String, buildType: String?) {
        if (buildType != DEBUG_BUILD_TYPE) return
        val verifyTask = VERIFY_TASK_PREFIX + variantName.replaceFirstChar(Char::uppercaseChar)
        tasks.named(CHECK_TASK).configure { dependsOn(verifyTask) }
    }

    private companion object {
        /** Where committed goldens live, resolved against each module's project dir. */
        const val ROBORAZZI_GOLDEN_DIR = "src/test/screenshots"
        const val CHECK_TASK = "check"
        const val DEBUG_BUILD_TYPE = "debug"

        /** `verifyRoborazzi` + the capitalized variant name, e.g. `verifyRoborazziDebug`. */
        const val VERIFY_TASK_PREFIX = "verifyRoborazzi"
    }
}
