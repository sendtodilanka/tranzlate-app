package com.codeboxlk.tranzlate.feature.language

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The lifetime of the card's ViewModel (#130 PR-16) — the trap PR-13 named, with
 * a test on each side of it.
 *
 * ## What the trap is
 *
 * `hiltViewModel()` resolves against `LocalViewModelStoreOwner`. Inside
 * `NavDisplay` that is the nav entry, cleared when the entry is popped. The
 * tablet card is composed OUTSIDE `NavDisplay` — it has to be, or the screen it
 * sits over would not be drawn — so there the same call resolves to the
 * **Activity**, and the picker would get a ViewModel that outlives every screen
 * in the app. That is the third screen-outliving scope the ruling's §2 inventory
 * bounces at review, and the user-visible half of it is that reopening the card
 * restores a search typed ten minutes ago on a different screen.
 *
 * The opposite mistake is just as quiet: a scope that is remembered in the
 * COMPOSITION rather than hung off the Activity is destroyed by a rotation, and
 * the card comes back with an empty search field and the list at the top.
 *
 * ## Why this drives [rememberPickerDialogScope] and not `PickerDialogHost`
 *
 * The host's one remaining line is `hiltViewModel(rememberPickerDialogScope())`,
 * and `hiltViewModel` needs a Hilt-instrumented application, which this module's
 * plain Robolectric Compose runtime (#186) does not have. So the SCOPE is tested
 * here with an ordinary ViewModel, and that the host uses it is a source rule in
 * `PickerHostAgnosticTest` — honest about being one, in the same way that test's
 * own rules are.
 *
 * ## What the two assertions cover between them
 *
 * - **Cleared when the card closes.** An Activity-scoped ViewModel is not, which
 *   is the mutation this test exists for.
 * - **Held in the Activity's store, not in the composition.** That is androidx's
 *   own mechanism for surviving a configuration change
 *   (`ViewModelStoreProvider`: `parentStore.getOrPut(parentKey)`), and a scope
 *   with no parent has none of it.
 *
 * The rotation ITSELF is not simulated here and the reason is worth stating
 * rather than leaving to be discovered: a Robolectric `key()` swap disposes the
 * subtree while the Activity is still RESUMED, which is exactly the case
 * androidx's clear-on-dispose reads as "the user closed it". It would therefore
 * prove the opposite of a rotation. The real rotation was run on
 * `emulator-5554` with a query typed into the card — see the PR body.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w800dp-h1280dp")
class PickerDialogScopeTest {
    @get:Rule
    val compose = createComposeRule()

    /** Something to scope. Reports its own clearing, which is the whole question. */
    private class ScopedProbe : ViewModel() {
        var cleared: Boolean = false
            private set

        override fun onCleared() {
            cleared = true
        }
    }

    private var open by mutableStateOf(true)
    private var probe: ScopedProbe? = null
    private var parentStoreKeysWhileOpen: Int = -1
    private var parentStoreKeysBefore: Int = -1

    @Composable
    private fun Harness() {
        val parent = checkNotNull(LocalViewModelStoreOwner.current)
        if (parentStoreKeysBefore < 0) parentStoreKeysBefore = parent.viewModelStore.keys().size
        if (open) {
            val owner = rememberPickerDialogScope()
            probe = viewModel(viewModelStoreOwner = owner) { ScopedProbe() }
            parentStoreKeysWhileOpen = parent.viewModelStore.keys().size
        }
    }

    private fun showCard() {
        open = true
        compose.setContent { Harness() }
        compose.waitForIdle()
    }

    /**
     * The scope is a CHILD of the Activity's store — which is what carries it
     * through a rotation, because that store is what survives one.
     *
     * `parent = null` in [rememberPickerDialogScope] would build a root provider
     * held by `remember` instead: the card would still work, right up until the
     * first rotation threw the search query away.
     */
    @Test
    fun `the card's scope hangs off the parent store`() {
        showCard()

        assertThat(probe).isNotNull()
        assertWithMessage(
            "the picker's scope must live inside the Activity's ViewModelStore — that is " +
                "what survives a configuration change (ViewModelStoreProvider: " +
                "parentStore.getOrPut(parentKey))",
        ).that(parentStoreKeysWhileOpen)
            .isGreaterThan(parentStoreKeysBefore)
    }

    /**
     * …and it is cleared the moment the card closes.
     *
     * This is the assertion a plain `hiltViewModel()` fails. An Activity-scoped
     * picker ViewModel is cleared when the Activity finishes and at no other
     * time, so this probe would come back from a close still alive, still
     * holding its flows, and still holding the last search.
     */
    @Test
    fun `closing the card clears its ViewModel`() {
        showCard()
        val scoped = checkNotNull(probe)
        assertThat(scoped.cleared).isFalse()

        open = false
        compose.waitForIdle()

        assertWithMessage(
            "an Activity-scoped picker would survive here — a third screen-outliving scope, " +
                "which the ruling's VM inventory bounces at review",
        ).that(scoped.cleared)
            .isTrue()
    }

    /**
     * Reopening gives a FRESH ViewModel, which is the same fact from the user's
     * side: the card opens with an empty search field rather than with whatever
     * was typed into it last time.
     */
    @Test
    fun `reopening the card starts a new ViewModel`() {
        showCard()
        val first = checkNotNull(probe)

        open = false
        compose.waitForIdle()
        open = true
        compose.waitForIdle()

        assertThat(probe).isNotNull()
        assertThat(probe).isNotSameInstanceAs(first)
    }

    /** While it stays open, recomposing does not build a second one. */
    @Test
    fun `the card keeps one ViewModel while it is open`() {
        showCard()
        val first = checkNotNull(probe)

        compose.runOnIdle { }
        compose.waitForIdle()

        assertThat(probe).isSameInstanceAs(first)
    }
}
