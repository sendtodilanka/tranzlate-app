package com.codeboxlk.tranzlate.core.testing

import com.codeboxlk.tranzlate.core.model.Engine
import com.codeboxlk.tranzlate.core.model.FailureReason
import com.codeboxlk.tranzlate.core.model.ModeId
import com.codeboxlk.tranzlate.core.model.Translation
import com.codeboxlk.tranzlate.core.model.TranslationOutcome
import com.codeboxlk.tranzlate.domain.ads.AdsCoordinator
import com.codeboxlk.tranzlate.domain.translate.TranslateTextUseCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Proves the single ask-flow encoding (plan §2): Access check → translate →
 * Usage +1 on success only (metered only, C-10/D-2) → history write (issue #11
 * Recents, best-effort) → Ads ask.
 */
class TranslateTextUseCaseTest {
    private class RecordingAdsCoordinator : AdsCoordinator {
        var completedCount = 0

        override suspend fun onTranslationCompleted() {
            completedCount++
        }
    }

    private fun useCase(
        translator: FakeTranslator = FakeTranslator(),
        usage: FakeUsagePolicy = FakeUsagePolicy(left = 5),
        ads: RecordingAdsCoordinator = RecordingAdsCoordinator(),
        repository: FakeTranslationRepository = FakeTranslationRepository(),
    ) = TranslateTextUseCase(translator, FakeFeatureAccess(), usage, ads, repository, FakeClock())

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
            assertThat(usage.remaining()).isEqualTo(4) // success-only +1
            assertThat(ads.completedCount).isEqualTo(1)
        }

    @Test
    fun `free engine success never charges the metered counter (C-10)`() =
        runTest {
            val usage = FakeUsagePolicy(left = 5)
            val ads = RecordingAdsCoordinator()

            val outcome = useCase(usage = usage, ads = ads).invoke("Good morning", "en", "fr", ModeId.ML2_MINI)

            assertThat(outcome).isInstanceOf(TranslationOutcome.Success::class.java)
            assertThat(usage.remaining()).isEqualTo(5) // unchanged
            assertThat(ads.completedCount).isEqualTo(1) // completed translations still ask ads (D-4)
        }

    @Test
    fun `failure increments nothing and never asks ads`() =
        runTest {
            val translator = FakeTranslator().apply { forcedFailure = FailureReason.NETWORK }
            val usage = FakeUsagePolicy(left = 5)
            val ads = RecordingAdsCoordinator()

            val outcome = useCase(translator, usage, ads).invoke("Offline test", "en", "fr", ModeId.ML2_ONLINE)

            assertThat(outcome).isEqualTo(TranslationOutcome.Error(FailureReason.NETWORK))
            assertThat(usage.remaining()).isEqualTo(5)
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
            val translator = FakeTranslator().apply { forcedFailure = FailureReason.NETWORK }
            val repository = FakeTranslationRepository()

            useCase(translator, repository = repository).invoke("Offline test", "en", "fr", ModeId.ML2_ONLINE)

            assertThat(repository.saved).isEmpty()
        }

    @Test
    fun `auto-detect source is not persisted until detect metadata exists`() =
        runTest {
            val repository = FakeTranslationRepository()

            val outcome = useCase(repository = repository).invoke("Good morning", "auto", "fr", ModeId.AUTO) // G7

            assertThat(outcome).isInstanceOf(TranslationOutcome.Success::class.java)
            assertThat(repository.saved).isEmpty() // Translation.sourceLang must be a RESOLVED id
        }

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
            assertThat(usage.incremented).isEqualTo(0) // zero quota spend
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
}
