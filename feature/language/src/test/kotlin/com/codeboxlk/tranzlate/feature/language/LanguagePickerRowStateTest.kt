package com.codeboxlk.tranzlate.feature.language

import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.model.OfflineModelFailure
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

/**
 * Issue #117 plan §3 — the six-state row matrix, one test per state, plus the
 * precedence rules that decide which state a row is when two facts are true at
 * once.
 */
class LanguagePickerRowStateTest {
    private fun language(
        id: String = "es",
        capable: Boolean = true,
        downloaded: Boolean = false,
    ) = Language(id, id.uppercase(Locale.ROOT), offlineAvailable = capable, offlineDownloaded = downloaded)

    // ---- the six states -----------------------------------------------------

    @Test
    fun `selected and on device`() {
        val state = rowStateOf(language(downloaded = true), null, selected = true)
        assertThat(state).isEqualTo(LanguageRowState.Selected(LanguageRowState.Downloaded()))
        assertThat((state as LanguageRowState.Selected).onDevice).isTrue()
    }

    @Test
    fun `selected and not on device carries no on-device line`() {
        val state = rowStateOf(language(downloaded = false), null, selected = true)
        assertThat(state).isEqualTo(LanguageRowState.Selected(LanguageRowState.Downloadable))
        assertThat((state as LanguageRowState.Selected).onDevice).isFalse()
    }

    @Test
    fun `downloaded`() {
        val state = rowStateOf(language(downloaded = true), null, selected = false)
        assertThat(state).isEqualTo(LanguageRowState.Downloaded())
    }

    @Test
    fun `downloading`() {
        val state = rowStateOf(language(), OfflineModelState.Downloading, selected = false)
        assertThat(state).isEqualTo(LanguageRowState.Downloading)
    }

    @Test
    fun `downloadable`() {
        val state = rowStateOf(language(capable = true, downloaded = false), null, selected = false)
        assertThat(state).isEqualTo(LanguageRowState.Downloadable)
    }

    @Test
    fun `online only`() {
        val state = rowStateOf(language(capable = false), null, selected = false)
        assertThat(state).isEqualTo(LanguageRowState.OnlineOnly)
    }

    @Test
    fun `failed carries its cause, so the retry is not blind`() {
        val state = rowStateOf(language(), OfflineModelState.Failed(OfflineModelFailure.STORAGE), selected = false)
        assertThat(state).isEqualTo(LanguageRowState.Failed(OfflineModelFailure.STORAGE))
    }

    // ---- R3: no invented sizes ---------------------------------------------

    /**
     * Plan R3, stated as strongly as a type can state it: [LanguageRowState.Downloadable]
     * has no size field, so a not-downloaded row cannot carry a number even if a
     * caller offers one.
     */
    @Test
    fun `a not-downloaded row exposes no size at all`() {
        val state = rowStateOf(language(), null, selected = false, sizeBytes = 45_700_000L)
        assertThat(state).isEqualTo(LanguageRowState.Downloadable)
        assertThat(state).isNotInstanceOf(LanguageRowState.Downloaded::class.java)
    }

    @Test
    fun `an online-only row exposes no size either`() {
        val state = rowStateOf(language(capable = false), null, selected = false, sizeBytes = 1L)
        assertThat(state).isEqualTo(LanguageRowState.OnlineOnly)
    }

    @Test
    fun `a downloaded row carries the measured size when one was measured`() {
        val state = rowStateOf(language(downloaded = true), null, selected = false, sizeBytes = 45_700_000L)
        assertThat(state).isEqualTo(LanguageRowState.Downloaded(sizeBytes = 45_700_000L))
    }

    /** Nothing measures it in production today — the row must survive that (R3). */
    @Test
    fun `a downloaded row with nothing measured carries a null size, not a guess`() {
        val state = rowStateOf(language(downloaded = true), null, selected = false, sizeBytes = null)
        assertThat((state as LanguageRowState.Downloaded).sizeBytes).isNull()
    }

    // ---- precedence ---------------------------------------------------------

    @Test
    fun `selected wins over every resting state`() {
        listOf(true to true, true to false, false to false).forEach { (capable, downloaded) ->
            val state = rowStateOf(language(capable = capable, downloaded = downloaded), null, selected = true)
            assertThat(state).isInstanceOf(LanguageRowState.Selected::class.java)
        }
    }

    @Test
    fun `selected wins over a transient download too`() {
        val state = rowStateOf(language(), OfflineModelState.Downloading, selected = true)
        assertThat(state).isInstanceOf(LanguageRowState.Selected::class.java)
    }

    @Test
    fun `a failure is never masked by the resting state`() {
        val failed = OfflineModelState.Failed(OfflineModelFailure.NETWORK)
        assertThat(rowStateOf(language(downloaded = true), failed, selected = false))
            .isEqualTo(LanguageRowState.Failed(OfflineModelFailure.NETWORK))
    }

    // ---- the states the design has no row for -------------------------------

    /**
     * `Deleting` has no row in the design. It renders as Downloadable because
     * that is the TRUE statement about it — the model is on its way off the
     * device. Calling it "Downloading" would be a false one.
     */
    @Test
    fun `deleting reads as downloadable, never as downloading`() {
        val state = rowStateOf(language(downloaded = false), OfflineModelState.Deleting, selected = false)
        assertThat(state).isEqualTo(LanguageRowState.Downloadable)
    }

    @Test
    fun `an explicit NotDownloaded reads as downloadable`() {
        val state = rowStateOf(language(), OfflineModelState.NotDownloaded, selected = false)
        assertThat(state).isEqualTo(LanguageRowState.Downloadable)
    }

    /**
     * The first frame: the model-state map has not arrived. Capability is a
     * COMPILE-TIME fact, so a capable row is Downloadable and only a genuinely
     * incapable one is Online only — the map's silence never labels 194 rows.
     */
    @Test
    fun `an empty state map does not turn capable rows into online-only ones`() {
        val rows = buildPickerRows(mixedCatalog, emptyMap(), selectedId = "", locale = Locale.ENGLISH)
        val byId = rows.associate { it.id to it.state }
        assertThat(byId["es"]).isEqualTo(LanguageRowState.Downloadable)
        assertThat(byId["ja"]).isEqualTo(LanguageRowState.OnlineOnly)
    }

    @Test
    fun `an explicit OnlineOnly model state agrees with the catalog`() {
        val state = rowStateOf(language(capable = false), OfflineModelState.OnlineOnly, selected = false)
        assertThat(state).isEqualTo(LanguageRowState.OnlineOnly)
    }

    // ---- counter ------------------------------------------------------------

    /**
     * Plan §4: the denominator is the OFFLINE-CAPABLE count. "12 of 194 on
     * device" would tell the user the other 182 are downloadable, and they
     * are not.
     */
    @Test
    fun `the counter denominator is the offline-capable count, not the catalog size`() {
        val counts = onDeviceCount(mixedCatalog)
        assertThat(mixedCatalog).hasSize(4)
        assertThat(counts.capable).isEqualTo(2)
        assertThat(counts.downloaded).isEqualTo(1)
    }

    @Test
    fun `a downloaded flag on a non-capable row cannot inflate the counter`() {
        val impossible =
            listOf(Language("xx", "X", offlineAvailable = false, offlineDownloaded = true))
        assertThat(onDeviceCount(impossible).downloaded).isEqualTo(0)
    }

    @Test
    fun `an empty catalog counts zero of zero`() {
        assertThat(onDeviceCount(emptyList())).isEqualTo(OnDeviceCount(downloaded = 0, capable = 0))
    }

    // ---- rail index ---------------------------------------------------------

    @Test
    fun `the rail indexes the first row per letter, offset by the headers above it`() {
        val rows = buildPickerRows(mixedCatalog, emptyMap(), selectedId = "", locale = Locale.ENGLISH)
        val index = rows.letterIndex(offset = 2)
        assertThat(rows.map { it.displayName })
            .containsExactly("English", "Japanese", "Sinhala", "Spanish")
            .inOrder()
        assertThat(index['E']).isEqualTo(2)
        assertThat(index['J']).isEqualTo(3)
        assertThat(index['S']).isEqualTo(4) // Sinhala, the FIRST S — not Spanish
    }

    private val mixedCatalog =
        listOf(
            Language("en", "English", offlineAvailable = true, offlineDownloaded = true),
            Language("es", "Spanish", offlineAvailable = true, offlineDownloaded = false),
            Language("ja", "Japanese", offlineAvailable = false, offlineDownloaded = false),
            Language("si", "Sinhala", offlineAvailable = false, offlineDownloaded = false),
        )
}
