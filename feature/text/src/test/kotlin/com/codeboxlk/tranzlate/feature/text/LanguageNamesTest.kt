package com.codeboxlk.tranzlate.feature.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

/**
 * Issue #117 — the 194-row catalog broke the old name helper in two ways, and
 * the helper is BOTH the row label and the sort key. Both failures are pinned
 * here with the exact tags the catalog ships.
 *
 * Measured on the build JDK (21.0.10) before the fix was written:
 * `getDisplayLanguage("fr-CA", ENGLISH)` = "French"  (region dropped)
 * `getDisplayName("fr-CA", ENGLISH)`     = "French (Canada)"
 * `getDisplayName("alz", ENGLISH)`       = "alz"     (the TAG, not blank)
 */
class LanguageNamesTest {
    private val english = Locale.ENGLISH

    /** The exact catalog rows Cloud Translation gives no CLDR name for. */
    private val noCldrNames =
        mapOf(
            "alz" to "Alur",
            "btx" to "Batak Karo",
            "bts" to "Batak Simalungun",
            "dov" to "Dombe",
            "cnh" to "Hakha Chin",
            "hrx" to "Hunsrik",
            "ktu" to "Kituba",
            "yua" to "Yucatec Maya",
        )

    @Test
    fun `a tag with no CLDR name renders the catalog name, never the raw tag`() {
        noCldrNames.forEach { (tag, catalogName) ->
            val display = languageDisplayName(tag, english, fallback = catalogName)
            assertThat(display).isEqualTo(catalogName)
            assertThat(display).isNotEqualTo(tag)
        }
    }

    /**
     * The platform hands an unknown tag BACK rather than returning blank, so a
     * `isBlank()` test would have shipped eight rows reading "alz", "btx"…
     */
    @Test
    fun `the platform really does echo an unknown tag back`() {
        assertThat(Locale.forLanguageTag("alz").getDisplayName(english)).isEqualTo("alz")
        assertThat(Locale.forLanguageTag("alz").getDisplayName(english)).isNotEmpty()
    }

    /**
     * The functional requirement, not a cosmetic one: in every one of these
     * families the BASE tag is offline-capable and the variants are not, so two
     * rows reading the same words leave "which one did I download?" unanswerable.
     */
    @Test
    fun `every region and script family renders distinct labels`() {
        val families =
            listOf(
                listOf("fr" to "French", "fr-CA" to "French (Canadian)", "fr-FR" to "French (French)"),
                listOf(
                    "pt" to "Portuguese",
                    "pt-BR" to "Portuguese (Brazil)",
                    "pt-PT" to "Portuguese (Portugal)",
                ),
                listOf("zh" to "Chinese (Simplified)", "zh-TW" to "Chinese (Traditional)"),
                listOf("ms" to "Malay", "ms-Arab" to "Malay (Jawi)"),
                listOf("pa" to "Punjabi", "pa-Arab" to "Punjabi (Shahmukhi)"),
            )
        families.forEach { family ->
            val labels = family.map { (tag, catalogName) -> languageDisplayName(tag, english, catalogName) }
            assertThat(labels).containsNoDuplicates()
        }
    }

    /** The old helper's exact failure, kept as a regression witness. */
    @Test
    fun `getDisplayLanguage would have collapsed the family - getDisplayName does not`() {
        val collapsed = listOf("fr", "fr-CA", "fr-FR").map { Locale.forLanguageTag(it).getDisplayLanguage(english) }
        assertThat(collapsed.toSet()).hasSize(1) // the bug this test exists to prevent

        val fixed = listOf("fr", "fr-CA", "fr-FR").map { languageDisplayName(it, english, fallback = "?") }
        assertThat(fixed).containsExactly("French", "French (Canada)", "French (France)").inOrder()
    }

    /**
     * Cloud's own label for `fr-FR` is the odd "French (French)". A CLDR name
     * exists for that tag, so the platform's normal English form wins and the
     * odd form never reaches a row.
     */
    @Test
    fun `Cloud's odd label form is corrected wherever CLDR has a real name`() {
        assertThat(languageDisplayName("fr-FR", english, fallback = "French (French)"))
            .isEqualTo("French (France)")
    }

    @Test
    fun `names are localized into the UI language, not frozen in English`() {
        val ptBr = Locale.forLanguageTag("pt-BR")
        assertThat(languageDisplayName("fr", ptBr, fallback = "French")).isNotEqualTo("French")
        assertThat(languageDisplayName("fr", ptBr, fallback = "French")).ignoringCase().contains("franc")
    }

    @Test
    fun `an empty catalog name still never yields a blank control`() {
        assertThat(languageDisplayName("alz", english, fallback = "")).isEqualTo("alz")
    }

    // ---- endonym ------------------------------------------------------------

    @Test
    fun `endonym is dropped when it repeats the display name`() {
        assertThat(languageEndonym("en", "English")).isNull()
    }

    @Test
    fun `endonym is kept when it adds information`() {
        val japanese = languageEndonym("ja", "Japanese")
        assertThat(japanese).isNotNull()
        assertThat(japanese).isNotEqualTo("Japanese")
    }

    /** An unnameable tag has no endonym either — "alz" is not a name in any language. */
    @Test
    fun `a tag with no CLDR name has no endonym`() {
        assertThat(languageEndonym("alz", "Alur")).isNull()
    }

    // ---- avatar code --------------------------------------------------------

    @Test
    fun `the avatar shows the primary subtag, upper-cased`() {
        assertThat(languageAvatarCode("es")).isEqualTo("ES")
        assertThat(languageAvatarCode("fr-CA")).isEqualTo("FR")
        assertThat(languageAvatarCode("haw")).isEqualTo("HAW")
    }

    /** A Turkish device must not turn "si" into "Sİ" — the code is a code. */
    @Test
    fun `the avatar code does not follow the device locale's casing quirks`() {
        val default = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"))
            assertThat(languageAvatarCode("si")).isEqualTo("SI")
        } finally {
            Locale.setDefault(default)
        }
    }

    /**
     * CLDR names these by region or script code, which drops the only word the
     * reader is scanning for. "Chinese (Taiwan)" does not say Traditional.
     *
     * Pinned as EXACT strings on purpose: the family test beside this one only
     * asserts the labels differ from each other, which the broken behaviour also
     * satisfied.
     */
    @Test
    fun `the catalog name wins where CLDR drops the script`() {
        assertThat(languageDisplayName("zh", Locale.ENGLISH, fallback = "Chinese (Simplified)"))
            .isEqualTo("Chinese (Simplified)")
        assertThat(languageDisplayName("zh-TW", Locale.ENGLISH, fallback = "Chinese (Traditional)"))
            .isEqualTo("Chinese (Traditional)")
        assertThat(languageDisplayName("ms-Arab", Locale.ENGLISH, fallback = "Malay (Jawi)"))
            .isEqualTo("Malay (Jawi)")
        assertThat(languageDisplayName("pa-Arab", Locale.ENGLISH, fallback = "Punjabi (Shahmukhi)"))
            .isEqualTo("Punjabi (Shahmukhi)")
    }

    /** The override is a named set, not a rule that quietly widens. */
    @Test
    fun `every other id still takes the localized platform name`() {
        assertThat(languageDisplayName("fr-CA", Locale.ENGLISH, fallback = "French (Canada)"))
            .isEqualTo("French (Canada)")
        assertThat(languageDisplayName("de", Locale.FRENCH, fallback = "German")).isEqualTo("allemand")
    }

    @Test
    fun `the rail keeps its ends and never exceeds what fits`() {
        val alphabet = ('A'..'Z').mapIndexed { index, letter -> letter to index }

        val sampled = alphabet.sampledTo(10)

        assertThat(sampled).hasSize(10)
        assertThat(sampled.first()).isEqualTo('A' to 0)
        // Dropping the tail instead would make the rail lie by omission: an
        // index that stops at M in a list that runs to Z.
        assertThat(sampled.last()).isEqualTo('Z' to 25)
        assertThat(sampled.map { it.second }).isInOrder()
    }

    @Test
    fun `a rail that already fits is untouched`() {
        val five = ('A'..'E').mapIndexed { index, letter -> letter to index }

        assertThat(five.sampledTo(10)).isEqualTo(five)
    }
}
