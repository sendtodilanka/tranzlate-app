package com.codeboxlk.tranzlate.feature.language

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * The 20c pack-actions sheet as the SCREEN wires it (#130 PR-24). The three joins
 * live in `@Composable` code, so — the rev3 ruling's fourth cause — only a render
 * test sees them: that the overflow opens the SHEET (not the remove flow straight
 * off, which is what PR-23 wired and `OfflineRemoveFlowRenderTest` now re-checks
 * for the new route), that "Use as target now" carries the pack id to the write,
 * that the voice line is drawn ONLY when the device can speak the language, and
 * that "Remove pack" reaches the existing 19f confirm.
 *
 * Rendered through the real content + the real classifier so a mutation to any of
 * those decisions reddens here rather than passing on a green ViewModel test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class PackActionsSheetRenderTest {
    @get:Rule
    val compose = createComposeRule()

    private val targetWrites = mutableListOf<String>()
    private val removeRequests = mutableListOf<String>()

    // onRemove both records AND raises the 19f question, so a single flow proves the
    // route the overflow used to take directly: overflow → 20c → Remove → 19f.
    private val pending = mutableStateOf<PendingPackRemoval?>(null)

    private fun show(row: OfflineLanguageRow) {
        compose.setContent {
            val sections =
                buildManagePacksSections(
                    listOf(row),
                    usage = emptyMap(),
                    targetId = "",
                    nowMillis = 0L,
                    locale = Locale.ENGLISH,
                )
            TranzlateTheme {
                ManagePacksContent(
                    loading = false,
                    sections = sections,
                    storage = null,
                    nudge = null,
                    suggestions = emptyList(),
                    capable = 59,
                    total = 194,
                    onBack = {},
                    onGet = {},
                    onStopDownload = {},
                    onRetry = {},
                    onRemove = {
                        removeRequests += it
                        pending.value = PendingPackRemoval(id = it, inUseAsTarget = false, savedCount = 0)
                    },
                    onDismissNudge = {},
                    onBrowseAll = {},
                    onUseAsTarget = { targetWrites += it },
                    pendingRemoval = pending.value,
                )
            }
        }
        compose.waitForIdle()
    }

    private fun openSheetFor(row: OfflineLanguageRow) {
        show(row)
        compose.onNodeWithTag("tt_manage_options").performClick()
        compose.waitForIdle()
    }

    /** The overflow opens the SHEET. Wiring it back to `onRemove` (PR-23's route) reddens here. */
    @Test
    fun `the overflow opens the 20c pack-actions sheet`() {
        show(OfflineLanguageRow("es", "Spanish", OfflineModelState.Downloaded))
        compose.onNodeWithTag(TT_SHEET_PACK_ACTIONS).assertDoesNotExist()

        compose.onNodeWithTag("tt_manage_options").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(TT_SHEET_PACK_ACTIONS).assertExists()
        compose.onNodeWithTag(TT_SHEET_PACK_USE).assertExists()
        compose.onNodeWithTag(TT_SHEET_PACK_REMOVE).assertExists()
    }

    /**
     * The tappable rows carry the button role, so TalkBack announces "button" and not
     * a bare label (the co-verify defect on PR-24). Mutation decided first: drop
     * `role = Role.Button` from [PackActionRow]'s `clickable` — the Role reads
     * Unspecified and both assertions redden. The voice line is deliberately NOT here:
     * it is informational, not a control, so it must carry no button role.
     */
    @Test
    fun `the action rows carry the button role`() {
        openSheetFor(OfflineLanguageRow("es", "Spanish", OfflineModelState.Downloaded))

        compose
            .onNodeWithTag(TT_SHEET_PACK_USE)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        compose
            .onNodeWithTag(TT_SHEET_PACK_REMOVE)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    /**
     * Mutation decided first: point the Use row's `onClick` at `{}` (or at `onRemove`)
     * — `targetWrites` is then empty and this reddens. The empty `removeRequests`
     * guard catches the "wired to the wrong action" mutation the other way.
     */
    @Test
    fun `Use as target now carries the pack id to the write`() {
        openSheetFor(OfflineLanguageRow("es", "Spanish", OfflineModelState.Downloaded))

        compose.onNodeWithTag(TT_SHEET_PACK_USE).performClick()
        compose.waitForIdle()

        assertThat(targetWrites).containsExactly("es")
        assertThat(removeRequests).isEmpty()
    }

    /** The voice line is drawn when the device can speak the language offline. */
    @Test
    fun `the voice line shows for a pack this device can speak offline`() {
        openSheetFor(OfflineLanguageRow("es", "Spanish", OfflineModelState.Downloaded, hasOfflineVoice = true))

        compose.onNodeWithTag(TT_SHEET_PACK_VOICE).assertExists()
    }

    /**
     * The IFF's other half. Mutation decided first: draw [PackVoiceLine] unconditionally
     * (drop the `if (target.hasOfflineVoice)`) — the voice node then exists for a no-voice
     * pack and this reddens. Asserting the Use row IS present keeps the absence honest:
     * it proves the sheet actually opened, so `assertDoesNotExist` is not passing merely
     * because nothing rendered.
     */
    @Test
    fun `the voice line is absent for a pack with no offline voice`() {
        openSheetFor(OfflineLanguageRow("af", "Afrikaans", OfflineModelState.Downloaded, hasOfflineVoice = false))

        compose.onNodeWithTag(TT_SHEET_PACK_USE).assertExists()
        compose.onNodeWithTag(TT_SHEET_PACK_VOICE).assertDoesNotExist()
    }

    /**
     * Remove routes to the EXISTING confirm rather than deleting on the tap. Mutation
     * decided first: point the Remove row's `onClick` at `{}` — `removeRequests` is empty,
     * the 19f sheet never appears, and this reddens.
     */
    @Test
    fun `Remove pack in the 20c sheet reaches the 19f confirm`() {
        openSheetFor(OfflineLanguageRow("es", "Spanish", OfflineModelState.Downloaded))

        compose.onNodeWithTag(TT_SHEET_PACK_REMOVE).performClick()
        compose.waitForIdle()

        assertThat(removeRequests).containsExactly("es")
        compose.onNodeWithTag(TT_SHEET_REMOVE).assertExists()
    }
}
