package com.codeboxlk.tranzlate.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Issue #158 — MEASUREMENT, not assumption.
 *
 * The claim: two DIFFERENT Home cards tapped in one frame both push, because
 * `navigateTo`'s equal-key-only guard never fires for two different keys. `#158`
 * says measure it — the pop-side race (#150/#156) was assumed unreachable too.
 *
 * This is the device-dependent half (F1): does Compose's input dispatch actually
 * deliver a tap to TWO different `clickable` nodes within one frame, before
 * recomposition removes the second? A genuine two-finger simultaneous gesture is
 * injected as one multi-pointer stream (down p0 on A, down p1 on B, up both) — not
 * two sequential clicks — onto two `Surface(onClick=…)` cards, the exact affordance
 * `HomeScreen.ToolCard` is built from.
 *
 * A click counter guards against a false negative: if only one card fired, the
 * gesture missed and the run proves nothing about reachability, not that the harm
 * is absent. Reachable ⇔ counter == 2 AND both keys landed on the stack.
 *
 * CI never runs this (no emulator; Espresso `onIdle` breaks on API 35+, #40). Run
 * locally on an API ≤ 34 image; the fast regression is the JVM `BackStackPushTest`.
 * Verified on `emulator-5556` (Tranzlate_API29).
 */
class NavDoublePushReachabilityTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val clicks = AtomicInteger(0)

    /**
     * Two always-present cards over the real back-stack type, wired to the guard
     * under test. `composedTop` is read in the composable body (a composition-time
     * snapshot read) and handed to the click handler as `from`, exactly as the fix
     * has the shell capture it for `navigateTo`.
     */
    @Composable
    private fun TwoCardHarness(
        backStack: NavBackStack<NavKey>,
        onCardClick: (from: NavKey?, key: NavKey) -> Unit,
    ) {
        val composedTop = backStack.lastOrNull()
        Column {
            Surface(
                onClick = {
                    clicks.incrementAndGet()
                    onCardClick(composedTop, CameraNavKey)
                },
                modifier = Modifier.fillMaxWidth().height(220.dp).testTag(CARD_A),
            ) { Text("Camera") }
            Spacer(Modifier.height(40.dp))
            Surface(
                onClick = {
                    clicks.incrementAndGet()
                    onCardClick(composedTop, LanguagesNavKey)
                },
                modifier = Modifier.fillMaxWidth().height(220.dp).testTag(CARD_B),
            ) { Text("Languages") }
        }
    }

    private fun twoFingerSimultaneousTap() {
        val a =
            composeTestRule
                .onNodeWithTag(CARD_A)
                .fetchSemanticsNode()
                .boundsInRoot.center
        val b =
            composeTestRule
                .onNodeWithTag(CARD_B)
                .fetchSemanticsNode()
                .boundsInRoot.center
        // One gesture stream: both fingers down, then both up, delivered before any
        // recomposition — the same-frame two-finger tap the issue describes.
        composeTestRule.onRoot().performTouchInput {
            down(0, a)
            down(1, b)
            up(0)
            up(1)
        }
        composeTestRule.waitForIdle()
    }

    /**
     * REACHABILITY: with the current equal-key-only guard, one two-finger gesture
     * pushes BOTH cards. Start `[Text]`, expect `[Text, Camera, Languages]`.
     */
    @Test
    fun equalKeyOnlyGuard_admitsTwoDifferentCardsInOneFrame() {
        val backStack = NavBackStack<NavKey>(TextNavKey)
        composeTestRule.setContent {
            TwoCardHarness(backStack) { _, key ->
                // The CURRENT production guard, verbatim (TranzlateApp.kt:89).
                if (backStack.lastOrNull() != key) backStack.add(key)
            }
        }

        twoFingerSimultaneousTap()

        composeTestRule.runOnIdle {
            // Control: the gesture actually reached both cards.
            assertEquals("both cards must have fired (else the gesture missed)", 2, clicks.get())
            // The harm: one gesture, two pushes.
            assertEquals(
                "unguarded push admits both different keys in one frame",
                listOf<NavKey>(TextNavKey, CameraNavKey, LanguagesNavKey),
                backStack.toList(),
            )
        }
    }

    /**
     * THE FIX under the SAME real input: route both same-frame taps through the real
     * [pushEntry] with `from` = the composed top. Both cards still fire (clicks == 2),
     * but the second — a card on a screen the first push already left — is declined, so
     * one gesture yields one push: `[Text, Camera]`.
     */
    @Test
    fun guardedPushEntry_declinesTheSecondSameFrameCard() {
        val backStack = NavBackStack<NavKey>(TextNavKey)
        composeTestRule.setContent {
            TwoCardHarness(backStack) { from, key ->
                pushEntry(backStack, from = from, key = key)
            }
        }

        twoFingerSimultaneousTap()

        composeTestRule.runOnIdle {
            assertEquals("both cards must have fired (else the gesture missed)", 2, clicks.get())
            assertEquals(
                "guarded push admits only the first of two same-frame cards",
                listOf<NavKey>(TextNavKey, CameraNavKey),
                backStack.toList(),
            )
        }
    }

    private companion object {
        const val CARD_A = "tt_probe_card_a"
        const val CARD_B = "tt_probe_card_b"
    }
}
