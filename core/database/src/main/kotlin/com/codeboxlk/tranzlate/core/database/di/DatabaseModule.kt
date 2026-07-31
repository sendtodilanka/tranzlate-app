package com.codeboxlk.tranzlate.core.database.di

import android.content.Context
import androidx.room.Room
import com.codeboxlk.tranzlate.core.database.LanguageDao
import com.codeboxlk.tranzlate.core.database.TRANZLATE_MIGRATIONS
import com.codeboxlk.tranzlate.core.database.TranslationDao
import com.codeboxlk.tranzlate.core.database.TranzlateDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
    ): TranzlateDatabase =
        Room
            .databaseBuilder(context, TranzlateDatabase::class.java, "tranzlate.db")
            // The pre-launch `fallbackToDestructiveMigration(dropAllTables = true)`
            // is GONE (A8). This ships as an update over a live install base, and a
            // forward destructive fallback means every future schema bump silently
            // deletes the user's whole translation history. Real migrations instead —
            // see TRANZLATE_MIGRATIONS for the binding schema stance. Registered by
            // iterating the ONE list rather than spelling the migrations out here —
            // a second hand-written list is exactly the drift MigrationCoverageTest
            // exists to catch, and it would be invisible to it.
            .apply { TRANZLATE_MIGRATIONS.forEach { migration -> addMigrations(migration) } }
            // Downgrade-only, and deliberately kept. It cannot fire on an upgrade, so
            // no user's data is destroyed going forward. It covers the one case Room
            // otherwise answers with an IllegalStateException on every launch — a
            // hand-sideloaded OLDER build — where an unrecoverable crash loop is a
            // worse outcome for that user than a reset.
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()

    @Provides
    fun translationDao(database: TranzlateDatabase): TranslationDao = database.translationDao()

    @Provides
    fun languageDao(database: TranzlateDatabase): LanguageDao = database.languageDao()
}
