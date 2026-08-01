package com.codeboxlk.tranzlate.feature.language

import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.model.LanguageRole
import com.codeboxlk.tranzlate.core.model.OfflineModelFailure
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

/**
 * 16a "Translate to" — the one property a target row carries that a source row
 * does not, and the `Selected(inner)` wrapper that lets it sit on the chosen
 * row alongside everything else (issue #130 rev.3 PR-12, ruling 1 + #123.1).
 *
 * The mutations these are answers to were written down BEFORE the tests
 * (CLAUDE.md rule 11): coupling the mark to the pack, dropping the role gate,
 * and letting `Selected` swallow the state it wraps.
 *
 * There is deliberately no "no offline voice" case here beyond the absence of a
 * mark. Rev 5 cut sheet 19j because a mark is only ever DRAWN where the voice
 * exists, so there is no unvoiced affordance left to explain (ruling §7.6 as
 * amended by issue #180).
 */
class TargetPickerRowTest {
    private fun language(
        id: String = "es",
        capable: Boolean = true,
        downloaded: Boolean = false,
        voice: Boolean = false,
    ) = Language(
        id = id,
        name = id.uppercase(Locale.ROOT),
        offlineAvailable = capable,
        offlineDownloaded = downloaded,
        hasOfflineVoice = voice,
    )

    private fun rowOf(
        language: Language,
        modelState: OfflineModelState? = null,
        selectedId: String = "",
    ) = buildPickerRows(
        languages = listOf(language),
        modelStates = modelState?.let { mapOf(language.id to it) } ?: emptyMap(),
        selectedId = selectedId,
        locale = Locale.ENGLISH,
    ).single()

    // ---- the voice flag is device truth, never crossed with the pack --------

    /**
     * The whole point, and the mutation most likely to pass unnoticed: a pack
     * and a voice are separate installs from separate sources. The export draws
     * this combination on 17a's landscape "to" frame — Arabic carries the
     * speaker while its pack is still downloading.
     *
     * A matrix whose rows only ever paired a voice with a downloaded pack would
     * stay green if someone wrote `hasOfflineVoice && offlineDownloaded`, which
     * is exactly why this case is its own test.
     */
    @Test
    fun `voiceMarkSurvivesWithNoPackOnDevice`() {
        val states =
            listOf(
                null to LanguageRowState.Downloadable,
                OfflineModelState.Downloading to LanguageRowState.Downloading,
                OfflineModelState.Failed(OfflineModelFailure.NETWORK) to
                    LanguageRowState.Failed(OfflineModelFailure.NETWORK),
            )
        states.forEach { (modelState, expected) ->
            val row = rowOf(language(downloaded = false, voice = true), modelState)

            assertThat(row.state).isEqualTo(expected)
            assertThat(row.hasOfflineVoice).isTrue()
            assertThat(row.showsVoiceMark(LanguageRole.TARGET)).isTrue()
        }
    }

    /** And an online-only language can be speakable too — MLKit's pack list is not the voice list. */
    @Test
    fun `an online-only language can still carry a voice`() {
        val row = rowOf(language(capable = false, voice = true))

        assertThat(row.state).isEqualTo(LanguageRowState.OnlineOnly)
        assertThat(row.showsVoiceMark(LanguageRole.TARGET)).isTrue()
    }

    /** The export's Afrikaans row: pack on device, no voice, therefore no mark. */
    @Test
    fun `a pack on device does not imply a voice`() {
        val row = rowOf(language(downloaded = true, voice = false))

        assertThat(row.state).isEqualTo(LanguageRowState.Downloaded())
        assertThat(row.hasOfflineVoice).isFalse()
        assertThat(row.showsVoiceMark(LanguageRole.TARGET)).isFalse()
    }

    /** The full matrix: voice × pack-state × selected, and the flag survives all of it. */
    @Test
    fun `the voice flag is independent of every row state and of selection`() {
        val packStates =
            listOf<Pair<Boolean, OfflineModelState?>>(
                false to null,
                true to null,
                false to OfflineModelState.Downloading,
                false to OfflineModelState.Failed(OfflineModelFailure.STORAGE),
                true to OfflineModelState.Failed(OfflineModelFailure.STORAGE),
            )
        listOf(true, false).forEach { voice ->
            listOf("es", "").forEach { selectedId ->
                packStates.forEach { (downloaded, modelState) ->
                    val row = rowOf(language(downloaded = downloaded, voice = voice), modelState, selectedId)

                    assertThat(row.hasOfflineVoice).isEqualTo(voice)
                    assertThat(row.showsVoiceMark(LanguageRole.TARGET)).isEqualTo(voice)
                }
            }
        }
    }

    // ---- the mark is a TARGET property --------------------------------------

    /**
     * Spec §2 lists it as one of 16a's "three deliberate differences", and the
     * drawings agree: the `from · landscape` frame carries no speaker anywhere,
     * `to · landscape` carries three.
     */
    @Test
    fun `sourceRowNeverShowsVoiceMark`() {
        val row = rowOf(language(downloaded = true, voice = true))

        assertThat(row.hasOfflineVoice).isTrue()
        assertThat(row.showsVoiceMark(LanguageRole.SOURCE)).isFalse()
        assertThat(row.showsVoiceMark(LanguageRole.TARGET)).isTrue()
    }

    /** A device with no installed voices marks nothing, on either side. */
    @Test
    fun `no installed voice means no mark anywhere`() {
        val row = rowOf(language(downloaded = true, voice = false))

        LanguageRole.entries.forEach { role ->
            assertThat(row.showsVoiceMark(role)).isFalse()
        }
    }

    // ---- Selected(inner) ----------------------------------------------------

    /**
     * Ruling 1's graft. Selection used to REPLACE the row state, so a chosen
     * language that was mid-download rendered identically to a chosen language
     * that was online only. 16a settles it by drawing all three facts at once on
     * the Spanish row: "On device", the speaker, and the tick.
     */
    @Test
    fun `selectedRowKeepsItsRestingStateInside`() {
        val cases =
            listOf<Triple<Language, OfflineModelState?, LanguageRowState>>(
                Triple(language(downloaded = true), null, LanguageRowState.Downloaded()),
                Triple(language(downloaded = false), null, LanguageRowState.Downloadable),
                Triple(language(capable = false), null, LanguageRowState.OnlineOnly),
                Triple(language(), OfflineModelState.Downloading, LanguageRowState.Downloading),
                Triple(
                    language(),
                    OfflineModelState.Failed(OfflineModelFailure.STORAGE),
                    LanguageRowState.Failed(OfflineModelFailure.STORAGE),
                ),
            )
        cases.forEach { (language, modelState, resting) ->
            val selected = rowOf(language, modelState, selectedId = language.id).state

            assertThat(selected).isEqualTo(LanguageRowState.Selected(resting))
            assertThat((selected as LanguageRowState.Selected).inner).isEqualTo(resting)
        }
    }

    /** The measured size reaches the row THROUGH the wrapper, not around it. */
    @Test
    fun `a selected downloaded row still carries its measured size`() {
        val state =
            rowStateOf(
                language(downloaded = true),
                modelState = null,
                selected = true,
                sizeBytes = 45_700_000L,
            )

        assertThat(state).isEqualTo(LanguageRowState.Selected(LanguageRowState.Downloaded(45_700_000L)))
        assertThat((state as LanguageRowState.Selected).onDevice).isTrue()
    }

    /**
     * The one shape the wrapper must not take. A doubly-wrapped value would
     * render as an ordinary selected row while hiding a whole state, so it
     * fails loudly instead of silently.
     */
    @Test
    fun `selectedCannotWrapSelected`() {
        val thrown =
            runCatching {
                LanguageRowState.Selected(LanguageRowState.Selected(LanguageRowState.Downloaded()))
            }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(IllegalArgumentException::class.java)
    }

    /** `rowStateOf` is the only production constructor, and it can never produce that shape. */
    @Test
    fun `rowStateOf never nests a selection`() {
        val state = rowOf(language(downloaded = true), selectedId = "es").state as LanguageRowState.Selected

        assertThat(state.inner).isNotInstanceOf(LanguageRowState.Selected::class.java)
    }
}
