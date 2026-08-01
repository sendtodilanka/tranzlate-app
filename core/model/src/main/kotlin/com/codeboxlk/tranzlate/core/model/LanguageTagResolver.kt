package com.codeboxlk.tranzlate.core.model

/**
 * The one place any incoming BCP-47 tag becomes a catalog id (issue #119 /
 * #130 rev.3 U-7). Lifted out of `:core:data`'s `BundledLanguageCatalog` so
 * every layer that receives a tag from outside the catalog — preference
 * writes, the ML Kit language-ID detector, TTS voice locales — resolves
 * through the SAME table; the catalog itself delegates here, so there is
 * exactly one table to maintain.
 *
 * An `object`, matching the codebase's style for pure compile-time catalogs
 * (`BundledLanguageCatalog` is one): the table is static data, and an
 * injectable would add a seam with nothing behind it to fake.
 *
 * Resolution implements RFC 4647 §3.4 "Lookup": the tag is progressively
 * truncated from the end until a match is found, and matching is
 * case-insensitive (RFC 4647: "Matching of language tags to language ranges
 * MUST be done in a case-insensitive manner"). `lowercase()` is the no-arg,
 * locale-invariant overload — the Turkish dotless-i would corrupt a
 * `Locale`-sensitive one.
 *
 * Deliberately does NOT go through `java.util.Locale`: its legacy-code
 * canonicalisation differs between the desktop JVM and Android, which would
 * make unit-test results a lie about device behaviour. The mapping here is an
 * explicit table, so both platforms agree.
 */
object LanguageTagResolver {
    /**
     * Every id the bundled catalog carries (spec 02 §4.1 derivation,
     * `docs/plan/issue-117-catalog.md`), in the catalog's own order. The
     * catalog pins its rows against this list in `BundledLanguageCatalogTest`,
     * so the two can never drift apart silently.
     */
    val canonicalIds: List<String> =
        listOf(
            "ab",
            "ace",
            "ach",
            "af",
            "sq",
            "alz",
            "am",
            "ar",
            "hy",
            "as",
            "awa",
            "ay",
            "az",
            "ban",
            "bm",
            "ba",
            "eu",
            "btx",
            "bts",
            "bbc",
            "be",
            "bem",
            "bn",
            "bew",
            "bho",
            "bik",
            "bs",
            "br",
            "bg",
            "bua",
            "yue",
            "ca",
            "ceb",
            "ny",
            "zh",
            "zh-TW",
            "cv",
            "co",
            "crh",
            "hr",
            "cs",
            "da",
            "din",
            "dv",
            "doi",
            "dov",
            "nl",
            "dz",
            "en",
            "eo",
            "et",
            "ee",
            "fj",
            "tl",
            "fi",
            "fr",
            "fr-CA",
            "fr-FR",
            "fy",
            "ff",
            "gaa",
            "gl",
            "lg",
            "ka",
            "de",
            "el",
            "gn",
            "gu",
            "ht",
            "cnh",
            "ha",
            "haw",
            "he",
            "hil",
            "hi",
            "hmn",
            "hu",
            "hrx",
            "is",
            "ig",
            "ilo",
            "id",
            "ga",
            "it",
            "ja",
            "jv",
            "kn",
            "pam",
            "kk",
            "km",
            "cgg",
            "rw",
            "ktu",
            "gom",
            "ko",
            "kri",
            "ku",
            "ckb",
            "ky",
            "lo",
            "ltg",
            "la",
            "lv",
            "lij",
            "li",
            "ln",
            "lt",
            "lmo",
            "luo",
            "lb",
            "mk",
            "mai",
            "mak",
            "mg",
            "ms",
            "ms-Arab",
            "ml",
            "mt",
            "mi",
            "mr",
            "chm",
            "mni-Mtei",
            "min",
            "lus",
            "mn",
            "my",
            "nr",
            "new",
            "ne",
            "nso",
            "no",
            "nus",
            "oc",
            "or",
            "om",
            "pag",
            "pap",
            "ps",
            "fa",
            "pl",
            "pt",
            "pt-BR",
            "pt-PT",
            "pa",
            "pa-Arab",
            "qu",
            "rom",
            "ro",
            "rn",
            "ru",
            "sm",
            "sg",
            "sa",
            "gd",
            "sr",
            "st",
            "crs",
            "shn",
            "sn",
            "scn",
            "szl",
            "sd",
            "si",
            "sk",
            "sl",
            "so",
            "es",
            "su",
            "sw",
            "ss",
            "sv",
            "tg",
            "ta",
            "tt",
            "te",
            "tet",
            "th",
            "ti",
            "ts",
            "tn",
            "tr",
            "tk",
            "ak",
            "uk",
            "ur",
            "ug",
            "uz",
            "vi",
            "cy",
            "xh",
            "yi",
            "yo",
            "yua",
            "zu",
        )

    /**
     * Alternate and legacy spellings that must resolve to a catalog id rather
     * than silently miss — ML Kit's Language-ID API, restored preferences and
     * Cloud's own documented "X or Y" cells all emit these forms. Keys are
     * lowercase because RFC 4647 requires case-insensitive matching.
     */
    private val legacyAliases: Map<String, String> =
        mapOf(
            "iw" to "he",
            "in" to "id",
            "ji" to "yi",
            "jw" to "jv",
            "fil" to "tl",
            // ISO 639-1 Bokmål; the catalog (like ML Kit's own NORWEGIAN
            // constant) carries Norwegian as "no", so plain truncation cannot
            // reach it (#119).
            "nb" to "no",
            "zh-cn" to "zh",
            "zh-hk" to "zh-TW",
            "zh-hant" to "zh-TW",
        )

    private val byLowercaseId: Map<String, String> = canonicalIds.associateBy { it.lowercase() }

    /**
     * Resolves any incoming BCP-47 tag to a catalog id, or `null` when nothing
     * in the catalog can serve it. The `"auto"` detect sentinel is not a
     * language and resolves to `null` — callers that must pass it through keep
     * it via their own `?: id` fallback.
     */
    fun canonicalId(tag: String): String? {
        var candidate = tag.trim().replace('_', '-')
        while (candidate.isNotEmpty()) {
            val key = candidate.lowercase()
            byLowercaseId[key]?.let { return it }
            legacyAliases[key]?.let { return it }
            val lastSeparator = candidate.lastIndexOf('-')
            if (lastSeparator < 0) return null
            candidate = candidate.substring(0, lastSeparator)
        }
        return null
    }
}
