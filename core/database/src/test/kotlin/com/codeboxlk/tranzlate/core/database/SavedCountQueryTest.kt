package com.codeboxlk.tranzlate.core.database

import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * [TranslationDao.savedCountUsing] — the number in the remove-pack sheet's
 * *"3 saved phrases use Spanish"* (#130 PR-19, ruling U-10), RUN against a real
 * SQLite rather than reasoned about.
 *
 * ## Why the SQL itself is executed here
 *
 * Two doubles in this repo re-implement this count in Kotlin
 * (`FakeTranslationRepository` and the `FakeTranslationDao` inside
 * `TranslationRepositoryImplTest`), and neither of them can be wrong in the same
 * way the SQL can. A `UNION` quietly written as `UNION ALL`, a dropped
 * `favourite = 1`, a branch that only looks at `source_lang` — all three pass a
 * Kotlin double that was written from the same misunderstanding. Room validates
 * that the query COMPILES; it has no opinion on what it means.
 *
 * ## And why the query PLAN is asserted
 *
 * The ruling asks for an index-backed count, and the obvious spelling of this
 * query is not one. Measured before the indices were chosen, with `sqlite3` on
 * the exported schema:
 * `SELECT COUNT(*) … WHERE favourite = 1 AND (source_lang = ? OR target_lang = ?)`
 * plans as `SEARCH translation USING INDEX index_translation_favourite` — every
 * saved row visited and looked up in the table — unless `ANALYZE` has run, and
 * **Room never runs `ANALYZE`**. So the two indices would have shipped, cost
 * every write, and served nothing.
 *
 * The plan assertion is what stops that coming back. It is made against the SQL
 * Room ACTUALLY runs, captured through `setQueryCallback` rather than retyped
 * here: a test that explains its own copy of the string proves nothing about the
 * DAO, which is the shape of mistake this project keeps finding in its own gates.
 */
@RunWith(RobolectricTestRunner::class)
class SavedCountQueryTest {
    private lateinit var db: TranzlateDatabase
    private lateinit var dao: TranslationDao
    private val executed = mutableListOf<Pair<String, List<Any?>>>()

    @Before
    fun openDatabase() {
        db =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), TranzlateDatabase::class.java)
                .setQueryCallback({ sql, args -> executed += sql to args }, { it.run() })
                .build()
        dao = db.translationDao()
    }

    @After
    fun closeDatabase() = db.close()

    /**
     * The fixture is deliberately ASYMMETRIC. A history where every row uses the
     * language on both sides lets a source-only rule and a target-only rule both
     * pass — the "example data that happens to be in order" trap the mandatory
     * rules name as the third cause.
     */
    private suspend fun seedHistory() {
        insert(id = 1, source = "en", target = "fr", favourite = true) // target only
        insert(id = 2, source = "fr", target = "en", favourite = true) // source only
        insert(id = 3, source = "fr", target = "fr", favourite = true) // both sides
        insert(id = 4, source = "en", target = "de", favourite = true) // another language
        insert(id = 5, source = "en", target = "fr", favourite = false) // not saved
    }

    @Test
    fun `counts saved phrases that use the language on either side`() =
        runTest {
            seedHistory()

            assertThat(dao.savedCountUsing("fr")).isEqualTo(3)
        }

    /** A row with the language on BOTH sides is one saved phrase — `UNION`, not `UNION ALL`. */
    @Test
    fun `a phrase that uses the language on both sides counts once`() =
        runTest {
            insert(id = 1, source = "fr", target = "fr", favourite = true)

            assertThat(dao.savedCountUsing("fr")).isEqualTo(1)
        }

    /** History is not the Saved tab: an unstarred row using the language is not counted. */
    @Test
    fun `unsaved history rows are not counted`() =
        runTest {
            insert(id = 1, source = "en", target = "fr", favourite = false)
            insert(id = 2, source = "fr", target = "en", favourite = false)

            assertThat(dao.savedCountUsing("fr")).isEqualTo(0)
        }

    /** Zero is a real answer — the sheet draws no line at all for it. */
    @Test
    fun `a language nothing was saved in counts zero`() =
        runTest {
            seedHistory()

            assertThat(dao.savedCountUsing("ja")).isEqualTo(0)
        }

    /** Neither branch may leak another language's rows. */
    @Test
    fun `the count is per language`() =
        runTest {
            seedHistory()

            assertThat(dao.savedCountUsing("de")).isEqualTo(1)
            assertThat(dao.savedCountUsing("en")).isEqualTo(3)
        }

    /**
     * The plan, on the query Room really issues.
     *
     * Both branches must be answered from a COVERING index: covering means
     * SQLite never opens the table row at all, which is the difference between a
     * count proportional to the matches and a count proportional to everything
     * the user has ever starred. The table must not be scanned, and the
     * single-column `index_translation_favourite` must not be what serves it —
     * that is the plan the rejected `OR` spelling produces.
     */
    @Test
    fun `the count is answered from both covering indices and never scans`() =
        runTest {
            seedHistory()
            executed.clear()
            dao.savedCountUsing("fr")

            val (sql, args) = executed.single { it.first.contains("COUNT(*)") }
            val plan = explain(sql, args)

            assertThat(plan).contains("COVERING INDEX index_translation_favourite_source_lang")
            assertThat(plan).contains("COVERING INDEX index_translation_favourite_target_lang")
            assertThat(plan).doesNotContain("SCAN translation")
            assertThat(plan).doesNotContain("USING INDEX index_translation_favourite ")
        }

    /** `EXPLAIN QUERY PLAN`'s `detail` column, one row per line. */
    private fun explain(
        sql: String,
        args: List<Any?>,
    ): String {
        val cursor =
            db.openHelper.readableDatabase.query(
                SimpleSQLiteQuery("EXPLAIN QUERY PLAN $sql", args.toTypedArray()),
            )
        return cursor.use {
            buildString {
                while (it.moveToNext()) appendLine(it.getString(it.columnCount - 1))
            }
        }
    }

    private suspend fun insert(
        id: Long,
        source: String,
        target: String,
        favourite: Boolean,
    ) {
        dao.insert(
            TranslationEntity(
                id = id,
                sourceLang = source,
                sourceText = "phrase $id",
                targetLang = target,
                targetText = "answer $id",
                engine = "OFFLINE_MLKIT",
                favourite = favourite,
                createdAt = id,
            ),
        )
    }
}
