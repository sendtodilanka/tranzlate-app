package com.codeboxlk.tranzlate.feature.language

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.codeboxlk.tranzlate.core.designsystem.Dimensions
import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.model.LanguageRole
import com.codeboxlk.tranzlate.core.model.OfflineModelFailure
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.codeboxlk.tranzlate.core.ui.DETECT_LANGUAGE_ID
import com.codeboxlk.tranzlate.core.ui.FoldPosture
import com.codeboxlk.tranzlate.core.ui.languageAvatarCode
import com.codeboxlk.tranzlate.core.ui.languageDisplayName
import com.codeboxlk.tranzlate.core.ui.languageEndonym
import com.codeboxlk.tranzlate.core.ui.searchNormalize
import java.text.Collator
import java.util.Locale

/** Recent is a shortcut, not a second catalog — more than a handful defeats the point. */
const val RECENT_LIMIT = 5

/**
 * Where the picker's list is standing: **which language is at the top of the
 * catalog**, and how far its row is pushed off the top edge.
 *
 * Kept OUT of the composable that owns the list for the reason #130 PR-13
 * exists: a `rememberLazyGridState()` is addressed through whichever
 * `SaveableStateHolder` is drawing the picker, so it is lost the moment the
 * picker is drawn from somewhere else. [LanguagePickerViewModel.listPosition] is
 * the home instead.
 *
 * **[anchorId] is a language, not an item index, and that is the whole point.**
 * PR-14 shipped a raw grid index here, and a raw index means nothing on its own:
 * the single-pane grid emits `[detect?] [recent header + rows?] [All languages]
 * …catalog`, while 17a moves the first two into the side pane and emits
 * `[All languages] …catalog`. The prefix differs by 1 to 7 items, so carrying the
 * same NUMBER across a rotation carried the user 1 to 7 languages away — measured
 * on `emulator-5554`: browsing at English in portrait, landscape opened at
 * Finnish. Naming the language instead makes the restore arrangement-independent
 * by construction: there is no prefix to know, at save time or at restore time,
 * and nothing to re-derive when a section appears or disappears for some other
 * reason (recents arriving late, the "All languages" header going away under a
 * search).
 *
 * null means the top — either the list has not been scrolled, or the row at the
 * top of the viewport is one of the things that stand ABOVE the catalog, all of
 * which are at the top of their own scroller in either arrangement.
 */
@Immutable
data class PickerListPosition(
    val anchorId: String? = null,
    val offset: Int = 0,
) {
    companion object {
        /** A list that has not been scrolled. */
        val Top = PickerListPosition()
    }
}

/**
 * The grid keys for the two sections that draw language rows.
 *
 * The same language legitimately appears under Recent AND under All languages, so
 * the prefix is what keeps the keys unique — duplicate keys are a hard crash. It
 * is also what [pickerAnchorOf] reads: only a CATALOG row anchors a saved
 * position, because only the catalog is drawn in both arrangements.
 */
const val CATALOG_ROW_KEY_PREFIX = "all_"

/** @see CATALOG_ROW_KEY_PREFIX */
const val RECENT_ROW_KEY_PREFIX = "rec_"

/** The grid key a catalog row for [id] is emitted under. */
fun catalogRowKey(id: String): String = CATALOG_ROW_KEY_PREFIX + id

/**
 * The language a saved position should name, read from the grid's OWN key for
 * the item at the top of the viewport.
 *
 * Read rather than re-derived on purpose. Turning `firstVisibleItemIndex` back
 * into a language would need the prefix arithmetic a second time, and a second
 * copy of that arithmetic is exactly the defect this replaces. The key is what
 * the grid itself anchors its scroll position to, so the two cannot disagree.
 *
 * Anything that is not a catalog row — a header, the detect pseudo-row, a recents
 * row, the loading or no-results placeholder — yields null, which reads as "the
 * top". All of them stand above the catalog, and in the other arrangement they are
 * either at the top of the same grid or in the side pane; either way the honest
 * restore is the top of the catalog.
 */
fun pickerAnchorOf(key: Any?): String? =
    (key as? String)
        ?.takeIf { it.startsWith(CATALOG_ROW_KEY_PREFIX) }
        ?.removePrefix(CATALOG_ROW_KEY_PREFIX)

/**
 * Which grid item has to be at the top of the viewport to put [anchorId] back
 * there — the ONE place a saved position becomes an index again.
 *
 * @param catalogRows the rows the grid is actually emitting right now (the
 *   filtered results while a search is running, the whole catalog otherwise).
 * @param catalogOffset [PickerListPlan.catalogOffset] for the arrangement being
 *   restored INTO, never the one it was captured in.
 */
fun pickerAnchorIndex(
    anchorId: String?,
    catalogRows: List<LanguagePickerRow>,
    catalogOffset: Int,
): Int {
    if (anchorId == null) return 0
    val inCatalog = catalogRows.indexOfFirst { it.id == anchorId }
    // A language that is not on the list any more (a query narrowed past it, a
    // catalog that has not arrived) has no place to restore to. The top is the
    // honest answer; guessing an index would put the user somewhere they never were.
    return if (inCatalog < 0) 0 else catalogOffset + inCatalog
}

/**
 * How the picker arranges itself in the window it was handed (17a and 17b).
 *
 * @property twoPane the side pane is drawn beside the catalog instead of
 *   scrolling above it.
 * @property columns how many language columns the catalog grid gets. Always 1 in
 *   the single-pane arrangement; 1 or 2 in two-pane, depending on what fits.
 * @property gutter the gap between the two panes — [PANE_GUTTER] on one flat
 *   surface, [Dimensions.pickerCreaseGutter] where a fold runs between them.
 * @property sidePaneWidth how wide the shortcut pane is. 17a and 17b draw it
 *   differently and the export measures both, so the gate that decided the
 *   arrangement is also what carries the number — the alternative is a second
 *   `if` at the draw site, reading a fact the gate has already established.
 * @property twoLeaf the window is FOLDED between the panes (17b), rather than
 *   merely wide enough for two of them (17a). What it changes: the gutter is the
 *   crease width, and the shortcut leaf carries the offline-library meter, which
 *   17a's 412dp-tall pane has no room for and the export draws in neither
 *   landscape frame.
 */
@Immutable
data class PickerArrangement(
    val twoPane: Boolean,
    val columns: Int,
    val gutter: Dp = PANE_GUTTER,
    val sidePaneWidth: Dp = Dimensions.pickerSidePaneWidth,
    val twoLeaf: Boolean = false,
) {
    init {
        // The one shape this type must not take, refused the way
        // `LanguageRowState.Selected` refuses a double wrap: a "leaf" layout
        // with nothing beside it would draw a crease gutter down a single
        // column and hang the meter off a pane that is not there.
        require(!twoLeaf || twoPane) { "A two-leaf arrangement is a two-pane arrangement" }
    }

    companion object {
        /** 15a / 16a as shipped: one column, everything in one scroller. */
        val SinglePane = PickerArrangement(twoPane = false, columns = 1)
    }
}

/** The gap between the side pane and the catalog on ONE flat surface (17a). */
internal val PANE_GUTTER: Dp = 8.dp

/**
 * Below this a two-pane arrangement has nowhere to put its second pane: the
 * shortcut pane + the gutter between them + one [Dimensions.pickerColumnMin]
 * column + the [Dimensions.touchTargetMin] the A–Z rail overlays on the trailing
 * edge.
 *
 * A function rather than a constant because 17a and 17b measure differently —
 * 272 + 8 = 568dp for the landscape pane, 296 + 24 = 608dp for the leaf — and a
 * single number would have to be one of the two, silently wrong for the other.
 */
private fun twoPaneMinWidth(
    sidePaneWidth: Dp,
    gutter: Dp,
): Dp = sidePaneWidth + gutter + Dimensions.pickerColumnMin + Dimensions.touchTargetMin

/**
 * Below this a window is SHORT: phone landscape rather than phone portrait or a
 * tablet.
 *
 * The number is Material's own medium height breakpoint —
 * `WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND`, which is what
 * `WindowInfo.heightCompact` is computed from — restated as a dp so this gate can
 * measure it against the same constraints it measures the width against. See
 * [pickerArrangement] for why asking one source rather than two is the fix and
 * not a shortcut.
 */
private val SHORT_WINDOW_MAX_HEIGHT: Dp = 480.dp

/**
 * Which arrangement 17a's window gets — the whole of PR-14's gate, in one pure
 * function so a JVM test can drive it. Written when this module had no Compose
 * test runtime; #186 has since added one, and CI still compiles instrumented
 * tests without running them (#40). The extraction stands on its own — a pure
 * gate is cheaper to exhaust than a rendered one.
 *
 * Three conditions, and each one is a layout this PR must NOT steal:
 *
 * - **[posture] must be [FoldPosture.FLAT].** A half-open foldable is 17b's
 *   two-leaf layout with a crease gutter, handled in its own branch above this
 *   one, and a tabletop fold puts a dead strip across the middle; neither is a
 *   side pane beside a catalog. Note this asks POSTURE, not [hinged] — a
 *   dual-screen device held fully open is FLAT and still separating, and it is
 *   17a's layout it should get, with the hinge handled where content is placed
 *   ([hinged] widens the gutter) rather than by refusing the arrangement
 *   outright.
 * - **[availableHeight] must be under [SHORT_WINDOW_MAX_HEIGHT].** A tall window
 *   is either phone portrait (15a / 16a) or a tablet, and a tablet is 17c/17d's
 *   dialog host (PR-16). Height is the axis that separates 892×412 from
 *   1280×800 — width cannot, because both are at least medium.
 * - **[availableWidth] must clear [TWO_PANE_MIN_WIDTH].** A dp sum rather than a
 *   size class, for the reason issue #99 recorded: the owner's OnePlus 7 Pro is
 *   832dp in landscape, 8dp short of the EXPANDED breakpoint, so a class-gated
 *   17a would leave a real device in the portrait layout while it has room for
 *   both panes.
 *
 * **Both sizes come from the SAME source, and that is a fix.** PR-14 took the
 * width from the picker's own constraints and the height from
 * `WindowInfo.heightCompact`, which reads Compose's window snapshot
 * (`LocalWindowInfo.containerSize`). Those two are refreshed by different things
 * and they disagree for a few frames after a rotation. Measured on
 * `emulator-5554`, four times in a 2.5-minute rotation hammer, always this shape:
 *
 * ```
 * box=914.29x411.43dp   ← the constraints: already landscape
 * container=1080x2400px ← Compose's window snapshot: still portrait
 * config=411x914 orient=PORTRAIT
 * metrics=2400x1080     ← WindowMetricsCalculator, asked in the same frame: landscape
 * ```
 *
 * A wide window that reports itself tall fails the height condition, so the
 * picker drew the portrait layout at full landscape width — the defect the PR
 * body recorded as "unexplained, not resolved". Measuring both axes against the
 * constraints of one layout pass removes the disagreement rather than papering
 * over it: two numbers from the same measurement cannot be half a rotation apart.
 *
 * @param availableWidth the width the picker's own content was handed — the
 *   `BoxWithConstraints` maximum, not the raw window, so a nav rail or a
 *   side-by-side host is already subtracted.
 * @param availableHeight the height from that same `BoxWithConstraints`. An
 *   unbounded height arrives here as a very large [Dp] and therefore reads as
 *   "not short", which is the safe side of the gate: an unmeasurable window never
 *   steals 17a's layout.
 *
 * ---
 *
 * **17b — the two-leaf branch (PR-15) — is decided first, and on a different
 * question.**
 *
 * [FoldPosture.BOOK] means the window is folded down the middle around a
 * VERTICAL crease: two leaves, side by side, with a physical ridge between them.
 * That is a reason to split regardless of how tall the window is — the export's
 * foldable frames are 760×812, three hundred dp taller than the height gate 17a
 * would fail them on — because the split is not being done to use up spare
 * width. It is being done because the device is already in two halves and
 * content that ignores that lands on the fold.
 *
 * [FoldPosture.TABLETOP] gets no split at all: its crease is HORIZONTAL, so two
 * side-by-side panes would each be cut across the middle by it, which is worse
 * than one scroller that the user can simply push past the fold.
 *
 * **[hinged] is a third, separate question and it only moves the gutter.** It
 * asks whether a hinge SEPARATES the window, which a fully-open dual-screen
 * device answers yes to while its posture is FLAT (see `WindowInfo.hinged`). Such
 * a device gets 17a's arrangement — it is flat and it is wide — but the gap
 * between its panes has a physical seam running down it, so it is drawn at the
 * crease width instead of the flat one. A [FoldPosture.BOOK] window always
 * separates by definition and does not need to ask.
 *
 * @param hinged a hinge splits this window into two logical areas
 *   (`WindowInfo.hinged`), which is NOT the same as being folded — see above.
 */
fun pickerArrangement(
    availableWidth: Dp,
    availableHeight: Dp,
    posture: FoldPosture,
    hinged: Boolean = false,
): PickerArrangement {
    if (posture == FoldPosture.BOOK) {
        return twoPaneOrSingle(
            availableWidth = availableWidth,
            sidePaneWidth = Dimensions.pickerLeafPaneWidth,
            gutter = Dimensions.pickerCreaseGutter,
            twoLeaf = true,
        )
    }
    val short = availableHeight < SHORT_WINDOW_MAX_HEIGHT
    if (posture != FoldPosture.FLAT || !short) return PickerArrangement.SinglePane
    return twoPaneOrSingle(
        availableWidth = availableWidth,
        sidePaneWidth = Dimensions.pickerSidePaneWidth,
        gutter = if (hinged) Dimensions.pickerCreaseGutter else PANE_GUTTER,
        twoLeaf = false,
    )
}

/**
 * The half of [pickerArrangement] that is the same for both arrangements: does
 * the width clear the minimum, and if it does, how many language columns are
 * left over once the pane, the gutter and the A–Z rail's touch strip are taken
 * out.
 */
private fun twoPaneOrSingle(
    availableWidth: Dp,
    sidePaneWidth: Dp,
    gutter: Dp,
    twoLeaf: Boolean,
): PickerArrangement {
    if (availableWidth < twoPaneMinWidth(sidePaneWidth, gutter)) return PickerArrangement.SinglePane
    val catalogWidth = availableWidth - sidePaneWidth - gutter - Dimensions.touchTargetMin
    return PickerArrangement(
        twoPane = true,
        columns = if (catalogWidth >= Dimensions.pickerColumnMin * 2) 2 else 1,
        gutter = gutter,
        sidePaneWidth = sidePaneWidth,
        twoLeaf = twoLeaf,
    )
}

/**
 * What a picker row IS — exactly one of six (issue #117 plan §3 matrix).
 *
 * A sealed type rather than the `selected`/`downloaded`/`downloading`/`failed`
 * booleans the design implies: those four booleans describe sixteen rows, twelve
 * of which cannot exist, and every one of the twelve would have rendered as
 * *something*. Here the impossible rows cannot be written down.
 *
 * The mapping is [rowStateOf] and every branch of it is unit-pinned.
 */
@Immutable
sealed interface LanguageRowState {
    /**
     * The choice this screen was opened to change — a WRAPPER, not a seventh
     * state (issue #130 rev.3 ruling 1, P1's graft).
     *
     * It used to replace whatever the row was, carrying a lone `onDevice`
     * boolean, and that could only ever say one of the six things the row might
     * have said. 16a settles it with a drawing: the selected Spanish row shows
     * "On device" AND the offline-voice speaker AND the tick, all at once. So
     * selection COMPOSES with the resting state instead of erasing it, and
     * every fact the row would have shown survives being chosen.
     *
     * What selection still decides alone is the trailing control: a selected row
     * shows the tick and nothing else. Pack repair lives in the offline manager
     * (D-E2), so the picker never puts a second control on the row it is about
     * to close over.
     *
     * @property inner what this row would be if it were not the current choice.
     */
    data class Selected(
        val inner: LanguageRowState,
    ) : LanguageRowState {
        init {
            // The one shape this type must not take. Nothing constructs it —
            // [rowStateOf] wraps a freshly computed resting state — and a
            // doubly-wrapped value would render as an ordinary selected row
            // while quietly hiding a whole state, so it fails loudly instead.
            require(inner !is Selected) { "Selected must wrap a resting state, not another Selected" }
        }

        /** The model is on disk, so the row keeps its on-device line. */
        val onDevice: Boolean get() = inner is Downloaded
    }

    /** Usable with the radio off. @property sizeBytes real bytes on disk, never an estimate (R3). */
    data class Downloaded(
        val sizeBytes: Long? = null,
    ) : LanguageRowState

    /**
     * A download is in flight. Indeterminate by force — `RemoteModelManager.download()`
     * returns `Task<Void>`, so there is no percentage to show (R1).
     */
    data object Downloading : LanguageRowState

    /** Offline-capable, nothing on disk yet. NO size is shown — none is knowable before the fact (R3). */
    data object Downloadable : LanguageRowState

    /** Not in ML Kit's on-device set at all: this language only ever works online (R4). */
    data object OnlineOnly : LanguageRowState

    /** The download failed and says why — EDGE_CASES forbids a bare retry that re-fails silently. */
    data class Failed(
        val cause: OfflineModelFailure,
    ) : LanguageRowState
}

/** Flags are wrong for languages (one language, many flags) — the avatar carries a code or a glyph. */
@Immutable
sealed interface LanguageAvatar {
    /** ISO-ish primary subtag, upper-cased. */
    data class Code(
        val text: String,
    ) : LanguageAvatar

    /** The "Detect language" pseudo-row's waveform glyph. */
    data object Detect : LanguageAvatar
}

/** Everything one row needs, precomputed once per data change. */
@Immutable
data class LanguagePickerRow(
    val id: String,
    val displayName: String,
    val avatar: LanguageAvatar,
    val state: LanguageRowState,
    val lastUsedAt: Long? = null,
    /**
     * This DEVICE can read the language aloud with no connection — an installed
     * TTS voice, which is a separate install from a separate source than the
     * translate pack. Device truth, copied straight from [Language.hasOfflineVoice]
     * and deliberately NOT crossed with any [state]: 17a's landscape "to" frame
     * draws the speaker on Arabic while its pack is still downloading.
     *
     * Whether the mark is DRAWN is a second question, answered by
     * [showsVoiceMark] — only a target picker shows it.
     */
    val hasOfflineVoice: Boolean = false,
    /** Folded `displayName + endonym + id`, ready for a plain `contains` scan. */
    val searchKey: String = "",
) {
    /** The letter this row files under in the A–Z rail. */
    val indexLetter: Char
        get() = displayName.firstOrNull()?.uppercaseChar() ?: '#'
}

/**
 * Does this row draw the offline-voice speaker?
 *
 * Target rows only. The spec states it as the third of 16a's "three deliberate
 * differences" — "a target row carries one property a source never needs" — and
 * draws the consequence: the `from · landscape` frame carries no speaker mark
 * anywhere, the `to · landscape` frame carries three. Speaking is what you do
 * with a RESULT, and the result is in the target language.
 *
 * Rev 5 also makes this the whole of the story. An earlier revision drew the
 * mark on every row and explained the empty ones in a "no offline voice" sheet
 * (19j); that sheet is cut, because a mark drawn where there is no voice is a
 * dead affordance (ruling §7.6) and rev 5 removes the dead case instead of
 * captioning it. A language with no voice simply carries no mark, and the
 * absence is reported where it costs something — the Speak action on the result
 * screen (`text_tts_unavailable`, issue #159).
 */
fun LanguagePickerRow.showsVoiceMark(role: LanguageRole): Boolean = role == LanguageRole.TARGET && hasOfflineVoice

/**
 * The one place a [Language] plus its live model state becomes a row state.
 *
 * The RESTING state is decided first, from five mutually exclusive facts, and
 * selection is then wrapped around whatever that turned out to be. Precedence
 * inside the resting set, and why:
 * 1. **Failed** / **Downloading** — transient, actionable, and rarer than
 *    everything below them; they must not be masked by the resting state.
 * 2. **Downloaded** — [Language.offlineDownloaded] is already the live overlay
 *    `LanguageRepositoryImpl` applies, so it is trusted even before the raw
 *    state map arrives.
 * 3. **OnlineOnly** — a COMPILE-TIME fact ([Language.offlineAvailable]), never
 *    inferred from an absent map entry. That distinction is what stops the first
 *    frame from labelling 194 rows "Online only" and contradicting itself a
 *    moment later.
 * 4. **Downloadable** — capable, nothing on disk. Also where `Deleting` lands:
 *    the picker has no delete control of its own, the model is on its way out,
 *    and "not on device" is the true statement about it. Calling it
 *    "Downloading" would be a false one.
 *
 * Selection is deliberately NOT a sixth branch of that `when` any more. As one
 * it consumed the row — a selected language that was mid-download rendered
 * exactly like a selected language that was online only. 16a draws the opposite:
 * the selected row states its pack, its voice and its tick together. See
 * [LanguageRowState.Selected].
 */
fun rowStateOf(
    language: Language,
    modelState: OfflineModelState?,
    selected: Boolean,
    sizeBytes: Long? = null,
): LanguageRowState {
    val resting =
        when {
            modelState is OfflineModelState.Failed -> LanguageRowState.Failed(modelState.cause)
            modelState == OfflineModelState.Downloading -> LanguageRowState.Downloading
            language.offlineDownloaded -> LanguageRowState.Downloaded(sizeBytes = sizeBytes)
            !language.offlineAvailable -> LanguageRowState.OnlineOnly
            else -> LanguageRowState.Downloadable
        }
    return if (selected) LanguageRowState.Selected(resting) else resting
}

/**
 * Builds the picker's rows: localized name, avatar code, row state, search
 * haystack — then sorts with a locale-aware [Collator], because
 * `sortedBy(String)` orders by UTF-16 code unit and would file "Ålandic" and
 * "Österreichisch" after "Zulu" in every European locale.
 *
 * @param sizes measured on-disk bytes per tag. Empty today (see [LanguageRowState.Downloaded]);
 *   a row simply omits the number rather than guessing one.
 * @param recents when each id was last chosen FOR THE SIDE THIS PICKER IS
 *   CHOOSING (`LanguageRepository.recentSelections`). It is the whole source of
 *   [LanguagePickerRow.lastUsedAt] — there is deliberately no fallback to
 *   [Language.lastUsedAt], which carries the merged source-and-target overlay
 *   and would file a source-only pick under 16a's "Recently used as target".
 */
fun buildPickerRows(
    languages: List<Language>,
    modelStates: Map<String, OfflineModelState>,
    selectedId: String,
    locale: Locale,
    sizes: Map<String, Long> = emptyMap(),
    recents: Map<String, Long> = emptyMap(),
): List<LanguagePickerRow> {
    val collator = Collator.getInstance(locale)
    return languages
        .map { language ->
            val displayName = languageDisplayName(language.id, locale, fallback = language.name)
            val endonym = languageEndonym(language.id, displayName)
            LanguagePickerRow(
                id = language.id,
                displayName = displayName,
                avatar = LanguageAvatar.Code(languageAvatarCode(language.id)),
                state =
                    rowStateOf(
                        language = language,
                        modelState = modelStates[language.id],
                        selected = language.id == selectedId,
                        sizeBytes = sizes[language.id],
                    ),
                lastUsedAt = recents[language.id],
                // Copied, never crossed with the pack state: a voice and a pack
                // are separate installs and either can be present alone.
                hasOfflineVoice = language.hasOfflineVoice,
                searchKey = searchNormalize("$displayName ${endonym.orEmpty()} ${language.id}"),
            )
        }.sortedWith { left, right -> collator.compare(left.displayName, right.displayName) }
}

/**
 * The "Detect language" pseudo-row (spec 02 §4.5). It is not a catalog entry:
 * detection is a server-side call in every engine we ship, so its state is
 * [LanguageRowState.OnlineOnly] — the same chip the design puts on it — unless
 * it is the current source choice.
 */
fun detectRow(
    label: String,
    selected: Boolean,
): LanguagePickerRow =
    LanguagePickerRow(
        id = DETECT_LANGUAGE_ID,
        displayName = label,
        avatar = LanguageAvatar.Detect,
        state =
            if (selected) {
                LanguageRowState.Selected(LanguageRowState.OnlineOnly)
            } else {
                LanguageRowState.OnlineOnly
            },
        searchKey = searchNormalize("$label $DETECT_LANGUAGE_ID"),
    )

/** Case- and diacritic-insensitive filter over the precomputed haystacks. */
fun List<LanguagePickerRow>.matching(normalizedQuery: String): List<LanguagePickerRow> =
    if (normalizedQuery.isEmpty()) this else filter { it.searchKey.contains(normalizedQuery) }

/**
 * Most-recent-first shortcut list. The current choice is INCLUDED — the design
 * shows it sitting at the top of Recent with its tick, which is also what a user
 * scanning for "the one I had" expects to find there.
 */
fun List<LanguagePickerRow>.recentRows(limit: Int = RECENT_LIMIT): List<LanguagePickerRow> =
    filter { it.lastUsedAt != null }
        .sortedByDescending { it.lastUsedAt }
        .take(limit)

/**
 * Counter arithmetic (plan §4). The denominator is the OFFLINE-CAPABLE count —
 * 59 of our 194 — because "12 of 194 on device" would tell the user the other
 * 182 are downloadable, and they are not.
 */
@Immutable
data class OnDeviceCount(
    val downloaded: Int,
    val capable: Int,
)

fun onDeviceCount(languages: List<Language>): OnDeviceCount =
    OnDeviceCount(
        downloaded = languages.count { it.offlineAvailable && it.offlineDownloaded },
        capable = languages.count { it.offlineAvailable },
    )

/** Which words head the recents section — or that it is not emitted at all. */
enum class RecentHeader {
    /** 15a: "Recent", role-neutral because the section is served the merged view. */
    GENERIC,

    /** 16a: "Recently used as target" — true of every row under it, or absent. */
    TARGET,
}

/**
 * What the picker's list emits above the alphabet, decided in one pure place so
 * a plain unit test can read it. When this was written the module had no Compose
 * test runtime at all, so a decision left inside the composable was a decision no
 * test could reach; #186 has since added one (`tranzlate.compose-test`). The
 * extraction still earns its place — "recents empty → the section is ABSENT" is a
 * claim about something that is NOT on screen, which a pure function states
 * directly and a rendered tree can only imply.
 *
 * @property showVoiceLegend the `volume_up` explainer, drawn ABOVE the
 *   `LazyColumn` and never inside it — see [pickerListPlan] for why that
 *   placement is load-bearing rather than cosmetic.
 * @property recentHeader null when the recents section is not emitted at all.
 * @property catalogOffset index of the first CATALOG row inside the grid — how
 *   many items the grid emits before the alphabet starts. Two things read it and
 *   they are the same number: a rail letter scrolls to `catalogOffset + n`, and
 *   [pickerAnchorIndex] restores a saved language to `catalogOffset + n`. The
 *   legend is not one of those items, so it is not counted here.
 *
 *   It is exact whenever a catalog row exists at all. The two items it does not
 *   count — the loading placeholder and the no-results message — are emitted only
 *   when the catalog or the filtered results are EMPTY, so in both cases there is
 *   no row for either reader to land on.
 */
@Immutable
data class PickerListPlan(
    val showVoiceLegend: Boolean,
    val recentHeader: RecentHeader?,
    val showAllHeader: Boolean,
    val catalogOffset: Int,
    /**
     * 17a's side pane is drawn. False in the single-pane arrangement, and false
     * in two-pane when the pane would have nothing in it — see [pickerListPlan].
     */
    val sidePane: Boolean = false,
    /**
     * The on-device counter sits in the top bar rather than beside the
     * "All languages" header. 17a moves it there because in two-pane that header
     * heads only the catalog pane, while the counter is a statement about the
     * whole screen.
     */
    val counterInTopBar: Boolean = false,
    /**
     * The offline-library meter card sits at the foot of the shortcut leaf
     * (17b, U-5). 17a does not draw it and the export shows why: at 412dp of
     * window height its pane is already full, and the card would push the
     * recents section — the reason the pane exists — off the bottom.
     */
    val showMeter: Boolean = false,
)

/**
 * The 16a/15a list plan.
 *
 * Three rules worth stating, because each is a thing the screen must NOT do:
 *
 * - **An empty recents section is absent, not empty.** No header over nothing,
 *   no "you have no recents yet" — the 18a first-run pattern, where Recent is
 *   simply not there. A header with no rows is furniture that reports a
 *   failure the user did not have.
 * - **The legend is drawn only where something carries the mark.** It explains
 *   the speaker; on a device with no installed offline voices at all — E-V1's
 *   AOSP-with-no-Google-TTS case, which resolves to the empty set — nothing on
 *   screen would carry one, and the explainer would describe an absence. That
 *   is the same dead-affordance rule (§7.6) rev 5 applied to the mark itself,
 *   one level up.
 * - **The offset counts everything above the alphabet.** Every header and
 *   pseudo-row emitted before the alphabet is a real item in the same list, so
 *   leaving one out of [PickerListPlan.catalogOffset] makes every rail letter —
 *   and every restored position — land a row short. Deterministic, silent, and
 *   invisible to any test that only looks at rows.
 * - **The legend is NOT one of those items, and that is a fix, not a detail.**
 *   The device's voice answer arrives after the list has been laid out — binding
 *   `TextToSpeech` is documented at up to 5000ms
 *   (`AndroidOfflineVoiceCatalog.INIT_TIMEOUT_MS`), and the picker paints long
 *   before that. While the legend lived inside the `LazyColumn`, its arrival
 *   INSERTED an item at index 0 of a list whose scroll position was already
 *   anchored to a key: `LazyListState` re-points the anchor at whatever item
 *   still carries the key it was showing, so the new item was laid out just
 *   ABOVE the viewport and was never seen. Measured on `Tranzlate_Resizable`:
 *   `totalItemsCount` 197 → 198 while `firstVisibleItemIndex` went 0 → 1 with
 *   the same first visible key, and `VoiceLegend` first composed 64.8s later,
 *   when the list was scrolled back to the top by hand. That is documented
 *   `LazyListState` behaviour — it is what stops a list jumping when content
 *   loads above it — so the answer is not to fight it but to keep the legend out
 *   of the anchored item set entirely. Drawn above the `LazyColumn` it occupies
 *   the same place in the 16a frame, appears the moment the device answers, and
 *   [catalogOffset] never has to know it exists.
 *
 * **17a moves three of those things out of the list entirely**, and that is the
 * whole of [twoPane]'s effect here. The detect row, the recents section and the
 * legend go into the side pane, which is not the catalog's scroller — so
 * [catalogOffset] must stop counting them, or every rail letter lands as many rows
 * short as the side pane happens to hold. It is the same class of silent,
 * deterministic miss the third rule above describes, arriving from the opposite
 * direction: there, an uncounted item; here, items counted that are no longer
 * there. **And it is why a saved position may not be an item index** — the two
 * arrangements number the same list differently, which is what
 * [PickerListPosition] now names a language to avoid. A bonus the arrangement
 * gets for free: nothing can be inserted at index
 * 0 of the anchored catalog any more, because the two things that arrive late —
 * the device's voice answer and the recents store — both land in the side pane.
 *
 * @param detectRowPresent the source-only "Detect language" pseudo-row.
 * @param anyVoiceMark at least one row would draw the speaker ([showsVoiceMark]).
 * @param railed the A–Z rail is up: a full, unfiltered, non-empty catalog.
 * @param arrangement what [pickerArrangement] decided this window is. Two things
 *   are read from it and they are NOT the same question:
 *   [PickerArrangement.twoPane] — the shortcuts moved into a pane, true of both
 *   17a and 17b, and the only thing [PickerListPlan.catalogOffset] cares about;
 *   and [PickerArrangement.twoLeaf] — the window is folded, which decides the
 *   meter and nothing else. Passed whole rather than as two booleans, for the
 *   reason the gutter is: the gate has already established which layout this is,
 *   and a caller unpacking it into flags is a second place that can disagree.
 * @param libraryReady the storage snapshot has arrived
 *   ([OfflineLibraryMeter] is non-null). Asked HERE rather than at the draw
 *   site, because it is the same question [PickerListPlan.sidePane] has to
 *   answer: while the disk is still being read the meter is not a tenant of the
 *   leaf yet, and a search that has cleared the recents would leave the pane
 *   genuinely empty. A `showMeter && library != null` test where the card is
 *   drawn would be a second source for one fact, and `sidePane` would be reading
 *   the wrong one.
 */
fun pickerListPlan(
    role: LanguageRole,
    detectRowPresent: Boolean,
    recentCount: Int,
    anyVoiceMark: Boolean,
    railed: Boolean,
    arrangement: PickerArrangement = PickerArrangement.SinglePane,
    libraryReady: Boolean = false,
): PickerListPlan {
    val twoPane = arrangement.twoPane
    val twoLeaf = arrangement.twoLeaf
    val showVoiceLegend = role == LanguageRole.TARGET && anyVoiceMark
    val recentHeader =
        when {
            recentCount == 0 -> null
            role == LanguageRole.TARGET -> RecentHeader.TARGET
            else -> RecentHeader.GENERIC
        }
    // Emission order, and therefore counting order: detect row · recents
    // (header + rows) · "All languages" header · the alphabet. The legend is
    // deliberately absent from both — it is not an item of this list. In
    // two-pane the first two move to the side pane and stop being counted; only
    // the "All languages" header is left above the alphabet.
    val aboveTheAlphabet =
        if (twoPane) {
            0
        } else {
            (if (detectRowPresent) 1 else 0) + (if (recentHeader == null) 0 else recentCount + 1)
        }
    // Once the disk has answered, the meter is a permanent tenant of the leaf,
    // so 17b's pane is never empty — which is why it is one of the terms below
    // rather than an afterthought. Before it answers there is nothing to draw,
    // and the pane has to be able to go.
    val showMeter = twoLeaf && libraryReady
    // A side pane with nothing in it is 272dp of empty surface next to the
    // results — reachable the moment a search clears the recents on the source
    // side. EDGE_CASES' no-dead-end rule cuts the same way for furniture as it
    // does for errors: the pane goes, and the catalog takes the width back.
    val sidePane = twoPane && (recentHeader != null || detectRowPresent || showVoiceLegend || showMeter)
    return PickerListPlan(
        showVoiceLegend = showVoiceLegend,
        recentHeader = recentHeader,
        showAllHeader = railed,
        catalogOffset = aboveTheAlphabet + (if (railed) 1 else 0),
        sidePane = sidePane,
        counterInTopBar = twoPane,
        showMeter = showMeter,
    )
}

// ---- U-5: the offline-library meter (17b) -------------------------------

/**
 * What the "Offline library" card is entitled to SAY — a closed set of three,
 * because the interesting part of this feature is the two things it must refuse
 * to claim.
 *
 * The card states a count and, when it can, a size. The count comes from the
 * catalogue and is always knowable. The size comes from walking ML Kit's model
 * store on disk ([com.codeboxlk.tranzlate.core.common.StorageProbe.packsBytes]),
 * and that walk can fail to find the store at all — ML Kit has never promised
 * the directory name, and this project only knows it because issue #90's
 * research measured it. So:
 *
 * - **`null` bytes may never render as `0 MB`.** Zero is a factual claim ("the
 *   packs occupy no space") that a missing directory cannot support. That is
 *   risk R8 in the ruling's register, and [Unsized] is the honest answer to it:
 *   say how much room is left instead, which is the number the user would have
 *   wanted the size for.
 * - **A count of zero outranks both.** With no packs there is nothing for a size
 *   to be the size OF, so "nothing downloaded" is the sentence even when the
 *   store is there and readable — a device that downloaded a pack and deleted it
 *   again keeps the directory.
 *
 * **[Empty] is not, however, what a first run looks like** — the thing this PR
 * first got wrong and co-verify measured (E-S1b, `pm clear` on `emulator-5554`,
 * 2026-08-02). The store directory really is absent on a fresh install, but the
 * COUNT is **1**, not 0: ML Kit reports the English pivot as on device before
 * anything has been downloaded and while this app's store does not exist. The
 * first card a new user sees is therefore [Unsized] — "1 of 59 packs · 8.6 GB
 * free" — and the export's `from · foldable first run` frame, which draws
 * "0 · nothing downloaded", is not what the device does. [Empty] stays because
 * it is the truthful card for a zero count wherever one occurs (no Play
 * Services, the fake flavour, a build with no ML Kit), and because the
 * alternative — letting a zero count print a size line — is the false one.
 */
@Immutable
sealed interface OfflineLibraryMeter {
    /** Packs on this device. */
    val downloaded: Int

    /** Languages that COULD be downloaded — 59 of our 194, the counter's denominator (C-11). */
    val capable: Int

    /** How full the bar is drawn, 0..1. */
    val fraction: Float

    /**
     * No packs at all. The bar is an empty track, not a zero-width fill.
     *
     * NOT the ordinary first-run card on hardware with Play Services — see the
     * interface KDoc: ML Kit counts the English pivot from the first launch, so
     * a real `pm clear` lands on [Unsized], and this state is what a build or a
     * device that reports no models at all gets.
     */
    data class Empty(
        override val capable: Int,
    ) : OfflineLibraryMeter {
        override val downloaded: Int get() = 0
        override val fraction: Float get() = 0f
    }

    /**
     * Packs on disk and the walk found them.
     *
     * @property usedBytes the measured sum, never an estimate (R3).
     * @property volumeBytes the whole volume the store sits on — the same disk
     *   [usedBytes] was measured from, so the bar is one fraction of one thing.
     */
    data class Sized(
        override val downloaded: Int,
        override val capable: Int,
        val usedBytes: Long,
        val volumeBytes: Long,
    ) : OfflineLibraryMeter {
        /**
         * Deliberately NOT floored at a visible minimum. On the E-S1 device one
         * pack was 42.1 MB of a 9.7 GB volume — 0.4%, which draws as very nearly
         * nothing, and that is the true statement. A floor would draw a bar
         * bigger than the fact it reports, and the count beside it already says
         * the packs are there.
         */
        override val fraction: Float
            get() = if (volumeBytes <= 0L) 0f else (usedBytes.toFloat() / volumeBytes).coerceIn(0f, 1f)
    }

    /**
     * Packs are counted but their size is unknown — the store directory is
     * absent, renamed, or empty. Free space is reported instead and the bar has
     * no fill, because a fraction of the disk is exactly what is not known.
     *
     * **This is the ordinary card, not only the broken one.** It was written as
     * the R8 degrade and is still the answer when ML Kit moves the store, but
     * E-S1b measured it as the state a first run arrives in as well: the pivot
     * makes the count 1 while nothing has been written to disk, so there is no
     * size to report and free space is the honest — and the more useful —
     * second clause. Nothing here distinguishes the two, and nothing can: the
     * walk sees an absent directory either way. That is why the card says only
     * what both readings support.
     */
    data class Unsized(
        override val downloaded: Int,
        override val capable: Int,
        val freeBytes: Long,
    ) : OfflineLibraryMeter {
        override val fraction: Float get() = 0f
    }
}

/**
 * The one place a storage snapshot plus a pack count becomes a meter (U-5).
 *
 * Precedence, and why each step is above the next:
 * 1. **No packs → [OfflineLibraryMeter.Empty].** The COUNT decides this, never
 *    the bytes. A store that survived the packs being deleted can still be
 *    walked and can still sum above zero, and "12 MB used" beside a count of
 *    nought is a size for something the user does not have. The count comes from
 *    the catalogue and needs no disk, so it is also the one number that is
 *    always available.
 * 2. **Packs, and a byte answer above zero → [OfflineLibraryMeter.Sized].**
 * 3. **Everything else → [OfflineLibraryMeter.Unsized].** `null` (no directory
 *    where issue #90's research measured one) and `0` while packs are counted
 *    (a directory that is no longer the store) both mean the size is not
 *    knowable, and neither supports "your packs take up no space".
 *
 * **What this order does NOT do, corrected after co-verify (E-S1b).** PR-15
 * originally justified step 1 as protecting the commonest state in the app —
 * the first run — from being degraded to a free-space number. A real `pm clear`
 * on `emulator-5554` disproved that: ML Kit reports the English pivot as on
 * device from the first launch, so `downloaded` is 1 rather than 0 and a first
 * run reaches step 3 whatever step 1 does. The order is kept for the reason
 * above, which is about a count of nought and holds on its own; it is not a
 * first-run protection and no longer claims to be. `Empty` is close to
 * unreachable on a Play-Services device, and that is a fact about ML Kit rather
 * than a branch to delete — the alternative to it is a size line under a zero.
 */
fun offlineLibraryMeter(
    downloaded: Int,
    capable: Int,
    packsBytes: Long?,
    volumeBytes: Long,
    freeBytes: Long,
): OfflineLibraryMeter =
    when {
        downloaded <= 0 -> {
            OfflineLibraryMeter.Empty(capable = capable)
        }

        packsBytes != null && packsBytes > 0L -> {
            OfflineLibraryMeter.Sized(
                downloaded = downloaded,
                capable = capable,
                usedBytes = packsBytes,
                volumeBytes = volumeBytes,
            )
        }

        else -> {
            OfflineLibraryMeter.Unsized(downloaded = downloaded, capable = capable, freeBytes = freeBytes)
        }
    }

/**
 * How short a picker row is allowed to be.
 *
 * Pulled out of the row composable for the same reason [pickerListPlan] was
 * pulled out of the list composable: when this was written the module had no
 * Compose test runtime, so a decision left inside a `@Composable` was a decision
 * no test could reach. A co-verify lens proved that literally — it deleted the
 * `!voiceMark` half of this condition and the whole module's unit tests stayed
 * BUILD SUCCESSFUL with zero failures.
 *
 * #186 added the runtime, and rendering the row corrected the story: the
 * voice-but-no-pack row measures 67dp, so the 60dp this function returns for it is
 * a floor it never reaches. `heightIn` is a minimum and cannot shrink a row whose
 * content is larger, so the deletion above does not in fact clip the mark — the
 * contract below is right, the harm attributed to breaking it was not. Measured in
 * `PickerRowRenderTest`.
 *
 * The rule: the mark is drawn on the SUPPORTING line, so any row that carries a
 * mark needs the two-line box even when it has no supporting words. The
 * voice-but-no-pack row — 17a's Arabic, and every `Downloadable`/`OnlineOnly`
 * language this device happens to have a voice for — is exactly that case, and
 * a 56dp single-line box would clip the mark away.
 *
 * @param hasSupportingText the row has state words to show ("On device",
 *   "Downloading…", a failure reason). `Downloadable` and `OnlineOnly` have none.
 * @param voiceMark the row draws the offline-voice speaker ([showsVoiceMark]).
 */
fun pickerRowMinHeight(
    hasSupportingText: Boolean,
    voiceMark: Boolean,
): Dp =
    if (!hasSupportingText && !voiceMark) {
        Dimensions.pickerRowHeight
    } else {
        Dimensions.pickerRowHeightTall
    }

/** First index per rail letter, so a rail tap can scroll straight to it. */
fun List<LanguagePickerRow>.letterIndex(offset: Int): Map<Char, Int> =
    withIndex()
        .groupBy { (_, row) -> row.indexLetter }
        .mapValues { (_, entries) -> entries.first().index + offset }
