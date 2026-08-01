package com.codeboxlk.tranzlate.feature.language

import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.codeboxlk.tranzlate.core.ui.languageDisplayName
import java.text.Collator
import java.util.Locale

/**
 * A Screen-B row as the SCREEN needs it: the catalog row plus the one name the
 * user will actually read.
 *
 * The name is computed here rather than at each call site because a row is read
 * out five times — the label and four content descriptions — and a co-verify
 * lens found what happens when only two of them are converted: on a pt-BR
 * device 58 of the 59 rows showed "francês" and had TalkBack announce
 * "Baixar French". One field, five readers, no way for them to disagree.
 */
data class OfflinePackRow(
    val id: String,
    val displayName: String,
    val state: OfflineModelState,
)

/**
 * Catalog rows → displayed rows, sorted the way the reader sees them.
 *
 * The sort is the second half of the same defect. The catalog is ordered by
 * ENGLISH name (`BundledLanguageCatalog`: "ordered by English name"), so
 * localizing the labels without re-sorting leaves Dutch under D as "holandês" —
 * 10 of 58 adjacent pairs out of order on pt-BR. `Collator` rather than
 * `sortedBy`: a raw String sort orders by UTF-16 code unit, which puts every
 * accented name after "z". The picker settled this in `buildPickerRows`; this
 * is the same answer, because two screens in one module may not disagree about
 * what alphabetical means.
 *
 * Pure and locale-in-the-signature on purpose: the composable remembers it on
 * the locale, so a language change recomposes into the new order, and a unit
 * test can pin both halves without a Compose harness.
 */
fun buildOfflineRows(
    rows: List<OfflineLanguageRow>,
    locale: Locale,
): List<OfflinePackRow> {
    val collator = Collator.getInstance(locale)
    return rows
        .map { row ->
            OfflinePackRow(
                id = row.id,
                displayName = languageDisplayName(row.id, locale, fallback = row.name),
                state = row.state,
            )
        }.sortedWith { left, right -> collator.compare(left.displayName, right.displayName) }
}
