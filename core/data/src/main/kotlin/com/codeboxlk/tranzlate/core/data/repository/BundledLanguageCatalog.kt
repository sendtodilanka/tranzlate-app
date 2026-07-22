package com.codeboxlk.tranzlate.core.data.repository

import com.codeboxlk.tranzlate.core.model.Language

/**
 * INTERIM minimal catalog (issue #11 Home vertical) — serves the language picker
 * until the brains phase seeds the full re-derived 180+ list into Room
 * (spec 02 §4.1/§4.2; the `TODO(#4-brains)` in [LanguageRepositoryImpl]).
 *
 * Every id below is BCP-47 AND present in ML Kit Translation's supported set
 * (developers.google.com/ml-kit/language/translation/translate-language-support),
 * so `offlineAvailable = true` is accurate for each row. `offlineDownloaded`
 * stays false — download state is runtime data the offline-manager owns (D-E2)
 * and is unknown until that vertical lands.
 */
internal object BundledLanguageCatalog {
    val minimal: List<Language> =
        listOf(
            language("ar", "Arabic"),
            language("de", "German"),
            language("en", "English"),
            language("es", "Spanish"),
            language("fr", "French"),
            language("hi", "Hindi"),
            language("id", "Indonesian"),
            language("it", "Italian"),
            language("ja", "Japanese"),
            language("ko", "Korean"),
            language("nl", "Dutch"),
            language("pl", "Polish"),
            language("pt", "Portuguese"),
            language("ru", "Russian"),
            language("sw", "Swahili"),
            language("ta", "Tamil"),
            language("th", "Thai"),
            language("tr", "Turkish"),
            language("vi", "Vietnamese"),
            language("zh", "Chinese"),
        )

    private fun language(
        id: String,
        name: String,
    ): Language =
        Language(
            id = id,
            name = name,
            offlineAvailable = true,
            offlineDownloaded = false,
        )
}
