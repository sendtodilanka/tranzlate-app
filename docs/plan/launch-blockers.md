# Launch blockers — production UPDATE (Track B)

status: accepted
Branch: `feat/launch-blockers` · scope: the four **user-visible** blockers from
`docs/plan/launch-readiness.md` (A4/A5 · A9 · A8 · A10) plus the honesty sweep of
every reachable Home entry (C2).

> **Out of scope by instruction** (other tracks own the files):
> `app/build.gradle.kts`, `build-logic/**`, `feature/text/ComposerScreen.kt`,
> `feature/text/AdaptiveLayout.kt`, `core/ui/ErrorCard.kt`.
> Signing (A1), AAB (A2), `versionName`/`versionCode` (A3), paywall removal (A6)
> and the legal surface (A7) therefore stay with Track A / the owner.

---

## Context that changes the calculus: this is an UPDATE, not a first upload

Verified, not assumed:

| Fact | Evidence |
|---|---|
| Old shipped app id | `applicationId = "com.codeboxlk.tranzlate.offlinetranslator"` — `~/StudioProjects/Tranzlate/app/build.gradle.kts:81` |
| New app id (flavor `tranzlate`) | same string — `app/build.gradle.kts:81` (this tree) |
| Old shipped version | `versionCode = 4`, `versionName = "1.0.$versionCode"` — old `app/build.gradle.kts:78-79` |
| Old DB **file** | `translator.db`, opened with `createFromAsset(DBNAME)` and 7 hand-written migrations — old `app/src/main/java/com/codeboxlk/tranzlate/di/DatabaseModule.kt:33,52-57` |
| New DB **file** | `tranzlate.db` — `core/database/.../di/DatabaseModule.kt` |

Same package ⇒ Play treats this as an update over the v1.0.4 install base.
Different DB filename ⇒ **the new build never opens the old database at all**, so
the destructive-migration flag could not have wiped v1.0.4 history — that history
simply becomes an orphaned file the new app does not read. The destructive flag is
still a live landmine for everyone who installs this release (see §3).

**⚠ Blocker I cannot clear from this branch:** `versionCode = 1`
(`app/build.gradle.kts:13`) is **lower than the shipped 4**. Play rejects an
upload whose versionCode is not greater than the highest already published, so
this update cannot be uploaded until Track A raises it (≥ 5). Flagged, not fixed —
that file is owned by another track.

---

## 1. Camera dead end + every other reachable Home entry

### Decision: option (a) — a real "coming soon" destination with a back affordance.
Rejected option (b) (hide behind `FeatureToggle`):

- The toggle plumbing is genuinely dead — `TranzlateApp.kt:40` is
  `@Suppress("UNUSED_PARAMETER") appConfig: AppConfig`, and the toggle-aware
  registry `TopLevelDestination.kt:65-74` has zero production callers. Making it
  load-bearing means threading booleans from the shell into `HomeScreen`'s
  `ToolsStack` — new API surface on a screen another track is editing, on launch
  day, to *hide* something rather than *fix* it.
- Hiding also does not make the app honest; it makes it quieter. The Conversation
  card has the identical missing-back problem, and hiding both would leave the
  2×2 tool grid with two holes.
- (a) is cheaper *and* fixes two entries with one component change, because
  `ComingSoonScreen` is already shared by Chat.

### What changed
`ComingSoonScreen` gains a `TopAppBar` with a back arrow, a per-destination
`message`, and a testTag. `CameraNavKey` now renders it instead of
`feature/camera`'s bare `Box` (that stub keeps its module for issue #78; it is
simply no longer reachable). Copy states a fact — "isn't available in this
version yet" — and points at what *does* work, per the EDGE_CASES no-dead-end
rule. No date is promised.

### Honesty sweep of every reachable Home entry

| Entry | Before | After |
|---|---|---|
| Offline mode (card) | opens the real offline manager | unchanged; subtitle fixed in §2 |
| Voice (card) | snackbar "Voice input arrives with the voice update" | unchanged — already true |
| **Camera** (card) | → blank `Box`, **no back** | → `ComingSoonScreen` with back + honest body |
| **Conversation** (card) | → `ComingSoonScreen`, **no back button** | → same screen, now with a back arrow + body |
| Download languages (row) | opens the real offline manager | unchanged; subtitle fixed in §2 |
| Phrasebook (mini) | snackbar "arrives in a later update" | unchanged — true |
| **Quotes** (mini) | "Saved quotes arrive with **the history update**" | **stale** — History/Saved already shipped; reworded |
| **Natural phrasing** (banner) | snackbar + a **NEW** badge | badge removed — a NEW badge on a feature that does not exist is a lie |
| Pro chip → paywall | out of scope (A6 belongs to another track) | untouched |

---

## 2. Lying strings on Home

| Key | Before | After | Why |
|---|---|---|---|
| `home_tool_offline_sub` | "6 languages ready" | "Translate without a connection" | **claim removed.** The downloaded count is knowable, but only through `OfflineModelManager.modelStates()` → `RemoteModelManager.getInstance()`. Injecting that into `TextViewModel` puts an ML Kit call on the **cold-start path** — and the release smoke for issue #5 caught a launch NPE in exactly that class (`docs/plan/issue-5-r8-release.md:24-35`). Not a risk worth taking on launch day for a subtitle. |
| `home_row_download_sub` | "133 available · 2 updates ready" | plural, real count: "20 languages available offline" | **claim wired.** The count comes from `LanguageRepository`, which `TextViewModel` **already injects** — zero new dependencies, zero new startup work. It tracks the catalog automatically when the brains phase seeds the full list. |

"2 updates ready" is deleted outright and is **not** replaced: ML Kit's
`RemoteModelManager` exposes downloaded models, not model *versions*, so an
"updates available" number is not computable — printing one would be inventing
data again.

Real count today = 20 (`BundledLanguageCatalog.kt:18-38`, every id
`offlineAvailable = true`; the Room `language` table is never written, so the
bundled fallback is what ships). Displayed via `plurals` so one/other read
correctly in all three locales.

Parity: every added/changed key lands in `values`, `values-fil` and
`values-pt-rBR` in the same commit. The fil/pt-rBR wordings are authored here and
inherit the existing repo caveat — queued for native-speaker review (DoD gate 12,
`feature/text/src/main/res/values-fil/strings.xml:2-4`).

---

## 3. Room destructive migration

### Decision: write the real migration, delete the forward destructive fallback.

`MIGRATION_1_2` is derived from the exported schemas, not guessed. The only delta
between `schemas/…/1.json` and `2.json` is
`index_translation_source_text_source_lang_target_lang_engine` flipping
`"unique": false → true`; every column, type and the `language` table are
byte-identical. So the migration is: de-duplicate the C-8 key, drop the old
index, create the unique one.

De-duplication keeps the **newest** row per key and **promotes** `favourite` if
*any* row in the group was starred — a naive `MAX(id)` survivor would silently
unstar saved translations.

`fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)` is kept
deliberately. It cannot fire on an upgrade, so no user's data is ever destroyed
going forward; it only covers a hand-sideloaded older build, where Room's
alternative is an `IllegalStateException` on every launch — an unrecoverable
crash loop is worse for that user than a reset.
(Google: *"Room permanently deletes all data ... when it attempts to perform a
migration and there's no defined migration path"* —
developer.android.com/training/data-storage/room/migrating-db-versions.)

**Schema stance recorded for the future:** `tranzlate.db` is now a
migration-managed database. Every `@Database(version = …)` bump must ship a
`Migration` in `Migrations.kt` and an exported schema JSON in the same commit.
`MigrationCoverageTest` fails the build if the chain from 1 to
`TRANZLATE_DB_VERSION` has a gap, so "bumped the version, forgot the migration"
cannot reach a release. `TRANZLATE_DB_VERSION` is now the single constant the
annotation and the test both read.

**No importer for `translator.db`.** Deliberate: the old schema is a different
app's shape, the file is only present for users upgrading in place, and writing +
testing a cross-schema importer is not a same-day change. Recorded as follow-up,
not silently dropped.

---

## 4. Backup exposure

### Decision: keep Auto Backup on, exclude the translation database from **cloud** backup; allow it in device-to-device transfer.

The `translation` table stores `source_text` / `target_text` — literally what the
user typed (`TranslationEntity.kt:29,31`). With the stock templates (every rule
commented out) that is copied to the user's Google Drive.

Why targeted exclusion rather than `allowBackup="false"`:

- Cloud backup is the actual exposure named on the Data safety form ("transferred
  off device"). Excluding the DB removes it, so the form can honestly answer that
  translation history stays on the device.
- `allowBackup="false"` would also kill device-to-device transfer and drop the
  user's theme / language-pair / mobile-data-consent prefs on every new phone —
  a real UX regression for zero extra privacy, since D2D never leaves the user's
  own two devices.
- Android's split exists for exactly this: *"exclude a file or directory from
  Google Drive backups while still transferring it during device-to-device (D2D)
  transfers"* (developer.android.com/identity/data/autobackup).

Implementation:

- `data_extraction_rules.xml` (API 31+): `<cloud-backup>` excludes
  `tranzlate.db`, `tranzlate.db-wal`, `tranzlate.db-shm`; `<device-transfer>`
  is declared explicitly and carries no exclusions.
- `backup_rules.xml` (API 24-30, no D2D split): same three excludes. On those
  releases the conservative reading wins — no cloud copy of typed text.
- Exclude-only rulesets, so everything else (DataStore prefs, usage counters)
  keeps its default include. Restoring usage counters is deliberate: it is the
  direction that cannot be abused to reset a quota.
- `disableIfNoEncryptionCapabilities` is **not** set: with the DB excluded, the
  remaining payload is theme + language prefs, and switching it on would silently
  disable backup entirely on non-E2E devices.

Data safety consequence to carry into B8: the "transferred off device" answer is
now driven **only** by the GOT/GCT network calls, no longer by backup.

---

## Verification — results

| Gate | Result |
|---|---|
| `./gradlew test --continue --rerun-tasks` | **160 tests, 0 failures** across 22 suites — *except* the 4 pre-existing `KonsistArchitectureTest` failures below |
| `./gradlew :app:assembleTranzlateProdDebug` | ✅ |
| `./gradlew spotlessCheck detekt --rerun-tasks` | ✅ |
| `./gradlew :app:testTranzlateProdDebugUnitTest --tests "*Konsist*"` | ❌ 4/4 — **pre-existing, environmental** (below) |
| `./gradlew :app:assembleTranzlateProdRelease` (R8, extra) | ✅ |
| `./gradlew :app:lintTranzlateProdDebug` | ✅ — zero `MissingTranslation` / `MissingQuantity` / `ExtraTranslation` in the SARIF, so all three locales are in parity |

New tests: `MigrationCoverageTest` (3, `core/database`) · two
`offlineLanguageCount` cases in `TextViewModelTest` (32 → 34) · two
`ComingSoonScreen` back-affordance cases in `NavShellSmokeTest`.

Backup rules verified in the built artifact, not just in source: both compiled
XMLs inside `app-tranzlate-prod-release-unsigned.apk` carry
`tranzlate.db` / `-wal` / `-shm` excludes, and the `<cloud-backup>` /
`<device-transfer>` split survives resource compilation.

### The Konsist failures are the worktree, not the change

`KonsistArchitectureTest` slices out every file whose path contains
`/.claude/worktrees/` (`KonsistArchitectureTest.kt:23-26`) so that a live agent
worktree cannot double-count declarations. This branch **is** such a worktree, so
the slice removes the entire project and the scope is empty — every
`assertThat(...).isNotEmpty()` fails.

Disconfirmed as mine: with all changes stashed (clean `main` tree, same
directory) the run produces the **identical** 4/4 failure set. CI checks out at
the repo root, where the fragment is absent, so this gate is green there.

### Two things the gates surfaced that are NOT mine to fix

1. **`:app:assembleTranzlateProdDebugAndroidTest` does not compile at all** —
   `[Dagger/MissingBinding] com.codeboxlk.tranzlate.domain.access.PurchaseFlow
   cannot be provided without an @Provides-annotated method`. Also confirmed
   pre-existing by stashing. Issue #40 records the androidTest suite as *failing
   on API 35+*; it in fact does not build on any API, so the two new smoke
   assertions above are written but unrun.
2. **`versionCode = 1` < the shipped `4`** — see the context section. Play will
   reject the upload. `app/build.gradle.kts` is another track's file.

**Note on the "preview gate":** the brief said a Konsist rule enforces
`@PreviewLightDark`. In this tree `KonsistArchitectureTest` has four tests (ring
purity, brain-impl imports, `:lib:*` independence, FROZEN `Translator` package)
and **none** of them checks previews. The convention is followed here regardless
(`ComingSoonScreen` and `HomeContent` both keep theirs); the missing gate is
recorded as follow-up.
