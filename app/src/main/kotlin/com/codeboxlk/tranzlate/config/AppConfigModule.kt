package com.codeboxlk.tranzlate.config

import com.codeboxlk.tranzlate.core.config.AppConfig
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Engine-agnostic — both variants get their AppConfig from BuildConfig here. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class AppConfigModule {

    @Binds
    @Singleton
    abstract fun appConfig(impl: BuildConfigAppConfig): AppConfig
}
