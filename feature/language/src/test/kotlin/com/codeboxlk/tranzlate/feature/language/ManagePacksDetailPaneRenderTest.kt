package com.codeboxlk.tranzlate.feature.language

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme
import com.codeboxlk.tranzlate.core.model.OfflineModelFailure
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The 20d detail-pane blocks the conformance fix (#332) adds, each verified by a render
 * because each is a decision inside `@Composable` code — the rev.3 ruling's fourth
 * cause. Every test names the mutation it reddens under, decided BEFORE the assertion
 * so the assertion is not shaped by the code it reads.
 *
 * The pane is mounted directly (it is `internal`) with LITERAL fake data, past the
 * two-pane and the width gate — those are `ManagePacksListDetailRenderTest`'s subject.
 * `w1280dp-h800dp` gives the pane the tablet height the frame is drawn at, so every
 * block is laid out (and `performScrollTo` covers anything below the fold).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1280dp-h800dp")
class ManagePacksDetailPaneRenderTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun downloadedPack(
        id: String = "af",
        name: String = "Afrikaans",
        hasOfflineVoice: Boolean = false,
        inUse: Boolean = false,
    ) = PackRow(
        id = id,
        displayName = name,
        state = OfflineModelState.Downloaded,
        usage = PackUsage.NoRecord,
        inUse = inUse,
        hasOfflineVoice = hasOfflineVoice,
    )

    private fun mount(
        pack: PackRow,
        savedCount: Int = 0,
        roleUsage: PackRoleUsage = PackRoleUsage(asSource = PackUsage.NoRecord, asTarget = PackUsage.NoRecord),
        onRemove: (String) -> Unit = {},
    ) {
        compose.setContent {
            TranzlateTheme {
                ManagePacksDetailPane(
                    pack = pack,
                    roleUsage = roleUsage,
                    savedCount = savedCount,
                    nowMillis = 0L,
                    onRemove = onRemove,
                )
            }
        }
        compose.waitForIdle()
    }

    /**
     * A downloaded pack draws the Text-offline capability card, "supported".
     *
     * Mutation (decided first): delete the Text `CapabilityCard` from `DetailCapabilities`.
     * The tagged subtitle then never mounts and this reddens.
     */
    @Test
    fun `the detail pane draws the text-offline card`() {
        mount(downloadedPack())

        compose
            .onNodeWithTag("tt_manage_detail_cap_text", useUnmergedTree = true)
            .assertTextEquals(context.getString(R.string.manage_detail_cap_text_ready))
    }

    /**
     * The voice card is "supported" ONLY when the pack has an on-device voice; without
     * one it is muted, and says so in words ("Needs a connection"), not colour alone.
     *
     * Mutation (decided first): in `packDetail`, drop `&& row.hasOfflineVoice` so
     * `voiceOffline` is Supported for every on-device pack. A no-voice pack then reads
     * "Has an on-device voice" and the muted assertion reddens — the paired voiced-pack
     * assertion stays green, which is what makes this an IFF and not "a card exists".
     */
    @Test
    fun `the voice card is supported only with an on-device voice`() {
        mount(downloadedPack(hasOfflineVoice = false))
        compose
            .onNodeWithTag("tt_manage_detail_cap_voice", useUnmergedTree = true)
            .assertTextEquals(context.getString(R.string.manage_detail_cap_offline_unavailable))
    }

    @Test
    fun `the voice card reads its ready subtitle when the pack has a voice`() {
        mount(downloadedPack(hasOfflineVoice = true))
        compose
            .onNodeWithTag("tt_manage_detail_cap_voice", useUnmergedTree = true)
            .assertTextEquals(context.getString(R.string.manage_detail_cap_voice_ready))
    }

    /**
     * Above zero, the saved-phrases line draws the plural with the language name.
     *
     * Mutation (decided first): make the `savedCount > 0` guard unconditional-false
     * (`if (false)`), hiding the line. The node then never mounts and this reddens.
     */
    @Test
    fun `the saved-phrases line shows above zero`() {
        mount(downloadedPack(name = "Afrikaans"), savedCount = 3)

        compose
            .onNodeWithTag("tt_manage_detail_saved", useUnmergedTree = true)
            .assertTextEquals(context.resources.getQuantityString(R.plurals.manage_detail_saved, 3, 3, "Afrikaans"))
    }

    /** At zero, the saved line is ABSENT — a missing reassurance, never "0 saved phrases". */
    @Test
    fun `the saved-phrases line is absent at zero`() {
        mount(downloadedPack(), savedCount = 0)

        compose.onNodeWithTag("tt_manage_detail_saved", useUnmergedTree = true).assertDoesNotExist()
    }

    /**
     * The Remove button ROUTES to the confirm flow — it calls `onRemove(id)`, the same
     * seam the 20c sheet uses; it never deletes on tap.
     *
     * Mutation (decided first): point the button's `onClick` at `{}`. The spy is then
     * never called and this reddens — the block drawn but wired to nothing.
     */
    @Test
    fun `the remove button routes the pack id to onRemove`() {
        var removed: String? = null
        mount(downloadedPack(id = "af"), onRemove = { removed = it })

        compose.onNodeWithTag("tt_manage_detail_remove").performScrollTo().performClick()

        assertThat(removed).isEqualTo("af")
    }

    /**
     * The Remove block is drawn ONLY for a removable pack. The English pivot (#224) is
     * non-actionable — removing it frees nothing — so its detail carries NO Remove.
     *
     * Mutation (decided first): drop `!isPivotLanguage(row.id)` from `packDetail.removable`
     * so English becomes removable. The button then mounts and this reddens.
     */
    @Test
    fun `the pivot has no remove block`() {
        mount(downloadedPack(id = "en", name = "English"))

        compose.onNodeWithTag("tt_manage_detail_remove").assertDoesNotExist()
    }

    /**
     * A FAILED pack is not on the device, so it draws neither the "On device" status nor
     * a Remove block, and its capabilities are muted — the honesty invariant across every
     * row the detail can select, not only a settled one.
     *
     * Mutation (decided first): make `packDetail.onDevice` true for a Failed state (e.g.
     * add `|| state is Failed`). The status subtitle then claims "On device" for a pack
     * that never downloaded, and this reddens.
     */
    @Test
    fun `a failed pack shows no on-device status and no remove`() {
        mount(
            PackRow(
                id = "hi",
                displayName = "Hindi",
                state = OfflineModelState.Failed(OfflineModelFailure.NETWORK),
                usage = PackUsage.NoRecord,
                inUse = false,
            ),
        )

        compose.onNodeWithTag("tt_manage_detail_remove").assertDoesNotExist()
        // The subtitle is the failure line, never the on-device claim (ruling ⑧).
        compose.onNodeWithText(context.getString(R.string.manage_detail_status_on_device)).assertDoesNotExist()
    }

    /** The on-device status subtitle is the frame's line, and it IS displayed for a downloaded pack. */
    @Test
    fun `an on-device pack shows the ready status subtitle`() {
        mount(downloadedPack())

        compose
            .onNodeWithTag("tt_manage_detail_status", useUnmergedTree = true)
            .assertTextEquals(context.getString(R.string.manage_detail_status_on_device))
            .assertIsDisplayed()
    }
}
