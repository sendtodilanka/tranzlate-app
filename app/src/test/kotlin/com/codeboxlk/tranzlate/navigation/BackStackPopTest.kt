package com.codeboxlk.tranzlate.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Issue #150 — the back-stack invariants, pinned.
 *
 * `popEntry` is a plain function over the `MutableList<NavKey>` that
 * `NavBackStack` is, precisely so these can run on the JVM: this repo has no
 * working instrumentation harness (#40, #111), and a shell-level fix nobody can
 * test is how the missing guard survived eight call sites in the first place.
 * The stacks below are the real type — `NavBackStack` delegates to a
 * `SnapshotStateList`, and two callbacks firing in ONE frame see exactly this:
 * the first write already applied, no recomposition in between.
 *
 * NOT reachable here (read-review only, no Android runtime): that `AppNavDisplay`
 * routes all nine affordances into `pop(...)`, and that the local `pop` fires
 * `TextViewModel.onComposerDismissed()` off the returned key. What IS pinned is
 * the decision every one of them delegates to, including the return value that
 * side effect keys on.
 */
class BackStackPopTest {
    private fun stackOf(vararg keys: NavKey): NavBackStack<NavKey> = NavBackStack(*keys)

    private val picker = LanguagePickerNavKey(forSource = true)

    // ---- The reported defect --------------------------------------------------------------------

    /**
     * The race the exemption left open, and the one that costs a draft.
     *
     * The picker's Done pops it, and a system back lands in the SAME frame —
     * NavDisplay counts entries it remembered (`DecoratedNavEntries.kt:125`),
     * so it is still working from the stack the tap already shortened. With
     * clamp-only on that door the back press took the composer, which leaves
     * without discarding its draft: #48 returning through #150's own exemption.
     *
     * The test I specified in #150 — "two Done taps leave the root on the
     * stack" — is deleted rather than kept. It passed with BOTH guards removed:
     * at depth 2 the root is what saves you, so it pinned neither of them.
     */
    @Test
    fun `a system back in the same frame as a Done tap cannot take the screen underneath`() {
        val stack = stackOf(TextNavKey, ComposerNavKey, picker)
        val composedTop = stack.last()

        popEntry(stack, from = picker)
        val second = popEntry(stack, from = composedTop)

        assertThat(second).isNull()
        assertThat(stack).containsExactly(TextNavKey, ComposerNavKey).inOrder()
    }

    /**
     * The side effect, pinned where it can be. Deleting the composer-discard
     * call used to leave all 462 tests green — a lens proved it by doing
     * exactly that — because the effect lived at a call site no JVM test can
     * reach. It is a parameter now, so its contract is testable: it fires with
     * the key that came off, and never on a pop that was declined.
     */
    @Test
    fun `a removed key is reported once, and a declined pop reports nothing`() {
        val stack = stackOf(TextNavKey, ComposerNavKey)
        val removed = mutableListOf<NavKey>()

        popEntry(stack, from = ComposerNavKey) { removed += it }
        popEntry(stack, from = ComposerNavKey) { removed += it }

        assertThat(removed).containsExactly(ComposerNavKey)
    }

    @Test
    fun `a second tap never pops the screen underneath`() {
        // The half of the defect a size clamp alone does NOT fix: at depth 3 the
        // stale tap is legal by size and still eats 5a — the user lands on Home
        // and the composer leaves without onComposerDismissed (the #48 draft bug).
        val backStack = stackOf(TextNavKey, ComposerNavKey, picker)

        repeat(2) { popEntry(backStack, picker) }

        assertThat(backStack).containsExactly(TextNavKey, ComposerNavKey).inOrder()
    }

    @Test
    fun `the composer reports itself removed exactly once, however hard it is tapped`() {
        // The side effect key: `pop` discards the draft only when this returns
        // ComposerNavKey, so a declined tap must return null — otherwise a
        // double-tap wipes the draft of a screen that is still on screen.
        val backStack = stackOf(TextNavKey, ComposerNavKey)

        val removed = List(3) { popEntry(backStack, ComposerNavKey) }

        assertThat(removed).containsExactly(ComposerNavKey, null, null).inOrder()
        assertThat(backStack).containsExactly(TextNavKey)
    }

    // ---- Ordinary back must still work ----------------------------------------------------------

    @Test
    fun `an ordinary single pop from depth two still works`() {
        val backStack = stackOf(TextNavKey, SettingsNavKey)

        assertThat(popEntry(backStack, SettingsNavKey)).isEqualTo(SettingsNavKey)
        assertThat(backStack).containsExactly(TextNavKey)
    }

    @Test
    fun `every destination pops itself, at every depth`() {
        // Guarding must not turn any affordance into a dead end (EDGE_CASES
        // no-dead-end): each key, popped from its own screen, leaves.
        listOf(CameraNavKey, ChatNavKey, HistoryNavKey, LanguagesNavKey, PaywallNavKey, picker)
            .forEach { key ->
                val backStack = stackOf(TextNavKey, SettingsNavKey, key)

                assertThat(popEntry(backStack, key)).isEqualTo(key)
                assertThat(backStack).containsExactly(TextNavKey, SettingsNavKey).inOrder()
            }
    }

    // ---- System back: no caller, so no identity check ---------------------------------------------

    @Test
    fun `system back pops the top whatever it is`() {
        val backStack = stackOf(TextNavKey, SettingsNavKey)

        assertThat(popEntry(backStack)).isEqualTo(SettingsNavKey)
        assertThat(backStack).containsExactly(TextNavKey)
    }

    @Test
    fun `system back fired once per entry removes them all`() {
        // NavDisplay.kt:564 — `repeat(entries.size - scene.previousEntries.size)
        // { onBack() }`. A multi-entry predictive-back gesture calls onBack more
        // than once ON PURPOSE, so the identity check must never reach this path.
        val backStack = stackOf(TextNavKey, SettingsNavKey, HistoryNavKey)

        repeat(2) { popEntry(backStack) }

        assertThat(backStack).containsExactly(TextNavKey)
    }

    @Test
    fun `nothing empties the stack, by either door`() {
        val systemBack = stackOf(TextNavKey)
        val ownScreen = stackOf(TextNavKey)

        repeat(5) { popEntry(systemBack) }
        repeat(5) { popEntry(ownScreen, TextNavKey) }

        assertThat(systemBack).containsExactly(TextNavKey)
        assertThat(ownScreen).containsExactly(TextNavKey)
    }

    // ---- Identity is by value, and the picker carries data ----------------------------------------

    @Test
    fun `the target picker cannot pop a source picker`() {
        // LanguagePickerNavKey is the one key with a payload, so its identity is
        // structural: a callback captured for the OTHER picker is a stale caller.
        val backStack = stackOf(TextNavKey, LanguagePickerNavKey(forSource = true))

        assertThat(popEntry(backStack, LanguagePickerNavKey(forSource = false))).isNull()
        assertThat(backStack).hasSize(2)
        assertThat(popEntry(backStack, LanguagePickerNavKey(forSource = true)))
            .isEqualTo(LanguagePickerNavKey(forSource = true))
    }
}
