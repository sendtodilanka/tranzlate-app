package com.codeboxlk.tranzlate.feature.language

import com.codeboxlk.tranzlate.core.model.Language
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

/**
 * 16a's "Recently used as target" section — what it lists, in what order, and
 * the one thing it must never be served.
 *
 * Ordering fixtures here are deliberately NOT already in the right order when
 * they reach the sort. `buildPickerRows` hands its rows over alphabetically, so
 * a fixture whose stamps ascend alphabetically would pass under `sortedBy` just
 * as happily as under `sortedByDescending`, and the test would prove nothing.
 * That fixture rule was written down with the mutation list, before this file
 * existed (CLAUDE.md rule 11).
 */
class TargetRecentsTest {
    /** Alphabetical: Afrikaans, English, Spanish. Chosen order: Spanish, English, Afrikaans. */
    private val catalog =
        listOf(
            Language("af", "Afrikaans", offlineAvailable = true, offlineDownloaded = true),
            Language("en", "English", offlineAvailable = true, offlineDownloaded = true),
            Language("es", "Spanish", offlineAvailable = true, offlineDownloaded = true),
            Language("ja", "Japanese", offlineAvailable = false, offlineDownloaded = false),
        )

    private fun rows(
        recents: Map<String, Long>,
        selectedId: String = "es",
    ) = buildPickerRows(
        languages = catalog,
        modelStates = emptyMap(),
        selectedId = selectedId,
        locale = Locale.ENGLISH,
        recents = recents,
    )

    /**
     * Newest first. The stamps run the OPPOSITE way to the alphabet — af=100,
     * en=200, es=300 — so an ascending sort would return the alphabetical order
     * the rows already arrived in, and only a descending one can produce this.
     */
    @Test
    fun `targetRecentsAreNewestFirst`() {
        val recent = rows(mapOf("af" to 100L, "en" to 200L, "es" to 300L)).recentRows()

        assertThat(recent.map { it.id }).containsExactly("es", "en", "af").inOrder()
    }

    /**
     * The mutation this exists for is a one-word fallback:
     * `recents[id] ?: language.lastUsedAt`. It looks defensive and it is a lie —
     * `Language.lastUsedAt` carries the MERGED source∪target overlay the
     * repository applies for the shipped 15a picker, so the fallback would file
     * a language the user only ever picked as a SOURCE under a header that says
     * "Recently used as target".
     */
    @Test
    fun `targetRecentsIgnoreTheMergedStamp`() {
        val merged =
            catalog.map { language ->
                // Every row arrives carrying a merged stamp, as it does in production.
                language.copy(lastUsedAt = 999L)
            }
        val built =
            buildPickerRows(
                languages = merged,
                modelStates = emptyMap(),
                selectedId = "es",
                locale = Locale.ENGLISH,
                // …but only Spanish was ever picked as a target.
                recents = mapOf("es" to 300L),
            )

        assertThat(built.recentRows().map { it.id }).containsExactly("es")
        assertThat(built.single { it.id == "en" }.lastUsedAt).isNull()
    }

    /** Empty map → no rows, which is what makes the whole section absent upstream. */
    @Test
    fun `no target picks yet means no recent rows at all`() {
        assertThat(rows(emptyMap()).recentRows()).isEmpty()
    }

    /** The current choice sits at the top of its own section, tick and all (16a). */
    @Test
    fun `the chosen target appears in its own recents`() {
        val recent = rows(mapOf("af" to 100L, "es" to 300L)).recentRows()

        assertThat(recent.first().id).isEqualTo("es")
        assertThat(recent.first().state).isInstanceOf(LanguageRowState.Selected::class.java)
    }

    /** A stamp for an id the catalog does not have cannot conjure a row. */
    @Test
    fun `a stamp for an unknown id is ignored`() {
        val recent = rows(mapOf("es" to 300L, "qaa" to 400L)).recentRows()

        assertThat(recent.map { it.id }).containsExactly("es")
    }

    @Test
    fun `the section is capped, newest kept`() {
        val many = (1..RECENT_LIMIT + 2).associate { "l$it" to it.toLong() }
        val padded =
            (1..RECENT_LIMIT + 2).map {
                Language("l$it", "Lang $it", offlineAvailable = true, offlineDownloaded = false)
            }
        val built =
            buildPickerRows(padded, emptyMap(), selectedId = "", locale = Locale.ENGLISH, recents = many)

        assertThat(built.recentRows()).hasSize(RECENT_LIMIT)
        assertThat(built.recentRows().first().id).isEqualTo("l${RECENT_LIMIT + 2}")
    }
}
