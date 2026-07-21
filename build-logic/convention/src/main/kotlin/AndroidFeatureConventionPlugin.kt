import com.codeboxlk.tranzlate.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/**
 * `tranzlate.android.feature` — a new feature module = 1 line (plan §5).
 * Features depend on contracts (`:core:domain`/`:core:model`) + designsystem/ui only —
 * never on brain impl modules (plan §2 dependency rules; Konsist-gated §8).
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("tranzlate.android.library")
            pluginManager.apply("tranzlate.android.library.compose")
            pluginManager.apply("tranzlate.hilt")
            dependencies {
                "implementation"(project(":core:common"))
                "implementation"(project(":core:model"))
                "implementation"(project(":core:domain"))
                "implementation"(project(":core:designsystem"))
                "implementation"(project(":core:ui"))
                "implementation"(libs.findLibrary("androidx-lifecycle-runtime-compose").get())
                "implementation"(libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
                "implementation"(libs.findLibrary("androidx-hilt-lifecycle-viewmodel-compose").get())
            }
        }
    }
}
