package com.codeboxlk.tranzlate.feature.text

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import java.util.Locale

/**
 * Source-language id meaning "let the engine detect it" — the `Translator`
 * contract's `"auto"` (TEST_A11Y_CONTRACT §1.1, golden row G7).
 */
const val DETECT_LANGUAGE_ID = "auto"

/**
 * BCP-47 id → localized display name via [java.util.Locale.getDisplayLanguage]
 * (no name table in the feature; the platform localizes).
 * Unknown ids fall back to the raw id so a control is never blank.
 */
fun languageDisplayName(id: String): String {
    val display = Locale.forLanguageTag(id).getDisplayLanguage(Locale.getDefault())
    return display.ifBlank { id }
}

/**
 * What the USER sees for a language id: [DETECT_LANGUAGE_ID] reads as
 * "Detect language" (GT parity), everything else as its localized name.
 */
@Composable
fun languageLabel(id: String): String =
    if (id == DETECT_LANGUAGE_ID) {
        stringResource(R.string.text_lang_detect)
    } else {
        languageDisplayName(id)
    }

/**
 * Result-block label form (UI_SPEC §2.4 — "ENGLISH" small-caps style label).
 * Upper-casing is locale-sensitive, so the locale is read OBSERVABLY here: a
 * locale change has to recompose the label (`Locale.getDefault()` would not).
 */
@Composable
fun languageBlockLabel(id: String): String = languageLabel(id).uppercase(LocalLocale.current.platformLocale)
