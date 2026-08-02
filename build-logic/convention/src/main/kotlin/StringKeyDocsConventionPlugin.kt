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
 *
 * ## Where the scan comes from (issue #173, hole 2)
 *
 * This plugin shipped with four `include()` globs, one per named ring (`app`, `core`, `feature`,
 * `lib`), each assuming exactly `<ring>/<module>/src/<sourceSet>/res/values.../strings.xml`. That
 * encoded a guess about where modules live. PR #172's co-verify lens proved
 * that a `strings.xml` at a **new top-level prefix** (`bench/perf/…`) or **one level deeper under
 * an existing ring** (`core/data/local/…`, which is exactly what splitting `:core:data` would
 * produce) was never scanned: build green, printed count byte-identical with and without the file.
 * Reproduced again on this branch before the fix. A gate whose entire purpose is "never again
 * silently miss a key" cannot have a silent miss.
 *
 * So discovery is derived from the **registered module list** (`subprojects`) instead. A module
 * exists because `settings.gradle.kts` says so, and nothing else ships resources into an APK, so
 * the module list IS the scope rather than a guess about it. A new ring is scanned the moment it is
 * included, and there is no path shape left to fall outside of.
 *
 * Rejected alternatives, with the reason:
 *
 *  - **A whole-tree walk from the repo root.** This repo's standing convention puts agent worktrees
 *    at `.claude/worktrees/<name>` — full checkouts of this same repo, nested inside it. Measured
 *    while writing this: **48** `strings.xml` files under that directory, twice the 24 the repo
 *    itself owns. A root-anchored `**` walk would scan every one of them and fail this build over
 *    another branch's uncommitted key. `subprojects` rather than `allprojects` is deliberate for
 *    the same reason: the root project's directory IS the repo root.
 *  - **Asking AGP for each variant's real `res` source dirs.** More precise in theory, but it makes
 *    the gate depend on the AGP variant API, on cross-project configuration and on evaluation
 *    order. Issue #163 is a Konsist gate that was green locally and red in CI on one commit with
 *    the cause never diagnosed; the reason THIS task has been trustworthy is that it reads plain
 *    checkout files — no classpath, no AST, no variant model — so local green is evidence. That is
 *    not traded away for a layout this repo does not have.
 *
 * Inside a module the pattern is depth-agnostic rather than one fixed source-set depth, so every
 * source set (`main`, a flavour, a build type, a flavour+type combination), every locale and any
 * non-standard `res` srcDir is covered — and a nested module is covered twice over, once through
 * its own entry and once through its parent's tree. `FileCollection.getFiles()` is a Set, so the
 * duplicate collapses and the count is unaffected.
 *
 * What is still out of reach, stated rather than hidden: a `strings.xml` in a directory that is
 * part of no registered module. Nothing compiles it and no APK packages it, so it ships no key and
 * owes no catalogue row — and `build-logic` is outside the scan for the same reason, being an
 * included build rather than a subproject. `include()`-ing such a directory is what would make its
 * keys real, and that is the same act that puts it in this scan.
 *
 * The adjacent case, named by the #202 co-verify lens: a directory that WAS registered and scanned
 * and stops being either — an `include()` line deleted while the resources stay on disk. The count
 * would move from a plausible N to a plausible N-1 with nothing to notice. **This scan does not
 * defend against that; something else currently does.** Every resource-bearing module here is a
 * hardcoded `projects.x.y` accessor in `app/build.gradle.kts`, so removing its `include()` fails
 * Gradle's own project resolution before any task runs — the lens confirmed it by deleting
 * `:feature:history`'s line and getting `Unresolved reference 'history'`. That closure is a side
 * effect of how this repo wires dependencies, not a property asserted here. A resource-bearing
 * module depended upon by nothing would lose it.
 */
class StringKeyDocsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(BasePlugin::class.java)

            // Every project instance exists by the time any build script is evaluated — Gradle
            // creates the hierarchy from settings first — so this is the complete registered
            // module list, intermediate projects such as `:core` included.
            val modules = subprojects.sortedBy { it.path }
            val resources = modules.map { module ->
                fileTree(module.projectDir).apply {
                    include("**/res/values*/strings.xml")
                    // Test-only resources are not product copy and owe no catalogue row;
                    // build/ holds AGP's merged copies of the files this scan already reads.
                    exclude("**/src/test*/**", "**/src/androidTest*/**", "**/build/**")
                }
            }

            val catalogues = fileTree(projectDir)
            catalogues.include("docs/**/STRINGS_*.md")

            val verify = tasks.register<VerifyStringKeyDocsTask>(TASK_NAME) {
                group = LifecycleBasePlugin.VERIFICATION_GROUP
                description =
                    "Fails when a shipped <string>/<plurals> key is in no STRINGS_*.md catalogue (C-3)."
                resourceFiles.from(resources)
                catalogueFiles.from(catalogues)
                // Scope is evidence. #173 is a scan that was silently narrower than the repo, so
                // the list the scan was derived from is printed with the result and snapshotted
                // as an input — a ring that stops being scanned now changes the output.
                modulePaths.set(modules.map { it.path })
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
