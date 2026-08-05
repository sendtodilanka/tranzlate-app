package com.codeboxlk.tranzlate.feature.language

import com.codeboxlk.tranzlate.core.model.OfflineModelFailure
import com.codeboxlk.tranzlate.domain.translate.PackEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The PackEvent → 20a snackbar mapping (#130 PR-22), pinned end to end: which event
 * becomes which snackbar KIND, and which kind carries which message + action string.
 *
 * Pure JVM (no renderer): the mapping is a `when`, the resources are ints. The
 * mutations decided first (rule 11) are swaps — a "removed" event routed to the
 * "started" snackbar, or the "failed" message paired with the "ready" action — and a
 * swap leaves every string present and every key valid, so only an assertion that ties
 * the event to its OWN kind and the kind to its OWN pair of strings can see it.
 */
class PackSnackbarMappingTest {
    @Test
    fun `DownloadStarted becomes the STARTED snackbar, tag carried`() {
        assertThat(PackEvent.DownloadStarted("en").toPackSnackbar())
            .isEqualTo(PackSnackbar(PackSnackbarKind.STARTED, "en"))
    }

    @Test
    fun `DownloadSucceeded becomes the READY snackbar`() {
        assertThat(PackEvent.DownloadSucceeded("fr").toPackSnackbar())
            .isEqualTo(PackSnackbar(PackSnackbarKind.READY, "fr"))
    }

    @Test
    fun `Deleted becomes the REMOVED snackbar`() {
        assertThat(PackEvent.Deleted("de").toPackSnackbar())
            .isEqualTo(PackSnackbar(PackSnackbarKind.REMOVED, "de"))
    }

    @Test
    fun `DownloadFailed becomes the FAILED snackbar, the cause is not part of the snackbar`() {
        assertThat(PackEvent.DownloadFailed("es", OfflineModelFailure.NETWORK).toPackSnackbar())
            .isEqualTo(PackSnackbar(PackSnackbarKind.FAILED, "es"))
    }

    /**
     * Each kind owns its own message resource — the mutation that swaps two arms of
     * `packSnackbarMessageRes` (e.g. READY → the failed string) reddens exactly here,
     * while the four rows below hold every kind's action label to the same standard.
     */
    @Test
    fun `each kind maps to its own message resource`() {
        assertThat(packSnackbarMessageRes(PackSnackbarKind.STARTED)).isEqualTo(R.string.lang_snackbar_downloading)
        assertThat(packSnackbarMessageRes(PackSnackbarKind.READY)).isEqualTo(R.string.lang_snackbar_ready)
        assertThat(packSnackbarMessageRes(PackSnackbarKind.REMOVED)).isEqualTo(R.string.lang_snackbar_removed)
        assertThat(packSnackbarMessageRes(PackSnackbarKind.FAILED)).isEqualTo(R.string.lang_snackbar_failed)
    }

    @Test
    fun `each kind maps to its own action resource`() {
        assertThat(packSnackbarActionRes(PackSnackbarKind.STARTED)).isEqualTo(R.string.lang_snackbar_action_view)
        assertThat(packSnackbarActionRes(PackSnackbarKind.READY)).isEqualTo(R.string.lang_snackbar_action_use)
        assertThat(packSnackbarActionRes(PackSnackbarKind.REMOVED))
            .isEqualTo(R.string.lang_snackbar_action_download_again)
        assertThat(packSnackbarActionRes(PackSnackbarKind.FAILED)).isEqualTo(R.string.lang_snackbar_action_retry)
    }
}
