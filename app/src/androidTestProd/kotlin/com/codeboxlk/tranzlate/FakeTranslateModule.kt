package com.codeboxlk.tranzlate

import com.codeboxlk.tranzlate.core.common.AppClock
import com.codeboxlk.tranzlate.core.common.AppResult
import com.codeboxlk.tranzlate.core.common.ConnectivityMonitor
import com.codeboxlk.tranzlate.core.common.DefaultDispatcherProvider
import com.codeboxlk.tranzlate.core.common.DispatcherProvider
import com.codeboxlk.tranzlate.core.common.StorageProbe
import com.codeboxlk.tranzlate.core.config.RemoteConfigSource
import com.codeboxlk.tranzlate.core.model.Entitlement
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.codeboxlk.tranzlate.core.model.PlanPrices
import com.codeboxlk.tranzlate.core.model.Tier
import com.codeboxlk.tranzlate.core.testing.FakeClock
import com.codeboxlk.tranzlate.core.testing.FakeConnectivityMonitor
import com.codeboxlk.tranzlate.core.testing.FakeFeatureAccess
import com.codeboxlk.tranzlate.core.testing.FakeOfflineVoiceCatalog
import com.codeboxlk.tranzlate.core.testing.FakeRemoteConfig
import com.codeboxlk.tranzlate.core.testing.FakeStorageProbe
import com.codeboxlk.tranzlate.core.testing.FakeTranslator
import com.codeboxlk.tranzlate.core.testing.FakeUsagePolicy
import com.codeboxlk.tranzlate.di.TranslateModule
import com.codeboxlk.tranzlate.domain.access.FeatureAccess
import com.codeboxlk.tranzlate.domain.access.PurchaseFlow
import com.codeboxlk.tranzlate.domain.ads.AdsCoordinator
import com.codeboxlk.tranzlate.domain.speech.OfflineVoiceCatalog
import com.codeboxlk.tranzlate.domain.translate.DownloadAttempt
import com.codeboxlk.tranzlate.domain.translate.OfflineModelManager
import com.codeboxlk.tranzlate.domain.translate.Translator
import com.codeboxlk.tranzlate.domain.usage.UsagePolicy
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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

    // Moved into TranslateModule with the Firebase integration (it is now a
    // per-flavor binding, not a shared one), so `replaces` takes it away and the
    // deterministic fake has to be re-supplied here.
    @Provides
    @Singleton
    fun remoteConfig(): RemoteConfigSource = FakeRemoteConfig()

    @Provides
    @Singleton
    fun connectivity(): ConnectivityMonitor = FakeConnectivityMonitor()

    /**
     * Same story as `remoteConfig` and `connectivity` above: `replaces` takes
     * `TranslateModule` away wholesale, and `StorageProbe` is bound there
     * (#130 PR-11), so the instrumented graph has to re-supply it.
     *
     * A UI test must not read the runner's real disk either. The offline-library
     * meter (#130 PR-15) prints a size, and the real probe would print whatever
     * the emulator image happens to hold — a different string on every runner.
     * `FakeStorageProbe`'s defaults model a fresh install: room to spare, and
     * `packs = null` because ML Kit's model store does not exist until the first
     * download (verified on `emulator-5554`, E-S1).
     */
    @Provides
    @Singleton
    fun storageProbe(): StorageProbe = FakeStorageProbe()

    /**
     * PRE-EXISTING GAP, found while adding the voice binding below and fixed
     * because the source set otherwise does not compile at all: `replaces` also
     * took `PurchaseFlow` away when billing moved into `TranslateModule`
     * (commit fabb214), and nothing re-supplied it, so the whole instrumented
     * graph has been failing to build with `[Dagger/MissingBinding]
     * PurchaseFlow` since then. CI never builds androidTest, which is why it
     * went unnoticed — the compile gap is the finding, this is only the unblock.
     */
    @Provides
    @Singleton
    fun purchaseFlow(): PurchaseFlow =
        object : PurchaseFlow {
            override val prices: Flow<PlanPrices> = flowOf(PlanPrices.Unavailable)

            override suspend fun refreshPrices() = Unit

            override suspend fun purchase(offeringId: String): AppResult<Entitlement> =
                AppResult.Failure(UnsupportedOperationException("no billing in instrumented tests"))

            override suspend fun restore(): AppResult<Entitlement> =
                AppResult.Failure(UnsupportedOperationException("no billing in instrumented tests"))
        }

    /**
     * A UI test must never bind a real TTS engine: the enumeration would depend
     * on the emulator image's installed voices and, on an image with none, would
     * spend the catalog's five-second timeout before the language list could
     * gain its marks.
     */
    @Provides
    @Singleton
    fun offlineVoiceCatalog(): OfflineVoiceCatalog = FakeOfflineVoiceCatalog(ids = setOf("en", "es", "fr"))

    @Provides
    @Singleton
    fun offlineModelManager(): OfflineModelManager =
        object : OfflineModelManager {
            override fun modelStates() = kotlinx.coroutines.flow.flowOf(emptyMap<String, OfflineModelState>())

            override suspend fun download(languageTag: String) = DownloadAttempt.Started

            override suspend fun delete(languageTag: String) = Unit
        }
}
