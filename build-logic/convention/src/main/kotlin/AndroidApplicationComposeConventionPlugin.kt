import com.android.build.api.dsl.ApplicationExtension
import com.codeboxlk.tranzlate.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** `tranzlate.android.application.compose` — Compose for the app module (BOM-managed). */
class AndroidApplicationComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            extensions.configure<ApplicationExtension> {
                buildFeatures.compose = true
            }
            dependencies {
                val bom = libs.findLibrary("androidx-compose-bom").get()
                "implementation"(platform(bom))
                "implementation"(libs.findLibrary("androidx-compose-ui").get())
                "implementation"(libs.findLibrary("androidx-compose-material3").get())
                "implementation"(libs.findLibrary("androidx-compose-ui-tooling-preview").get())
                "debugImplementation"(libs.findLibrary("androidx-compose-ui-tooling").get())
                "debugImplementation"(libs.findLibrary("androidx-compose-ui-test-manifest").get())
                "androidTestImplementation"(platform(bom))
                "androidTestImplementation"(libs.findLibrary("androidx-compose-ui-test-junit4").get())
            }
        }
    }
}
