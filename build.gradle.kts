// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    // Declared here so :app only needs the one-line `alias(...)` in its own
    // plugins block. Library modules must NOT apply it — see the catalog note.
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
    // Whole-repo C-3 gate: a shipped string key with no STRINGS_*.md row fails the build.
    id("tranzlate.string-key-docs")
    // `./gradlew preflight` — the one gate name (#253). The list of what it runs lives
    // in PreflightConventionPlugin, not in prose every brief has to copy correctly.
    id("tranzlate.preflight")
}

// ---- Quality gates (whole-repo, single home) ----------------------------------------------------
//
// WHERE THESE GATES LOOK, AND WHY IT IS DERIVED (#201)
//
// detekt and spotless used to find their files through four hardcoded ring globs — `app/…`,
// `core/…`, `feature/…`, `lib/…`. A module at a new top-level prefix was therefore analysed by
// neither, and nothing said so: with a deliberate detekt violation and deliberate formatting
// damage sitting in `bench/perf`, `./gradlew detekt spotlessCheck` was BUILD SUCCESSFUL and the
// scanned-file count did not move by one file. CI's gate step is that same command, so green
// there meant "everything in four named rings is clean", not "everything is clean".
//
// That is the hole `verifyStringKeyDocs` had six lines away (#173), and it is closed the same
// way: discovery is derived from the registered module list instead of from a guess about where
// modules live. A module exists because `settings.gradle.kts` says so; that file is committed and
// CI checks it out, so the scope cannot differ between a laptop and CI. Every project instance
// exists before any build script is evaluated — Gradle builds the hierarchy from settings first —
// so the list is complete, the intermediate `:core`, `:feature` and `:lib` projects included, and
// nothing has to be *evaluated* to read it.
//
// The hole here was NARROWER than #173's, and #201's own text overstates it — checked rather than
// repeated. These globs carried an extra `**` (`core/**/src/**`), so a module nested deeper under
// an existing ring was already covered: a damaged `core/data/local/…/NestedProbe.kt` failed both
// gates on the UNFIXED config. Only a new top-level ring escaped.
//
// `subprojects`, not `allprojects` and not a `**` walk from `rootDir`: the root project's
// directory IS the repo root, and this repo keeps agent worktrees — whole checkouts of itself —
// under `.claude/worktrees/`. #173 measured 48 `strings.xml` under there against the 24 the repo
// owns. A root-anchored walk would lint another branch's uncommitted code, fail this build on it,
// and differ laptop-vs-CI by construction — #163's shape, deliberately built in. The old ring
// globs kept those checkouts out only by accident of the ring names (a path starting `.claude/`
// matches none of them); anchoring every tree at a module directory makes it structural instead.
//
// Residuals, named rather than hidden:
//  - A `.kt` inside a module but outside every `src/` directory is not analysed. These patterns
//    are depth-agnostic, not shape-agnostic: source sets here live under `src/` at some depth,
//    and Kotlin outside one is compiled by nothing.
//  - The root project is not a module, so its own scripts are added below at depth 1 only —
//    deliberately, because deeper is where the worktrees are.
//  - `build-logic`'s Kotlin (15 files) is analysed by neither gate, and was not before this
//    change either: it is an included build, so it is in no module's tree. Its build SCRIPTS are
//    covered, via `gradle.includedBuilds` — derived too, rather than named.

val gateModuleDirs = subprojects.sortedBy { it.path }.map { it.projectDir }

val gateKotlinSources =
    files(
        gateModuleDirs.map { dir ->
            fileTree(dir).apply {
                include("**/src/**/*.kt")
                exclude("**/build/**")
            }
        },
    )

val gateGradleScripts =
    files(
        (gateModuleDirs + gradle.includedBuilds.map { it.projectDir }).map { dir ->
            fileTree(dir).apply {
                include("**/*.gradle.kts")
                exclude("**/build/**")
            }
        },
        fileTree(rootDir).apply { include("*.gradle.kts") },
    )

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    source.setFrom(gateKotlinSources)
    parallel = true
}

spotless {
    kotlin {
        target(gateKotlinSources)
        targetExclude("**/build/**")
        ktlint()
    }
    kotlinGradle {
        target(gateGradleScripts)
        targetExclude("**/build/**")
        ktlint()
    }
}
