package com.codeboxlk.tranzlate.feature.text

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import java.text.Normalizer
import java.util.Locale

/**
 * Source-language id meaning "let the engine detect it" — the `Translator`
 * contract's `"auto"` (TEST_A11Y_CONTRACT §1.1, golden row G7).
 */
const val DETECT_LANGUAGE_ID = "auto"

/** Combining marks left behind by NFD decomposition (accents, tone marks, umlauts…). */
private val COMBINING_MARKS = Regex("\\p{Mn}+")

/** The avatar carries the code, not a flag — at most a primary subtag's worth of it. */
private const val AVATAR_CODE_MAX = 3

/**
 * BCP-47 id → localized display name (no name table in the feature; the platform
 * localizes). Two platform behaviours drive the shape of this, both MEASURED on
 * the build JDK (21.0.10) rather than assumed — see `LanguageNamesTest`:
 *
 * 1. **`getDisplayName`, never `getDisplayLanguage`.** `getDisplayLanguage`
 *    drops region and script, so `fr` / `fr-CA` / `fr-FR` all come back
 *    "French" — and since this same function is the picker's SORT key, the three
 *    would land adjacent and indistinguishable. That is not cosmetic: within
 *    each such family the base tag is offline-capable and the variants are not,
 *    so "which row is the one I downloaded" would have no answer. Same for the
 *    `pt`, `zh`, `ms` and `pa` families.
 * 2. **An unknown tag comes back as ITSELF, not blank.** `Locale.forLanguageTag("alz")
 *    .getDisplayName(ENGLISH)` is `"alz"`, so a `isBlank()` test never fires and
 *    the eight Cloud-only tags (alz, btx, bts, dov, cnh, hrx, ktu, yua) would
 *    render as raw codes. When the platform hands the tag back we use
 *    [fallback] — the catalog's own English name — and only fall through to the
 *    raw id when there is nothing better, so a control is never blank.
 */
fun languageDisplayName(
    id: String,
    locale: Locale = Locale.getDefault(),
    fallback: String = id,
): String {
    if (id in CATALOG_NAME_WINS && fallback.isNotBlank()) return fallback
    val self = Locale.forLanguageTag(id)
    val display = self.getDisplayName(locale)
    val platformKnowsIt = display.isNotBlank() && display != id && display != self.toLanguageTag()
    return if (platformKnowsIt) display else fallback.ifBlank { id }
}

/**
 * The handful of ids where CLDR is *less* informative than our own catalog, and
 * the catalog label wins outright.
 *
 * CLDR names these by region or script code, which loses the word the user is
 * actually looking for: `zh` becomes "Chinese" and `zh-TW` "Chinese (Taiwan)",
 * so neither row says **Simplified** or **Traditional** — the one distinction a
 * Chinese reader is scanning for. Likewise "Malay (Arabic)" for Jawi and
 * "Punjabi (Arabic)" for Shahmukhi.
 *
 * The trade-off is deliberate and narrow: these four rows read in English on a
 * device whose UI language is something else, whereas the other 190 stay
 * localized. A name that is localized but does not say which script you are
 * choosing is worse than one that does — and the alternative, hand-translating
 * script qualifiers into every locale, is a bigger promise than this list.
 *
 * An explicit set rather than a heuristic, so a future entry is an argued
 * decision rather than a rule quietly widening.
 */
private val CATALOG_NAME_WINS = setOf("zh", "zh-TW", "ms-Arab", "pa-Arab")

/**
 * The language's name **in itself** — "Español", "සිංහල", "日本語".
 *
 * The redesigned picker does not PRINT this (the design gives the supporting
 * line to offline state), but it stays in the search haystack: a user who does
 * not read the UI language types the name they know, and that is the one they
 * know. Null when it carries no extra information: blank, or the same word the
 * UI already shows. The equality check is case-insensitive because CLDR
 * lower-cases many endonyms ("español", "français") while the UI name is
 * capitalized.
 */
fun languageEndonym(
    id: String,
    displayName: String,
): String? {
    val self = Locale.forLanguageTag(id)
    val endonym = self.getDisplayName(self)
    return endonym.takeIf {
        it.isNotBlank() && !it.equals(displayName, ignoreCase = true) && it != id && it != self.toLanguageTag()
    }
}

/**
 * The two-or-three letter code the row's avatar circle shows in place of a flag
 * (issue #117 design: flags are wrong for languages — one language, many flags).
 *
 * The PRIMARY subtag only: "fr-CA" shows "FR", disambiguated by the name beside
 * it ("French (Canada)"). The avatar is decorative and is never read aloud
 * (plan §5), so an ambiguous pair of letters costs nothing.
 */
fun languageAvatarCode(id: String): String =
    id
        .substringBefore('-')
        .take(AVATAR_CODE_MAX)
        // ROOT, never the UI locale: a BCP-47 subtag is ASCII, and a Turkish
        // device would otherwise print "Sİ" for Sinhala.
        .uppercase(Locale.ROOT)

/**
 * Fold a name or a query into its searchable form. Applied to BOTH sides of
 * every comparison — that symmetry is what makes it safe for scripts NFD splits
 * structurally (Hangul syllables become jamo either way).
 *
 * 1. NFD — canonical decomposition, so `ñ` becomes `n` + a combining tilde.
 * 2. drop the combining marks — "Español" folds to "espanol", "Tiếng Việt" to
 *    "tieng viet", so a user typing without accents still finds the language.
 * 3. lowercase in [Locale.ROOT], never the default locale: a Turkish device
 *    would otherwise fold `I` to `ı` and match differently from every other
 *    device. Turkish input still works — `İ` decomposes to `I` + a mark that
 *    step 2 removes.
 */
fun searchNormalize(raw: String): String =
    Normalizer
        .normalize(raw, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)
        .trim()

/**
 * What the USER sees for a language id: [DETECT_LANGUAGE_ID] reads as
 * "Detect language" (GT parity), everything else as its localized name.
 */
@Composable
fun languageLabel(id: String): String =
    if (id == DETECT_LANGUAGE_ID) {
        stringResource(R.string.text_lang_detect)
    } else {
        languageDisplayName(id, LocalLocale.current.platformLocale)
    }

/**
 * Result-block label form (UI_SPEC §2.4 — "ENGLISH" small-caps style label).
 * Upper-casing is locale-sensitive, so the locale is read OBSERVABLY here: a
 * locale change has to recompose the label (`Locale.getDefault()` would not).
 */
@Composable
fun languageBlockLabel(id: String): String = languageLabel(id).uppercase(LocalLocale.current.platformLocale)
