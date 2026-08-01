package com.codeboxlk.tranzlate.core.testing

import com.codeboxlk.tranzlate.core.model.AttemptCause
import com.codeboxlk.tranzlate.core.model.Engine
import com.codeboxlk.tranzlate.core.model.Entitlement
import com.codeboxlk.tranzlate.core.model.LanguageRole
import com.codeboxlk.tranzlate.core.model.LanguageTagResolver
import com.codeboxlk.tranzlate.core.model.ModeId
import com.codeboxlk.tranzlate.core.model.Tier
import com.codeboxlk.tranzlate.core.model.Translation
import com.codeboxlk.tranzlate.core.model.TranslationOutcome
import com.codeboxlk.tranzlate.domain.ads.AdsCoordinator
import com.codeboxlk.tranzlate.domain.translate.TranslateTextUseCase
import com.codeboxlk.tranzlate.domain.translate.Translator
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Proves the single ask-flow encoding (plan §2): Access check → translate →
 * Usage +1 on success only (metered only, C-10/D-2) → history write (issue #11
 * Recents, best-effort) → language-usage stamp (issue #122, success-only R6) →
 * Ads ask.
 */
class TranslateTextUseCaseTest {
    private class RecordingAdsCoordinator : AdsCoordinator {
        var completedCount = 0

        override suspend fun onTranslationCompleted() {
            completedCount++
        }
    }

    /**
     * Extension on [TestScope]: the stamper is fire-and-forget on an
     * application-lifetime scope, and this stands one up on the TEST scheduler
     * so `advanceUntilIdle` deterministically flushes the stamp. NOT
     * `backgroundScope`: its jobs only run while the test body is suspended,
     * so a launch fired inside `invoke` stays queued through every assertion —
     * verified empirically before landing here.
     */
    private fun TestScope.stamperScope() = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

    private fun TestScope.useCase(
        translator: FakeTranslator = FakeTranslator(),
        usage: FakeUsagePolicy = FakeUsagePolicy(left = 5),
        ads: RecordingAdsCoordinator = RecordingAdsCoordinator(),
        repository: FakeTranslationRepository = FakeTranslationRepository(),
        access: FakeFeatureAccess = FakeFeatureAccess(),
        languageUsage: FakeLanguageUsageRepository = FakeLanguageUsageRepository(),
    ) = TranslateTextUseCase(translator, access, usage, ads, repository, languageUsage, FakeClock(), stamperScope())

    @Test
    fun `G11 metered ask at limit short-circuits to LimitReached without engine call`() =
        runTest {
            val translator = FakeTranslator()
            val atLimit = FakeUsagePolicy(left = 0)

            val outcome = useCase(translator, atLimit).invoke("Quota text", "en", "fr", ModeId.NLP35)

            assertThat(outcome).isEqualTo(TranslationOutcome.LimitReached)
            assertThat(translator.calls).isEmpty() // no quota burn, no network (G11)
        }

    @Test
    fun `metered success increments usage once and asks ads`() =
        runTest {
            val usage = FakeUsagePolicy(left = 5)
            val ads = RecordingAdsCoordinator()

            val outcome = useCase(usage = usage, ads = ads).invoke("Good morning", "en", "fr", ModeId.NLP35)

            assertThat(outcome).isInstanceOf(TranslationOutcome.Success::class.java)
            assertThat(usage.state.value).isEqualTo(4) // success-only net spend
            assertThat(usage.refunds).isEqualTo(0)
            assertThat(ads.completedCount).isEqualTo(1)
        }

    @Test
    fun `free engine success never charges the metered counter (C-10)`() =
        runTest {
            val usage = FakeUsagePolicy(left = 5)
            val ads = RecordingAdsCoordinator()

            val outcome = useCase(usage = usage, ads = ads).invoke("Good morning", "en", "fr", ModeId.ML2_MINI)

            assertThat(outcome).isInstanceOf(TranslationOutcome.Success::class.java)
            assertThat(usage.state.value).isEqualTo(5) // unchanged
            assertThat(usage.spends).isEqualTo(0)
            assertThat(ads.completedCount).isEqualTo(1) // completed translations still ask ads (D-4)
        }

    @Test
    fun `failure increments nothing and never asks ads`() =
        runTest {
            val translator = FakeTranslator().apply { forcedFailure = AttemptCause.OFFLINE }
            val usage = FakeUsagePolicy(left = 5)
            val ads = RecordingAdsCoordinator()

            val outcome = useCase(translator, usage, ads).invoke("Offline test", "en", "fr", ModeId.ML2_ONLINE)

            assertThat((outcome as TranslationOutcome.Error).primaryCause).isEqualTo(AttemptCause.OFFLINE)
            assertThat(usage.state.value).isEqualTo(5)
            assertThat(usage.spends).isEqualTo(0) // unmetered mode never touched the pool
            assertThat(ads.completedCount).isEqualTo(0)
        }

    @Test
    fun `AUTO at metered limit keeps working (C-11 - AUTO keeps working underneath)`() =
        runTest {
            val outcome = useCase(usage = FakeUsagePolicy(left = 0)).invoke("Good morning", "en", "fr", ModeId.AUTO)

            assertThat(outcome).isInstanceOf(TranslationOutcome.Success::class.java)
        }

    // ---- History write-on-success (issue #11 drawer Recents) -----------------

    @Test
    fun `success writes one history row with the resolved engine`() =
        runTest {
            val repository = FakeTranslationRepository()

            useCase(repository = repository).invoke("Good morning", "en", "fr", ModeId.ML2_MINI) // G1

            val row = repository.saved.single()
            assertThat(row.sourceText).isEqualTo("Good morning")
            assertThat(row.targetText).isEqualTo("Bonjour (fake)")
            assertThat(row.engine).isEqualTo(Engine.OFFLINE_MLKIT) // C-9 resolved form, never AUTO
            assertThat(row.createdAt).isEqualTo(FakeClock().nowMillis()) // injectable clock, not wall time
        }

    @Test
    fun `identical repeat translation is C-8 deduped - no second history row`() =
        runTest {
            val repository = FakeTranslationRepository()
            val uc = useCase(repository = repository)

            uc.invoke("Good morning", "en", "fr", ModeId.ML2_MINI)
            uc.invoke("  Good   morning ", "en", "fr", ModeId.ML2_MINI) // same normalized tuple

            assertThat(repository.saved).hasSize(1)
        }

    @Test
    fun `failure writes no history row`() =
        runTest {
            val translator = FakeTranslator().apply { forcedFailure = AttemptCause.OFFLINE }
            val repository = FakeTranslationRepository()

            useCase(translator, repository = repository).invoke("Offline test", "en", "fr", ModeId.ML2_ONLINE)

            assertThat(repository.saved).isEmpty()
        }

    @Test
    fun `auto-detect persists under the RESOLVED source once detect metadata exists`() =
        runTest {
            val repository = FakeTranslationRepository()

            val outcome = useCase(repository = repository).invoke("Good morning", "auto", "fr", ModeId.AUTO) // G7

            assertThat(outcome).isInstanceOf(TranslationOutcome.Success::class.java)
            val row = repository.saved.single() // issue #61: detectedSource drives the write
            assertThat(row.sourceLang).isEqualTo("en") // resolved, never the "auto" sentinel
        }

    // ---- issue #151: the detect door writes ids the catalog can serve --------

    /**
     * `MlKitLanguageIdentifier` returns the platform's tag verbatim, and the
     * platform still says `iw` for Hebrew. Written raw, the row named a language
     * the catalog has no row for while the usage stamp — canonicalising inside
     * its own repository — recorded `he` for the very same translation.
     */
    @Test
    fun `a legacy detect tag reaches the history row canonicalised`() =
        runTest {
            val hebrew = detecting("iw")
            val repository = FakeTranslationRepository()
            val languageUsage = FakeLanguageUsageRepository()

            useCase(translator = hebrew, repository = repository, languageUsage = languageUsage)
                .invoke("Good morning", "auto", "fr", ModeId.AUTO)
            advanceUntilIdle()

            val row = repository.saved.single()
            assertThat(row.sourceLang).isEqualTo("he")
            // The point of the id: the catalog can serve it, so the row can be
            // named rather than printed as a code nobody recognises.
            assertThat(LanguageTagResolver.canonicalIds).contains(row.sourceLang)
            // ONE translation, ONE spelling: history and the usage store agree.
            assertThat(languageUsage.stamps.map { it.languageId }).contains(row.sourceLang)
            assertThat(languageUsage.stamps.map { it.languageId }).doesNotContain("iw")
        }

    /**
     * `canonicalOrSelf`, not `canonicalId`: a detector naming something the
     * catalog cannot serve must cost the row its NAME, never its existence. A
     * translation the user is reading has to still be in Recents afterwards.
     */
    @Test
    fun `a detect tag the catalog cannot serve is written as itself, not dropped`() =
        runTest {
            val repository = FakeTranslationRepository()

            useCase(translator = detecting("zzz"), repository = repository)
                .invoke("Good morning", "auto", "fr", ModeId.AUTO)

            assertThat(repository.saved.single().sourceLang).isEqualTo("zzz")
        }

    /**
     * The sentinel is a picker affordance, not a language, and the resolver
     * hands it straight back. So the ONE thing that must keep the row out is
     * the missing detect metadata itself — not a spelling test that "auto"
     * would pass.
     */
    @Test
    fun `the auto sentinel is never written as a source language`() =
        runTest {
            val repository = FakeTranslationRepository()
            val undetected =
                FakeTranslator(
                    golden =
                        mapOf(
                            FakeTranslator.GoldenKey("Good morning", "auto", "fr", ModeId.AUTO) to
                                TranslationOutcome.Success("Bonjour (fake)", Engine.OFFLINE_MLKIT),
                        ),
                )

            useCase(translator = undetected, repository = repository)
                .invoke("Good morning", "auto", "fr", ModeId.AUTO)

            assertThat(repository.saved).isEmpty()
        }

    /**
     * The other side of the same door, and the reason the caller's own ids are
     * passed through untouched: `srcLang` is what the C-8 cache read above and
     * the engine call already used. Re-spelling it for the write alone would
     * query the store under one id and record it under another — the exact
     * split this issue is about, moved one column over.
     */
    @Test
    fun `a concrete source is recorded exactly as the caller asked`() =
        runTest {
            val repository = FakeTranslationRepository()

            useCase(repository = repository).invoke("Good morning", "en", "fr", ModeId.ML2_MINI)

            val row = repository.saved.single()
            assertThat(row.sourceLang).isEqualTo("en")
            assertThat(row.targetLang).isEqualTo("fr")
        }

    /** G7's shape with the detected tag swapped — the fixture rule's "add a row", not "mutate a tuple". */
    private fun detecting(tag: String) =
        FakeTranslator(
            golden =
                mapOf(
                    FakeTranslator.GoldenKey("Good morning", "auto", "fr", ModeId.AUTO) to
                        TranslationOutcome.Success("Bonjour (fake)", Engine.OFFLINE_MLKIT, detectedSource = tag),
                ),
        )

    @Test
    fun `history write failure never fails the translation`() =
        runTest {
            val repository = FakeTranslationRepository(failWrites = true)
            val ads = RecordingAdsCoordinator()

            val outcome =
                useCase(
                    ads = ads,
                    repository = repository,
                ).invoke("Good morning", "en", "fr", ModeId.ML2_MINI)

            assertThat(outcome).isEqualTo(
                TranslationOutcome.Success("Bonjour (fake)", Engine.OFFLINE_MLKIT),
            )
            assertThat(ads.completedCount).isEqualTo(1) // flow continues past the failed write
        }
    // ---- issue #53 A4: atomic spend + success-only via refund ----------------

    @Test
    fun `metered failure refunds the spend - net zero charge`() =
        runTest {
            val translator = FakeTranslator().apply { forcedFailure = AttemptCause.OFFLINE }
            val usage = FakeUsagePolicy(left = 5)

            val outcome = useCase(translator, usage).invoke("Offline test", "en", "fr", ModeId.NLP35)

            assertThat((outcome as TranslationOutcome.Error).primaryCause).isEqualTo(AttemptCause.OFFLINE)
            assertThat(usage.spends).isEqualTo(1) // gate spent up front (atomic)
            assertThat(usage.refunds).isEqualTo(1) // failure returned it
            assertThat(usage.state.value).isEqualTo(5) // net zero (DECISIONS success-only)
        }

    @Test
    fun `cancellation mid-translate refunds the spend (NonCancellable path)`() =
        runTest {
            val usage = FakeUsagePolicy(left = 5)
            val entered = CompletableDeferred<Unit>()
            val hanging =
                object : Translator {
                    override suspend fun translate(
                        text: String,
                        srcLang: String,
                        tgtLang: String,
                        mode: ModeId,
                    ): TranslationOutcome {
                        entered.complete(Unit)
                        awaitCancellation() // in-flight until the caller cancels
                    }
                }
            val uc =
                TranslateTextUseCase(
                    hanging,
                    FakeFeatureAccess(),
                    usage,
                    RecordingAdsCoordinator(),
                    FakeTranslationRepository(),
                    FakeLanguageUsageRepository(),
                    FakeClock(),
                    stamperScope(),
                )

            val job = launch { uc.invoke("Good morning", "en", "fr", ModeId.NLP35) }
            entered.await()
            assertThat(usage.spends).isEqualTo(1) // atomic gate spent before the engine

            job.cancelAndJoin()

            assertThat(usage.refunds).isEqualTo(1) // the dying scope returned it
            assertThat(usage.state.value).isEqualTo(5) // net zero
        }

    @Test
    fun `PRO translates past an exhausted FREE pool (tier-aware gate)`() =
        runTest {
            val usage = FakeUsagePolicy(left = 0) // FREE pool empty
            val access = FakeFeatureAccess(Tier.PRO)

            val outcome = useCase(usage = usage, access = access).invoke("Good morning", "en", "fr", ModeId.NLP35)

            assertThat(outcome).isInstanceOf(TranslationOutcome.Success::class.java)
            assertThat(usage.spends).isEqualTo(1) // spent from the PRO pool
            assertThat(usage.state.value).isEqualTo(0) // FREE meter untouched
        }

    @Test
    fun `access denial is NotEntitled - never masked as LimitReached`() =
        runTest {
            val access = FakeFeatureAccess().apply { engineAllowed = false }
            val usage = FakeUsagePolicy(left = 5)

            val outcome = useCase(usage = usage, access = access).invoke("Good morning", "en", "fr", ModeId.NLP35)

            assertThat(outcome).isEqualTo(TranslationOutcome.NotEntitled)
            assertThat(usage.spends).isEqualTo(0) // denial happens before any spend
        }

    // ---- issue #53 A1/A7: the Loading-gate (never decide on Loading) ---------

    @Test
    fun `metered gate suspends on Loading and only decides once resolved`() =
        runTest {
            val access = FakeFeatureAccess().apply { state.value = Entitlement.Loading }
            val translator = FakeTranslator()
            val atLimit = FakeUsagePolicy(left = 0)
            val uc = useCase(translator, atLimit, access = access)

            val pending = async { uc.invoke("Quota text", "en", "fr", ModeId.NLP35) }
            runCurrent()
            // Loading is not FREE: no decision, no engine call, nothing returned yet.
            assertThat(pending.isCompleted).isFalse()
            assertThat(translator.calls).isEmpty()

            access.state.value = Entitlement.Free
            assertThat(pending.await()).isEqualTo(TranslationOutcome.LimitReached)
        }

    @Test
    fun `free engines never wait on the entitlement (gate is metered-only)`() =
        runTest {
            val access = FakeFeatureAccess().apply { state.value = Entitlement.Loading }

            val outcome = useCase(access = access).invoke("Good morning", "en", "fr", ModeId.ML2_MINI)

            assertThat(outcome).isInstanceOf(TranslationOutcome.Success::class.java)
        }

    // ---- issue #53 A2/A9: cache-first read + atomic dedupe ------------------

    @Test
    fun `cache hit skips the engine, the meter and the ads ask`() =
        runTest {
            val translator = FakeTranslator()
            val usage = FakeUsagePolicy(left = 5)
            val ads = RecordingAdsCoordinator()
            val repository = FakeTranslationRepository()
            repository.save(
                Translation(
                    sourceLang = "en",
                    sourceText = "Good morning",
                    targetLang = "fr",
                    targetText = "Bonjour (fake)",
                    engine = Engine.OFFLINE_MLKIT,
                    createdAt = 1L,
                ),
            )
            val useCase = useCase(translator = translator, usage = usage, ads = ads, repository = repository)

            val outcome = useCase("Good morning", "en", "fr", ModeId.NLP35)

            val success = outcome as TranslationOutcome.Success
            assertThat(success.fromCache).isTrue()
            assertThat(success.text).isEqualTo("Bonjour (fake)")
            assertThat(translator.calls).isEmpty() // zero engine calls
            assertThat(usage.spends).isEqualTo(0) // zero quota spend
            assertThat(ads.completedCount).isEqualTo(0) // zero ads asks
        }

    @Test
    fun `cache read ignores which engine produced the hit`() =
        runTest {
            val translator = FakeTranslator()
            val repository = FakeTranslationRepository()
            repository.save(
                Translation(
                    sourceLang = "en",
                    sourceText = "Good morning",
                    targetLang = "fr",
                    targetText = "Bonjour (cloud)",
                    engine = Engine.ONLINE_CLOUD_NLP, // a different engine's answer
                    createdAt = 2L,
                ),
            )

            val outcome =
                useCase(translator = translator, repository = repository)("Good morning", "en", "fr", ModeId.AUTO)

            assertThat((outcome as TranslationOutcome.Success).resolvedEngine).isEqualTo(Engine.ONLINE_CLOUD_NLP)
            assertThat(translator.calls).isEmpty()
        }

    @Test
    fun `auto-detect source skips the cache read and still translates`() =
        runTest {
            val translator = FakeTranslator()
            val repository = FakeTranslationRepository()
            repository.save(
                Translation(
                    sourceLang = "en",
                    sourceText = "Good morning",
                    targetLang = "fr",
                    targetText = "Bonjour (fake)",
                    engine = Engine.OFFLINE_MLKIT,
                    createdAt = 1L,
                ),
            )

            useCase(translator = translator, repository = repository)("Good morning", "auto", "fr", ModeId.AUTO)

            assertThat(translator.calls).hasSize(1) // pair unknown -> no cache read
        }

    @Test
    fun `a duplicate history insert loses to the unique index, not a crash`() =
        runTest {
            val repository = FakeTranslationRepository()
            val row =
                Translation(
                    sourceLang = "en",
                    sourceText = "Good  morning", // un-normalized on purpose
                    targetLang = "fr",
                    targetText = "Bonjour (fake)",
                    engine = Engine.OFFLINE_MLKIT,
                    createdAt = 1L,
                )
            assertThat(repository.save(row)).isGreaterThan(0L)
            assertThat(repository.save(row.copy(createdAt = 9L))).isEqualTo(-1L) // IGNORE semantics
            assertThat(repository.saved).hasSize(1)
        }

    // ---- issue #122 R6: language-usage stamps on translation success ONLY ----

    @Test
    fun `success stamps resolved source as SOURCE and target as TARGET`() =
        runTest {
            val languageUsage = FakeLanguageUsageRepository()

            useCase(languageUsage = languageUsage).invoke("Good morning", "en", "fr", ModeId.ML2_MINI) // G1
            advanceUntilIdle() // the stamp is fire-and-forget; flush the app scope

            assertThat(languageUsage.stamps).containsExactly(
                FakeLanguageUsageRepository.Stamp("en", LanguageRole.SOURCE, FakeClock().nowMillis()),
                FakeLanguageUsageRepository.Stamp("fr", LanguageRole.TARGET, FakeClock().nowMillis()),
            )
        }

    /** The ruling records WHY: an online-served use still proves the user uses that language. */
    @Test
    fun `any engine's success stamps - online engines included`() =
        runTest {
            val languageUsage = FakeLanguageUsageRepository()

            useCase(languageUsage = languageUsage).invoke("Good morning", "en", "fr", ModeId.NLP35) // G3
            advanceUntilIdle()

            assertThat(languageUsage.stamps.map { it.languageId to it.role })
                .containsExactly("en" to LanguageRole.SOURCE, "fr" to LanguageRole.TARGET)
        }

    @Test
    fun `failure stamps nothing`() =
        runTest {
            val translator = FakeTranslator().apply { forcedFailure = AttemptCause.OFFLINE }
            val languageUsage = FakeLanguageUsageRepository()

            useCase(translator, languageUsage = languageUsage).invoke("Offline test", "en", "fr", ModeId.ML2_ONLINE)
            advanceUntilIdle()

            assertThat(languageUsage.stamps).isEmpty()
        }

    @Test
    fun `at-limit and access-denied short-circuits stamp nothing`() =
        runTest {
            val languageUsage = FakeLanguageUsageRepository()
            val atLimit = FakeUsagePolicy(left = 0)
            val denied = FakeFeatureAccess().apply { engineAllowed = false }

            useCase(usage = atLimit, languageUsage = languageUsage).invoke("Quota text", "en", "fr", ModeId.NLP35)
            useCase(access = denied, languageUsage = languageUsage).invoke("Good morning", "en", "fr", ModeId.NLP35)
            advanceUntilIdle()

            assertThat(languageUsage.stamps).isEmpty()
        }

    @Test
    fun `auto-detect stamps the RESOLVED source id, never the sentinel`() =
        runTest {
            val languageUsage = FakeLanguageUsageRepository()

            useCase(languageUsage = languageUsage).invoke("Good morning", "auto", "fr", ModeId.AUTO) // G7 detect→en
            advanceUntilIdle()

            assertThat(languageUsage.stamps.map { it.languageId to it.role })
                .containsExactly("en" to LanguageRole.SOURCE, "fr" to LanguageRole.TARGET)
            assertThat(languageUsage.stamps.map { it.languageId }).doesNotContain("auto")
        }

    /** An auto success WITHOUT detect metadata has no resolved source — only the target is proven. */
    @Test
    fun `undetected auto success stamps the target only`() =
        runTest {
            val undetected =
                FakeTranslator(
                    golden =
                        mapOf(
                            FakeTranslator.GoldenKey("Good morning", "auto", "fr", ModeId.AUTO) to
                                TranslationOutcome.Success("Bonjour (fake)", Engine.OFFLINE_MLKIT),
                        ),
                )
            val languageUsage = FakeLanguageUsageRepository()

            useCase(translator = undetected, languageUsage = languageUsage)
                .invoke("Good morning", "auto", "fr", ModeId.AUTO)
            advanceUntilIdle()

            assertThat(languageUsage.stamps.map { it.languageId to it.role })
                .containsExactly("fr" to LanguageRole.TARGET)
        }

    /**
     * A cache-served answer is still a USE of both languages — a pack the user
     * exercises daily must never look months stale in Manage packs just
     * because the C-8 cache keeps answering for it (the ruling's own
     * nudge-to-delete-an-active-pack rationale).
     */
    @Test
    fun `a cache hit stamps both roles too`() =
        runTest {
            val languageUsage = FakeLanguageUsageRepository()
            val repository = FakeTranslationRepository()
            repository.save(
                Translation(
                    sourceLang = "en",
                    sourceText = "Good morning",
                    targetLang = "fr",
                    targetText = "Bonjour (fake)",
                    engine = Engine.OFFLINE_MLKIT,
                    createdAt = 1L,
                ),
            )

            val outcome =
                useCase(repository = repository, languageUsage = languageUsage)
                    .invoke("Good morning", "en", "fr", ModeId.AUTO)
            advanceUntilIdle()

            assertThat((outcome as TranslationOutcome.Success).fromCache).isTrue()
            assertThat(languageUsage.stamps.map { it.languageId to it.role })
                .containsExactly("en" to LanguageRole.SOURCE, "fr" to LanguageRole.TARGET)
        }

    /** The stamp is fire-and-forget: a dying usage store must cost the stamp, never the translation. */
    @Test
    fun `a failed stamp never fails the translation`() =
        runTest {
            val languageUsage = FakeLanguageUsageRepository(failStamps = true)
            val ads = RecordingAdsCoordinator()

            val outcome =
                useCase(ads = ads, languageUsage = languageUsage)
                    .invoke("Good morning", "en", "fr", ModeId.ML2_MINI)
            advanceUntilIdle() // the launched stamp fails HERE, after the outcome returned

            assertThat(outcome).isEqualTo(
                TranslationOutcome.Success("Bonjour (fake)", Engine.OFFLINE_MLKIT),
            )
            assertThat(ads.completedCount).isEqualTo(1) // flow continued past the failed stamp
            assertThat(languageUsage.stamps).isEmpty()
        }

    /** The outcome must not WAIT on the stamp: it returns before the app scope ever runs. */
    @Test
    fun `the stamp is off the critical path - success returns before the stamp lands`() =
        runTest {
            val languageUsage = FakeLanguageUsageRepository()

            val outcome = useCase(languageUsage = languageUsage).invoke("Good morning", "en", "fr", ModeId.ML2_MINI)

            // No scheduler flush yet: the user already has the translation…
            assertThat(outcome).isInstanceOf(TranslationOutcome.Success::class.java)
            assertThat(languageUsage.stamps).isEmpty()
            // …and the stamp lands when the background scope gets its turn.
            advanceUntilIdle()
            assertThat(languageUsage.stamps).hasSize(2)
        }
}
