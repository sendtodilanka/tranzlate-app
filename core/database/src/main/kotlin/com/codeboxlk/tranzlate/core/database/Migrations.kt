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
val TRANZLATE_MIGRATIONS: List<Migration> = listOf(MigrationOneToTwo)

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
