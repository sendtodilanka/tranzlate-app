import com.codeboxlk.tranzlate.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * `tranzlate.compose-test` — a Compose test rule that runs in the ordinary unit-test
 * task, so a decision inside a `@Composable` stops being a decision no test can reach
 * (#186).
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
 */
class ComposeTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("tranzlate.robolectric")

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
            }
        }
    }
}
