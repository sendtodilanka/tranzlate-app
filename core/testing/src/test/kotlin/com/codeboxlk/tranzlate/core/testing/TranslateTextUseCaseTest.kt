package com.codeboxlk.tranzlate.core.testing

import com.codeboxlk.tranzlate.core.model.FailureReason
import com.codeboxlk.tranzlate.core.model.ModeId
import com.codeboxlk.tranzlate.core.model.TranslationOutcome
import com.codeboxlk.tranzlate.domain.ads.AdsCoordinator
import com.codeboxlk.tranzlate.domain.translate.TranslateTextUseCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Proves the single ask-flow encoding (plan §2): Access check → translate →
 * Usage +1 on success only (metered only, C-10/D-2) → Ads ask.
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
    ) = TranslateTextUseCase(translator, FakeFeatureAccess(), usage, ads)

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
}
