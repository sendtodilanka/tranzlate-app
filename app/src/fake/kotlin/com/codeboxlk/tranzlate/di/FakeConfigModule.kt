package com.codeboxlk.tranzlate.di

import com.codeboxlk.tranzlate.core.config.RemoteConfigSource
import com.codeboxlk.tranzlate.core.data.config.StaticRemoteConfigSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * The fake variant's remote-config binding (plan §6.4 — a PRODUCTION
 * `@InstallIn` module, because `@TestInstallIn` never compiles into an installed
 * APK and Maestro needs the real thing).
 *
 * Counterpart to `:app/src/prod` TranslateModule's Firebase binding. Keeping the
 * static source here is what guarantees a Maestro run cannot be changed by a
 * console edit — and cannot reach a real billing key, since the static source
 * answers every credential with "".
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class FakeConfigModule {
    @Binds
    abstract fun remoteConfigSource(impl: StaticRemoteConfigSource): RemoteConfigSource
}
