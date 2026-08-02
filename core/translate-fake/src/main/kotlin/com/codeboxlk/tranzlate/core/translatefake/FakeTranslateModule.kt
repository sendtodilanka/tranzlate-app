package com.codeboxlk.tranzlate.core.translatefake

import com.codeboxlk.tranzlate.core.common.AppClock
import com.codeboxlk.tranzlate.core.common.ConnectivityMonitor
import com.codeboxlk.tranzlate.core.common.DefaultDispatcherProvider
import com.codeboxlk.tranzlate.core.common.DispatcherProvider
import com.codeboxlk.tranzlate.core.common.StorageProbe
import com.codeboxlk.tranzlate.core.model.Tier
import com.codeboxlk.tranzlate.core.testing.FakeClock
import com.codeboxlk.tranzlate.core.testing.FakeConnectivityMonitor
import com.codeboxlk.tranzlate.core.testing.FakeFeatureAccess
import com.codeboxlk.tranzlate.core.testing.FakeOfflineVoiceCatalog
import com.codeboxlk.tranzlate.core.testing.FakeStorageProbe
import com.codeboxlk.tranzlate.core.testing.FakeTranslator
import com.codeboxlk.tranzlate.core.testing.FakeUsagePolicy
import com.codeboxlk.tranzlate.domain.access.FeatureAccess
import com.codeboxlk.tranzlate.domain.access.PurchaseFlow
import com.codeboxlk.tranzlate.domain.ads.AdsCoordinator
import com.codeboxlk.tranzlate.domain.speech.OfflineVoiceCatalog
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
    fun connectivityMonitor(): ConnectivityMonitor = FakeConnectivityMonitor()

    @Provides
    @Singleton
    fun offlineModelManager(): OfflineModelManager = FakeOfflineModelManager()

    /**
     * Deterministic voice answer for the fake variant: a Maestro run must see
     * the same speaker marks on every machine, and the real enumeration depends
     * on which TTS voices the emulator image happens to ship. The three ids are
     * golden-fixture languages (§1.2 rows en↔fr and en→es), so the marked rows
     * are rows a fake-variant flow already touches.
     */
    @Provides
    @Singleton
    fun offlineVoiceCatalog(): OfflineVoiceCatalog = FakeOfflineVoiceCatalog(ids = setOf("en", "es", "fr"))

    /**
     * Deterministic storage answers, for the same reason the voice catalogue is
     * faked: the offline-library meter (#130 PR-15) prints a size, and the real
     * probe would print whatever the machine's disk happens to hold — so a
     * Maestro assertion on that card would pass on one runner and fail on the
     * next.
     *
     * The numbers are `FakeStorageProbe`'s defaults, which model a healthy fresh
     * install: room to spare, and `packs = null` because ML Kit's model store
     * does not exist until the first download (verified on `emulator-5554`,
     * E-S1). The fake variant's [offlineModelManager] downloads nothing real, so
     * a device with no packs is the honest state for it to be in and the card
     * reads "nothing downloaded".
     */
    @Provides
    @Singleton
    fun storageProbe(): StorageProbe = FakeStorageProbe()

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
