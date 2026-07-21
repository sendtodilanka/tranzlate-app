import com.codeboxlk.tranzlate.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * `tranzlate.hilt` — KSP + Hilt.
 * Android modules get the full Hilt Gradle plugin + `hilt-android`;
 * pure-JVM modules (e.g. `:core:translate-fake`) get `hilt-core` only (plan §9 verified pattern).
 */
class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            dependencies {
                "ksp"(libs.findLibrary("hilt-compiler").get())
            }
            pluginManager.withPlugin("com.android.base") {
                pluginManager.apply("com.google.dagger.hilt.android")
                dependencies {
                    "implementation"(libs.findLibrary("hilt-android").get())
                }
            }
            pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
                dependencies {
                    "implementation"(libs.findLibrary("hilt-core").get())
                }
            }
        }
    }
}
