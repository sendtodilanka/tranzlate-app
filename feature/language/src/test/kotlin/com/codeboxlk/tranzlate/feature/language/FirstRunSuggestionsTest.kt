package com.codeboxlk.tranzlate.feature.language

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The 18a/18b first-run derivation (#130 PR-21), driven by locale TAGS rather
 * than a platform `LocaleList` — which is the whole reason it is a pure function:
 * the composable reads `LocaleList.getAdjustedDefault()` and the derivation is
 * exhausted here without one.
 *
 * The mutations these tests are written against, each decided before the test:
 * dropping the locale ORDER (iterate the rows instead), dropping the PIVOT guard,
 * dropping the `Downloadable`-only STATE filter, dropping the DEDUPE, and dropping
 * the LIMIT. Every one reddens at least one case below.
 */
class FirstRunSuggestionsTest {
    /** A catalog row in a chosen state — direct, so the state under test is exact. */
    private fun row(
        id: String,
        state: LanguageRowState = LanguageRowState.Downloadable,
    ) = LanguagePickerRow(
        id = id,
        displayName = id.replaceFirstChar { it.uppercase() },
        avatar = LanguageAvatar.Code(id.uppercase()),
        state = state,
    )

    @Test
    fun `a locale resolves through the catalog table and is suggested as the device language`() {
        val out = firstRunSuggestions(listOf("fr-FR"), listOf(row("fr")))

        // fr-FR is not a catalog id; it resolves to `fr` by RFC-4647 truncation.
        assertThat(out.map { it.id }).containsExactly("fr")
        assertThat(out.single().reason).isEqualTo(SuggestionReason.DEVICE_LANGUAGE)
    }

    /**
     * The order is the LOCALE order, and the reason is the locale's RANK. The rows
     * are passed scrambled precisely so a derivation that walked them instead of
     * the locales would come out `de, es, fr` and redden here.
     */
    @Test
    fun `suggestions follow the locale order, and only the first is the device language`() {
        val out =
            firstRunSuggestions(
                preferredLocaleTags = listOf("fr-FR", "es-ES", "de-DE"),
                rows = listOf(row("de"), row("es"), row("fr")),
            )

        assertThat(out.map { it.id }).containsExactly("fr", "es", "de").inOrder()
        assertThat(out.map { it.reason })
            .containsExactly(
                SuggestionReason.DEVICE_LANGUAGE,
                SuggestionReason.COMMON_WHERE_YOU_ARE,
                SuggestionReason.COMMON_WHERE_YOU_ARE,
            ).inOrder()
    }

    /**
     * English ships inside every pack and is on-device from first launch, so a
     * "Get English" would offer a download already done. Even as the device
     * language (index 0) it is dropped — and the language behind it keeps the
     * COMMON reason its POSITION earns, not the DEVICE one its rank-after-filtering
     * might suggest.
     */
    @Test
    fun `the English pivot is never suggested, and the next locale keeps its own rank`() {
        val out = firstRunSuggestions(listOf("en-US", "fr"), listOf(row("en"), row("fr")))

        assertThat(out.map { it.id }).containsExactly("fr")
        assertThat(out.single().reason).isEqualTo(SuggestionReason.COMMON_WHERE_YOU_ARE)
    }

    @Test
    fun `a downloaded or online-only locale is not offered a download`() {
        val out =
            firstRunSuggestions(
                preferredLocaleTags = listOf("es", "ja", "fr"),
                rows =
                    listOf(
                        row("es", LanguageRowState.Downloaded()),
                        row("ja", LanguageRowState.OnlineOnly),
                        row("fr"),
                    ),
            )

        assertThat(out.map { it.id }).containsExactly("fr")
    }

    /** A selected language is `Selected(...)`, not plain `Downloadable`, so it is not re-suggested. */
    @Test
    fun `a selected locale is not re-suggested`() {
        val out =
            firstRunSuggestions(
                preferredLocaleTags = listOf("fr", "es"),
                rows = listOf(row("fr", LanguageRowState.Selected(LanguageRowState.Downloadable)), row("es")),
            )

        assertThat(out.map { it.id }).containsExactly("es")
    }

    @Test
    fun `two locales that resolve to one id are suggested once`() {
        val out = firstRunSuggestions(listOf("fr-FR", "fr-CA", "es"), listOf(row("fr"), row("es")))

        assertThat(out.map { it.id }).containsExactly("fr", "es").inOrder()
    }

    /**
     * The catalog carries `fr-FR` as its own online-only id; the offline pack is
     * the base `fr`. A `fr-FR` device — which is exactly what
     * `LocaleList.getAdjustedDefault()` reports — must reach that base pack, so a
     * region tag falls back to the primary subtag when its own row is not
     * downloadable. Without the fallback this device would be offered nothing.
     */
    @Test
    fun `a region locale reaches the base-language pack when its own has none`() {
        val out =
            firstRunSuggestions(
                preferredLocaleTags = listOf("fr-FR"),
                rows = listOf(row("fr-FR", LanguageRowState.OnlineOnly), row("fr")),
            )

        assertThat(out.map { it.id }).containsExactly("fr")
    }

    /** …but a region that DOES have its own downloadable pack keeps it, most-specific-first. */
    @Test
    fun `a region locale keeps its own pack when the catalog has one`() {
        val out =
            firstRunSuggestions(
                preferredLocaleTags = listOf("pt-BR"),
                rows = listOf(row("pt-BR"), row("pt")),
            )

        assertThat(out.map { it.id }).containsExactly("pt-BR")
    }

    /**
     * The honest empty case: a strictly monolingual-English device has no
     * offline-capable non-pivot locale, so the block draws its explainer alone.
     * The ruling's "never empty" is a property of the INPUT locale list, not a
     * promise every device has a second language worth a pack.
     */
    @Test
    fun `an English-only device yields no suggestions`() {
        val out = firstRunSuggestions(listOf("en-US"), listOf(row("en"), row("fr")))

        assertThat(out).isEmpty()
    }

    @Test
    fun `no more than the limit are offered, keeping the most-preferred`() {
        val tags = listOf("fr", "es", "de", "it", "pt")

        val out = firstRunSuggestions(tags, tags.map { row(it) })

        assertThat(out).hasSize(SUGGESTION_LIMIT)
        assertThat(out.map { it.id }).containsExactly("fr", "es", "de").inOrder()
    }

    @Test
    fun `a locale with no catalog match is skipped rather than fatal`() {
        val out = firstRunSuggestions(listOf("zzz", "fr"), listOf(row("fr")))

        assertThat(out.map { it.id }).containsExactly("fr")
        // zzz was the device language (index 0) and resolved to nothing, so fr —
        // the second locale — is correctly "Common where you are", not the device one.
        assertThat(out.single().reason).isEqualTo(SuggestionReason.COMMON_WHERE_YOU_ARE)
    }
}
