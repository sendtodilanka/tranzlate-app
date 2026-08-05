package com.codeboxlk.tranzlate.feature.language

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pop-out survival (#130 PR-22, U-1): a 20a snackbar shown on the app-scoped host
 * outlives a nav pop-out, because [PackSnackbarScaffold] hosts the `SnackbarHost` ABOVE
 * the nav content, not inside a destination. This renders the REAL scaffold the app
 * shell uses; only the swappable content is a stand-in for the `NavDisplay`, since a
 * nav push/pop is, to the composition, exactly a swap of which destination renders.
 *
 * The snackbar duration is Indefinite so the test clock cannot dismiss it out from under
 * the assertion.
 *
 * Mutation decided first (rule 11): delete the `SnackbarHost(...)` from
 * [PackSnackbarScaffold]. The first test's post-swap `assertIsDisplayed` then reddens —
 * with no host above the content there is nothing to render the snackbar. The second
 * test pins the harm the scaffold prevents (a host INSIDE the departing destination is
 * lost on the swap), so the pair cannot both pass under a scaffold that hosts in the
 * wrong place.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class PackSnackbarScaffoldTest {
    @get:Rule
    val compose = createComposeRule()

    private val ready = "English is ready"

    @Test
    fun `a snackbar on the scaffold host survives a nav content swap`() {
        val page = mutableStateOf(0)
        compose.setContent {
            TranzlateTheme {
                // Host + state + the showing coroutine all live ABOVE the swap.
                val hostState = remember { SnackbarHostState() }
                LaunchedEffect(Unit) {
                    hostState.showSnackbar(ready, duration = SnackbarDuration.Indefinite)
                }
                PackSnackbarScaffold(hostState) {
                    if (page.value == 0) {
                        Text("Destination A", Modifier.testTag("destA"))
                    } else {
                        Text("Destination B", Modifier.testTag("destB"))
                    }
                }
            }
        }

        compose.onNodeWithText(ready).assertIsDisplayed()

        page.value = 1 // the pop-out: the destination swaps
        compose.onNodeWithText("Destination B").assertIsDisplayed()

        // Untouched — the host is the shell's, not the departed destination's.
        compose.onNodeWithText(ready).assertIsDisplayed()
    }

    @Test
    fun `a snackbar hosted inside a destination is lost when that destination swaps out`() {
        val page = mutableStateOf(0)
        compose.setContent {
            TranzlateTheme {
                val hostState = remember { SnackbarHostState() }
                if (page.value == 0) {
                    // The wrong place: host tied to destination A.
                    Text("Destination A", Modifier.testTag("destA"))
                    SnackbarHost(hostState)
                    LaunchedEffect(Unit) {
                        hostState.showSnackbar(ready, duration = SnackbarDuration.Indefinite)
                    }
                } else {
                    Text("Destination B", Modifier.testTag("destB"))
                }
            }
        }

        compose.onNodeWithText(ready).assertIsDisplayed()

        page.value = 1 // the pop-out takes destination A — and its host — away
        compose.onNodeWithText("Destination B").assertIsDisplayed()

        compose.onNodeWithText(ready).assertDoesNotExist()
    }
}
