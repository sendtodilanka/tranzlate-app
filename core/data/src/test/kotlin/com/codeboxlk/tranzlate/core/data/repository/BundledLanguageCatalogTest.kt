package com.codeboxlk.tranzlate.core.data.repository

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

/**
 * The catalog is DATA, and data rots silently — a mistyped tag does not fail to
 * compile, it just makes one language stop translating. These tests pin it to
 * the two official sources it was derived from on 2026-07-31
 * (`docs/plan/issue-117-catalog.md`).
 */
class BundledLanguageCatalogTest {
    @Test
    fun `the catalog carries the whole Cloud Translation NMT list`() {
        assertThat(BundledLanguageCatalog.all).hasSize(EXPECTED_TOTAL)
    }

    @Test
    fun `ids are unique`() {
        val ids = BundledLanguageCatalog.all.map { it.id }

        assertThat(ids).containsNoDuplicates()
    }

    @Test
    fun `ids are unique regardless of case`() {
        val ids = BundledLanguageCatalog.all.map { it.id.lowercase() }

        assertThat(ids).containsNoDuplicates()
    }

    @Test
    fun `every row carries a display name`() {
        val nameless = BundledLanguageCatalog.all.filter { it.name.isBlank() }

        assertThat(nameless).isEmpty()
    }

    /**
     * The offline-capable subset is the whole reason `offlineAvailable` exists,
     * and it is exactly ML Kit's `TranslateLanguage` constant set. Pinned as a
     * literal here rather than read back from the object under test, so an edit
     * to the catalog cannot quietly redefine what "offline" means.
     */
    @Test
    fun `the offline-capable subset is exactly ML Kit's TranslateLanguage set`() {
        val offlineIds = BundledLanguageCatalog.all.filter { it.offlineAvailable }.map { it.id }

        assertThat(offlineIds).containsExactlyElementsIn(ML_KIT_TAGS)
        assertThat(BundledLanguageCatalog.offlineCapableIds).containsExactlyElementsIn(ML_KIT_TAGS)
    }

    @Test
    fun `ML Kit's set is a strict subset of the online catalog`() {
        val allIds = BundledLanguageCatalog.all.map { it.id }

        assertThat(allIds).containsAtLeastElementsIn(ML_KIT_TAGS)
        assertThat(ML_KIT_TAGS.size).isLessThan(allIds.size)
    }

    /**
     * `offlineDownloaded` is per-device runtime truth. A constant that claimed it
     * would be wrong on every device that has downloaded anything, so the catalog
     * must stay silent and let [LanguageRepositoryImpl] overlay the live value.
     */
    @Test
    fun `no row claims a downloaded model`() {
        val claiming = BundledLanguageCatalog.all.filter { it.offlineDownloaded }

        assertThat(claiming).isEmpty()
    }

    /**
     * Every id must be a tag the platform actually parses: `Locale` handing back
     * the identical tag proves it was understood as a language tag and not
     * silently degraded to `und`, which is what a malformed id would produce.
     */
    @Test
    fun `every id is a well-formed BCP-47 tag the platform round-trips`() {
        val broken =
            BundledLanguageCatalog.all
                .map { it.id }
                .filter { Locale.forLanguageTag(it).toLanguageTag() != it }

        assertThat(broken).isEmpty()
    }

    /**
     * CLDR names most of the catalog, but not all of it, and the gap is why
     * [com.codeboxlk.tranzlate.core.model.Language.name] exists instead of the UI
     * calling `getDisplayLanguage`. The exception list is pinned so the day CLDR
     * learns one of these we find out rather than guess.
     *
     * Measured on the desktop JVM this test runs on. Android ships its own ICU
     * data, so the device set may be smaller — which is safe in one direction
     * only, and is exactly why the UI must not depend on it.
     */
    @Test
    fun `the ids CLDR cannot name are the known eight`() {
        val unnamed =
            BundledLanguageCatalog.all
                .map { it.id }
                .filter { id ->
                    val locale = Locale.forLanguageTag(id)
                    locale.getDisplayLanguage(Locale.ENGLISH) == locale.language
                }

        assertThat(unnamed)
            .containsExactly("alz", "btx", "bts", "dov", "cnh", "hrx", "ktu", "yua")
    }

    @Test
    fun `an exact id resolves to itself`() {
        for (language in BundledLanguageCatalog.all) {
            assertThat(BundledLanguageCatalog.canonicalId(language.id)).isEqualTo(language.id)
        }
    }

    /**
     * The legacy and alternate spellings, pinned by name with the source that
     * justifies each one (plan-doc §4). Getting any of these wrong does not
     * crash — it silently drops the language, which is the worst failure mode
     * there is.
     */
    @Test
    fun `legacy and alternate spellings resolve to the catalog id`() {
        // Cloud Translation prints these cells as "X or Y"; we keep the modern form,
        // except Filipino where ML Kit's own constant is "tl".
        assertThat(BundledLanguageCatalog.canonicalId("iw")).isEqualTo("he")
        assertThat(BundledLanguageCatalog.canonicalId("jw")).isEqualTo("jv")
        assertThat(BundledLanguageCatalog.canonicalId("fil")).isEqualTo("tl")
        assertThat(BundledLanguageCatalog.canonicalId("zh-CN")).isEqualTo("zh")
        // ISO 639 legacy codes still emitted by older platform APIs.
        assertThat(BundledLanguageCatalog.canonicalId("in")).isEqualTo("id")
        assertThat(BundledLanguageCatalog.canonicalId("ji")).isEqualTo("yi")
        // Cloud's language-variants table: zh-HK is served by the zh-TW model.
        assertThat(BundledLanguageCatalog.canonicalId("zh-HK")).isEqualTo("zh-TW")
        // Traditional script must not truncate to zh, which is Simplified.
        assertThat(BundledLanguageCatalog.canonicalId("zh-Hant")).isEqualTo("zh-TW")
    }

    /**
     * The trap this guards: RFC 4647 truncation alone would send every `zh-*`
     * to `zh`, and `zh` is Simplified. Traditional text would then be translated
     * by the wrong model with no error anywhere.
     */
    @Test
    fun `Chinese variants keep their script`() {
        assertThat(BundledLanguageCatalog.canonicalId("zh-Hans")).isEqualTo("zh")
        assertThat(BundledLanguageCatalog.canonicalId("zh-Hans-CN")).isEqualTo("zh")
        assertThat(BundledLanguageCatalog.canonicalId("zh-Hant-TW")).isEqualTo("zh-TW")
        assertThat(BundledLanguageCatalog.canonicalId("zh-TW")).isEqualTo("zh-TW")
    }

    /** RFC 4647: "Matching ... MUST be done in a case-insensitive manner". */
    @Test
    fun `matching is case-insensitive`() {
        assertThat(BundledLanguageCatalog.canonicalId("EN")).isEqualTo("en")
        assertThat(BundledLanguageCatalog.canonicalId("IW")).isEqualTo("he")
        assertThat(BundledLanguageCatalog.canonicalId("pt-br")).isEqualTo("pt-BR")
        assertThat(BundledLanguageCatalog.canonicalId("ZH-tw")).isEqualTo("zh-TW")
    }

    /**
     * RFC 4647 §3.4 Lookup: "the language range is progressively truncated from
     * the end until a matching language tag is located". A regional tag we do not
     * carry must fall back to its base language rather than disappear.
     */
    @Test
    fun `an unknown region falls back to the base language`() {
        assertThat(BundledLanguageCatalog.canonicalId("en-GB")).isEqualTo("en")
        assertThat(BundledLanguageCatalog.canonicalId("es-419")).isEqualTo("es")
        assertThat(BundledLanguageCatalog.canonicalId("de-AT-1901")).isEqualTo("de")
    }

    /** Underscore is the `java.util.Locale#toString` separator, not BCP-47's. */
    @Test
    fun `an underscore-separated tag is accepted`() {
        assertThat(BundledLanguageCatalog.canonicalId("pt_BR")).isEqualTo("pt-BR")
    }

    @Test
    fun `a tag no catalog row can serve resolves to null`() {
        assertThat(BundledLanguageCatalog.canonicalId("und")).isNull()
        assertThat(BundledLanguageCatalog.canonicalId("zzz")).isNull()
        assertThat(BundledLanguageCatalog.canonicalId("")).isNull()
        assertThat(BundledLanguageCatalog.canonicalId("   ")).isNull()
    }
}

private const val EXPECTED_TOTAL = 194

/**
 * ML Kit Translation `TranslateLanguage` constant values, read from the API
 * reference on 2026-07-31 (59 constants). The prose page spec 02 cites now
 * redirects away, so this reference is the authority.
 */
private val ML_KIT_TAGS =
    listOf(
        "af",
        "ar",
        "be",
        "bg",
        "bn",
        "ca",
        "cs",
        "cy",
        "da",
        "de",
        "el",
        "en",
        "eo",
        "es",
        "et",
        "fa",
        "fi",
        "fr",
        "ga",
        "gl",
        "gu",
        "he",
        "hi",
        "hr",
        "ht",
        "hu",
        "id",
        "is",
        "it",
        "ja",
        "ka",
        "kn",
        "ko",
        "lt",
        "lv",
        "mk",
        "mr",
        "ms",
        "mt",
        "nl",
        "no",
        "pl",
        "pt",
        "ro",
        "ru",
        "sk",
        "sl",
        "sq",
        "sv",
        "sw",
        "ta",
        "te",
        "th",
        "tl",
        "tr",
        "uk",
        "ur",
        "vi",
        "zh",
    )
