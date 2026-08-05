package com.codeboxlk.tranzlate.feature.text

import androidx.lifecycle.SavedStateHandle
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
 * pins that the VM (a) raises 19m only for that state, (b) makes Swap restore the
 * pair the duplicate displaced, and (c) — the #299 co-verify fix — NEVER
 * dead-ends 19m even when the remembered pair is gone after process death.
 *
 * Mutations decided BEFORE the assertions (rule 11), each run afterwards and each
 * reddened the test it was aimed at (see the PR body's Reproduced: block):
 * - `source == target` → `source != target` in `duplicateSelection`: the sheet
 *   fires on every ordinary pair and never on the degenerate one — reddens both
 *   `...raises no...` and `...raises 19m...`.
 * - swap direction `lastValidTarget to lastValidSource` → the two swapped:
 *   restores the pre-duplicate pair UNSWAPPED — reddens `Swap restores...`.
 * - `degenerateResolution`'s no-remembered-pair fallback → `duplicated to
 *   duplicated`: Swap leaves the selection degenerate — reddens BOTH
 *   `Swap resolves ... process death` and `resolution never degenerates ...`.
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

    @Test
    fun `Swap resolves the degenerate state even with no remembered pair (process death)`() {
        // The #299 co-verify scenario, at the ViewModel: a degenerate pair persisted
        // in DataStore while the last-valid pair (in-memory-written by the init
        // collector) was dropped by process death. Before the fix onSwapLanguages
        // returned false and changed nothing — Swap dead, scrim/back dead. It must
        // resolve now, on the exit the sheet keeps.
        val handle = SavedStateHandle()
        val prefs =
            FakeTranslatePrefsRepository().apply {
                source.value = "de"
                target.value = "de"
            }
        val vm = textViewModel(dispatcher, prefs = prefs, handle = handle)
        settle()
        // Reproduce "the remembered pair is gone": clear the saved-state keys the
        // init collector wrote, leaving the degenerate DataStore pair standing.
        handle["text_valid_pair_src"] = null
        handle["text_valid_pair_tgt"] = null
        assertThat(vm.duplicateSelection.value).isEqualTo("de") // 19m is showing

        assertThat(vm.onSwapLanguages()).isTrue()
        settle()

        // RESOLVED — no dead end. The exact pair is a fallback default; what the
        // test pins is that it is NOT degenerate and the guard clears.
        assertThat(prefs.source.value).isNotEqualTo(prefs.target.value)
        assertThat(vm.duplicateSelection.value).isNull()
    }

    // ---- degenerateResolution (pure) — the no-dead-end guarantee ------------

    @Test
    fun `resolution with a remembered pair swaps it`() {
        assertThat(degenerateResolution(duplicated = "af", lastValidSource = "af", lastValidTarget = "es"))
            .isEqualTo("es" to "af")
    }

    @Test
    fun `resolution never degenerates even with no remembered pair`() {
        // The property the whole fix rests on: whatever comes in, the pair out is
        // valid. Covers the ordinary id AND the id that equals the default source.
        for (duplicated in listOf("de", "en", "fr")) {
            val (source, target) = degenerateResolution(duplicated, lastValidSource = null, lastValidTarget = null)
            assertThat(source).isNotEqualTo(target)
            // The duplicated language the user picked is kept, as the target.
            assertThat(target).isEqualTo(duplicated)
        }
    }
}
