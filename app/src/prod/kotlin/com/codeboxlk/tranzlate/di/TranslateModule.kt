package com.codeboxlk.tranzlate.di

import com.codeboxlk.ads.AdsConfig
import com.codeboxlk.ads.AdsGateway
import com.codeboxlk.ads.NoOpAdsGateway
import com.codeboxlk.consent.ConsentGateway
import com.codeboxlk.consent.NoOpConsentGateway
import com.codeboxlk.subscription.NoOpSubscriptionGateway
import com.codeboxlk.subscription.SubscriptionConfig
import com.codeboxlk.subscription.SubscriptionGateway
import com.codeboxlk.tranzlate.core.access.RealFeatureAccess
import com.codeboxlk.tranzlate.core.access.SubscriptionPurchaseFlow
import com.codeboxlk.tranzlate.core.ads.RealAdsCoordinator
import com.codeboxlk.tranzlate.core.common.AppClock
import com.codeboxlk.tranzlate.core.common.DefaultDispatcherProvider
import com.codeboxlk.tranzlate.core.common.DispatcherProvider
import com.codeboxlk.tranzlate.core.config.AppConfig
import com.codeboxlk.tranzlate.core.translate.RealOfflineModelManager
import com.codeboxlk.tranzlate.core.translate.RealTranslator
import com.codeboxlk.tranzlate.core.usage.RealUsagePolicy
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
 * THE prod seam wiring (plan §6.1 — audit-mandated placement): the four brain
 * seams are @Provides-bound HERE, the single classpath position where all four
 * real impls are visible. `:core:*` brain modules carry NO Hilt modules.
 *
 * The androidTestProd contract wrapper (`@TestInstallIn(replaces =
 * [TranslateModule::class])`, TEST_A11Y_CONTRACT §1.6) targets this class by
 * name — do not rename/move without updating the contract.
 *
 * Also prod-side per plan §6.2: SystemAppClock + default dispatchers, and the
 * AppConfig → :lib:* config mapping (plan §2 Ring 4 — libraries stay
 * Tranzlate-agnostic; the host maps its config into theirs).
 */
@Module
@InstallIn(SingletonComponent::class)
object TranslateModule {
    // ---- The four ask-seams --------------------------------------------------

    @Provides
    @Singleton
    fun translator(impl: RealTranslator): Translator = impl

    @Provides
    @Singleton
    fun featureAccess(impl: RealFeatureAccess): FeatureAccess = impl

    @Provides
    @Singleton
    fun usagePolicy(impl: RealUsagePolicy): UsagePolicy = impl

    @Provides
    @Singleton
    fun adsCoordinator(impl: RealAdsCoordinator): AdsCoordinator = impl

    // ---- Brain-adjacent seams ------------------------------------------------

    @Provides
    @Singleton
    fun offlineModelManager(impl: RealOfflineModelManager): OfflineModelManager = impl

    @Provides
    @Singleton
    fun purchaseFlow(impl: SubscriptionPurchaseFlow): PurchaseFlow = impl

    // ---- Prod-side platform wiring (plan §6.2) -------------------------------

    @Provides
    @Singleton
    fun appClock(): AppClock = SystemAppClock()

    @Provides
    @Singleton
    fun dispatcherProvider(): DispatcherProvider = DefaultDispatcherProvider()

    // ---- AppConfig → reusable-library configs (plan §4 config flow) ----------

    @Provides
    @Singleton
    fun subscriptionConfig(config: AppConfig): SubscriptionConfig =
        SubscriptionConfig(projectKey = config.qonversionKey, offeringIds = emptyList())

    @Provides
    @Singleton
    fun subscriptionGateway(config: SubscriptionConfig): SubscriptionGateway =
        NoOpSubscriptionGateway(config) // TODO(#4-brains): swap for the SDK-backed gateway

    @Provides
    @Singleton
    fun adsConfig(config: AppConfig): AdsConfig =
        AdsConfig(
            applicationId = config.admobAppId,
            bannerUnitId = config.adUnitBanner,
            interstitialUnitId = config.adUnitInterstitial,
        )

    @Provides
    @Singleton
    fun adsGateway(config: AdsConfig): AdsGateway =
        NoOpAdsGateway(config) // TODO(#4-brains): swap for the SDK-backed gateway

    @Provides
    @Singleton
    fun consentGateway(): ConsentGateway = NoOpConsentGateway() // TODO(#4-brains): swap for the UMP-backed gateway
}
