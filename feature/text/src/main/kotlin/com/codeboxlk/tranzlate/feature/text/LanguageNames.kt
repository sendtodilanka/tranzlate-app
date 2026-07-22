package com.codeboxlk.tranzlate.feature.text

import java.util.Locale

/**
 * BCP-47 id → localized display name via [java.util.Locale.getDisplayLanguage]
 * (plan Stage C — no name table in the feature; the platform localizes).
 * Unknown ids fall back to the raw id so a chip is never blank.
 */
fun languageDisplayName(id: String): String {
    val display = Locale.forLanguageTag(id).getDisplayLanguage(Locale.getDefault())
    return display.ifBlank { id }
}

/** Result-block label form (UI_SPEC §2.4 — "ENGLISH" small-caps style label). */
fun languageBlockLabel(id: String): String = languageDisplayName(id).uppercase(Locale.getDefault())
