package com.codeboxlk.tranzlate.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Schema version of `tranzlate.db`. The ONE place it is written: the annotation
 * below and `MigrationCoverageTest` both read this constant, so a bump cannot be
 * made in one and forgotten in the other.
 *
 * v2 (issue #53 A9): the C-8 cache index became UNIQUE — see [TRANZLATE_MIGRATIONS].
 */
const val TRANZLATE_DB_VERSION = 2

/**
 * DATA_MODEL — Room db `tranzlate.db`. Collections tables land with their feature spec.
 *
 * Migration-managed (see [TRANZLATE_MIGRATIONS] for the release-binding stance):
 * bumping [TRANZLATE_DB_VERSION] requires a `Migration` and an exported schema in
 * the same commit. There is no destructive fallback on the upgrade path.
 */
@Database(
    entities = [
        TranslationEntity::class,
        LanguageEntity::class,
    ],
    version = TRANZLATE_DB_VERSION,
    exportSchema = true,
)
abstract class TranzlateDatabase : RoomDatabase() {
    abstract fun translationDao(): TranslationDao

    abstract fun languageDao(): LanguageDao
}
