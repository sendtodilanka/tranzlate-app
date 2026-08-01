package com.codeboxlk.tranzlate.di

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The durable half of the mobile-data question (#130 PR-13, loss class PP-5.f).
 *
 * The harm this closes, plainly: the user taps ⬇ on a language while on mobile
 * data, the app asks whether to spend the data plan, they switch away to think
 * about it, Android reclaims the process, and on return the question has quietly
 * withdrawn itself. Nothing was charged — but the app asked something and then
 * behaved as though it had not.
 *
 * "Process death" here is a new store over the same [SavedStateHandle], which is
 * exactly what a rebuilt ViewModel is handed. It does not exercise the Bundle
 * round-trip; there is no Android runtime in a unit test. What it does test is
 * the half that can be got wrong in code: whether the question is written to the
 * handle at all, and whether a new store reads it back instead of starting empty.
 */
class SavedStateConsentQuestionStoreTest {
    @Test
    fun `a fresh store has no question`() {
        assertThat(SavedStateConsentQuestionStore(SavedStateHandle()).question.value).isNull()
    }

    @Test
    fun `a question asked before the process died is still asked after it`() {
        val handle = SavedStateHandle()
        SavedStateConsentQuestionStore(handle).raise("de")

        assertThat(SavedStateConsentQuestionStore(handle).question.value).isEqualTo("de")
    }

    /**
     * An ANSWERED question must not come back. "Download once" closes it; a
     * restore that re-opened it would ask the user again for a download they have
     * already approved and which is already running.
     */
    @Test
    fun `an answered question does not come back`() {
        val handle = SavedStateHandle()
        val store = SavedStateConsentQuestionStore(handle)
        store.raise("de")
        assertThat(store.take()).isEqualTo("de")

        assertThat(store.question.value).isNull()
        assertThat(SavedStateConsentQuestionStore(handle).question.value).isNull()
    }

    /** Dismissing is the same erasure — "Wait for Wi-Fi" must not survive a kill either. */
    @Test
    fun `a dismissed question does not come back`() {
        val handle = SavedStateHandle()
        SavedStateConsentQuestionStore(handle).apply {
            raise("de")
            take()
        }

        assertThat(SavedStateConsentQuestionStore(handle).question.value).isNull()
    }

    /**
     * [SavedStateConsentQuestionStore.take] is one step, not read-then-write.
     * Two taps on "Download once" landing in the same frame must produce ONE
     * consent token — the second one has nothing left to answer. `DownloadGate`'s
     * matrix asserts the outcome; this asserts the mechanism it rests on.
     */
    @Test
    fun `taking twice yields the question once`() {
        val store = SavedStateConsentQuestionStore(SavedStateHandle())
        store.raise("de")

        assertThat(store.take()).isEqualTo("de")
        assertThat(store.take()).isNull()
    }

    /** There is only ever ONE open question — a newer tap replaces the older one. */
    @Test
    fun `a second question replaces the first`() {
        val handle = SavedStateHandle()
        val store = SavedStateConsentQuestionStore(handle)
        store.raise("de")
        store.raise("fr")

        assertThat(store.question.value).isEqualTo("fr")
        assertThat(SavedStateConsentQuestionStore(handle).question.value).isEqualTo("fr")
    }
}
