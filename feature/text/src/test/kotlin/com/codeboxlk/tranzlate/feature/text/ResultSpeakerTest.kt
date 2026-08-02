package com.codeboxlk.tranzlate.feature.text

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The parts of `AndroidResultSpeaker` that decide something, tested without the
 * platform (issue #159 co-verify).
 *
 * `TextToSpeech` cannot be constructed on the JVM, so the two decisions that were
 * wrong are lifted out of the adapter into named functions rather than left
 * untestable: **how a speak tap treats a bind that has not reported yet**
 * (block 2), and **what happens when one half of the release throws** (medium 4).
 * Both are exercised here through their own arguments — no Android class is loaded.
 *
 * This KDoc used to give "there is no Robolectric in this project" as part of the
 * reason. #186 has since put Robolectric on this module's test classpath, so that
 * half is no longer true — but the conclusion is unchanged, because a shadowed
 * `TextToSpeech` would exercise the shadow's behaviour rather than the adapter's,
 * which is the opposite of what these tests are for.
 */
class ResultSpeakerTest {
    // ---- block 2: a bind in flight is not an unavailable engine --------------

    @Test
    fun `a bind that reports late is waited for, not called unavailable`() =
        runTest {
            val signal = CompletableDeferred<Boolean>()
            // The measured bind is 478-601ms; a cache hit renders the result
            // inside that window, so this is the tap the device reproduced.
            launch {
                delay(500)
                signal.complete(true)
            }

            assertThat(awaitBind(signal, timeoutMs = 5_000)).isTrue()
        }

    @Test
    fun `a bind that reports failure is reported as failure, not waited on forever`() =
        runTest {
            val signal = CompletableDeferred<Boolean>()
            launch {
                delay(50)
                signal.complete(false)
            }

            assertThat(awaitBind(signal, timeoutMs = 5_000)).isFalse()
        }

    @Test
    fun `a bind that never reports gives up instead of hanging the tap`() =
        runTest {
            assertThat(awaitBind(CompletableDeferred(), timeoutMs = 5_000)).isFalse()
        }

    @Test
    fun `no bind was ever started - nothing to wait for`() =
        runTest {
            assertThat(awaitBind(null, timeoutMs = 5_000)).isFalse()
        }

    // ---- medium 4: one throw must not strand the engine ---------------------

    @Test
    fun `a stop that throws does not skip the shutdown`() {
        var shutdowns = 0
        val escapes = mutableListOf<String>()

        guardedRelease(
            stop = { throw IllegalArgumentException("Service not registered") },
            shutdown = { shutdowns++ },
            onEscape = { what, _ -> escapes += what },
        )

        // The caller has already nulled its field by the time this runs, so a
        // skipped shutdown() is an engine nobody can ever release again.
        assertThat(shutdowns).isEqualTo(1)
        assertThat(escapes).containsExactly("engine stop")
    }

    @Test
    fun `a shutdown that throws is reported, not propagated`() {
        val escapes = mutableListOf<String>()

        guardedRelease(
            stop = {},
            shutdown = { throw IllegalArgumentException("Service not registered") },
            onEscape = { what, _ -> escapes += what },
        )

        assertThat(escapes).containsExactly("engine shutdown")
    }

    // ---- medium 5: the guard itself -----------------------------------------

    @Test
    fun `a guarded call that throws reports the escape and answers null`() {
        var reported: String? = null

        val result = guarded<String>("engine bind", { what, _ -> reported = what }) { error("boom") }

        assertThat(result).isNull()
        assertThat(reported).isEqualTo("engine bind")
    }

    @Test
    fun `a guarded call that succeeds returns its value and reports nothing`() {
        var reported: String? = null

        val result = guarded("engine bind", { what, _ -> reported = what }) { "engine" }

        assertThat(result).isEqualTo("engine")
        assertThat(reported).isNull()
    }
}
