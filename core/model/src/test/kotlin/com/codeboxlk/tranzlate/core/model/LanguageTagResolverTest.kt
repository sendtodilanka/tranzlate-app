package com.codeboxlk.tranzlate.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The resolver is DATA, and data rots silently — a mistyped alias does not fail
 * to compile, it silently drops a language, which is the worst failure mode
 * there is. Moved here with the table itself (issue #119 lift); the catalog-side
 * suite keeps the delegation + drift pins.
 */
class LanguageTagResolverTest {
    @Test
    fun `an exact canonical id resolves to itself`() {
        for (id in LanguageTagResolver.canonicalIds) {
            assertThat(LanguageTagResolver.canonicalId(id)).isEqualTo(id)
        }
    }

    @Test
    fun `canonical ids are unique regardless of case`() {
        val lowered = LanguageTagResolver.canonicalIds.map { it.lowercase() }

        assertThat(lowered).containsNoDuplicates()
    }

    /**
     * The legacy and alternate spellings, pinned by name with the source that
     * justifies each one (plan-doc issue-117 §4).
     */
    @Test
    fun `legacy and alternate spellings resolve to the catalog id`() {
        // Cloud Translation prints these cells as "X or Y"; we keep the modern form,
        // except Filipino where ML Kit's own constant is "tl".
        assertThat(LanguageTagResolver.canonicalId("iw")).isEqualTo("he")
        assertThat(LanguageTagResolver.canonicalId("jw")).isEqualTo("jv")
        assertThat(LanguageTagResolver.canonicalId("fil")).isEqualTo("tl")
        assertThat(LanguageTagResolver.canonicalId("zh-CN")).isEqualTo("zh")
        // ISO 639 legacy codes still emitted by older platform APIs.
        assertThat(LanguageTagResolver.canonicalId("in")).isEqualTo("id")
        assertThat(LanguageTagResolver.canonicalId("ji")).isEqualTo("yi")
        // Cloud's language-variants table: zh-HK is served by the zh-TW model.
        assertThat(LanguageTagResolver.canonicalId("zh-HK")).isEqualTo("zh-TW")
        // Traditional script must not truncate to zh, which is Simplified.
        assertThat(LanguageTagResolver.canonicalId("zh-Hant")).isEqualTo("zh-TW")
    }

    /**
     * The alias the #119 record named as missing: ISO 639-1 `nb` (Bokmål) is
     * what `Locale("nb")`-built tags and several platform APIs emit, while the
     * catalog — like ML Kit's own NORWEGIAN constant — carries `no`. Truncation
     * cannot bridge two different subtags, so without the alias the language
     * silently disappeared.
     */
    @Test
    fun `Norwegian Bokmal resolves to the catalog's no`() {
        assertThat(LanguageTagResolver.canonicalId("nb")).isEqualTo("no")
        assertThat(LanguageTagResolver.canonicalId("nb-NO")).isEqualTo("no")
    }

    /**
     * The trap this guards: RFC 4647 truncation alone would send every `zh-*`
     * to `zh`, and `zh` is Simplified. Traditional text would then be translated
     * by the wrong model with no error anywhere.
     */
    @Test
    fun `Chinese variants keep their script`() {
        assertThat(LanguageTagResolver.canonicalId("zh-Hans")).isEqualTo("zh")
        assertThat(LanguageTagResolver.canonicalId("zh-Hans-CN")).isEqualTo("zh")
        assertThat(LanguageTagResolver.canonicalId("zh-Hant-TW")).isEqualTo("zh-TW")
        assertThat(LanguageTagResolver.canonicalId("zh-TW")).isEqualTo("zh-TW")
    }

    /** RFC 4647: "Matching ... MUST be done in a case-insensitive manner". */
    @Test
    fun `matching is case-insensitive`() {
        assertThat(LanguageTagResolver.canonicalId("EN")).isEqualTo("en")
        assertThat(LanguageTagResolver.canonicalId("IW")).isEqualTo("he")
        assertThat(LanguageTagResolver.canonicalId("pt-br")).isEqualTo("pt-BR")
        assertThat(LanguageTagResolver.canonicalId("ZH-tw")).isEqualTo("zh-TW")
    }

    /**
     * RFC 4647 §3.4 Lookup: "the language range is progressively truncated from
     * the end until a matching language tag is located". A regional tag we do not
     * carry must fall back to its base language rather than disappear.
     */
    @Test
    fun `an unknown region falls back to the base language`() {
        assertThat(LanguageTagResolver.canonicalId("en-GB")).isEqualTo("en")
        assertThat(LanguageTagResolver.canonicalId("es-419")).isEqualTo("es")
        assertThat(LanguageTagResolver.canonicalId("de-AT-1901")).isEqualTo("de")
    }

    /** Underscore is the `java.util.Locale#toString` separator, not BCP-47's. */
    @Test
    fun `an underscore-separated tag is accepted`() {
        assertThat(LanguageTagResolver.canonicalId("pt_BR")).isEqualTo("pt-BR")
    }

    @Test
    fun `a tag no catalog row can serve resolves to null`() {
        assertThat(LanguageTagResolver.canonicalId("und")).isNull()
        assertThat(LanguageTagResolver.canonicalId("zzz")).isNull()
        assertThat(LanguageTagResolver.canonicalId("")).isNull()
        assertThat(LanguageTagResolver.canonicalId("   ")).isNull()
    }

    /**
     * The `"auto"` detect sentinel is a picker affordance, not a language
     * ([LanguageRole]'s contract). It must resolve to null so write-side
     * canonicalisers fall back to passing it through unchanged — mapping it to
     * any catalog row would turn "Detect language" into a concrete language.
     */
    @Test
    fun `the auto sentinel is not a language and resolves to null`() {
        assertThat(LanguageTagResolver.canonicalId("auto")).isNull()
    }
}
