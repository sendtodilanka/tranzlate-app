import com.android.build.api.dsl.CommonExtension
import com.codeboxlk.tranzlate.buildlogic.RobolectricSdkArgumentProvider
import com.codeboxlk.tranzlate.buildlogic.TranzlateSdk
import com.codeboxlk.tranzlate.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.newInstance

/**
 * `tranzlate.robolectric` — a JVM Android runtime in the ordinary unit-test task, with
 * **no Compose in it** (#231).
 *
 * ## Why this is its own plugin
 *
 * All of this lived inside `tranzlate.compose-test`, which was correct for what #186
 * built it for and wrong as the project's only door to Robolectric. `:core:database`
 * has no Compose in it and applied the Compose plugin anyway, because that was the only
 * way to get a JVM Android test runtime — and the coupling showed itself immediately:
 * the first non-Compose module to apply it failed dependency resolution with
 * `Could not find androidx.compose.ui:ui-test-manifest:`, an unversioned coordinate
 * that had been resolving by accident off the Compose convention plugin's BOM.
 *
 * A module that wants SQLite, `Context`, or merged resources under JUnit now takes this
 * one, and `tranzlate.compose-test` applies it and adds the Compose half on top. The
 * wiring still exists exactly once, which is the property its own KDoc says it was
 * written to protect.
 *
 * ## What a module gets
 *
 *  - `unitTests.isIncludeAndroidResources` — Robolectric reads the module's MERGED
 *    manifest and resources. Without it every `stringResource(...)` resolves to nothing,
 *    and a Room database built from an asset schema cannot find the asset.
 *  - Robolectric's Android image pinned as a real dependency — see
 *    [RobolectricSdkArgumentProvider] for why that is about CI honesty and not about
 *    download speed.
 *
 * ## Which SDK the tests run against
 *
 * Nothing is pinned here on purpose. Robolectric takes its level from the merged
 * manifest's `targetSdkVersion`, which [TranzlateSdk.TARGET_SDK] fixes at 36 for every
 * module, so the tests run on the API the app targets rather than on a number a test
 * author picked. The pinned image must therefore keep up with `TARGET_SDK`: Robolectric
 * 4.16.1 supports up to exactly 36, so raising the target without raising Robolectric
 * fails loudly at the first test rather than quietly running on the wrong Android.
 * (`compileSdk` 37 does not enter into it — Robolectric never reads it.)
 */
class RobolectricConventionPlugin : Plugin<Project> {
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
                "testImplementation"(libs.findLibrary("robolectric").get())
            }

            val sdkArgs =
                objects.newInstance<RobolectricSdkArgumentProvider>().apply {
                    sdkJars.from(resolvable)
                }

            extensions.configure<CommonExtension> {
                testOptions.targetSdk = TranzlateSdk.TARGET_SDK
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
