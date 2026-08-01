import com.codeboxlk.tranzlate.buildlogic.VerifyStringKeyDocsTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.BasePlugin
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * `tranzlate.string-key-docs` — registers the whole-repo `verifyStringKeyDocs` C-3 gate on the
 * root project and hangs it off `check`, next to the detekt/spotless gates that already live
 * there.
 *
 * Why a Gradle task and not a Konsist test: Konsist reads the Kotlin AST, and this gate reads
 * resource XML plus Markdown, so Konsist would contribute a test runner and nothing else. It
 * would also inherit issue #163, where a Konsist gate passed locally and failed in CI on the
 * same commit with no diagnosis — local green would then not be evidence. And the natural home
 * for a Konsist gate, `app/src/test`, makes a repo-wide rule the `:app` module's private
 * business. A verification task with declared file inputs runs from `check` (so CI's
 * `./gradlew build` already covers it), runs identically on a developer machine, and is
 * up-to-date-checkable.
 */
class StringKeyDocsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(BasePlugin::class.java)

            // Same shape as the detekt/spotless source lists in the root build file: named
            // top-level rings rather than a walk of the whole working tree.
            val resources = fileTree(projectDir)
            resources.include(
                "app/src/*/res/values*/strings.xml",
                "core/*/src/*/res/values*/strings.xml",
                "feature/*/src/*/res/values*/strings.xml",
                "lib/*/src/*/res/values*/strings.xml",
            )
            // Test-only resources are not product copy and owe no catalogue row.
            resources.exclude("**/src/test*/**", "**/src/androidTest*/**", "**/build/**")

            val catalogues = fileTree(projectDir)
            catalogues.include("docs/**/STRINGS_*.md")

            val verify = tasks.register<VerifyStringKeyDocsTask>(TASK_NAME) {
                group = LifecycleBasePlugin.VERIFICATION_GROUP
                description =
                    "Fails when a shipped <string>/<plurals> key is in no STRINGS_*.md catalogue (C-3)."
                resourceFiles.from(resources)
                catalogueFiles.from(catalogues)
                reportFile.set(layout.buildDirectory.file("reports/string-key-docs/report.txt"))
                rootDirPath.set(projectDir.absolutePath)
            }

            tasks.named<org.gradle.api.Task>(LifecycleBasePlugin.CHECK_TASK_NAME) {
                dependsOn(verify)
            }
        }
    }

    private companion object {
        const val TASK_NAME = "verifyStringKeyDocs"
    }
}
