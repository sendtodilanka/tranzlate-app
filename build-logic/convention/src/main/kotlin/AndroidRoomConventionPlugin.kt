import androidx.room.gradle.RoomExtension
import com.codeboxlk.tranzlate.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** `tranzlate.android.room` — Room + KSP + schema export. */
class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("androidx.room")
            pluginManager.apply("com.google.devtools.ksp")
            extensions.configure<RoomExtension> {
                schemaDirectory("$projectDir/schemas")
            }
            dependencies {
                "implementation"(libs.findLibrary("androidx-room-runtime").get())
                "implementation"(libs.findLibrary("androidx-room-ktx").get())
                "ksp"(libs.findLibrary("androidx-room-compiler").get())
            }
        }
    }
}
