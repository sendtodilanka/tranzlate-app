package com.codeboxlk.tranzlate.core.database.di

import android.content.Context
import androidx.room.Room
import com.codeboxlk.tranzlate.core.database.LanguageDao
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
            // ⚠ PRE-LAUNCH ONLY (issue #53 / A8, DATA_MODEL "Migration policy"): with no
            // policy at all, the first version bump throws for every existing install.
            // There are no shipped users yet, so a schema change may drop data. This
            // line MUST be replaced by real Migration objects before the first release
            // — release-checklist item; grep for fallbackToDestructiveMigration.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun translationDao(database: TranzlateDatabase): TranslationDao = database.translationDao()

    @Provides
    fun languageDao(database: TranzlateDatabase): LanguageDao = database.languageDao()
}
