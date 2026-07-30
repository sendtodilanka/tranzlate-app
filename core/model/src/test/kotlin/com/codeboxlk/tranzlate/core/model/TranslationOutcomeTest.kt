package com.codeboxlk.tranzlate.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Issue #94 (debate-ruled): [TranslationOutcome.Error.primaryCause] is the
 * deepest attempt the waterfall ACTUALLY ran — a `SKIPPED_*` tail never masks
 * the real, actionable failure; all-skip falls back to the last entry.
 */
class TranslationOutcomeTest {
    private fun error(vararg attempts: Pair<Engine, AttemptCause>) =
        TranslationOutcome.Error(attempts.map { EngineAttempt(it.first, it.second) })

    @Test
    fun `single real failure is the primary cause`() {
        val outcome = error(Engine.OFFLINE_MLKIT to AttemptCause.OFFLINE)
        assertThat(outcome.primaryCause).isEqualTo(AttemptCause.OFFLINE)
    }

    @Test
    fun `a skip HEAD never outranks the real failures behind it`() {
        val outcome =
            error(
                Engine.OFFLINE_MLKIT to AttemptCause.SKIPPED_SOURCE_UNKNOWN,
                Engine.ONLINE_GOOGLE to AttemptCause.OFFLINE,
                Engine.ONLINE_CLOUD_NLP to AttemptCause.OFFLINE,
            )
        assertThat(outcome.primaryCause).isEqualTo(AttemptCause.OFFLINE)
    }

    @Test
    fun `a skip TAIL no longer masks the real failure - the fix itself`() {
        val outcome =
            error(
                Engine.OFFLINE_MLKIT to AttemptCause.MODEL_NOT_DOWNLOADED,
                Engine.ONLINE_GOOGLE to AttemptCause.UNSUPPORTED_PAIR,
                Engine.ONLINE_CLOUD_NLP to AttemptCause.SKIPPED_NO_QUOTA,
            )
        assertThat(outcome.primaryCause).isEqualTo(AttemptCause.UNSUPPORTED_PAIR)
    }

    @Test
    fun `all-skipped falls back to the last skip - never throws`() {
        val outcome =
            error(
                Engine.ONLINE_GOOGLE to AttemptCause.SKIPPED_SOURCE_UNKNOWN,
                Engine.ONLINE_CLOUD_NLP to AttemptCause.SKIPPED_NO_QUOTA,
            )
        assertThat(outcome.primaryCause).isEqualTo(AttemptCause.SKIPPED_NO_QUOTA)
    }
}
