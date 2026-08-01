package com.codeboxlk.tranzlate.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The migration chain for `tranzlate.db`.
 *
 * **Schema stance (release-binding).** This database is migration-managed. Every
 * bump of [TRANZLATE_DB_VERSION] ships a [Migration] in this list and its exported
 * `schemas/…/<n>.json` in the SAME commit. There is no destructive fallback on the
 * upgrade path any more: what a user typed is theirs, and Room's own warning is
 * unambiguous — with `fallbackToDestructiveMigration` Room *"permanently deletes
 * all data from the tables in the user's database"* the moment a version bump has
 * no migration (developer.android.com/training/data-storage/room/migrating-db-versions).
 *
 * `MigrationCoverageTest` fails the build if this chain has a gap, so the mistake
 * that destructive fallback used to paper over cannot reach a release instead.
 */
val TRANZLATE_MIGRATIONS: List<Migration> = listOf(MigrationOneToTwo, MigrationTwoToThree)

/**
 * v1 → v2 — the C-8 cache key becomes UNIQUE.
 *
 * Derived from the exported schemas, not from memory: the ONLY delta between
 * `schemas/…/1.json` and `2.json` is
 * `index_translation_source_text_source_lang_target_lang_engine` flipping
 * `"unique": false` → `true`. Columns, affinities, the primary key and the whole
 * `language` table are byte-identical, so nothing else is touched here.
 *
 * A v1 database may already hold rows that violate the new key (that race is
 * exactly why the index became unique), so the duplicates are collapsed first —
 * SQLite refuses to build a unique index over a table that already breaks it, and
 * the migration would throw mid-upgrade.
 */
internal object MigrationOneToTwo : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Carry the star across the collapse FIRST. A duplicate group can hold a
        // favourited older row and an unfavourited newer one; keeping the newest
        // blindly would silently unstar something the user chose to save. Setting
        // the flag on the whole group before deleting means the survivor inherits
        // it whichever row wins.
        db.execSQL(
            """
            UPDATE translation SET favourite = 1
            WHERE id IN (
                SELECT t.id FROM translation t
                WHERE EXISTS (
                    SELECT 1 FROM translation o
                    WHERE o.source_text = t.source_text
                      AND o.source_lang = t.source_lang
                      AND o.target_lang = t.target_lang
                      AND o.engine = t.engine
                      AND o.favourite = 1
                )
            )
            """.trimIndent(),
        )
        // Keep one row per C-8 key. MAX(id) is the newest: `id` is INTEGER PRIMARY
        // KEY AUTOINCREMENT and `created_at` is stamped at insert, so id order and
        // recency agree. `id` is NOT NULL, so the subquery cannot return NULL and
        // poison `NOT IN`.
        db.execSQL(
            """
            DELETE FROM translation
            WHERE id NOT IN (
                SELECT MAX(id) FROM translation
                GROUP BY source_text, source_lang, target_lang, engine
            )
            """.trimIndent(),
        )
        // Full statements, not constants shared with the entity: a migration has to
        // keep describing the schema as it was at THIS version even after the
        // entity moves on (Room migration guidance).
        db.execSQL("DROP INDEX IF EXISTS `index_translation_source_text_source_lang_target_lang_engine`")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_translation_source_text_source_lang_target_lang_engine` " +
                "ON `translation` (`source_text`, `source_lang`, `target_lang`, `engine`)",
        )
    }
}

/**
 * v2 → v3 — the `language_usage` table lands (issue #122).
 *
 * Purely additive: the ONLY delta between `schemas/…/2.json` and `3.json` is the
 * new table — translation-success stamps per (id, role), what Manage packs'
 * deletion honesty reads from. No existing table, index or row is touched, so
 * there is nothing to collapse or carry across.
 *
 * The statement matches Room's own `createSql` for [LanguageUsageEntity]
 * byte-for-byte (checked against the exported `3.json`) — a drifted hand-written
 * CREATE is exactly what Room's schema validation would reject on next open.
 * Full statement, not a constant shared with the entity: a migration has to keep
 * describing the schema as it was at THIS version even after the entity moves on
 * (Room migration guidance).
 *
 * Verification stance, honestly: `MigrationCoverageTest` gates this step
 * structurally (chain completeness + the exported schema per version), but the
 * SQL is NOT executed in CI — Room's `MigrationTestHelper` needs the
 * instrumentation suite, which is red on API 35+ (issue #40, follow-up #111).
 * Same pre-existing gap as v1→v2, recorded rather than papered over.
 *
 * MagicNumber is suppressed, not obeyed: a migration step IS its two version
 * numbers, frozen at the moment it shipped — naming them constants would invite
 * sharing with [TRANZLATE_DB_VERSION], which is exactly the drift this file's
 * header forbids. (1 and 2 sit in the rule's default ignore list, which is the
 * only reason v1→v2 needed no suppression.)
 */
@Suppress("MagicNumber")
internal object MigrationTwoToThree : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `language_usage` (" +
                "`lang_id` TEXT NOT NULL, " +
                "`role` TEXT NOT NULL, " +
                "`last_used_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`lang_id`, `role`))",
        )
    }
}
