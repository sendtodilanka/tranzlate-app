package com.codeboxlk.tranzlate.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DataStoreModule {
    @Provides
    @Singleton
    fun preferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            // DataStore does not self-heal: without a handler a corrupt file makes
            // every read throw CorruptionException forever, so the app crashes on
            // launch and the only user-side fix is clearing app data. Replacing the
            // file loses the stored preferences, which is the right trade — they are
            // all choices the user can make again, and the alternative is a dead app.
            // Load-bearing from issue #17 A6 onward, where the splash waits on this
            // flow: an unhandled corruption there would hang the splash forever
            // instead of merely crashing.
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        ) {
            context.preferencesDataStoreFile("tranzlate_preferences")
        }
}
