# Plan — #241: execute MigrationThreeToFour's SQL in a test that can go red

status: accepted
(accepted basis: the rev5 completion plan, wave 1b — `issue-130-rev5-completion.md`,
already `status: accepted`; owner endorsed severity-order wave 1b, 2026-08-04,
"#241 first". This plan is the how.)

Refs: #241 (type:bug, P1, S1).

## The harm

`MigrationThreeToFour` (`core/database/.../Migrations.kt:157`) runs two
`CREATE INDEX` statements on upgrade. If either statement's SQL or index name is
wrong, **every user who already has the app crashes on next launch** (Room
validates the schema on open), while **CI stays green and a fresh install works** —
because nothing executes the migration SQL. `SavedCountQueryTest` builds from
`TranslationEntity`'s annotations (the fresh-install path); `MigrationCoverageTest`
is explicitly structural (chain has no gap, JSON committed) and its own KDoc says it
never runs a step's SQL.

## Why this is now doable as a UNIT test (no emulator — #40)

All the infra already exists — confirmed on `main` @ c91cb08:
- **Robolectric** is wired into `core:database` via the `tranzlate.compose.test`
  convention plugin (`SavedCountQueryTest` already runs `@RunWith(RobolectricTestRunner)`
  in the ordinary `test` task). No build-logic change needed.
- **`androidx-room-testing`** (MigrationTestHelper) is already in the version
  catalogue (`libs.versions.toml:122`), just not yet a dependency of this module.
- **Exported schemas** are committed at `core/database/schemas/<db>/{1,2,3,4}.json`
  (Room's `schemaDirectory("$projectDir/schemas")`, `AndroidRoomConventionPlugin:15`).

## The fix

1. `core/database/build.gradle.kts` — add `testImplementation(libs.androidx.room.testing)`.
2. New `core/database/src/test/.../MigrationThreeToFourTest.kt`:
   - Construct `MigrationTestHelper` and point it at the exported schemas.
     **CORRECTION (verified at build, `javap` on room-testing 2.8.4):** there is no
     "schema-directory File/Path" constructor on Android — `AndroidMigrationTestHelper`
     loads schemas from **assets** (`<db-fqcn>/<version>.json`) for both variants, and
     the constructor's `File` param is the *database* file. So the actual approach is the
     **instrumentation constructor run UNDER Robolectric**
     (`MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), TranzlateDatabase::class.java)`),
     with `sourceSets.test.assets` pointed at `schemas/` — works without an emulator,
     in the ordinary `:core:database:test` task. This is the evidence-wins case (rule 12):
     the agent proved the plan's assumption wrong and did the right thing.
   - `createDatabase(name, 3)` from `3.json`, then
     `runMigrationsAndValidate(name, 4, validateDroppedTables=true, MigrationThreeToFour)`.
     `runMigrationsAndValidate` **executes the two CREATE INDEX statements** and then
     **validates the resulting schema against `4.json`** — a wrong name or column list
     fails validation.
3. No production code changes. Test-only + one dependency line.

## Mutate-first (rule 11 — and #242 is literally "tests that cannot fail")

The mutation is chosen BEFORE the test is trusted: temporarily change one index in
`MigrationThreeToFour` — e.g. drop `source_lang` from the first `CREATE INDEX`, or
misspell `index_translation_favourite_source_lang`. The test MUST go **RED**
(validation mismatch against `4.json`). Revert. Record the red output in the PR body.
A test that stays green under this mutation is the bug, not the fix.

## Verify

- `./gradlew :core:database:test` green (the new test runs the real SQL under Robolectric).
- The mutation above shows RED, then reverts to GREEN.
- `./gradlew preflight`.

## Co-verify (rule 5 — data-layer-adjacent, treat as high-risk)

Cross-model lens. The one question that matters: **does the test actually execute the
migration SQL and would it go red if the SQL were wrong** — i.e. re-run the mutation
independently. Also: is the schema-dir path correct under Robolectric (the real
unknown), and does `runMigrationsAndValidate` validate indices (not just tables)?

## The one genuine unknown / what would change this plan

`MigrationTestHelper`'s file-path constructor working under **Robolectric** (rather
than instrumentation) is the single risk. If it cannot read the schema dir or needs
an emulator, the fallback is the instrumentation `MigrationTestHelper` behind an
`androidTest` source set — but that does NOT run in CI (#40), so it would only
partly close #241 and must be said plainly rather than reported as done. Try the
Robolectric/JVM path first; escalate only if it provably cannot work.

## Landed

The CI-executable v3→v4 schema test landed in **PR #266** (cross-model co-verify,
3 mutations RED). It is `Refs: #241`, **not** `Fixes:` — #241 stays OPEN for its
three "Mandatory when done" items this PR does not do: an `androidTest` with
row-survival + the full 1→4 chain, the CI androidTest compile guard, and an API-24
run. The first two are #40-blocked (no CI emulator); whether the Robolectric route
supersedes them is an owner scope call.
