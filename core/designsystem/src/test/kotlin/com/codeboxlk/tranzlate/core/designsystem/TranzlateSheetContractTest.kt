package com.codeboxlk.tranzlate.core.designsystem

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Spec-§5 sheet-anatomy contract (issue #130 PR-8), pinned to the depth the
 * JVM harness allows. This repo has NO Robolectric/screenshot harness (plan
 * issue-17 §: catalog carries none), so composables cannot be composed here;
 * what CAN be pinned on the JVM is everything the composables delegate to —
 * the pure tone→role resolvers, the type metrics, the contract dimensions and
 * the action-shape validator — against the real token schemes.
 *
 * INSTRUMENTED-ONLY assertions (documented per the PR-8 contract; they need a
 * compose rule on a device/emulator, `app/src/androidTestProd` style):
 *  - the content root carries `paneTitle == title` and the title node
 *    `heading()` semantics;
 *  - each action NODE carries the caller's testTag and is clickable/enabled
 *    per [TranzlateSheetAction.enabled];
 *  - dismiss affordances actually fire `onDismissRequest` (back press, scrim
 *    tap, handle drag) and the drag handle is always present;
 *  - measured geometry: ≥48dp action touch targets, 44dp icon slot, list
 *    region scrolling while actions stay on screen.
 */
class TranzlateSheetContractTest {
    private val schemes = listOf(TranzlateLightColors, TranzlateDarkColors)

    // ---- Tone → icon-slot roles -----------------------------------------------------------------

    @Test
    fun `neutral icon slot sits on the primary container roles in both schemes`() {
        schemes.forEach { scheme ->
            assertThat(sheetIconContainerColor(TranzlateSheetTone.Neutral, scheme))
                .isEqualTo(scheme.primaryContainer)
            assertThat(sheetIconContentColor(TranzlateSheetTone.Neutral, scheme))
                .isEqualTo(scheme.onPrimaryContainer)
        }
    }

    @Test
    fun `loss icon slot sits on the error container roles in both schemes`() {
        schemes.forEach { scheme ->
            assertThat(sheetIconContainerColor(TranzlateSheetTone.Loss, scheme))
                .isEqualTo(scheme.errorContainer)
            assertThat(sheetIconContentColor(TranzlateSheetTone.Loss, scheme))
                .isEqualTo(scheme.onErrorContainer)
        }
    }

    // ---- Tone → action roles --------------------------------------------------------------------

    @Test
    fun `neutral filled action is stock primary on onPrimary`() {
        schemes.forEach { scheme ->
            assertThat(sheetFilledActionContainerColor(TranzlateSheetTone.Neutral, scheme))
                .isEqualTo(scheme.primary)
            assertThat(sheetFilledActionContentColor(TranzlateSheetTone.Neutral, scheme))
                .isEqualTo(scheme.onPrimary)
        }
    }

    @Test
    fun `loss filled action is error on onError — the 19f Remove shape`() {
        schemes.forEach { scheme ->
            assertThat(sheetFilledActionContainerColor(TranzlateSheetTone.Loss, scheme))
                .isEqualTo(scheme.error)
            assertThat(sheetFilledActionContentColor(TranzlateSheetTone.Loss, scheme))
                .isEqualTo(scheme.onError)
        }
    }

    @Test
    fun `text action content is primary, or error only when the action itself is the loss`() {
        schemes.forEach { scheme ->
            assertThat(sheetTextActionContentColor(TranzlateSheetTone.Neutral, scheme))
                .isEqualTo(scheme.primary)
            assertThat(sheetTextActionContentColor(TranzlateSheetTone.Loss, scheme))
                .isEqualTo(scheme.error)
        }
    }

    // ---- The reservation rule: the sheet itself never turns red ---------------------------------

    @Test
    fun `title and body colours take no tone parameter and never resolve to an error role`() {
        // The API shape is the first half of the guarantee: sheetTitleColor /
        // sheetBodyColor accept only a scheme, so no caller CAN pass Loss.
        schemes.forEach { scheme ->
            assertThat(sheetTitleColor(scheme)).isEqualTo(scheme.onSurface)
            assertThat(sheetBodyColor(scheme)).isEqualTo(scheme.onSurfaceVariant)
            assertThat(sheetTitleColor(scheme)).isNotEqualTo(scheme.error)
            assertThat(sheetBodyColor(scheme)).isNotEqualTo(scheme.error)
        }
    }

    @Test
    fun `neutral tone resolves no error-family colour anywhere`() {
        schemes.forEach { scheme ->
            val neutralColors =
                listOf(
                    sheetIconContainerColor(TranzlateSheetTone.Neutral, scheme),
                    sheetIconContentColor(TranzlateSheetTone.Neutral, scheme),
                    sheetFilledActionContainerColor(TranzlateSheetTone.Neutral, scheme),
                    sheetFilledActionContentColor(TranzlateSheetTone.Neutral, scheme),
                    sheetTextActionContentColor(TranzlateSheetTone.Neutral, scheme),
                )
            val errorFamily =
                listOf(scheme.error, scheme.errorContainer, scheme.onErrorContainer)
            neutralColors.forEach { color -> assertThat(color).isNotIn(errorFamily) }
        }
    }

    // ---- Type metrics (spec §5: 20sp title, 13.5sp body; export: 15sp actions) ------------------

    @Test
    fun `title style is 20sp over 26sp and REGULAR weight`() {
        val style = sheetTitleTextStyle(TranzlateTypography)
        assertThat(style.fontSize).isEqualTo(20.sp)
        assertThat(style.lineHeight).isEqualTo(26.sp)
        assertThat(style.fontWeight).isEqualTo(FontWeight.Normal)
        assertThat(style.letterSpacing).isEqualTo(0.sp)
    }

    @Test
    fun `body style is 13_5sp on the bodyMedium metrics`() {
        val style = sheetBodyTextStyle(TranzlateTypography)
        assertThat(style.fontSize).isEqualTo(13.5.sp)
        assertThat(style.lineHeight).isEqualTo(TranzlateTypography.bodyMedium.lineHeight)
        assertThat(style.letterSpacing).isEqualTo(TranzlateTypography.bodyMedium.letterSpacing)
    }

    @Test
    fun `action label style is 15sp Medium on the labelLarge metrics`() {
        val style = sheetActionTextStyle(TranzlateTypography)
        assertThat(style.fontSize).isEqualTo(15.sp)
        assertThat(style.fontWeight).isEqualTo(FontWeight.Medium)
        assertThat(style.letterSpacing).isEqualTo(TranzlateTypography.labelLarge.letterSpacing)
    }

    // ---- Contract dimensions --------------------------------------------------------------------

    @Test
    fun `spec contract metrics are pinned — 44dp slot, 48dp actions, 48dp compact rows`() {
        assertThat(TranzlateSheetDefaults.IconSlotSize).isEqualTo(44.dp)
        assertThat(TranzlateSheetDefaults.IconSize).isEqualTo(22.dp)
        assertThat(TranzlateSheetDefaults.ActionMinHeight).isEqualTo(48.dp)
        assertThat(TranzlateSheetDefaults.ListRowMinHeight).isEqualTo(48.dp)
    }

    // ---- Action shape ---------------------------------------------------------------------------

    @Test
    fun `action defaults are neutral tone and enabled`() {
        val action = TranzlateSheetAction(label = "x", testTag = "tt_x", onClick = {})
        assertThat(action.tone).isEqualTo(TranzlateSheetTone.Neutral)
        assertThat(action.enabled).isTrue()
        assertThat(action.testTag).isEqualTo("tt_x")
    }

    @Test
    fun `a secondary action without a primary is rejected`() {
        val secondary = TranzlateSheetAction(label = "x", testTag = "tt_x", onClick = {})
        assertThrows(IllegalArgumentException::class.java) {
            validateSheetActions(primaryAction = null, secondaryAction = secondary)
        }
    }

    @Test
    fun `every drawn action combination is accepted`() {
        val a = TranzlateSheetAction(label = "x", testTag = "tt_x", onClick = {})
        validateSheetActions(primaryAction = null, secondaryAction = null)
        validateSheetActions(primaryAction = a, secondaryAction = null)
        validateSheetActions(primaryAction = a, secondaryAction = a)
    }
}
