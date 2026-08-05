# Research — issue #191: can Room's `MigrationTestHelper` actually run this project's migration SQL on a permitted emulator?

Read-only record (Mandatory Rule 4). Experiments run 2026-08-02/03 from a throwaway
`androidTest` source set in `:core:database`, since **deleted** — this document is the
deliverable, not code.

**Provenance of every measurement below** (rule 12, third shape — record it before
comparing anything):

| | |
|---|---|
| Worktree | `.claude/worktrees/i191-migration-verify` |
| Branch / base | `research/issue-191-migration-verifiability`, off `main` @ `9d78655` |
| Build | `:core:database` **debug** variant (the module has no flavours), Room `2.8.4`, AGP `9.3.0` |
| Devices | `Tranzlate_API29` = `emulator-5556` · `Tranzlate_API28` = `emulator-5558` · `Tranzlate_API24` = `emulator-5558` (28 killed first, port reused) |
| Not used | `emulator-5554` (`Resizable_Experimental`) was claimed by another agent for PR-19 the whole time and was never driven |
| Env | `JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home`, `ANDROID_HOME=$HOME/Library/Android/sdk`, `ANDROID_SERIAL` set per run |

Every run below is a **single-device** run with `ANDROID_SERIAL` naming the device, and
every device reading is `adb -s <serial>`, never a bare `adb`.

---

## The question

`core/database/…/Migrations.kt:99-104` records the verification stance:

> Verification stance, honestly: `MigrationCoverageTest` gates this step structurally
> (chain completeness + the exported schema per version), but the SQL is **NOT** executed
> in CI — Room's `MigrationTestHelper` needs the instrumentation suite, which is red on
> API 35+ (issue #40, follow-up #111).

The #191 soft-delete design (owner ruling, 2026-08-02: *"undo කළොත් පරණ row එක
අනිවාර්යෙන්ම එන්න ඕන"*) needs a `deleted_at` column, which is a **schema migration**.
Whether that migration can be verified at all is the gate on the whole plan, and until
now it was an inherited sentence rather than a measurement.

---

## Answer

**YES.** `MigrationTestHelper` runs this project's real, already-shipped migration SQL,
validates the result against the committed exported schemas, and **passes on all three
sub-35 emulators including the min-SDK floor**. Espresso — the whole of #40's failure
mechanism — is not on the classpath and not in the test APK, so #40 has no reach into it.

The blocker is **neither the API level nor #40**. It is exactly the fourth thing the
brief suspected: **CI has no emulator runner.** `.github/workflows/ci.yml:12` is
`runs-on: ubuntu-latest` with no emulator action anywhere in the file.

And there is a **second blocker nobody had named**, found by mutation below: a permanent
migration test in `:core:database` would be invisible to every gate CI runs today —
including the `#148` guard that exists to catch exactly this. See H-5.

---

## Hypotheses and their disconfirmation experiments

Each hypothesis is stated with the experiment that would have **proved it wrong**, and
the experiment was run before the hypothesis was believed.

### H-1 — `MigrationTestHelper` needs an exported schema, and this project exports and commits them. Confidence: **certain** (both halves directly observed, not inferred)

*Disconfirmation:* ask the helper for a version whose `<n>.json` does not exist. If it
proceeds anyway, the schema is not load-bearing and half of H-1 is wrong.

Export is configured by the Room Gradle plugin, once, for every module that applies the
convention — `build-logic/convention/src/main/kotlin/AndroidRoomConventionPlugin.kt`:

```kotlin
extensions.configure<RoomExtension> { schemaDirectory("$projectDir/schemas") }
```

Committed, all three versions:

```console
$ git ls-files core/database/schemas/
core/database/schemas/com.codeboxlk.tranzlate.core.database.TranzlateDatabase/1.json
core/database/schemas/com.codeboxlk.tranzlate.core.database.TranzlateDatabase/2.json
core/database/schemas/com.codeboxlk.tranzlate.core.database.TranzlateDatabase/3.json
```

They reach the test APK **automatically** — no assets wiring is needed in the module:

```console
$ ./gradlew :core:database:assembleDebugAndroidTest --dry-run | grep -i copyRoom
:core:database:copyRoomSchemas SKIPPED
:core:database:copyRoomSchemasToAndroidTestAssetsDebugAndroidTest SKIPPED

$ unzip -l core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk | grep -i assets
     4852  01-01-1981 01:01   assets/com.codeboxlk.tranzlate.core.database.TranzlateDatabase/1.json
     4858  01-01-1981 01:01   assets/com.codeboxlk.tranzlate.core.database.TranzlateDatabase/2.json
     5750  01-01-1981 01:01   assets/com.codeboxlk.tranzlate.core.database.TranzlateDatabase/3.json
```

The disconfirmation ran — `createDatabase(db, 4)`, a version with no `4.json` — and the
helper refused, loudly and by name:

```
java.io.FileNotFoundException: Cannot find the schema file in the assets folder. Make
sure to include the exported json schemas in your test assert inputs. See
https://developer.android.com/training/data-storage/room/migrating-db-versions#export-schema
for details. Missing file: com.codeboxlk.tranzlate.core.database.TranzlateDatabase/4.json
```

So the schema is load-bearing, it is present, and a missing one cannot pass silently.

### H-2 — #40's failure mode cannot reach a `MigrationTestHelper` test, because Espresso is not there at all. Confidence: **70%** for the general claim; the specific claim "not on `:core:database`'s androidTest classpath or in its APK" is **directly observed**

*Disconfirmation:* find Espresso, or `android.hardware.input.InputManager.getInstance`,
anywhere on the resolved classpath or in the packaged dex. Either finding kills it.

**Enumerated by:** two searches that could not both miss the same thing —
(A) the Gradle dependency model, (B) the packaged artefact's dex, which is produced by a
different pipeline and would still show a dependency the model resolved differently.

*(A) Gradle's resolved classpath:*

```console
$ ./gradlew -q :core:database:dependencies --configuration debugAndroidTestRuntimeClasspath > deps.txt
$ grep -ci "espresso" deps.txt
0
$ grep -nE "androidx\.test\.espresso|uiautomator|ui-test" deps.txt
$        # (no output)
```

*(B) The shipped dex of the test APK:*

```console
$ for d in classes.dex … classes9.dex; do unzip -p …androidTest.apk $d | strings | grep -E "^L?androidx/test/espresso"; done
        # (no output — the Espresso classes are not packaged)
$ … | strings | grep -E "onIdle|IdlingResource"
        # (no output)
```

**The one hit worth naming, because it looks like a refutation and is not.** A plain
`strings` sweep does return two Espresso-ish tokens and two `InputManager` tokens in
`classes.dex`:

```console
$ unzip -p …androidTest.apk classes.dex | strings | grep -i espresso
ESPRESSO_VERSION
2androidx.test.espresso.web.bridge.JavaScriptBridge

$ unzip -p …androidTest.apk classes.dex | strings | grep InputManager
%Landroid/hardware/input/InputManager;
!Landroid/media/tv/TvInputManager;
```

Both are **string constants and a class literal, not code that runs.** `dexdump -d`
attributes the `InputManager` reference to exactly one class, and to a `const-class`
instruction — a static service-name lookup table in `androidx.core`:

```console
$ dexdump -d classes.dex | <attribute each InputManager reference to its enclosing class>
   Landroidx/core/content/ContextCompat$LegacyServiceMapHolder;

$ grep -c "InputManager;.*getInstance" classes.dump
0
$ grep -n "hardware/input/InputManager" classes.dump
289536:1df5fe: 1c01 de00   |00bf: const-class v1, Landroid/hardware/input/InputManager; // type@00de
```

**Zero** invocations of `getInstance` on `InputManager` in the entire dex. #40's exception
is `Espresso.onIdle` → `InputManager.getInstance`; neither end of that path exists here.

*Why 70% and not higher:* the observation is about **this** module's androidTest artefact
on **this** commit. The general claim — "`MigrationTestHelper` never touches Espresso" —
is supported by it but not proven by it, and the API 35+ leg was never run (see
"Not measured"). Rule 4 caps a single-hypothesis claim at 70% and that is the right
number for the general form.

### H-3 — the harness runs, and passes, on `Tranzlate_API29`. Confidence: **certain** (observed)

*Disconfirmation:* run it and see it fail or not start.

```console
$ ANDROID_SERIAL=emulator-5556 ./gradlew :core:database:connectedDebugAndroidTest
…
Starting 4 tests on Tranzlate_API29(AVD) - 10
Finished 4 tests on Tranzlate_API29(AVD) - 10
BUILD SUCCESSFUL in 4s
```

From `core/database/build/outputs/androidTest-results/connected/debug/TEST-*.xml`:

```
tests=4 failures=0 errors=0 skipped=0    device = Tranzlate_API29(AVD) - 10
   runsAndValidatesTheWholeChain            0.037  PASS
   runsAndValidatesOneToTwo                 0.029  PASS
   createsAV1DatabaseFromTheExportedSchema  0.001  PASS
   aDeliberatelyWrongMigrationIsRejected    0.545  PASS
```

Device identity, read directly rather than assumed:

```console
$ adb -s emulator-5556 shell getprop ro.build.version.sdk      → 29
$ adb -s emulator-5556 shell getprop ro.build.version.release  → 10
```

The tests were the **already-shipped** migrations — `MigrationOneToTwo` and
`MigrationTwoToThree`, reached from `androidTest` even though both are `internal`
(the androidTest compilation is a friend of `main`; no visibility change was needed).
`runsAndValidatesOneToTwo` seeded two rows that violate the C-8 unique key, one of them
favourited, and asserted the documented behaviour: one row survives, it is the newest
(`id = 2`, `created_at = 2000`), and it **inherited the older row's star** — the exact
carry-the-star-first reasoning in `Migrations.kt:38-42`, now executed rather than
reasoned about.

### H-4 — it is not an API-29-only result; it works on API 28 and on the min-SDK floor, API 24. Confidence: **certain** (observed)

*Disconfirmation:* run the same APK on 28 and 24 and watch it break.

```console
$ ANDROID_SERIAL=emulator-5558 ./gradlew :core:database:connectedDebugAndroidTest   # API 28
Starting 4 tests on Tranzlate_API28(AVD) - 9
Finished 4 tests on Tranzlate_API28(AVD) - 9
BUILD SUCCESSFUL in 4s
→ tests=4 failures=0 errors=0 skipped=0   device = Tranzlate_API28(AVD) - 9
```

```console
$ ANDROID_SERIAL=emulator-5558 ./gradlew :core:database:connectedDebugAndroidTest   # API 24
Starting 4 tests on Tranzlate_API24(AVD) - 7.0
Finished 4 tests on Tranzlate_API24(AVD) - 7.0
BUILD SUCCESSFUL in 5s
→ tests=4 failures=0 errors=0 skipped=0   device = Tranzlate_API24(AVD) - 7.0
```

API 24 matters more than the other two: `TranzlateSdk.MIN_SDK = 24`, so this is the
oldest SQLite the app will ever meet, and it is where a migration statement using newer
SQLite syntax would break first. It passes.

### H-5 — the real blocker is the absence of an emulator in CI. Confidence: **certain for the emulator half; and the half nobody had named is worse**

*Disconfirmation:* find an emulator step in `ci.yml`, or find that some existing CI gate
would at least keep a migration test compiling.

`ci.yml:12` is `runs-on: ubuntu-latest`, and there is no emulator action in the file.
`ci.yml:49-51` already says so in its own words: *"needs a device this workflow does not
have."* That much was already recorded correctly.

**The part that was not.** A migration test living in `:core:database/src/androidTest`
would be compiled by **nothing** CI runs. Proved by mutation — the mutation was chosen
before the check, and it is the smallest possible one, an unresolved symbol:

```kotlin
// appended to the throwaway test file
val broken: Int = thisSymbolDoesNotExist()
```

```console
$ ./gradlew :core:database:build          → BUILD SUCCESSFUL in 10s     ← green, with a compile error in the tree
$ ./gradlew :app:assembleAndroidTest      → BUILD SUCCESSFUL in 22s     ← the #148 guard, also green
$ ./gradlew :core:database:assembleDebugAndroidTest
e: …/MigrationHarnessProbeTest.kt:128:19 Unresolved reference 'thisSymbolDoesNotExist'.
BUILD FAILED in 7s
```

Why `build` misses it — the only androidTest tasks it pulls in are lint-model tasks, and
lint analysis is not a compiler:

```console
$ ./gradlew :core:database:build --dry-run | grep -i androidTest
:core:database:preDebugAndroidTestBuild SKIPPED
:core:database:generateDebugAndroidTestLintModel SKIPPED
:core:database:lintAnalyzeDebugAndroidTest SKIPPED
$ ./gradlew :core:database:build --dry-run | grep -c compileDebugAndroidTestKotlin
0
```

And why the `#148` guard misses it: that step is `:app:assembleAndroidTest`, scoped to
`:app`. Its own comment explains it was written as `assembleAndroidTest` rather than a
named variant so a *flavour* gaining an androidTest variant could not slip through — it
does not generalise to a **different module** gaining one.

This is #111's shape exactly: sources that exist, that everyone believes are covered, that
no gate ever compiles. The file restore after this mutation was done with a `cp` backup,
never `git checkout --`.

### H-6 — the harness's passes are real, not vacuous. Confidence: **certain** (observed, by mutation decided in advance)

*Disconfirmation:* feed it a migration that is deliberately **wrong** and see it pass anyway.

The mutation was fixed before the assertions were written (rule 11, third cause): build
`language_usage` with `last_used_ts` where the schema says `last_used_at`. One character
class of difference, in the additive migration that is the closest analogue to the
`deleted_at` column #191 needs.

`aDeliberatelyWrongMigrationIsRejected` passes on all three devices because Room throws.
The message, captured by deliberately failing an assertion so the report would print it:

```
java.lang.IllegalStateException: Migration didn't properly handle:  language_usage
Expected:
TableInfo { name = 'language_usage', columns = {
  Column { name = 'lang_id',      type = 'TEXT',    notNull = 'true', primaryKeyPosition = '1' },
  Column { name = 'last_used_at', type = 'INTEGER', notNull = 'true', primaryKeyPosition = '0' },
  Column { name = 'role',         type = 'TEXT',    notNull = 'true', primaryKeyPosition = '2' } } …
```

Column-by-column, against the committed `3.json`. And a **missing** step is caught too:

```
java.lang.IllegalStateException: A migration from 1 to 3 was required but not found.
Please provide the necessary Migration path via RoomDatabase.Builder.addMigration(...)
or allow for destructive migrations via one of the RoomDatabase.Builder.fallbackToDestructiveMigration* functions.
```

Three distinct failure modes — wrong SQL, missing schema, missing step — each fails loudly
and names what is wrong. This is a real gate, not a green light.

---

## What the current documents get wrong

**Enumerated by:** two independent searches, and the second one found sites the first
structurally could not (rule 12, first shape — a search written from what I already knew
would have stopped at the class name).

*Search A — by the harness's own name:*

```console
$ git grep -n "MigrationTestHelper"
core/database/src/main/kotlin/…/Migrations.kt:101
core/database/src/test/kotlin/…/MigrationCoverageTest.kt:18
core/database/src/test/kotlin/…/MigrationCoverageTest.kt:63
```

*Search B — by the claim's wording, ignoring the class name:*

```console
$ grep -rnE "migration|schema" --include=*.kt --include=*.md --include=*.yml . \
    | grep -viE "/build/" | grep -iE "#40|#111|API 35|instrumentation"
core/database/src/test/kotlin/…/MigrationCoverageTest.kt:63
core/data/src/test/kotlin/…/TranslationRepositoryImplTest.kt:20      ← A could not find this
core/data/src/test/kotlin/…/LanguageUsageRepositoryImplTest.kt:17    ← nor this
```

**Sites: 5 found.** Three name the class; two more repeat the framing as `(#40/#111)`
without ever naming it, and search A had no way to reach them.

| Site | What it says | Verdict |
|---|---|---|
| `Migrations.kt:99-104` | *"needs the instrumentation suite, which is red on API 35+ (issue #40, follow-up #111)"* | **Two errors.** #111 is CLOSED, so it is not a follow-up. And the suite being red on API 35+ is not why this SQL is unverified — it is unverified because CI has no device, and it is verifiable on 24/28/29 today. |
| `MigrationCoverageTest.kt:15-21` | *"Verifying the SQL needs `MigrationTestHelper` on a device, and the instrumentation suite is red on API 35+ (issue #40); that verification is recorded as follow-up rather than pretended (#111)."* | *"needs a device"* is right and is the whole truth. The #40/#111 clause is not. |
| `MigrationCoverageTest.kt:63` | *"what a future `MigrationTestHelper` run (#111) …"* | #111 is the wrong issue for this; it was about the suite **compiling**. |
| `TranslationRepositoryImplTest.kt:20` | *"SQL execution is the instrumented gap this project has recorded since the migrations (#40/#111)"* | Same stale pairing, second-hand. |
| `LanguageUsageRepositoryImplTest.kt:17` | *"SQL execution is the instrumented gap recorded on the migration (#40/#111)"* | Same. |

**One inherited claim checked and found CORRECT**, stated because rule 12 cuts both ways:
"#111 was fixed by PR #147" is true. Commit `eadb5bb` on PR #147 restored the
`PurchaseFlow` provider in `app/src/androidTestProd/…/FakeTranslateModule.kt`, and #111's
own closing comment (2026-08-01T11:30:34Z) says *"Fixed by #147 and now guarded by #148."*
The issue was then closed by hand — no PR carries a `Fixes #111` trailer, which is why
`git log --grep` does not find one and why the attribution is worth pinning here.

**#40 itself needs no correction.** It is accurate about what it describes: Espresso
`onIdle` on API 35+. It simply never had anything to do with migration SQL.

---

## Not measured — and what would settle it

**Does the harness run on API 35+?** *Verified data නෑ.* The only permitted device above
API 34 is `Resizable_Experimental` (`emulator-5554`), and it was claimed by the PR-19
agent for the entire session; driving it is precisely the contention rule 12's fourth
shape exists to prevent. H-2's dex evidence says #40's mechanism is absent regardless of
API level, but that is an inference about the artefact, not a run.

*What would settle it:* claim `Resizable_Experimental` exclusively, then

```console
ANDROID_SERIAL=emulator-5554 ./gradlew :core:database:connectedDebugAndroidTest
```

It is a **nice-to-have, not a gate.** API 24/28/29 already covers min-SDK through the
newest sub-35 image, and a migration verified at the floor is the one that matters.

**Could CI run it after all?** *Verified data නෑ.* GitHub's `ubuntu-latest` runners and
`reactivecircus/android-emulator-runner` are the usual route, and nothing in this repo has
ever tried it. Deciding that needs its own measurement — runner nested-virtualisation
support, wall-clock cost against the 45-minute `timeout-minutes`, and flakiness over
repeated runs. Not attempted here, and it should not gate #191: the local run already
gives the coverage.

---

## What a #191 plan doc must MANDATE

The answer being "yes" converts this from a blocker into a **procedure**, and a procedure
that is not mechanised is the thing this project has learned twice does not hold. In
descending order of how badly its absence would hurt:

1. **A permanent `androidTest` source set in `:core:database`, with the v3→v4 migration
   test written before the migration is called done.** It must cover, at minimum: the new
   column exists with the right affinity and nullability; **existing rows survive with
   their data intact** (the whole point of the schema stance in `Migrations.kt:9-19`); and
   the full `1 → 4` chain, because that is the walk a v1 install actually performs.

2. **Extend the CI compile guard to the new source set — this is not optional and it is
   the finding of this record.** Today `./gradlew build` is green with an unresolved symbol
   in that directory (H-5), so a migration test could rot there exactly as #111 did. The
   `ci.yml` step must gain `:core:database:assembleDebugAndroidTest` alongside
   `:app:assembleAndroidTest`, and the step's comment must say why a module-scoped target
   was needed. Compiling is the half CI *can* do; refusing it is refusing the free half.

3. **A named, mandatory local device run, recorded in the PR body with its output.** The
   form that matches this project's existing markers:
   `Migration verified: Tranzlate_API24 + Tranzlate_API29, :core:database:connectedDebugAndroidTest, N tests, 0 failures`.
   API 24 is the non-negotiable one — min-SDK is where migration SQL breaks first.
   A migration PR without that line has not been verified, and "the unit tests are green"
   must not be accepted in its place, because `MigrationCoverageTest` is structural and
   would stay green through a completely broken statement.

4. **Add `androidx.room:room-testing` to `gradle/libs.versions.toml`** under the existing
   `room` version ref. The probe used a raw coordinate because it was disposable; a
   permanent test must not.

5. **A mutation control shipped beside the real test**, in the shape of
   `aDeliberatelyWrongMigrationIsRejected` above — a deliberately wrong `deleted_at`
   migration that Room must reject. Without it, a harness that silently stopped validating
   would look identical to one that works, and every run would be a green light meaning
   nothing.

6. **Correct the five stale sites in the same PR**, per the table above — including the two
   in `:core:data` that the obvious search does not reach. Leaving them says the SQL cannot
   be verified while the repo contains a test that verifies it, which is the drift that
   makes documents stop being read.

Nothing here needs `MigrationTestHelper` to work in CI, and nothing here waits on #40.

---

## The throwaway

`core/database/src/androidTest/…/MigrationHarnessProbeTest.kt` (4 tests) and a temporary
`HarnessDiagnosticsProbeTest.kt` (3 deliberately-failing probes, used only to capture the
messages quoted above) were **deleted**, along with the `androidTestImplementation` block
added to `core/database/build.gradle.kts`. The tree carries only this document.

**Recommendation, for the caller to decide rather than for me to act on:** the four
assertions in `MigrationHarnessProbeTest` are worth keeping — they cost 4 seconds, they
executed a shipped migration's data handling for the first time in this project's life,
and the mutation control is item 5 above already written. But keeping them without item 2
would put unguarded sources back into the exact directory this record just proved CI does
not compile. They belong in the #191 implementation PR, with the CI guard, not on their
own.
