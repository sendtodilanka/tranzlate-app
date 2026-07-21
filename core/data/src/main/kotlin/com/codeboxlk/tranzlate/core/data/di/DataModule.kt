package com.codeboxlk.tranzlate.core.data.di

import com.codeboxlk.tranzlate.core.config.RemoteConfigSource
import com.codeboxlk.tranzlate.core.data.config.StaticRemoteConfigSource
import com.codeboxlk.tranzlate.core.data.network.ConnectivityMonitor
import com.codeboxlk.tranzlate.core.data.network.StubConnectivityMonitor
import com.codeboxlk.tranzlate.core.data.repository.LanguageRepositoryImpl
import com.codeboxlk.tranzlate.core.data.repository.TranslationRepositoryImpl
import com.codeboxlk.tranzlate.domain.repository.LanguageRepository
import com.codeboxlk.tranzlate.domain.repository.TranslationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Engine-agnostic data bindings — present in BOTH prod and fake variants.
 * The four brain seams are deliberately NOT bound here (plan §6.1 — they live in
 * `:app/src/prod` TranslateModule / `:core:translate-fake` FakeTranslateModule).
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {
    @Binds
    abstract fun translationRepository(impl: TranslationRepositoryImpl): TranslationRepository

    @Binds
    abstract fun languageRepository(impl: LanguageRepositoryImpl): LanguageRepository

    @Binds
    abstract fun connectivityMonitor(impl: StubConnectivityMonitor): ConnectivityMonitor

    @Binds
    abstract fun remoteConfigSource(impl: StaticRemoteConfigSource): RemoteConfigSource
}
