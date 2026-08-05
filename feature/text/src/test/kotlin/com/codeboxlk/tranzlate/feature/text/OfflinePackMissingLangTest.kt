package com.codeboxlk.tranzlate.feature.text

import com.codeboxlk.tranzlate.core.model.AttemptCause
import com.codeboxlk.tranzlate.core.model.Engine
import com.codeboxlk.tranzlate.core.model.ModeId
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Sheet 19h's trigger (#130 PR-20): [offlinePackMissingLang] decides which state
 * raises the app-shell "offline, pack missing" sheet. Pure, so the decision is
 * pinned here without a Compose rule or the shell.
 *
 * Mutations these were written against (decided before the assertions, rule 11):
 * - `it.cause == AttemptCause.OFFLINE` → any other cause: an offline error stops
 *   naming a target and a non-offline error starts (reddens both cause tests).
 * - `.targetLang` → `.sourceLang`: the offline error names the source, not the
 *   pack that is missing (reddens `...names the target...`, which uses a distinct
 *   source and target so the swap is visible).
 * - dropping the `Error` cast: a Result or Idle starts raising 19h.
 */
class OfflinePackMissingLangTest {
    private fun request(
        source: String = "en",
        target: String = "fr",
    ) = TranslateRequest(text = "hola", sourceLang = source, targetLang = target, mode = ModeId.AUTO)

    @Test
    fun `an offline error names the target that has no pack`() {
        val state = TextUiState.Error(request(source = "en", target = "fr"), AttemptCause.OFFLINE)
        assertThat(offlinePackMissingLang(state)).isEqualTo("fr")
    }

    @Test
    fun `a non-offline error raises nothing`() {
        val state = TextUiState.Error(request(target = "fr"), AttemptCause.TIMEOUT)
        assertThat(offlinePackMissingLang(state)).isNull()
    }

    @Test
    fun `a cause-less error raises nothing`() {
        val state = TextUiState.Error(request(target = "fr"), cause = null)
        assertThat(offlinePackMissingLang(state)).isNull()
    }

    @Test
    fun `a result raises nothing`() {
        val state =
            TextUiState.Result(
                request = request(target = "fr"),
                translatedText = "Bonjour",
                transliteration = null,
                engine = Engine.OFFLINE_MLKIT,
                resolvedSourceLang = "en",
            )
        assertThat(offlinePackMissingLang(state)).isNull()
    }

    @Test
    fun `idle raises nothing`() {
        assertThat(offlinePackMissingLang(TextUiState.Idle)).isNull()
    }
}
