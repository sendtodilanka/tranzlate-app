package com.codeboxlk.tranzlate.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

/** DATA_MODEL — Room db `tranzlate.db`. Collections tables land with their feature spec. */
@Database(
    entities = [
        TranslationEntity::class,
        LanguageEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class TranzlateDatabase : RoomDatabase() {
    abstract fun translationDao(): TranslationDao

    abstract fun languageDao(): LanguageDao
}
