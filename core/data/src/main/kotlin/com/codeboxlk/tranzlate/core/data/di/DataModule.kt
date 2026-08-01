package com.codeboxlk.tranzlate.core.data.di

import com.codeboxlk.tranzlate.core.common.ApplicationScope
import com.codeboxlk.tranzlate.core.common.DispatcherProvider
import com.codeboxlk.tranzlate.core.data.repository.DownloadPrefsRepositoryImpl
import com.codeboxlk.tranzlate.core.data.repository.LanguageRepositoryImpl
import com.codeboxlk.tranzlate.core.data.repository.LanguageUsageRepositoryImpl
import com.codeboxlk.tranzlate.core.data.repository.ThemePrefsRepositoryImpl
import com.codeboxlk.tranzlate.core.data.repository.TranslatePrefsRepositoryImpl
import com.codeboxlk.tranzlate.core.data.repository.TranslationRepositoryImpl
import com.codeboxlk.tranzlate.domain.repository.DownloadPrefsRepository
import com.codeboxlk.tranzlate.domain.repository.LanguageRepository
import com.codeboxlk.tranzlate.domain.repository.LanguageUsageRepository
import com.codeboxlk.tranzlate.domain.repository.ThemePrefsRepository
import com.codeboxlk.tranzlate.domain.repository.TranslatePrefsRepository
import com.codeboxlk.tranzlate.domain.repository.TranslationRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * Engine-agnostic data bindings — present in BOTH prod and fake variants.
 * The four brain seams are deliberately NOT bound here (plan §6.1 — they live in
 * `:app/src/prod` TranslateModule / `:core:translate-fake` FakeTranslateModule).
 *
 * `RemoteConfigSource` left this module for the same reason: it now has two
 * genuinely different implementations per engine flavor (Firebase in prod, the
 * static defaults in fake), so binding it here would have forced the network-
 * backed one onto Maestro runs.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {
    @Binds
    abstract fun translationRepository(impl: TranslationRepositoryImpl): TranslationRepository

    @Binds
    abstract fun languageRepository(impl: LanguageRepositoryImpl): LanguageRepository

    @Binds
    abstract fun languageUsageRepository(impl: LanguageUsageRepositoryImpl): LanguageUsageRepository

    @Binds
    abstract fun translatePrefsRepository(impl: TranslatePrefsRepositoryImpl): TranslatePrefsRepository

    @Binds
    abstract fun themePrefsRepository(impl: ThemePrefsRepositoryImpl): ThemePrefsRepository

    @Binds
    abstract fun downloadPrefsRepository(impl: DownloadPrefsRepositoryImpl): DownloadPrefsRepository

    companion object {
        /**
         * Application-lifetime scope for fire-and-forget data writes (first
         * user: the translate flow's usage stamper, issue #122). Provided HERE
         * — not in the per-flavor TranslateModules — because it is
         * engine-agnostic plumbing both flavors need, and a single provider
         * cannot drift. SupervisorJob so one failed write never cancels the
         * scope for every later one; IO because everything launched on it is
         * disk work.
         */
        @Provides
        @Singleton
        @ApplicationScope
        fun applicationScope(dispatchers: DispatcherProvider): CoroutineScope =
            CoroutineScope(SupervisorJob() + dispatchers.io)
    }
}
