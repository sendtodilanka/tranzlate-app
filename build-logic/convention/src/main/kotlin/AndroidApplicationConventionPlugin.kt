import com.android.build.api.dsl.ApplicationExtension
import com.codeboxlk.tranzlate.buildlogic.TranzlateSdk
import com.codeboxlk.tranzlate.buildlogic.configureAndroidCommon
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** `tranzlate.android.application` — base app module setup (SDK 36/24, desugaring). */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            extensions.configure<ApplicationExtension> {
                configureAndroidCommon(this)
                defaultConfig.targetSdk = TranzlateSdk.TARGET_SDK
                defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                buildFeatures.buildConfig = true
            }
        }
    }
}
