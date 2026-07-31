package com.codeboxlk.tranzlate.di

import android.app.Application
import android.content.Context
import com.codeboxlk.ads.AdsConfig
import com.codeboxlk.ads.AdsGateway
import com.codeboxlk.ads.NoOpAdsGateway
import com.codeboxlk.consent.ConsentGateway
import com.codeboxlk.consent.NoOpConsentGateway
import com.codeboxlk.subscription.ForegroundActivityProvider
import com.codeboxlk.subscription.QonversionSubscriptionGateway
import com.codeboxlk.subscription.SubscriptionConfig
import com.codeboxlk.subscription.SubscriptionGateway
import com.codeboxlk.tranzlate.core.access.RealFeatureAccess
import com.codeboxlk.tranzlate.core.access.SubscriptionPurchaseFlow
import com.codeboxlk.tranzlate.core.ads.RealAdsCoordinator
import com.codeboxlk.tranzlate.core.common.AppClock
import com.codeboxlk.tranzlate.core.common.ConnectivityMonitor
import com.codeboxlk.tranzlate.core.common.DefaultDispatcherProvider
import com.codeboxlk.tranzlate.core.common.DispatcherProvider
import com.codeboxlk.tranzlate.core.common.StorageProbe
import com.codeboxlk.tranzlate.core.config.AppConfig
import com.codeboxlk.tranzlate.core.config.RemoteConfigSource
import com.codeboxlk.tranzlate.core.config.effectiveQonversionKey
import com.codeboxlk.tranzlate.core.data.config.FirebaseRemoteConfigSource
import com.codeboxlk.tranzlate.core.translate.RealOfflineModelManager
import com.codeboxlk.tranzlate.core.translate.RealTranslator
import com.codeboxlk.tranzlate.core.usage.DataStoreUsagePersistence
import com.codeboxlk.tranzlate.core.usage.RealUsagePolicy
import com.codeboxlk.tranzlate.core.usage.UsagePersistence
import com.codeboxlk.tranzlate.di.AppStartupTask
import com.codeboxlk.tranzlate.domain.access.FeatureAccess
import com.codeboxlk.tranzlate.domain.access.PurchaseFlow
import com.codeboxlk.tranzlate.domain.ads.AdsCoordinator
import com.codeboxlk.tranzlate.domain.translate.OfflineModelManager
import com.codeboxlk.tranzlate.domain.translate.Translator
import com.codeboxlk.tranzlate.domain.usage.UsagePolicy
import com.codeboxlk.tranzlate.feature.paywall.PaywallPlan
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import javax.inject.Qualifier
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
    fun usagePersistence(impl: DataStoreUsagePersistence): UsagePersistence = impl

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
    fun connectivityMonitor(
        @ApplicationContext context: Context,
    ): ConnectivityMonitor = AndroidConnectivityMonitor(context)

    @Provides
    @Singleton
    fun storageProbe(
        @ApplicationContext context: Context,
    ): StorageProbe = AndroidStorageProbe(context)

    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient = OkHttpClient()

    @Provides
    @Singleton
    fun dispatcherProvider(): DispatcherProvider = DefaultDispatcherProvider()

    // ---- Remote config (prod = Firebase; fake binds the static source) -------

    @Provides
    @Singleton
    fun remoteConfigSource(impl: FirebaseRemoteConfigSource): RemoteConfigSource = impl

    // ---- AppConfig → reusable-library configs (plan §4 config flow) ----------

    /**
     * Long-lived scope for the billing gateway's own work (initial entitlement
     * resolution). Deliberately NOT a ViewModel scope: the entitlement must be
     * resolved before any screen asks, and it must survive the paywall closing.
     */
    @Provides
    @Singleton
    @BillingScope
    fun billingScope(dispatchers: DispatcherProvider): CoroutineScope = CoroutineScope(SupervisorJob() + dispatchers.io)

    /**
     * The real billing gateway. Three things worth knowing:
     *
     * 1. **The key is resolved lazily**, not here. Remote Config has nothing to
     *    serve on a cold first launch, so deciding "configured or not" at
     *    graph-construction time would kill billing for that whole session.
     *    `configProvider` waits for the first fetch to settle, then applies the
     *    one precedence rule ([effectiveQonversionKey]).
     * 2. **A blank key is not an error.** The gateway then behaves exactly like
     *    [NoOpSubscriptionGateway] — entitlement resolves Free, purchase/restore
     *    fail with `NotConfigured` — which is why the NoOp stays available for
     *    hosts that want no SDK at all but is not what this app binds.
     * 3. **Offering ids come from the paywall enum**, so the screen's plan list
     *    and the store lookup can never drift apart. They must match the product
     *    identifiers configured in the Qonversion dashboard.
     */
    @Provides
    @Singleton
    fun subscriptionGateway(
        application: Application,
        appConfig: AppConfig,
        remoteConfig: RemoteConfigSource,
        @BillingScope scope: CoroutineScope,
        activityProvider: ForegroundActivityProvider,
    ): SubscriptionGateway =
        QonversionSubscriptionGateway(
            application = application,
            activityProvider = activityProvider,
            scope = scope,
            configProvider = {
                remoteConfig.awaitFirstFetch()
                SubscriptionConfig(
                    projectKey = remoteConfig.effectiveQonversionKey(appConfig),
                    offeringIds = PaywallPlan.entries.map(PaywallPlan::offeringId),
                )
            },
        )

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

/**
 * Distinguishes the billing gateway's application-lifetime scope from any other
 * `CoroutineScope` a later module might provide — an unqualified one would be a
 * graph-wide singleton nobody owns.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BillingScope

/**
 * Prod-only startup work. The store-launch Activity tracker has to be listening
 * before the first Activity resumes, and Android never replays that callback —
 * so it cannot wait for the billing graph to be built on demand.
 */
@Module
@InstallIn(SingletonComponent::class)
object BillingStartupModule {
    @Provides
    @Singleton
    fun provideForegroundActivityProvider(): ForegroundActivityProvider = ForegroundActivityProvider()

    @Provides
    @IntoSet
    fun provideActivityTrackingStartup(provider: ForegroundActivityProvider): AppStartupTask =
        AppStartupTask { application -> provider.register(application) }
}
