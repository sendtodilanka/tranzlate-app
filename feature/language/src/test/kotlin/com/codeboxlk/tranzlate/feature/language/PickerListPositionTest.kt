package com.codeboxlk.tranzlate.feature.language

import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.model.LanguageRole
import com.codeboxlk.tranzlate.core.ui.DETECT_LANGUAGE_ID
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * A list position captured in one arrangement must mean the same LANGUAGE in the
 * other (#198 co-verify, F1).
 *
 * **The defect this replaces.** PR-14 carried the position across a rotation as a
 * raw grid item index. Single-pane's grid emits
 * `[detect?] [recent header + rows?] [All languages] …catalog`; 17a moves the
 * first two into the side pane and emits `[All languages] …catalog`. The prefix
 * differs by 1 to 7 items, so the same NUMBER means a different language on the
 * other side of the rotation. Reproduced on `emulator-5554` with three recents
 * and no search: portrait standing at **English**, landscape opened at
 * **Finnish**.
 *
 * **How it hid.** The PR's own device check typed `ar` into the search box first,
 * and that query suppresses the detect row and empties the recents section in
 * BOTH arrangements — collapsing the prefix difference to exactly zero. The
 * ritual accidentally selected the one case where the bug cannot appear. So
 * nothing here searches, except the one case that shows the collapse is a
 * property of that QUERY and not of searching
 * (`a query that matches Detect keeps the source picker's gap`).
 *
 * **How the emission order gets into a JVM test.** There is no Compose test
 * runtime in this module (#186) and CI compiles instrumented tests without
 * running them (#40), so nothing here can mount the grid and read its keys.
 * [emittedKeys] transcribes `PickerCatalog`'s emission block instead —
 * deliberately NOT in terms of [PickerListPlan.catalogOffset], because a model
 * built from the number under test would agree with itself whatever it said. The
 * drift that transcription can suffer is closed by
 * `the grid emits its sections in the order this file models`, which reads the
 * order back out of the shipped file.
 */
class PickerListPositionTest {
    // ---- the round trip ------------------------------------------------------

    /**
     * THE regression test for F1, in the shape the harm was reproduced in:
     * browsing a source picker that has recents, no search, position captured in
     * one arrangement and restored in the other.
     *
     * Asserted BOTH ways round, because a rotation is not one-way and the two
     * directions are not the same arithmetic: one drops a prefix, the other adds
     * one back.
     */
    @Test
    fun `a position captured browsing in one arrangement means the same language in the other`() {
        val catalog = catalog()
        val recents = catalog.recentRows()
        val stacked = arrangement(twoPane = false, recentCount = recents.size)
        val split = arrangement(twoPane = true, recentCount = recents.size)

        assertRestoresTo("ca", from = stacked, to = split, recents = recents, catalog = catalog)
        assertRestoresTo("ca", from = split, to = stacked, recents = recents, catalog = catalog)
    }

    /**
     * …and the number it replaced would NOT have. Without this, the test above
     * could pass on two arrangements that happen to agree — which is exactly how
     * the search-mode ritual passed.
     */
    @Test
    fun `the raw item index it replaced lands on a different language`() {
        val catalog = catalog()
        val recents = catalog.recentRows()
        val stacked = keysOf(arrangement(twoPane = false, recentCount = recents.size), recents, catalog)
        val split = keysOf(arrangement(twoPane = true, recentCount = recents.size), recents, catalog)

        val rawIndex = stacked.indexOf(catalogRowKey("ca"))

        assertThat(rawIndex).isAtLeast(0)
        assertThat(split[rawIndex]).isNotEqualTo(catalogRowKey("ca"))
    }

    /**
     * The prefix gap with recents PRESENT and ABSENT, on both sides of the
     * picker — four shapes, because a test that covers one is a test that passes
     * on part of the bug.
     *
     * The gaps are asserted as numbers first: 5 for a source picker with three
     * recents (its detect row, the recents header and three rows), **2** for one
     * with none (the detect row AND the 18a first-run explainer, which stand in the
     * single-pane grid and move to the side pane in the split one — PR-21), 4 for a
     * target picker with three (no detect row on that side), and **0** for a target
     * picker with none (the source-only first-run block does not apply, so the two
     * arrangements genuinely agree — included so the suite states where the round
     * trip is trivially true rather than quietly relying on it).
     */
    @Test
    fun `the gap is covered with recents present and absent, on both sides`() {
        val catalog = catalog()
        val recents = catalog.recentRows()

        assertThat(recents).hasSize(3)
        assertGapAndRoundTrip(LanguageRole.SOURCE, recents, catalog, expectedGap = 5)
        // Source, no recents: detect row + first-run explainer above the catalog.
        assertGapAndRoundTrip(LanguageRole.SOURCE, emptyList(), catalog, expectedGap = 2)
        assertGapAndRoundTrip(LanguageRole.TARGET, recents, catalog, expectedGap = 4)
        assertGapAndRoundTrip(LanguageRole.TARGET, emptyList(), catalog, expectedGap = 0)
    }

    /**
     * The one search case that belongs here, and it is the opposite of the
     * ritual's. A query matching "Detect language" leaves that row in the
     * single-pane grid and not in the split one, so the gap is 1 while searching.
     * "Search collapses the difference" is true of `ar` and false of `de`.
     */
    @Test
    fun `a query that matches Detect keeps the source picker's gap`() {
        // A search empties the recents section in both arrangements (the screen
        // hands `recentCount = 0` while filtering), so detect is the whole gap,
        // and an unrailed list has no "All languages" header either.
        val results = catalog().filter { it.displayName.startsWith("D") }
        val stacked = arrangement(twoPane = false, recentCount = 0, railed = false)
        val split = arrangement(twoPane = true, recentCount = 0, railed = false)

        assertThat(results).hasSize(2)
        assertThat(stacked.plan.catalogOffset - split.plan.catalogOffset).isEqualTo(1)
        assertRestoresTo(results.first().id, from = stacked, to = split, recents = emptyList(), catalog = results)
    }

    // ---- what "no anchor" means ---------------------------------------------

    /**
     * Everything above the catalog restores to the top, and that is the right
     * answer rather than a shortcut: the detect row, the recents header and its
     * rows are in the SIDE pane in 17a, and the "All languages" header is the
     * first item of the split grid. A user standing on any of them is standing at
     * the top of whatever the other arrangement draws.
     */
    @Test
    fun `nothing above the catalog anchors a position`() {
        val catalog = catalog()
        val recents = catalog.recentRows()
        val stacked = arrangement(twoPane = false, recentCount = recents.size)
        val aboveTheCatalog = keysOf(stacked, recents, catalog).take(stacked.plan.catalogOffset)

        // Never vacuous: this arrangement really does put six things above the
        // alphabet, and they really are the ones named above.
        assertThat(aboveTheCatalog)
            .containsExactly(
                "detect_$DETECT_LANGUAGE_ID",
                "header_recent",
                RECENT_ROW_KEY_PREFIX + "en",
                RECENT_ROW_KEY_PREFIX + "fr",
                RECENT_ROW_KEY_PREFIX + "de",
                "header_all",
            ).inOrder()
        aboveTheCatalog.forEach { key ->
            assertWithMessage("$key must not anchor a position").that(pickerAnchorOf(key)).isNull()
        }
        assertThat(pickerAnchorIndex(null, catalog, stacked.plan.catalogOffset)).isEqualTo(0)
    }

    /**
     * A language the current list does not hold — a query narrowed past it, or a
     * catalog that has not arrived yet — restores to the top. Guessing an index
     * would put the user somewhere they have never been, which is worse.
     */
    @Test
    fun `an anchor the list does not hold restores to the top`() {
        val offset = arrangement(twoPane = false, recentCount = 0).plan.catalogOffset

        assertThat(pickerAnchorIndex("ca", emptyList(), offset)).isEqualTo(0)
        assertThat(pickerAnchorIndex("ca", catalog().filter { it.id == "de" }, offset)).isEqualTo(0)
    }

    /** A recents row names the same language, but it is not the row a position anchors to. */
    @Test
    fun `only a catalog row anchors a position`() {
        assertThat(pickerAnchorOf(catalogRowKey("ca"))).isEqualTo("ca")
        assertThat(pickerAnchorOf(RECENT_ROW_KEY_PREFIX + "ca")).isNull()
        assertThat(pickerAnchorOf("header_all")).isNull()
        assertThat(pickerAnchorOf("catalog_loading")).isNull()
        // A tag carrying a region subtag survives the round trip intact.
        assertThat(pickerAnchorOf(catalogRowKey("zh-CN"))).isEqualTo("zh-CN")
        // The grid's key type is Any: a non-String key is not a crash, and not an anchor.
        assertThat(pickerAnchorOf(42)).isNull()
        assertThat(pickerAnchorOf(null)).isNull()
    }

    // ---- the model above is kept honest by the file it models -----------------

    /**
     * [emittedKeys] transcribes `PickerCatalog`'s emission block, and a
     * transcription drifts. This reads the order back out of the shipped file, so
     * a section that moves — or one that is added without being counted — fails
     * here rather than in production three rotations later.
     */
    @Test
    fun `the grid emits its sections in the order this file models`() {
        val body = pickerCatalogSource()
        val inOrder =
            listOf(
                "\"detect_",
                "\"catalog_loading\"",
                "\"empty_result\"",
                // The 18a block, emitted where recents would be (PR-21).
                "firstRunItems(",
                "\"header_recent\"",
                "RECENT_ROW_KEY_PREFIX",
                "\"header_all\"",
                "CATALOG_ROW_KEY_PREFIX",
            )

        var at = -1
        inOrder.forEach { token ->
            val found = body.indexOf(token, at + 1)
            assertWithMessage(
                "PickerCatalog does not emit $token after the section before it. " +
                    "emittedKeys() in this file models that order and would now be wrong.",
            ).that(found)
                .isGreaterThan(at)
            at = found
        }
    }

    /**
     * **Both split arrangements go through ONE `PickerCatalog` call**, and that
     * call is told the shortcuts are not its business.
     *
     * The alternative shape — a second call for the leaf, or the same call with
     * `shortcutsInGrid = true` — is what "the two leaves are given the same
     * content" looks like in this file, and it fails in two ways at once. The
     * loud one is a crash: the detect row and the recents rows would be emitted
     * under keys that already exist in the pane, and duplicate keys in a lazy
     * grid are a hard failure, not a warning. The quiet one is worse and would
     * arrive first — `catalogOffset` is 0 in both split arrangements, so a grid
     * that emitted a prefix anyway would land every rail letter and every
     * restored anchor as many rows short as the pane happens to hold.
     *
     * A source rule for the reason this whole module's UI rules are source
     * rules: there is no Compose test runtime here (#186) and CI compiles
     * instrumented tests without running them (#40).
     */
    @Test
    fun `the split arrangements never emit the shortcuts twice`() {
        val body = pickerBodySource()
        val flags = Regex("""shortcutsInGrid = (true|false)""").findAll(body).map { it.groupValues[1] }.toList()

        // Never vacuous: a renamed parameter or a moved body reports nothing and
        // would satisfy "exactly one false" by having no flags at all.
        assertWithMessage("PickerBody passes shortcutsInGrid $flags")
            .that(flags)
            .containsExactly("false", "true")
            .inOrder()
    }

    /**
     * The gutter between the panes comes from the ARRANGEMENT, not from an `if`
     * at the draw site.
     *
     * Two numbers live here — 8dp between two things on one surface, 24dp across
     * a physical fold — and the gate has already established which window this
     * is. A second decision at the draw site is a second place that has to be
     * kept in step, which is the shape of the defect PR-14's co-verify found
     * when width and height were measured from two sources.
     */
    @Test
    fun `the gutter is whatever the arrangement said it was`() {
        val body = pickerBodySource()

        assertThat(body).contains("Modifier.width(arrangement.gutter)")
        // …and the two constants are NOT spelled at the draw site.
        assertThat(body).doesNotContain("PANE_GUTTER")
        assertThat(body).doesNotContain("pickerCreaseGutter")
    }

    // ---- 17b: the fold (PR-15) ----------------------------------------------

    /**
     * **The regression this PR is most likely to cause**, and the reason it is
     * written as a round trip rather than as an assertion about an index.
     *
     * A fold is a configuration change: the composition is destroyed and the
     * ViewModel is kept, exactly as on a rotation. PR-14 discovered what that
     * costs when the saved position is an item INDEX — the single-pane grid
     * emits `[detect] [recents…] [All languages] …catalog` and a split one emits
     * `[All languages] …catalog`, so the same number named a language one to
     * seven rows away. Measured then: browsing at English in portrait, landscape
     * opened at Finnish.
     *
     * PR-15 adds a third arrangement to that set, so the same question has to be
     * asked again — and now in both directions, because a foldable is the one
     * device where the user genuinely goes back and forth.
     */
    @Test
    fun `a language anchored in portrait comes back as the same language after a fold`() {
        val catalog = catalog()
        val recents = catalog.recentRows()
        val portrait = arrangement(twoPane = false, recentCount = recents.size)
        val folded = arrangement(twoPane = false, twoLeaf = true, recentCount = recents.size)

        assertRestoresTo("ca", from = portrait, to = folded, recents = recents, catalog = catalog)
        assertRestoresTo("ca", from = folded, to = portrait, recents = recents, catalog = catalog)
    }

    /**
     * …and unfolding into 17a's landscape, which is the trip a Fold makes when
     * it is opened and then turned. Two arrangements that both drop the same
     * prefix must number the catalog identically, so this pair would survive a
     * mistake in either — which is why the portrait leg above is the one that
     * carries the proof, and this one pins that they have not silently drifted
     * apart from each other.
     */
    @Test
    fun `folding and unfolding between the two split arrangements keeps the language`() {
        val catalog = catalog()
        val recents = catalog.recentRows()
        val landscape = arrangement(twoPane = true, recentCount = recents.size)
        val folded = arrangement(twoPane = false, twoLeaf = true, recentCount = recents.size)

        assertThat(folded.plan.catalogOffset).isEqualTo(landscape.plan.catalogOffset)
        assertRestoresTo("ca", from = landscape, to = folded, recents = recents, catalog = catalog)
        assertRestoresTo("ca", from = folded, to = landscape, recents = recents, catalog = catalog)
    }

    /**
     * The target side too, because its prefix is a different size: 16a's
     * "Recently used as target" header and rows sit above the alphabet where the
     * source side also has the Detect row. A fold that was only ever checked on
     * one side would pass with the other still off by one.
     */
    @Test
    fun `the target side survives a fold as well`() {
        val catalog = catalog()
        val recents = catalog.recentRows()
        val portrait = arrangement(twoPane = false, role = LanguageRole.TARGET, recentCount = recents.size)
        val folded =
            arrangement(twoPane = false, twoLeaf = true, role = LanguageRole.TARGET, recentCount = recents.size)

        assertThat(portrait.plan.catalogOffset).isGreaterThan(folded.plan.catalogOffset)
        assertRestoresTo("ca", from = portrait, to = folded, recents = recents, catalog = catalog)
        assertRestoresTo("ca", from = folded, to = portrait, recents = recents, catalog = catalog)
    }

    /**
     * A language the fold cannot restore to — one filtered out of the catalog
     * being restored INTO — lands at the top rather than at a guessed index.
     * The top is honest; an index would put the user somewhere they never were.
     *
     * **Asserted against the PORTRAIT offset, and that is not incidental.** The
     * folded arrangement's offset is 1 (just the "All languages" header), so a
     * `catalogOffset + (-1)` fallback would come out at 0 there and this test
     * would pass on the broken code — which is what the mutation run found when
     * it was written the other way round. Portrait's offset is the detect row
     * plus the recents section plus that header, so the two answers differ.
     */
    @Test
    fun `a fold into a filtered catalog restores to the top`() {
        val catalog = catalog()
        val recents = catalog.recentRows()
        val folded = arrangement(twoPane = false, twoLeaf = true, recentCount = recents.size)
        val portrait = arrangement(twoPane = false, recentCount = recents.size)
        val filtered = catalog.filter { it.id != "ca" }

        assertThat(portrait.plan.catalogOffset).isGreaterThan(1)
        assertThat(pickerAnchorIndex("ca", filtered, portrait.plan.catalogOffset)).isEqualTo(0)
        assertThat(pickerAnchorIndex("ca", filtered, folded.plan.catalogOffset)).isEqualTo(0)
    }

    // ---- helpers -------------------------------------------------------------

    /** A plan plus the two inputs [emittedKeys] needs and [PickerListPlan] does not carry. */
    private class TestArrangement(
        val plan: PickerListPlan,
        val twoPane: Boolean,
        val detect: Boolean,
    )

    private fun arrangement(
        twoPane: Boolean,
        role: LanguageRole = LanguageRole.SOURCE,
        recentCount: Int = 0,
        railed: Boolean = true,
        twoLeaf: Boolean = false,
    ): TestArrangement {
        val detect = role == LanguageRole.SOURCE
        val split = twoPane || twoLeaf
        return TestArrangement(
            plan =
                pickerListPlan(
                    role = role,
                    detectRowPresent = detect,
                    recentCount = recentCount,
                    anyVoiceMark = true,
                    wholeCatalog = railed,
                    arrangement =
                        PickerArrangement(twoPane = split, columns = 1, twoLeaf = twoLeaf),
                    libraryReady = twoLeaf,
                ),
            twoPane = split,
            detect = detect,
        )
    }

    private fun keysOf(
        arrangement: TestArrangement,
        recents: List<LanguagePickerRow>,
        catalog: List<LanguagePickerRow>,
    ) = emittedKeys(arrangement.plan, arrangement.twoPane, arrangement.detect, recents, catalog)

    private fun assertRestoresTo(
        id: String,
        from: TestArrangement,
        to: TestArrangement,
        recents: List<LanguagePickerRow>,
        catalog: List<LanguagePickerRow>,
    ) {
        val browsing = keysOf(from, recents, catalog)
        val restoredInto = keysOf(to, recents, catalog)
        // Where the user is standing, read the way the screen reads it: the key
        // of the grid's first visible item.
        val standingOn = browsing[browsing.indexOf(catalogRowKey(id))]

        val captured = PickerListPosition(pickerAnchorOf(standingOn), offset = 0)
        val seeded = pickerAnchorIndex(captured.anchorId, catalog, to.plan.catalogOffset)

        assertWithMessage("a position captured at $standingOn restored to ${restoredInto[seeded]}")
            .that(restoredInto[seeded])
            .isEqualTo(catalogRowKey(id))
    }

    private fun assertGapAndRoundTrip(
        role: LanguageRole,
        recents: List<LanguagePickerRow>,
        catalog: List<LanguagePickerRow>,
        expectedGap: Int,
    ) {
        val stacked = arrangement(twoPane = false, role = role, recentCount = recents.size)
        val split = arrangement(twoPane = true, role = role, recentCount = recents.size)

        assertWithMessage("$role with ${recents.size} recents")
            .that(stacked.plan.catalogOffset - split.plan.catalogOffset)
            .isEqualTo(expectedGap)
        assertRestoresTo("ca", from = stacked, to = split, recents = recents, catalog = catalog)
        assertRestoresTo("ca", from = split, to = stacked, recents = recents, catalog = catalog)
    }

    private fun catalog(): List<LanguagePickerRow> =
        buildPickerRows(
            languages =
                listOf(
                    Language("af", "Afrikaans", offlineAvailable = true, offlineDownloaded = false),
                    Language("sq", "Albanian", offlineAvailable = true, offlineDownloaded = false),
                    Language("ar", "Arabic", offlineAvailable = true, offlineDownloaded = false),
                    Language("bn", "Bengali", offlineAvailable = true, offlineDownloaded = false),
                    Language("ca", "Catalan", offlineAvailable = true, offlineDownloaded = false),
                    Language("hr", "Croatian", offlineAvailable = true, offlineDownloaded = false),
                    Language("da", "Danish", offlineAvailable = true, offlineDownloaded = false),
                    Language("nl", "Dutch", offlineAvailable = true, offlineDownloaded = false),
                    Language("en", "English", offlineAvailable = true, offlineDownloaded = true),
                    Language("fi", "Finnish", offlineAvailable = true, offlineDownloaded = false),
                    Language("fr", "French", offlineAvailable = true, offlineDownloaded = false),
                    Language("de", "German", offlineAvailable = true, offlineDownloaded = false),
                ),
            modelStates = emptyMap(),
            selectedId = "en",
            locale = Locale.ENGLISH,
            recents = mapOf("en" to 3L, "fr" to 2L, "de" to 1L),
        )
}

/**
 * `PickerCatalog`'s emission block, transcribed.
 *
 * `catalog_loading` and `empty_result` are deliberately absent: each is emitted
 * only when there is no catalog row to stand on — an empty catalog, or a search
 * with no results — so no position can anchor while either is up, and
 * [PickerListPlan.catalogOffset] says as much.
 */
private fun emittedKeys(
    plan: PickerListPlan,
    twoPane: Boolean,
    detect: Boolean,
    recents: List<LanguagePickerRow>,
    catalog: List<LanguagePickerRow>,
): List<String> =
    buildList {
        if (!twoPane && detect) add("detect_$DETECT_LANGUAGE_ID")
        // The 18a block sits where recents would, and is mutually exclusive with
        // them (PR-21). These arrangements carry no suggestions, so it is the
        // explainer alone — the one item `firstRunSuggestionCount = 0` counts.
        if (!twoPane && plan.firstRun) add("first_run_explainer")
        if (!twoPane && plan.recentHeader != null) {
            add("header_recent")
            recents.forEach { add(RECENT_ROW_KEY_PREFIX + it.id) }
        }
        if (plan.showAllHeader) add("header_all")
        catalog.forEach { add(catalogRowKey(it.id)) }
    }

/** The body of `PickerBody`, from its declaration to the next top-level one. */
private fun pickerBodySource(): String = sourceBetween("private fun PickerBody(", "private fun PickerSidePane(")

/** The body of `PickerCatalog`, from its declaration to the next top-level one. */
private fun pickerCatalogSource(): String {
    val checkoutRoot =
        generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
    val source = checkoutRoot.resolve(PICKER_SCREEN).readText()
    val from = source.indexOf("private fun PickerCatalog(")
    val to = source.indexOf("private fun LazyGridScope.fullSpanItem(")

    check(from in 0 until to) { "PickerCatalog is not where this test reads it from ($from..$to)" }
    return source.substring(from, to)
}

private fun sourceBetween(
    from: String,
    to: String,
): String {
    val checkoutRoot =
        generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
    val source = checkoutRoot.resolve(PICKER_SCREEN).readText()
    val start = source.indexOf(from)
    val end = source.indexOf(to)

    check(start in 0 until end) { "LanguagePickerScreen.kt no longer has $from before $to ($start..$end)" }
    return source.substring(start, end)
}

private const val PICKER_SCREEN =
    "feature/language/src/main/kotlin/com/codeboxlk/tranzlate/feature/language/LanguagePickerScreen.kt"
