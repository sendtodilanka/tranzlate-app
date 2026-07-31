package com.codeboxlk.tranzlate.feature.text

import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

/**
 * The picker's pure model: search folding, ordering and the Recent shortcut.
 * Harvested from the parked `feat/issue-117-picker-ux` work — the search is
 * design-independent, and it was already right.
 *
 * Every case runs against an EXPLICIT [Locale] so the assertions do not depend
 * on whatever locale the build machine happens to have.
 */
class LanguagePickerSearchTest {
    private val catalog =
        listOf(
            Language("en", "English", offlineAvailable = true, offlineDownloaded = false),
            Language("es", "Spanish", offlineAvailable = true, offlineDownloaded = false),
            Language("fr", "French", offlineAvailable = true, offlineDownloaded = false),
            Language("si", "Sinhala", offlineAvailable = true, offlineDownloaded = false),
            Language("vi", "Vietnamese", offlineAvailable = true, offlineDownloaded = false),
            Language("ja", "Japanese", offlineAvailable = false, offlineDownloaded = false),
        )

    private fun rows(
        states: Map<String, OfflineModelState> = emptyMap(),
        selectedId: String = "",
    ) = buildPickerRows(catalog, states, selectedId, Locale.ENGLISH)

    private fun search(query: String): List<String> = rows().matching(searchNormalize(query)).map { it.id }

    // ---- normalization ------------------------------------------------------

    @Test
    fun `normalization strips diacritics and case`() {
        assertThat(searchNormalize("Español")).isEqualTo("espanol")
        assertThat(searchNormalize("FRANÇAIS")).isEqualTo("francais")
        assertThat(searchNormalize("Tiếng Việt")).isEqualTo("tieng viet")
        assertThat(searchNormalize("  Deutsch  ")).isEqualTo("deutsch")
    }

    /**
     * The failure this guards: `lowercase()` with a Turkish DEFAULT locale maps
     * `I` to the dotless `ı`, so the same query would match on one device and
     * miss on another. ROOT keeps it device-independent.
     */
    @Test
    fun `normalization is locale-independent`() {
        val default = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"))
            assertThat(searchNormalize("INGILIZCE")).isEqualTo("ingilizce")
            assertThat(searchNormalize("İngilizce")).isEqualTo("ingilizce")
        } finally {
            Locale.setDefault(default)
        }
    }

    /** Both sides fold identically, which is what makes non-Latin scripts safe. */
    @Test
    fun `normalization is symmetric for non-latin scripts`() {
        assertThat(searchNormalize("සිංහල")).isEqualTo(searchNormalize("සිංහල"))
        assertThat(searchNormalize("한국어")).isEqualTo(searchNormalize("한국어"))
    }

    // ---- search -------------------------------------------------------------

    @Test
    fun `accent-free query finds the accented endonym`() {
        // "Spanish" in an English UI carries the endonym "español" — a user who
        // cannot type the tilde must still land on it.
        val endonym = languageEndonym("es", "Spanish")
        assertThat(endonym).isNotNull()
        assertThat(searchNormalize(endonym!!)).isEqualTo("espanol")
        assertThat(search("espanol")).contains("es")
    }

    @Test
    fun `search is case-insensitive`() {
        assertThat(search("SPANISH")).contains("es")
        assertThat(search("spanish")).contains("es")
        assertThat(search("ESPANOL")).contains("es")
    }

    @Test
    fun `search matches the accented form typed WITH its accents`() {
        assertThat(search("Español")).contains("es")
        assertThat(search("français")).contains("fr")
        assertThat(search("francais")).contains("fr")
    }

    @Test
    fun `search matches the BCP-47 code`() {
        assertThat(search("fr")).contains("fr")
        assertThat(search("ja")).contains("ja")
    }

    @Test
    fun `search matches an endonym-only query`() {
        val sinhala = languageEndonym("si", "Sinhala")
        // The display name is "Sinhala"; nothing in it contains the native name.
        assertThat(sinhala).isNotNull()
        assertThat(search(sinhala!!)).contains("si")
    }

    @Test
    fun `search matches the display name`() {
        assertThat(search("viet")).contains("vi")
    }

    @Test
    fun `an unmatched query returns nothing rather than everything`() {
        assertThat(search("klingon")).isEmpty()
    }

    @Test
    fun `an empty query returns the whole catalog`() {
        assertThat(search("")).hasSize(catalog.size)
        assertThat(search("   ")).hasSize(catalog.size)
    }

    /** The "Detect language" pseudo-row is searchable like any other row. */
    @Test
    fun `detect language is searchable`() {
        val detect = detectRow("Detect language", selected = false)
        assertThat(detect.searchKey).contains(searchNormalize("detect"))
        assertThat(detect.searchKey).contains(DETECT_LANGUAGE_ID)
    }

    // ---- ordering + recents -------------------------------------------------

    @Test
    fun `rows are sorted by localized display name`() {
        assertThat(rows().map { it.displayName })
            .containsExactly("English", "French", "Japanese", "Sinhala", "Spanish", "Vietnamese")
            .inOrder()
    }

    /**
     * UTF-16 order files "Æ" (U+00C6) AFTER "Z" (U+005A); an English collator
     * files it next to "A". The catalog names below ride on private-use tags
     * (qaa–qtz) that the platform deliberately cannot localize, which also
     * exercises the catalog-name fallback.
     */
    @Test
    fun `sorting is collated, not code-unit ordered`() {
        val accented =
            listOf(
                Language("qab", "Zulu", offlineAvailable = false, offlineDownloaded = false),
                Language("qac", "Eesti", offlineAvailable = false, offlineDownloaded = false),
                Language("qaa", "Ærøsk", offlineAvailable = false, offlineDownloaded = false),
            )
        val ordered = buildPickerRows(accented, emptyMap(), "", Locale.ENGLISH).map { it.displayName }
        assertThat(ordered).containsExactly("Ærøsk", "Eesti", "Zulu")
        assertThat(ordered.indexOf("Ærøsk")).isLessThan(ordered.indexOf("Zulu"))
    }

    @Test
    fun `an unlocalizable tag falls back to the catalog name, never the raw tag`() {
        val exotic = listOf(Language("qaa", "Nheengatu", offlineAvailable = false, offlineDownloaded = false))
        assertThat(buildPickerRows(exotic, emptyMap(), "", Locale.ENGLISH).single().displayName)
            .isEqualTo("Nheengatu")
    }

    /**
     * The design puts the current choice at the top of Recent with its tick, so
     * unlike the parked version this list does NOT exclude it.
     */
    @Test
    fun `recents are most-recent-first, capped, and include the current choice`() {
        val used =
            listOf(
                Language("en", "English", offlineAvailable = true, offlineDownloaded = false, lastUsedAt = 30L),
                Language("es", "Spanish", offlineAvailable = true, offlineDownloaded = false, lastUsedAt = 10L),
                Language("fr", "French", offlineAvailable = true, offlineDownloaded = false, lastUsedAt = 20L),
                Language("ja", "Japanese", offlineAvailable = false, offlineDownloaded = false),
            )
        val built = buildPickerRows(used, emptyMap(), selectedId = "en", locale = Locale.ENGLISH)
        assertThat(built.recentRows().map { it.id }).containsExactly("en", "fr", "es").inOrder()
        assertThat(built.recentRows(limit = 2).map { it.id }).containsExactly("en", "fr").inOrder()
    }

    @Test
    fun `a never-used catalog has no recents`() {
        assertThat(rows().recentRows()).isEmpty()
    }

    @Test
    fun `the selected row is the one marked selected, and only that one`() {
        val built = rows(selectedId = "fr")
        assertThat(built.filter { it.state is LanguageRowState.Selected }.map { it.id })
            .containsExactly("fr")
    }
}
