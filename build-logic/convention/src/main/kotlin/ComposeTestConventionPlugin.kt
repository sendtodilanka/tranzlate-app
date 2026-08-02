import com.android.build.api.dsl.CommonExtension
import com.codeboxlk.tranzlate.buildlogic.RobolectricSdkArgumentProvider
import com.codeboxlk.tranzlate.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.newInstance

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
 * ## What a module gets
 *
 * One line — `alias(libs.plugins.tranzlate.compose.test)` — and then
 * `createComposeRule()` works in `src/test`. Same shape as `tranzlate.string-key-docs`
 * (#152): a named plugin applied where wanted, never a dependency block copied between
 * feature modules.
 *
 *  - `unitTests.isIncludeAndroidResources` — Robolectric reads the module's MERGED
 *    manifest and resources. Without it every `stringResource(...)` in a composable
 *    under test resolves to nothing, which is fatal for a11y tests whose whole subject
 *    is what a string announces.
 *  - `ui-test-junit4` on the test classpath, `ui-test-manifest` on `debugImplementation`
 *    — the second is not optional and not decorative: `createComposeRule()` launches an
 *    empty `ComponentActivity` that ONLY that artifact's manifest declares, and its
 *    absence surfaces as an activity-not-found at run time rather than as a compile
 *    error.
 *  - Robolectric's Android image pinned as a real dependency — see
 *    [RobolectricSdkArgumentProvider] for why that is about CI honesty and not about
 *    download speed.
 *
 * ## Which SDK the tests run against
 *
 * Nothing is pinned here on purpose. Robolectric takes its level from the merged
 * manifest's `targetSdkVersion`, which `TranzlateSdk.TARGET_SDK` fixes at 36 for every
 * module, so the tests run on the API the app targets rather than on a number a test
 * author picked. The pinned image must therefore keep up with `TARGET_SDK`: Robolectric
 * 4.16.1 supports up to exactly 36, so raising the target without raising Robolectric
 * fails loudly at the first test rather than quietly running on the wrong Android.
 * (`compileSdk` 37 does not enter into it — Robolectric never reads it.)
 */
class ComposeTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // Declaration and resolution split, per the Gradle 9 configuration roles —
            // `create` plus `isCanBeResolved` is the deprecated spelling of this.
            val declared = configurations.dependencyScope(SDK_DECLARATION)
            val resolvable =
                configurations.resolvable(SDK_CLASSPATH) {
                    extendsFrom(declared.get())
                }

            dependencies {
                add(SDK_DECLARATION, libs.findLibrary("robolectric-android-all").get())

                val bom = libs.findLibrary("androidx-compose-bom").get()
                "testImplementation"(platform(bom))
                "testImplementation"(libs.findLibrary("androidx-compose-ui-test-junit4").get())
                "testImplementation"(libs.findLibrary("robolectric").get())
                // The empty ComponentActivity `createComposeRule()` launches lives in
                // this artifact's manifest and nowhere else. `debugImplementation`
                // because unit tests build against the debug variant, and because a
                // test-only activity has no business in a release manifest.
                "debugImplementation"(libs.findLibrary("androidx-compose-ui-test-manifest").get())
            }

            val sdkArgs =
                objects.newInstance<RobolectricSdkArgumentProvider>().apply {
                    sdkJars.from(resolvable)
                }

            extensions.configure<CommonExtension> {
                testOptions.targetSdk = com.codeboxlk.tranzlate.buildlogic.TranzlateSdk.TARGET_SDK
                testOptions.unitTests.isIncludeAndroidResources = true
                testOptions.unitTests.all { test ->
                    test.jvmArgumentProviders.add(sdkArgs)
                }
            }
        }
    }

    private companion object {
        const val SDK_DECLARATION = "robolectricSdk"
        const val SDK_CLASSPATH = "robolectricSdkClasspath"
    }
}
