package com.codeboxlk.tranzlate.core.translate

import com.codeboxlk.tranzlate.core.model.OfflineModelFailure
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OfflineModelStatesTest {
    @Test
    fun `truth is the downloaded set and transients override it`() {
        val states =
            mergeModelStates(
                capable = setOf("en", "fr", "de", "si"),
                downloaded = setOf("en", "de"),
                transient =
                    mapOf(
                        "de" to OfflineModelState.Deleting,
                        "si" to OfflineModelState.Failed(OfflineModelFailure.NETWORK),
                    ),
            )

        assertThat(states["en"]).isEqualTo(OfflineModelState.Downloaded)
        assertThat(states["fr"]).isEqualTo(OfflineModelState.NotDownloaded)
        assertThat(states["de"]).isEqualTo(OfflineModelState.Deleting) // transient wins
        assertThat(states["si"]).isEqualTo(OfflineModelState.Failed(OfflineModelFailure.NETWORK))
        assertThat(states).doesNotContainKey("xx") // never invents languages
    }
}
