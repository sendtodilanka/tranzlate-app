package com.codeboxlk.tranzlate

import com.codeboxlk.tranzlate.core.common.AppClock
import com.codeboxlk.tranzlate.core.common.DefaultDispatcherProvider
import com.codeboxlk.tranzlate.core.common.DispatcherProvider
import com.codeboxlk.tranzlate.core.model.Tier
import com.codeboxlk.tranzlate.core.testing.FakeClock
import com.codeboxlk.tranzlate.core.testing.FakeFeatureAccess
import com.codeboxlk.tranzlate.core.testing.FakeTranslator
import com.codeboxlk.tranzlate.core.testing.FakeUsagePolicy
import com.codeboxlk.tranzlate.di.TranslateModule
import com.codeboxlk.tranzlate.domain.access.FeatureAccess
import com.codeboxlk.tranzlate.domain.ads.AdsCoordinator
import com.codeboxlk.tranzlate.domain.translate.Translator
import com.codeboxlk.tranzlate.domain.usage.UsagePolicy
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * TEST_A11Y_CONTRACT §1.6 mandated Hilt test wiring — verbatim: replaces the
 * prod TranslateModule with the golden fakes for @HiltAndroidTest runs on PROD
 * variants (fake variants disable androidTest entirely, plan §6.3 — their
 * determinism is production-installed via :core:translate-fake).
 *
 * NOTE: `replaces` removes ALL TranslateModule bindings; providers beyond the
 * contract's four get added here as instrumentation tests start requesting them
 * (Dagger validates lazily — unused absent bindings don't break the graph).
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [TranslateModule::class])
object FakeTranslateModule {
    @Provides
    @Singleton
    fun translator(): Translator = FakeTranslator()

    @Provides
    @Singleton
    fun featureAccess(): FeatureAccess = FakeFeatureAccess(Tier.FREE)

    @Provides
    @Singleton
    fun usage(): UsagePolicy = FakeUsagePolicy(left = 5)

    @Provides
    @Singleton
    fun clock(): AppClock = FakeClock()

    // Requested since issue #11 (TextViewModel → TranslateTextUseCase): the ads
    // ask must exist for the graph; show/no-show is a no-op decision here.
    @Provides
    @Singleton
    fun adsCoordinator(): AdsCoordinator =
        object : AdsCoordinator {
            override suspend fun onTranslationCompleted() = Unit
        }

    @Provides
    @Singleton
    fun dispatcherProvider(): DispatcherProvider = DefaultDispatcherProvider()
}
