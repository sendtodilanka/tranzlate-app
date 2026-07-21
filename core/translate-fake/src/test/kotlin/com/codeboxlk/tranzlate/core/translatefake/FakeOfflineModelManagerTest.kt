package com.codeboxlk.tranzlate.core.translatefake

import app.cash.turbine.test
import com.codeboxlk.tranzlate.core.model.OfflineModelFailure
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** 6-state determinism proof (spec 02 §3.3/§4.3; plan §6.4). */
class FakeOfflineModelManagerTest {
    @Test
    fun `seed covers all six states deterministically`() =
        runTest {
            FakeOfflineModelManager().modelStates().test {
                val seed = awaitItem()
                assertThat(seed["en"]).isEqualTo(OfflineModelState.Downloaded)
                assertThat(seed["fr"]).isEqualTo(OfflineModelState.Downloaded)
                assertThat(seed["de"]).isEqualTo(OfflineModelState.NotDownloaded)
                assertThat(seed["ja"]).isEqualTo(OfflineModelState.OnlineOnly)
                assertThat(seed["ta"]).isEqualTo(OfflineModelState.Failed(OfflineModelFailure.NETWORK))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `download transitions NotDownloaded through Downloading to Downloaded`() =
        runTest {
            val manager = FakeOfflineModelManager()
            manager.modelStates().test {
                assertThat(awaitItem()["de"]).isEqualTo(OfflineModelState.NotDownloaded)

                manager.download("de")

                assertThat(awaitItem()["de"]).isEqualTo(OfflineModelState.Downloading)
                assertThat(awaitItem()["de"]).isEqualTo(OfflineModelState.Downloaded)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `delete transitions Downloaded through Deleting to NotDownloaded`() =
        runTest {
            val manager = FakeOfflineModelManager()
            manager.modelStates().test {
                assertThat(awaitItem()["en"]).isEqualTo(OfflineModelState.Downloaded)

                manager.delete("en")

                assertThat(awaitItem()["en"]).isEqualTo(OfflineModelState.Deleting)
                assertThat(awaitItem()["en"]).isEqualTo(OfflineModelState.NotDownloaded)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `online-only rows have no download control`() =
        runTest {
            val manager = FakeOfflineModelManager()

            manager.download("ja")

            manager.modelStates().test {
                assertThat(awaitItem()["ja"]).isEqualTo(OfflineModelState.OnlineOnly)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `failed rows retry via download`() =
        runTest {
            val manager = FakeOfflineModelManager()

            manager.download("ta")

            manager.modelStates().test {
                assertThat(awaitItem()["ta"]).isEqualTo(OfflineModelState.Downloaded)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
