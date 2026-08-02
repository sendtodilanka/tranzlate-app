package com.codeboxlk.tranzlate.feature.language

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme
import com.codeboxlk.tranzlate.core.model.OfflineModelFailure
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Sheets **19d** and **19b**, RENDERED — the same reason `MobileDataSheetTest`
 * is rendered rather than shape-checked: the mutations that matter here swap
 * lambdas and labels while leaving every tag and every string present in the
 * file, and nothing without a renderer can separate those.
 *
 * All callbacks are recorded, not only the expected one — a sheet that fired
 * BOTH on one tap would pass a test that asserted only the one it wanted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class PackFailureSheetsTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private var retries = 0
    private var manages = 0
    private var dismissals = 0

    private fun showInterrupted(cause: OfflineModelFailure = OfflineModelFailure.NETWORK) {
        compose.setContent {
            TranzlateTheme {
                InterruptedSheet(
                    languageName = "Spanish",
                    sheet = downloadFailureCopy(cause).sheet as DownloadFailureSheet.Interrupted,
                    onRetry = { retries++ },
                    onDismiss = { dismissals++ },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun showNoSpace(
        freeBytes: Long = 12L * 1024 * 1024,
        volumeBytes: Long = 64L * 1024 * 1024 * 1024,
    ) {
        compose.setContent {
            TranzlateTheme {
                NoSpaceSheet(
                    freeBytes = freeBytes,
                    volumeBytes = volumeBytes,
                    onManagePacks = { manages++ },
                    onDismiss = { dismissals++ },
                )
            }
        }
        compose.waitForIdle()
    }

    // ---- 19d ------------------------------------------------------------------------------

    /** The title names the language, because the sheet is about one download. */
    @Test
    fun `19d names the language that did not download`() {
        showInterrupted()

        compose.onNodeWithText("Spanish did not download").assertIsDisplayed()
    }

    /**
     * **The drawn body against its own caption.** The frame's caption says the
     * important reassurance is that *progress is kept*; the frame's body says
     * *nothing is on the device yet*. `DESIGNER-BRIEF.md:73` forbids the first
     * ("Do not claim kept progress") because ML Kit exposes no resume, so the
     * body is what ships. This test is where that ruling is enforced rather than
     * merely written down.
     */
    @Test
    fun `19d promises no kept progress`() {
        showInterrupted()

        compose
            .onNodeWithText(context.getString(R.string.lang_sheet_failed_body_network))
            .assertIsDisplayed()
        assertThat(context.getString(R.string.lang_sheet_failed_body_network))
            .contains("nothing is on the device yet")
    }

    /** The cause card: what happened, and what the filled action will do. */
    @Test
    fun `19d states the cause and what Retry will do`() {
        showInterrupted()

        compose
            .onNodeWithTag(TT_SHEET_FAILED_CAUSE)
            .assertTextEquals(context.getString(R.string.lang_sheet_failed_cause_network))
    }

    /**
     * A failure ML Kit did not explain gets its own two sentences. The mutation
     * is reusing the connection copy, which states a reason the app never had.
     */
    @Test
    fun `19d does not blame the connection for an unexplained failure`() {
        showInterrupted(OfflineModelFailure.UNKNOWN)

        compose
            .onNodeWithTag(TT_SHEET_FAILED_CAUSE)
            .assertTextEquals(context.getString(R.string.lang_sheet_failed_cause_generic))
        compose.onNodeWithText("Cause: connection lost. Retrying starts the download again.").assertDoesNotExist()
    }

    @Test
    fun `19d's filled action says Retry and retries`() {
        showInterrupted()

        compose.onNodeWithTag(TT_SHEET_FAILED_RETRY).assertTextEquals("Retry")
        compose.onNodeWithTag(TT_SHEET_FAILED_RETRY).performClick()

        assertThat(retries).isEqualTo(1)
        assertThat(dismissals).isEqualTo(0)
    }

    /** Close leaves the row exactly as it was — its cause line and its Retry stay. */
    @Test
    fun `19d's text action says Close and retries nothing`() {
        showInterrupted()

        compose.onNodeWithTag(TT_SHEET_FAILED_CLOSE).assertTextEquals("Close")
        compose.onNodeWithTag(TT_SHEET_FAILED_CLOSE).performClick()

        assertThat(dismissals).isEqualTo(1)
        assertThat(retries).isEqualTo(0)
    }

    // ---- 19b ------------------------------------------------------------------------------

    /**
     * The FREE figure the probe measured, in the body — never the frame's `12 MB`
     * as copy, and never the volume's figure by a slip of the argument order.
     *
     * The expected label is built with the platform formatter rather than typed
     * out, and that is deliberate rather than lazy: `Formatter.formatShortFileSize`
     * is locale- and API-dependent (it switched to SI units in Android O, so
     * 12 MiB is not the string "12 MB"), and a test that pinned one machine's
     * rendering would fail on another for a reason that has nothing to do with
     * this sheet. What it DOES pin is the sentence template and which of the two
     * byte figures fills it — the assertion below on the volume's label is what
     * makes the second half non-vacuous.
     */
    @Test
    fun `19b states the free space it measured, not the size of the disk`() {
        val free = 12L * 1024 * 1024
        val volume = 64L * 1024 * 1024 * 1024
        showNoSpace(freeBytes = free, volumeBytes = volume)

        val freeLabel =
            android.text.format.Formatter
                .formatShortFileSize(context, free)
        val volumeLabel =
            android.text.format.Formatter
                .formatShortFileSize(context, volume)
        assertThat(freeLabel).isNotEqualTo(volumeLabel)

        compose
            .onNodeWithText(context.getString(R.string.lang_sheet_space_body, freeLabel))
            .assertIsDisplayed()
        compose
            .onNodeWithText(context.getString(R.string.lang_sheet_space_body, volumeLabel))
            .assertDoesNotExist()
    }

    /** The bar is drawn, and its two legends name its two segments. */
    @Test
    fun `19b's bar is used against free on one volume`() {
        val free = 12L * 1024 * 1024
        showNoSpace(freeBytes = free)

        val freeLabel =
            android.text.format.Formatter
                .formatShortFileSize(context, free)
        compose.onNodeWithTag(TT_SHEET_SPACE_BAR, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Other apps and system").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.lang_sheet_space_free, freeLabel)).assertIsDisplayed()
    }

    /**
     * **The no-dead-end assertion** (EDGE_CASES §7). 19b's drawn second action
     * opens 20e, which is PR-25 and does not exist, so the ruling has it omitted
     * — which leaves ONE action, and that action has to lead somewhere. The
     * mutation is a "Free up space" button wired to a dismiss.
     */
    @Test
    fun `19b's one action leads to Manage packs`() {
        showNoSpace()

        compose.onNodeWithTag(TT_SHEET_SPACE_MANAGE).assertTextEquals("Manage packs")
        compose.onNodeWithTag(TT_SHEET_SPACE_MANAGE).performClick()

        assertThat(manages).isEqualTo(1)
        assertThat(dismissals).isEqualTo(0)
    }

    /**
     * The action that is NOT there, asserted as absent rather than left to a
     * reader's memory of the ruling. A "Free up space" button wired to nothing
     * is the dead end EDGE_CASES §7 forbids, and PR-25 is where it earns its
     * place back.
     */
    @Test
    fun `19b offers no Free up space until 20e exists`() {
        showNoSpace()

        compose.onNodeWithText("Free up space").assertDoesNotExist()
    }
}
