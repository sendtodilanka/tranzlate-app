# Plan — #253 + #231: one gate task, and the Robolectric runtime split out of Compose

status: accepted

Refs: **#253** (the local gate never compiles `app/src/androidTestProd`), **#231**
(`tranzlate.compose-test` is the only route to Robolectric), **#241** (the migration
SQL nothing compiles), **#148** (the CI guard this repeats), **#210** (`build-logic`
is analysed by neither lint gate).

Shipped as **PR #260**.

Wave **1a** of `docs/plan/issue-130-rev5-completion.md`. These two gate everything
after them: #241 cannot be done honestly until a gate compiles `:core:database`'s
androidTest, and #241's permanent test needs a Robolectric runtime that does not drag
Compose into the database module.

**File ownership for this PR:** `build-logic/**`, `.github/workflows/ci.yml`, Also touched, and missing from this list as first written: `gradle/libs.versions.toml` (room-testing) and root `build.gradle.kts` (plugin registration) — neither collides with the live siblings, but an incomplete ownership statement is how a collision goes unnoticed.
CLAUDE.md's rule 6 gate list, `docs/plan/**`. Sibling agents hold `.claude/hookify.*`
+ `.claude/agents/` (PR #233) and two files under `.claude/hooks/`. Nothing under
`core/**` or `feature/**` is changed here.

---

## The problem, measured before it was fixed

Three instances of one shape: **a gate believed to cover something it does not.**

### Instance 1 — the documented local gate is blind to `app/src/androidTestProd/`

The command in `.claude/skills/land-pr/SKILL.md:39` and
`.claude/agents/co-verify-lens.md:66` — the one every brief repeats — is

```
./gradlew test :app:assembleTranzlateProdDebug :app:assembleTranzlateFakeDebug spotlessCheck detekt
```

With a deliberate `Unresolved reference` planted in
`app/src/androidTestProd/kotlin/.../GateCoverageProbe.kt`, that command is **BUILD
SUCCESSFUL in 20s, exit 0**. It bit for real in #246, where the author had to
remember a second command to know their change compiled.

### Instance 2 — `:core:database`'s androidTest is compiled by nothing at all (#241)

With a deliberate `Unresolved reference` in
`core/database/src/androidTest/kotlin/.../MigrationCompileProbe.kt`:

| Command | Result |
|---|---|
| `./gradlew :core:database:build` | **BUILD SUCCESSFUL**, exit 0 |
| `./gradlew build` | **BUILD SUCCESSFUL**, exit 0, 2m 20s |
| `./gradlew :app:assembleAndroidTest` — the **#148 CI guard** | **BUILD SUCCESSFUL**, exit 0 |

The #148 guard is `:app`-scoped by construction, so it cannot reach a second module.
Same reading as `docs/research/issue-191-migration-verifiability.md` H-5, reproduced
here independently rather than quoted.

### Instance 3 — a `CONFLICTING` PR runs no workflow, and keeps its stale green

Added to #253 as a comment. **Out of scope for this PR** and said so plainly: the fix
is a SHA comparison inside `/land-pr`, and `.claude/skills/` is not this PR's to
change. Recorded as a finding.

---

## The fix

### One lifecycle task — `preflight`

`build-logic/convention/src/main/kotlin/PreflightConventionPlugin.kt`, applied at the
root, registers **`./gradlew preflight`**: one name to remember, with the list living
in the build instead of in prose that every brief has to copy correctly.

It depends on, all **derived** rather than named — the #201/#173 lesson, one level
out:

| Contribution | Derived from |
|---|---|
| `verifyStringKeyDocs`, `detekt`, `spotlessCheck` | the three root-project gates |
| every module's `test` | `subprojects` that have a build file |
| every androidTest component's `compile…AndroidTestSources` | AGP's `onVariants` → `HasAndroidTest.androidTest` |
| every application module's debug `assemble…` | AGP's `onVariants`, `buildType == "debug"` |

A module added at a new ring, an androidTest source set added to a module that has
none today, or a second white-label brand flavour, are all covered the moment they
exist. No path glob, no module name, no source-directory guess anywhere in it.

**Why `preflight` and not `verifyAll`, which #253 suggested.** `verifyAll` asserts
totality, and it would be false the day it shipped: it does not RUN instrumentation
tests (#40, and no device in CI) and does not analyse `build-logic`'s own Kotlin
(#210). A name that claims complete coverage is the exact failure this issue is
about, one level out. `preflight` says *when* to run it, which cannot go stale, and
the task's `description` states both exclusions by issue number.

**Why `compile…AndroidTestSources` and not `assembleAndroidTest`.** Measured, not
preferred: `./gradlew assembleAndroidTest` across all 19 Android modules **fails** —
`:core:ui:mergeExtDexDebugAndroidTest` → `D8: java.lang.OutOfMemoryError: Java heap
space` at the repo's `-Xmx4g`, after 57s. Dexing and packaging 19 test APKs is both
unaffordable and beside the point: what the gate must catch is that androidTest
sources **compile**. The compile anchor pulls `compile…AndroidTestKotlin` in — verified
from the task graph, not assumed:

```
$ ./gradlew :core:database:compileDebugAndroidTestSources --dry-run
:core:database:kspDebugAndroidTestKotlin SKIPPED
:core:database:compileDebugAndroidTestKotlin SKIPPED
:core:database:compileDebugAndroidTestJavaWithJavac SKIPPED
:core:database:compileDebugAndroidTestSources SKIPPED
```

`:app`'s androidTest APK still gets **assembled** in CI by the untouched #148 step, so
nothing that ran before stops running.

### Wired into CI and into CLAUDE.md rule 6

- `ci.yml` gains a `./gradlew preflight` step, so CI runs **the same task name** an
  agent runs locally. The #148 `:app:assembleAndroidTest` step stays: it assembles,
  which `preflight` deliberately does not.
- CLAUDE.md rule 6 gains the gate list it never actually carried — as one command.

### #231 — `tranzlate.robolectric` split out

`RobolectricConventionPlugin` carries the Robolectric/JVM-Android runtime:
`android-all-instrumented` pinned through the version catalog,
`robolectric.offline=true` via `RobolectricSdkArgumentProvider`,
`unitTests.isIncludeAndroidResources`, `testOptions.targetSdk`.
`ComposeTestConventionPlugin` applies it and adds **only** the Compose pieces
(`ui-test-junit4` on `testImplementation`, `ui-test-manifest` + the BOM on
`debugImplementation`).

`androidx.room:room-testing` is added to the version catalog under the existing
`room` ref — #191's probe used a raw coordinate because it was disposable, and #241's
permanent test must not.

**`:core:database`'s build file is NOT flipped to the new plugin in this PR.**
`core/**` belongs to wave 1b. Flipping it is one line and it belongs in #241's PR,
which is the change that gives it a reason.

---

## Mutations, decided before any code was written

Full record: the scratchpad file written before the first edit. Summary:

| | Mutation | Before | After |
|---|---|---|---|
| M1 | unresolved symbol in `app/src/androidTestProd/` | documented gate green | `preflight` red, naming the file |
| M2 | unresolved symbol in `core/database/src/androidTest/` | `:core:database:build`, `build`, `:app:assembleAndroidTest` all green | `preflight` red, naming the file |
| M3 | **negative control** — a *valid* file in the same place | — | `preflight` green |
| M4 | `tranzlate.robolectric` alone on a module | — | Robolectric test green **and** `createComposeRule()` does not resolve |
| M5 | `MigrationTestHelper` under Robolectric | — | measured, both outcomes reported verbatim |

M3 is the one a lazy fix fails: without it, M2's red proves only that *a file there*
fails the build, not that a *broken* file there does.

---

## Known scope limit in the derivation itself

`PreflightConventionPlugin.kt:117,134` registers androidTest coverage inside
`withId("com.android.application")` / `withId("com.android.library")` only. A module
applying `com.android.dynamic-feature` or `com.android.test` would get **zero** entries
and no error — silent absence, which is the #201/#173 shape this task exists to fix one
level out. **Dormant today**: no `.kts` in this repo applies either id (grepped by the
#260 lens). Named rather than left, because the next module to use one would inherit
the gap invisibly.

## What this change does NOT cover, stated so nobody reads more into the green tick

1. **Instrumentation tests still do not RUN** — #40, and CI has no emulator.
0. **androidTest is COMPILED, not assembled**, so duplicate-class, AAR-metadata and
   dex-merge defects outside `:app` still escape. Found by the #260 lens, which diffed
   `compileDebugAndroidTestSources` against `assembleDebugAndroidTest`:
   `check*DuplicateClasses`, `check*AarMetadata`, `mergeExtDex*`, `dexBuilder*`,
   `mergeProjectDex*`, `package*` are all absent from the compile graph. Assembling all
   19 modules is what OOMs, so this is a deliberate trade — but it belongs in this list
   beside #40 and #210, not left for a reader to discover.
   `preflight` compiles them.
2. **`build-logic`'s own 15 Kotlin files are still analysed by neither detekt nor
   spotless** — #210. This PR edits that directory, so its own changes landed
   unchecked. `preflight` cannot close it: it aggregates existing tasks, and no task
   analysing those files exists yet. #210 needs detekt and spotless pointed at
   `gradle.includedBuilds`, which is a different change with its own expected findings.
3. **A `CONFLICTING` PR still reports a stale green** — instance 3, `/land-pr`'s to fix.

## What would prove this plan wrong

If `preflight` costs enough wall-clock that agents skip it, the fix is worse than the
gap — a gate nobody runs is #253's shape one level out. The cost is therefore measured
and stated in the PR body, not assumed.
