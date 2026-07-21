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
    ): TranzlateDatabase = Room.databaseBuilder(context, TranzlateDatabase::class.java, "tranzlate.db").build()

    @Provides
    fun translationDao(database: TranzlateDatabase): TranslationDao = database.translationDao()

    @Provides
    fun languageDao(database: TranzlateDatabase): LanguageDao = database.languageDao()
}
