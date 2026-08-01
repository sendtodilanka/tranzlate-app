package com.codeboxlk.tranzlate.feature.language

import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

/**
 * Screen B's rows: the name the user reads, and the order they read it in.
 *
 * These exist because a co-verify lens reverted the screen's localization and
 * the whole suite stayed green — the product claim of an entire PR had nothing
 * asserting it. Both halves are pinned here, at the pure function, so neither
 * needs a Compose harness (this project has none — #40).
 */
class OfflineRowsTest {
    private val ptBr = Locale.forLanguageTag("pt-BR")

    private fun row(
        id: String,
        catalogName: String,
    ) = OfflineLanguageRow(id, catalogName, OfflineModelState.NotDownloaded)

    /**
     * The defect this screen shipped with: it rendered the catalog's own
     * English string. CLDR does not always agree with the catalog, and where
     * they differ the picker showed one word and this screen showed the other,
     * on a plain English device.
     */
    @Test
    fun `a row is named the way the picker names it, not the way the catalog does`() {
        val shown = buildOfflineRows(listOf(row("bn", "Bengali")), Locale.ENGLISH)

        assertThat(shown.single().displayName).isEqualTo("Bangla")
    }

    /** Where CLDR has nothing to add, the catalog name is still what shows. */
    @Test
    fun `a row CLDR cannot name keeps the catalog name`() {
        val shown = buildOfflineRows(listOf(row("alz", "Alur")), Locale.ENGLISH)

        assertThat(shown.single().displayName).isEqualTo("Alur")
    }

    /** The name follows the reader's language, not the build's. */
    @Test
    fun `names are localized into the UI language`() {
        val shown = buildOfflineRows(listOf(row("fr", "French")), ptBr)

        assertThat(shown.single().displayName).isEqualTo("francês")
    }

    /**
     * The second half of the same defect, and the easier one to ship without
     * noticing: the catalog is ordered by ENGLISH name, so localizing the
     * labels alone leaves Dutch filed under D as "holandês".
     *
     * The seven rows below are chosen so that the catalog order, a raw String
     * sort and a Collator sort are three DIFFERENT sequences — measured, not
     * assumed:
     *
     *     catalog(EN)      árabe azerbaijano dinamarquês holandês inglês esperanto estoniano
     *     sortedBy(String) azerbaijano … inglês árabe        ← "á" is U+00E1, so it lands after "z"
     *     Collator(pt-BR)  árabe azerbaijano dinamarquês esperanto estoniano holandês inglês
     *
     * A first version of this test used da/nl/en, whose catalog order already
     * equals their sorted order — deleting the sort entirely left it green.
     */
    @Test
    fun `rows are ordered by the displayed name, not the catalog's English order`() {
        val catalogOrder =
            listOf(
                row("ar", "Arabic"),
                row("az", "Azerbaijani"),
                row("da", "Danish"),
                row("nl", "Dutch"),
                row("en", "English"),
                row("eo", "Esperanto"),
                row("et", "Estonian"),
            )

        val shown = buildOfflineRows(catalogOrder, ptBr).map { it.displayName }

        assertThat(shown)
            .containsExactly(
                "árabe",
                "azerbaijano",
                "dinamarquês",
                "esperanto",
                "estoniano",
                "holandês",
                "inglês",
            ).inOrder()
    }

    /**
     * `sortedBy(String)` orders by UTF-16 code unit, which files every accented
     * name after "z". A Collator is what makes "á" sort as "a" for a reader who
     * expects it to — and this is the assertion a raw sort cannot pass.
     */
    @Test
    fun `an accented name sorts where the reader expects, not after z`() {
        val rows = listOf(row("az", "Azerbaijani"), row("ar", "Arabic"))

        val shown = buildOfflineRows(rows, ptBr).map { it.displayName }

        assertThat(shown).containsExactly("árabe", "azerbaijano").inOrder()
    }

    /** Every row survives the mapping: presentation may not drop a language. */
    @Test
    fun `mapping never loses or invents a row`() {
        val rows = listOf(row("fr", "French"), row("de", "German"), row("bn", "Bengali"))

        val shown = buildOfflineRows(rows, Locale.ENGLISH)

        assertThat(shown.map { it.id }).containsExactlyElementsIn(rows.map { it.id })
    }
}
