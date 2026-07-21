import com.android.build.api.dsl.LibraryExtension
import com.codeboxlk.tranzlate.buildlogic.configureAndroidCommon
import com.codeboxlk.tranzlate.buildlogic.defaultTranzlateNamespace
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * `tranzlate.android.library` — base Android library setup with per-module default
 * namespace `com.codeboxlk.tranzlate.<ring>.<name>` (plan §5). `:lib:*` modules
 * override the namespace in their own build files.
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            extensions.configure<LibraryExtension> {
                configureAndroidCommon(this)
                namespace = defaultTranzlateNamespace()
                defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
        }
    }
}
