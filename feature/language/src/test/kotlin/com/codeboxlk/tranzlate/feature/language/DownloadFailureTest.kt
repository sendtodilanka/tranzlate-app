package com.codeboxlk.tranzlate.feature.language

import com.codeboxlk.tranzlate.core.model.OfflineModelFailure
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The ONE failure-cause map, as a table (#175, #130 PR-18).
 *
 * The mutations this file was written against — chosen before it, per rule 11 —
 * are the two that a reader would call cosmetic and a user would meet as a lie:
 *
 * 1. **Un-folding `WIFI_REQUIRED` from `NETWORK`.** The fold is the reason the
 *    two old maps were the same map; without a test on it, a later edit gives
 *    `WIFI_REQUIRED` its own sentence, that sentence promises a Wi-Fi wait the
 *    app cannot perform (E-W1 never ran), and nothing goes red.
 * 2. **Routing `STORAGE` to 19d.** 19d's body says the connection dropped. On a
 *    full disk that is simply false, and it is false in the direction that sends
 *    the user to check their signal instead of their storage.
 *
 * Assertions are on RESOURCE IDS rather than on the English. Both are true
 * statements, but only the id catches mutation 1: a new `WIFI_REQUIRED` key
 * whose value happens to read the same as the network one would slip past a text
 * assertion, and the copy is what would then drift. The English is checked where
 * the user meets it — `PackFailureCopyTest`, which renders both screens.
 */
class DownloadFailureTest {
    @Test
    fun `no connection and a Wi-Fi requirement are the same sentence`() {
        assertThat(downloadFailureCopy(OfflineModelFailure.NETWORK).rowLine)
            .isEqualTo(R.string.lang_pack_error_network)
        assertThat(downloadFailureCopy(OfflineModelFailure.WIFI_REQUIRED).rowLine)
            .isEqualTo(R.string.lang_pack_error_network)
    }

    @Test
    fun `a full disk and an unexplained stop each get their own sentence`() {
        assertThat(downloadFailureCopy(OfflineModelFailure.STORAGE).rowLine)
            .isEqualTo(R.string.lang_pack_error_storage)
        assertThat(downloadFailureCopy(OfflineModelFailure.UNKNOWN).rowLine)
            .isEqualTo(R.string.lang_pack_error_generic)
    }

    /** Every cause answers, and no two causes answer with the same OBJECT by accident. */
    @Test
    fun `every cause the platform can report has a line`() {
        val lines = OfflineModelFailure.entries.map { downloadFailureCopy(it).rowLine }

        assertThat(lines).hasSize(OfflineModelFailure.entries.size)
        assertThat(lines.toSet()).hasSize(3) // four causes, three sentences — the fold
    }

    /**
     * Which sheet a cause opens. The whole of 19b's trigger, and the ruling's
     * named PR-18 test ("STORAGE pre-flight→19b") in its decidable half.
     */
    @Test
    fun `a full disk opens 19b and never 19d`() {
        assertThat(downloadFailureCopy(OfflineModelFailure.STORAGE).sheet)
            .isEqualTo(DownloadFailureSheet.NoSpace)
    }

    @Test
    fun `everything else opens 19d`() {
        val interrupted =
            listOf(OfflineModelFailure.NETWORK, OfflineModelFailure.WIFI_REQUIRED, OfflineModelFailure.UNKNOWN)
                .map { downloadFailureCopy(it).sheet }

        assertThat(interrupted).doesNotContain(DownloadFailureSheet.NoSpace)
        interrupted.forEach { assertThat(it).isInstanceOf(DownloadFailureSheet.Interrupted::class.java) }
    }

    /**
     * 19d's copy is per cause, and this is the assertion that says so.
     *
     * The drawn frame is written for a dropped connection. Reusing its two
     * sentences for a failure ML Kit did not explain would state a reason the app
     * does not have — which is the same class of invention as a percentage on an
     * indeterminate download, and this project has a rule about that.
     */
    @Test
    fun `an unexplained failure does not claim the connection dropped`() {
        val network = downloadFailureCopy(OfflineModelFailure.NETWORK).sheet as DownloadFailureSheet.Interrupted
        val unknown = downloadFailureCopy(OfflineModelFailure.UNKNOWN).sheet as DownloadFailureSheet.Interrupted

        assertThat(unknown.body).isNotEqualTo(network.body)
        assertThat(unknown.cause).isNotEqualTo(network.cause)
    }

    /** A Wi-Fi requirement is a connection problem all the way down — sheet included. */
    @Test
    fun `a Wi-Fi requirement opens the same 19d as a lost connection`() {
        assertThat(downloadFailureCopy(OfflineModelFailure.WIFI_REQUIRED).sheet)
            .isEqualTo(downloadFailureCopy(OfflineModelFailure.NETWORK).sheet)
    }

    /**
     * How much of the volume is spoken for — 19b's bar, and the one number on it.
     *
     * The mutation is the swap (`free` and `total` the wrong way round), which
     * draws a device that is 99% empty at the moment it refuses a download.
     */
    @Test
    fun `the storage bar fills with what is NOT free`() {
        assertThat(deviceUsedFraction(freeBytes = 25L, volumeBytes = 100L)).isEqualTo(0.75f)
        assertThat(deviceUsedFraction(freeBytes = 0L, volumeBytes = 100L)).isEqualTo(1f)
        assertThat(deviceUsedFraction(freeBytes = 100L, volumeBytes = 100L)).isEqualTo(0f)
    }

    /**
     * `StatFs` can answer with more free bytes than the volume has, on a
     * filesystem with reserved blocks — and a fill wider than its track draws as
     * a FULL bar next to the words "12 MB free", which is the sheet
     * contradicting itself.
     */
    @Test
    fun `an impossible free figure clamps instead of overflowing`() {
        assertThat(deviceUsedFraction(freeBytes = 200L, volumeBytes = 100L)).isEqualTo(0f)
    }

    /**
     * An unmeasurable volume draws nothing rather than something. Unknown is not
     * "empty" and it is not "full" — the same honest degrade the library meter
     * makes when ML Kit's model store cannot be found (R8).
     */
    @Test
    fun `an unmeasurable volume claims nothing`() {
        assertThat(deviceUsedFraction(freeBytes = 12L, volumeBytes = 0L)).isEqualTo(0f)
        assertThat(deviceUsedFraction(freeBytes = 12L, volumeBytes = -1L)).isEqualTo(0f)
    }
}
