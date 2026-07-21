package com.codeboxlk.tranzlate.core.translatefake

import com.codeboxlk.tranzlate.core.common.AppClock
import com.codeboxlk.tranzlate.core.common.DefaultDispatcherProvider
import com.codeboxlk.tranzlate.core.common.DispatcherProvider
import com.codeboxlk.tranzlate.core.model.Tier
import com.codeboxlk.tranzlate.core.testing.FakeClock
import com.codeboxlk.tranzlate.core.testing.FakeFeatureAccess
import com.codeboxlk.tranzlate.core.testing.FakeTranslator
import com.codeboxlk.tranzlate.core.testing.FakeUsagePolicy
import com.codeboxlk.tranzlate.domain.access.FeatureAccess
import com.codeboxlk.tranzlate.domain.access.PurchaseFlow
import com.codeboxlk.tranzlate.domain.ads.AdsCoordinator
import com.codeboxlk.tranzlate.domain.translate.OfflineModelManager
import com.codeboxlk.tranzlate.domain.translate.Translator
import com.codeboxlk.tranzlate.domain.usage.UsagePolicy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * PRODUCTION `@InstallIn` module for the installable fake variant (plan §6.4;
 * TEST_A11Y_CONTRACT §1.6/§1.10 rationale: `@TestInstallIn` never compiles into
 * an installed APK, so Maestro needs a real production binding — NIA `demo`
 * flavor precedent).
 *
 * Covers the COMPLETE binding surface of the four excluded prod brains
 * (audit-mandated fix, plan §2 Ring 3): the four contract seams + offline-model
 * manager + purchase-flow + ads + prod-side wiring stand-ins (clock/dispatchers,
 * §6.2). Golden data lives in `:core:testing` — single source with unit/UI tests.
 */
@Module
@InstallIn(SingletonComponent::class)
object FakeTranslateModule {

    @Provides
    @Singleton
    fun translator(): Translator = FakeTranslator()

    @Provides
    @Singleton
    fun featureAccess(): FeatureAccess = FakeFeatureAccess(Tier.FREE)

    @Provides
    @Singleton
    fun usagePolicy(): UsagePolicy = FakeUsagePolicy(left = 5)

    @Provides
    @Singleton
    fun appClock(): AppClock = FakeClock()

    @Provides
    @Singleton
    fun offlineModelManager(): OfflineModelManager = FakeOfflineModelManager()

    @Provides
    @Singleton
    fun purchaseFlow(): PurchaseFlow = NoOpPurchaseFlow()

    @Provides
    @Singleton
    fun adsCoordinator(): AdsCoordinator = NoOpAdsCoordinator()

    @Provides
    @Singleton
    fun dispatcherProvider(): DispatcherProvider = DefaultDispatcherProvider()
}
