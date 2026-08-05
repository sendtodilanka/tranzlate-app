package com.codeboxlk.tranzlate.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [MigrationThreeToFour] — its two `CREATE INDEX` statements RUN against a real
 * SQLite and validated against the exported `4.json`, rather than only reasoned
 * about (#241).
 *
 * ## Why this test exists
 *
 * `MigrationCoverageTest` is structural: it proves the chain has no gap and that
 * every version's schema is committed, and its own KDoc says it never runs a
 * step's SQL. `SavedCountQueryTest` builds from [TranslationEntity]'s annotations —
 * the FRESH-INSTALL path, which never walks a migration. So until this test,
 * nothing executed `MigrationThreeToFour`'s SQL: a wrong index name or column list
 * would crash every existing user on their next launch (Room validates the schema
 * on open) while CI stayed green and a fresh install worked. That is the exact
 * shape #241 describes.
 *
 * ## How it runs without an emulator (#40)
 *
 * Room's [MigrationTestHelper] under Robolectric — the same JVM Android runtime
 * `SavedCountQueryTest` already uses. `createDatabase(TEST_DB, 3)` builds a v3
 * database from the exported `3.json`; `runMigrationsAndValidate(TEST_DB, 4, …)`
 * then EXECUTES [MigrationThreeToFour] and validates the resulting schema against
 * `4.json`, throwing if a table, column or **index** does not match.
 *
 * room-testing reads the exported schemas through the unit-test `AssetManager`
 * (`<db-fully-qualified-name>/<version>.json`), so `core/database/build.gradle.kts`
 * exposes the committed `schemas/` directory as a test asset source. The helper's
 * default open factory is the framework one, so each migration step is handed a
 * `SupportSQLiteConnection` and `Migration.migrate(SQLiteConnection)` bridges to
 * [MigrationThreeToFour]'s `migrate(SupportSQLiteDatabase)` override — the real
 * production code path, unchanged.
 *
 * ## Mutate-first (rule 11, and #242 is "tests that cannot fail")
 *
 * The mutation was chosen before the test was trusted. Dropping `source_lang` from
 * the first `CREATE INDEX`, or reordering its columns, makes the migrated schema
 * disagree with `4.json` — `runMigrationsAndValidate` itself catches that. But
 * misspelling only the index NAME is invisible to `runMigrationsAndValidate`:
 * Room's `TableInfo` comparison ignores index names that start with its own
 * `index_` prefix (verified against room-runtime 2.8.4), so the explicit
 * `indexColumns(...)` assertions below are what catch that case — they are not
 * redundant. All three mutations turn this test RED (#241 co-verify, 3 mutations).
 */
@RunWith(RobolectricTestRunner::class)
class MigrationThreeToFourTest {
    /**
     * The framework open factory (the default) rather than a driver: it hands each
     * migration a `SupportSQLiteConnection`, which is what makes the base
     * `Migration.migrate(SQLiteConnection)` delegate to
     * [MigrationThreeToFour]'s `migrate(SupportSQLiteDatabase)` instead of throwing
     * `NotImplementedError`. A bundled/android driver would not, and the production
     * migration only overrides the `SupportSQLiteDatabase` form.
     */
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            TranzlateDatabase::class.java,
        )

    @Test
    fun `migrating 3 to 4 adds both saved-by-language indices and matches the exported v4 schema`() {
        // A v3 database, built from the committed `3.json` — the state on the phone
        // of a user who installed before v4 shipped.
        helper.createDatabase(TEST_DB, version = 3).close()

        // Runs MigrationThreeToFour's real SQL and validates the result against
        // `4.json`. This call THROWS on any table/column/index mismatch, so the
        // migration executing wrongly fails the test on its own.
        val migrated =
            helper.runMigrationsAndValidate(
                TEST_DB,
                version = 4,
                validateDroppedTables = true,
                MigrationThreeToFour,
            )

        // And, independently of what Room's validator covers, assert the concrete
        // user-facing outcome: the two new indices exist on `translation` with
        // exactly the columns `4.json` declares, in order.
        migrated.use { db ->
            assertThat(indexColumns(db, "index_translation_favourite_source_lang"))
                .containsExactly("favourite", "source_lang")
                .inOrder()
            assertThat(indexColumns(db, "index_translation_favourite_target_lang"))
                .containsExactly("favourite", "target_lang")
                .inOrder()
        }
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
        const val TEST_DB = "migration-three-to-four-test.db"
    }
}
