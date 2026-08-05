package com.codeboxlk.tranzlate.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [MigrationThreeToFour] on real device SQLite (issue #241) — the instrumented half
 * that complements the Robolectric [MigrationThreeToFourTest].
 *
 * ## What this adds over the Robolectric sibling
 *
 * [MigrationThreeToFourTest] runs under Robolectric and checks the SCHEMA SHAPE
 * only: it creates an EMPTY v3 database, migrates it, and lets
 * `runMigrationsAndValidate` compare the result against `4.json`. It never puts a
 * row in the database, so it cannot see a migration that corrupts or drops the
 * user's data while leaving the schema correct. This test closes that half:
 *
 *  1. **Row survival** — a real `translation` row is INSERTed into a v3 database,
 *     the migration runs, and every column of that row is asserted byte-identical
 *     afterwards, plus the two new indices are asserted to exist with exactly the
 *     columns `4.json` declares, in order. A schema-only check — Robolectric's, or
 *     `runMigrationsAndValidate` itself — stays green even when the row is gone.
 *  2. **Full 1 -> 4 chain** — a v1 database is walked all the way to v4 through
 *     every migration in order and validated against `4.json` at the end.
 *
 * It runs on device SQLite through the framework open factory (the 2-arg
 * [MigrationTestHelper] constructor), NOT a `BundledSQLiteDriver`: production
 * [MigrationThreeToFour] only overrides `migrate(SupportSQLiteDatabase)`, and only
 * the framework factory hands the base `Migration.migrate(SQLiteConnection)` a
 * `SupportSQLiteConnection` to bridge from — a driver would throw
 * `NotImplementedError`. (Room 2.8.4; the skill's Room 3 `SQLiteConnection`/driver
 * form does not apply to this module. Same reasoning as the sibling's KDoc.)
 *
 * ## Test names are camelCase, not the sibling's backticked spaces — deliberately
 *
 * This is `src/androidTest`, so it dexes and runs on the API 24 emulator. D8 emits
 * the DEX format for this module's `minSdk = 24`, whose `SimpleName` grammar has no
 * space character; spaces in member names need DEX 040 (minSdk 30+). A test named
 * with backticks and spaces compiles for the JVM `test` task but is not a valid
 * instrumented method on API 24, so these are camelCase.
 *
 * ## Mutate-first (rule 11; #242 is "tests that cannot fail")
 *
 * Chosen before the assertions were written, and deliberately one the schema
 * validator CANNOT catch. Temporarily append to [MigrationThreeToFour.migrate],
 * AFTER its two CREATE INDEX statements:
 *
 * ```
 * db.execSQL("UPDATE translation SET target_text = ''")
 * ```
 *
 * The schema still matches `4.json`, so `runMigrationsAndValidate` still passes —
 * but [migrating3To4PreservesTheRowAndAddsBothIndices] then fails on
 * `getString(4) == "Buenos días"` (RED). Remove the line to go GREEN. A blunter
 * variant, `db.execSQL("DELETE FROM translation")`, turns the same test RED on
 * `cursor.count == 1`. No schema-only test — the Robolectric sibling included —
 * notices either, which is the exact gap this test exists to close.
 *
 * This RED proof runs on `Tranzlate_API24`; it is NOT executed here (the
 * orchestrator holds the single emulator).
 */
@RunWith(AndroidJUnit4::class)
class MigrationThreeToFourAndroidTest {
    /**
     * The framework open factory (the 2-arg constructor's default) rather than a
     * driver — see the class KDoc: it is what lets production
     * [MigrationThreeToFour]'s `migrate(SupportSQLiteDatabase)` override run.
     */
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            TranzlateDatabase::class.java,
        )

    /** Each test builds its database from scratch; clear any file a prior run left. */
    @Before
    fun deleteDatabases() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(ROW_SURVIVAL_DB)
        context.deleteDatabase(FULL_CHAIN_DB)
    }

    @Test
    fun migrating3To4PreservesTheRowAndAddsBothIndices() {
        // A v3 database, built from the committed `3.json` — the state on the phone
        // of a user who installed before v4 shipped — with one real saved row in it.
        // The non-ASCII target text also proves UTF-8 survives the upgrade.
        helper.createDatabase(ROW_SURVIVAL_DB, version = 3).use { db ->
            db.execSQL(
                "INSERT INTO translation (id, source_lang, source_text, target_lang, " +
                    "target_text, engine, detected, favourite, created_at) " +
                    "VALUES (7, 'en', 'Good morning', 'es', 'Buenos días', 'MLKIT', 1, 1, 1699999999999)",
            )
        }

        // Runs MigrationThreeToFour's real SQL on device SQLite and validates the
        // resulting schema against `4.json` (throws on any table/column/index
        // mismatch), then hands back the open, migrated database.
        val migrated =
            helper.runMigrationsAndValidate(
                ROW_SURVIVAL_DB,
                version = 4,
                validateDroppedTables = true,
                MigrationThreeToFour,
            )

        migrated.use { db ->
            // Row survival — the half the Robolectric schema test does NOT cover.
            // A migration that DROPs the row or blanks a column still validates
            // against `4.json`, so only reading the row back can catch it.
            db
                .query(
                    "SELECT id, source_lang, source_text, target_lang, target_text, " +
                        "engine, detected, favourite, created_at FROM translation WHERE id = 7",
                ).use { cursor ->
                    assertThat(cursor.count).isEqualTo(1)
                    assertThat(cursor.moveToFirst()).isTrue()
                    assertThat(cursor.getLong(0)).isEqualTo(7L)
                    assertThat(cursor.getString(1)).isEqualTo("en")
                    assertThat(cursor.getString(2)).isEqualTo("Good morning")
                    assertThat(cursor.getString(3)).isEqualTo("es")
                    assertThat(cursor.getString(4)).isEqualTo("Buenos días")
                    assertThat(cursor.getString(5)).isEqualTo("MLKIT")
                    assertThat(cursor.getInt(6)).isEqualTo(1)
                    assertThat(cursor.getInt(7)).isEqualTo(1)
                    assertThat(cursor.getLong(8)).isEqualTo(1699999999999L)
                }

            // And, independently of what Room's validator covers, the two new
            // indices exist on `translation` with exactly the columns `4.json`
            // declares, in order.
            assertThat(indexColumns(db, "index_translation_favourite_source_lang"))
                .containsExactly("favourite", "source_lang")
                .inOrder()
            assertThat(indexColumns(db, "index_translation_favourite_target_lang"))
                .containsExactly("favourite", "target_lang")
                .inOrder()
        }
    }

    @Test
    fun migratingFullChain1To4ProducesTheV4Schema() {
        // The oldest install that can still be walked forward: a v1 database built
        // from `1.json`, migrated the whole way with every step in the chain. The
        // final `runMigrationsAndValidate` validates the result against `4.json`.
        helper.createDatabase(FULL_CHAIN_DB, version = 1).close()

        helper
            .runMigrationsAndValidate(
                FULL_CHAIN_DB,
                version = 4,
                validateDroppedTables = true,
                MigrationOneToTwo,
                MigrationTwoToThree,
                MigrationThreeToFour,
            ).close()
    }

    /** The columns an index covers, in position order, read from the live database. */
    private fun indexColumns(
        db: SupportSQLiteDatabase,
        indexName: String,
    ): List<String> =
        db.query("PRAGMA index_info(`$indexName`)").use { cursor ->
            val seqno = cursor.getColumnIndexOrThrow("seqno")
            val name = cursor.getColumnIndexOrThrow("name")
            buildList {
                while (cursor.moveToNext()) add(cursor.getInt(seqno) to cursor.getString(name))
            }.sortedBy { it.first }.map { it.second }
        }

    private companion object {
        const val ROW_SURVIVAL_DB = "migration-3-to-4-row-survival.db"
        const val FULL_CHAIN_DB = "migration-1-to-4-chain.db"
    }
}
