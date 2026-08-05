package com.codeboxlk.tranzlate.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Issue #158 — the push-side invariant, pinned, mirroring [BackStackPopTest].
 *
 * `pushEntry` is a plain function over the `MutableList<NavKey>` that `NavBackStack`
 * is, so this runs on the JVM and gates every build. The device test
 * (`NavDoublePushReachabilityTest`) only proves the same-frame race is REACHABLE —
 * #40 keeps CI off the emulator — so the regression that must never rot lives here.
 * The stacks are the real type; `NavBackStack` delegates to a `SnapshotStateList`,
 * and two callbacks firing in ONE frame see exactly this: the first push already
 * applied, no recomposition between, so the second reads the grown stack.
 */
class BackStackPushTest {
    private fun stackOf(vararg keys: NavKey): NavBackStack<NavKey> = NavBackStack(*keys)

    private val picker = LanguagePickerNavKey(forSource = true)

    // ---- The reported defect --------------------------------------------------------------------

    /**
     * The harm: two DIFFERENT Home cards tapped in the same frame both push. Both
     * callbacks were composed while Home (TextNavKey) was top, so both carry
     * `from = TextNavKey`. The first push moves the top to Camera; the second — a card
     * on a screen already leaving — must be declined, or the user taps one card and
     * lands on two screens with the wanted one underneath.
     *
     * MUTATION (decided before the test): delete `pushEntry`'s `from` identity check,
     * leaving the equal-key-only guard `navigateTo` shipped. Then the second push
     * lands (top Camera ≠ Languages) → `[Text, Camera, Languages]`, and `second` is
     * non-null — RED on both assertions.
     */
    @Test
    fun `two different cards tapped in one frame push only the first`() {
        val stack = stackOf(TextNavKey)

        pushEntry(stack, from = TextNavKey, key = CameraNavKey) // first card
        val second = pushEntry(stack, from = TextNavKey, key = LanguagesNavKey) // second, stale caller

        assertThat(second).isNull()
        assertThat(stack).containsExactly(TextNavKey, CameraNavKey).inOrder()
    }

    /**
     * The same race one level deep: a language chip on the composer and another push
     * landing in the same frame. The chip's `from` is the composer; once the first
     * push moves the top, the chip's push is declined rather than stacking over it.
     */
    @Test
    fun `a stale caller cannot push onto a screen it no longer sits on`() {
        val stack = stackOf(TextNavKey, ComposerNavKey)

        pushEntry(stack, from = ComposerNavKey, key = PaywallNavKey) // first, from the composer
        val second = pushEntry(stack, from = ComposerNavKey, key = picker) // second, stale

        assertThat(second).isNull()
        assertThat(stack).containsExactly(TextNavKey, ComposerNavKey, PaywallNavKey).inOrder()
    }

    // ---- Ordinary pushes must still work --------------------------------------------------------

    @Test
    fun `the top screen still pushes`() {
        val stack = stackOf(TextNavKey)

        assertThat(pushEntry(stack, from = TextNavKey, key = CameraNavKey)).isEqualTo(CameraNavKey)
        assertThat(stack).containsExactly(TextNavKey, CameraNavKey).inOrder()
    }

    @Test
    fun `every destination the top can reach still pushes`() {
        // No affordance may become a dead end (EDGE_CASES no-dead-end): each key,
        // pushed from the screen that offers it, lands.
        listOf(CameraNavKey, ChatNavKey, HistoryNavKey, LanguagesNavKey, PaywallNavKey, SettingsNavKey, picker)
            .forEach { key ->
                val stack = stackOf(TextNavKey)

                assertThat(pushEntry(stack, from = TextNavKey, key = key)).isEqualTo(key)
                assertThat(stack).containsExactly(TextNavKey, key).inOrder()
            }
    }

    // ---- The self-dedup popEntry relies on ------------------------------------------------------

    @Test
    fun `a key is never stacked on top of itself`() {
        // popEntry's identity check is sound ONLY because no two adjacent entries are
        // ever equal. A second tap on a card whose screen is already the top is
        // refused. MUTATION: delete the `top == key` line → `[Text, Camera, Camera]`.
        val stack = stackOf(TextNavKey, CameraNavKey)

        assertThat(pushEntry(stack, from = CameraNavKey, key = CameraNavKey)).isNull()
        assertThat(stack).containsExactly(TextNavKey, CameraNavKey).inOrder()
    }

    // ---- from = null: no caller, so no identity check (defensive mirror of system back) ---------

    @Test
    fun `a push with no caller skips the identity check`() {
        val stack = stackOf(TextNavKey)

        assertThat(pushEntry(stack, from = null, key = CameraNavKey)).isEqualTo(CameraNavKey)
        assertThat(stack).containsExactly(TextNavKey, CameraNavKey).inOrder()
    }
}
