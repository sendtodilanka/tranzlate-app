package com.codeboxlk.tranzlate.feature.text

import com.codeboxlk.tranzlate.core.testing.TestDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Rule
import org.junit.Test

/**
 * Sheet 19m's guard — the "TextViewModel verify" the ruling names for #130 PR-20.
 *
 * The picker commits a language choice straight to prefs (it does not call this
 * VM) and does not refuse the opposite side's language, so it can leave the
 * selection **degenerate** — source and target the same real language. This class
 * pins that the VM (a) raises 19m only for that state and (b) makes Swap restore
 * the pair the duplicate displaced.
 *
 * Mutations decided BEFORE the assertions (rule 11), each run afterwards and each
 * reddened the test it was aimed at (see the PR body's Reproduced: block):
 * - `source == target` → `source != target` in `duplicateSelection`: the sheet
 *   fires on every ordinary pair and never on the degenerate one — reddens both
 *   `...raises no...` and `...raises 19m...`.
 * - swap direction `setLanguagePair(sourceId = validTarget, targetId = validSource)`
 *   → the two ids swapped: Swap restores the pre-duplicate pair UNSWAPPED, so the
 *   language the user picked lands on the wrong side — reddens `Swap restores...`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DuplicateSelectionTest {
    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val dispatcherRule = TestDispatcherRule(dispatcher)

    private fun settle() = dispatcher.scheduler.advanceUntilIdle()

    @Test
    fun `a distinct pair raises no duplicate-selection sheet`() {
        val prefs = FakeTranslatePrefsRepository() // en -> fr
        val vm = textViewModel(dispatcher, prefs = prefs)
        settle()

        assertThat(vm.duplicateSelection.value).isNull()
    }

    @Test
    fun `picking the target already on the source raises 19m with that language`() {
        val prefs =
            FakeTranslatePrefsRepository().apply {
                source.value = "af"
                target.value = "es"
            }
        val vm = textViewModel(dispatcher, prefs = prefs)
        settle()
        assertThat(vm.duplicateSelection.value).isNull()

        // What the picker does: it writes the choice straight to prefs. Here the
        // user picked af (already the source) as the target.
        prefs.target.value = "af"
        settle()

        assertThat(vm.duplicateSelection.value).isEqualTo("af")
    }

    @Test
    fun `Swap restores the pair the duplicate displaced`() {
        val prefs =
            FakeTranslatePrefsRepository().apply {
                source.value = "af"
                target.value = "es"
            }
        val vm = textViewModel(dispatcher, prefs = prefs)
        settle()
        // Degenerate: the user picked af (the source) as the target, displacing es.
        prefs.target.value = "af"
        settle()

        assertThat(vm.onSwapLanguages()).isTrue()
        settle()

        // The last valid pair was af -> es; Swap restores it swapped, so the
        // language the user just picked (af) lands where they picked it (target),
        // and the displaced es comes back as the source. The guard clears itself.
        assertThat(prefs.source.value).isEqualTo("es")
        assertThat(prefs.target.value).isEqualTo("af")
        assertThat(vm.duplicateSelection.value).isNull()
    }

    @Test
    fun `a Detect source never looks like a duplicate`() {
        // "auto" is never a real target, so source == target can never hold with
        // Detect — 19m must stay silent even though the ids are equal strings only
        // when both are real.
        val prefs =
            FakeTranslatePrefsRepository().apply {
                source.value = "auto"
                target.value = "auto"
            }
        val vm = textViewModel(dispatcher, prefs = prefs)
        settle()

        assertThat(vm.duplicateSelection.value).isNull()
    }
}
