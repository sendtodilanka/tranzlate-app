package com.codeboxlk.tranzlate.feature.language

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import com.codeboxlk.tranzlate.core.designsystem.Dimensions
import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.model.LanguageRole
import com.codeboxlk.tranzlate.core.model.OfflineModelFailure
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.codeboxlk.tranzlate.core.ui.DETECT_LANGUAGE_ID
import com.codeboxlk.tranzlate.core.ui.languageAvatarCode
import com.codeboxlk.tranzlate.core.ui.languageDisplayName
import com.codeboxlk.tranzlate.core.ui.languageEndonym
import com.codeboxlk.tranzlate.core.ui.searchNormalize
import java.text.Collator
import java.util.Locale

/** Recent is a shortcut, not a second catalog — more than a handful defeats the point. */
const val RECENT_LIMIT = 5

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
 * a plain unit test can read it. This module has no Robolectric and no Compose
 * test rule, so a decision left inside the composable is a decision no test can
 * reach — and "recents empty → the section is ABSENT" is precisely a claim about
 * something that is not on screen.
 *
 * @property showVoiceLegend the `volume_up` explainer, drawn ABOVE the
 *   `LazyColumn` and never inside it — see [pickerListPlan] for why that
 *   placement is load-bearing rather than cosmetic.
 * @property recentHeader null when the recents section is not emitted at all.
 * @property railOffset index of the first alphabetical row inside the same
 *   `LazyColumn`, which is what a rail letter scrolls to. The legend is not one
 *   of those items, so it is not counted here.
 */
@Immutable
data class PickerListPlan(
    val showVoiceLegend: Boolean,
    val recentHeader: RecentHeader?,
    val showAllHeader: Boolean,
    val railOffset: Int,
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
 * - **The rail counts everything above the alphabet.** Every header and
 *   pseudo-row emitted before the alphabet is a real item in the same list, so
 *   leaving one out of [PickerListPlan.railOffset] makes every letter land a row
 *   short — deterministic, silent, and invisible to any test that only looks at
 *   rows.
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
 *   [railOffset] never has to know it exists.
 *
 * @param detectRowPresent the source-only "Detect language" pseudo-row.
 * @param anyVoiceMark at least one row would draw the speaker ([showsVoiceMark]).
 * @param railed the A–Z rail is up: a full, unfiltered, non-empty catalog.
 */
fun pickerListPlan(
    role: LanguageRole,
    detectRowPresent: Boolean,
    recentCount: Int,
    anyVoiceMark: Boolean,
    railed: Boolean,
): PickerListPlan {
    val showVoiceLegend = role == LanguageRole.TARGET && anyVoiceMark
    val recentHeader =
        when {
            recentCount == 0 -> null
            role == LanguageRole.TARGET -> RecentHeader.TARGET
            else -> RecentHeader.GENERIC
        }
    // Emission order, and therefore counting order: detect row · recents
    // (header + rows) · "All languages" header · the alphabet. The legend is
    // deliberately absent from both — it is not an item of this list.
    val railOffset =
        (if (detectRowPresent) 1 else 0) +
            (if (recentHeader == null) 0 else recentCount + 1) +
            (if (railed) 1 else 0)
    return PickerListPlan(
        showVoiceLegend = showVoiceLegend,
        recentHeader = recentHeader,
        showAllHeader = railed,
        railOffset = railOffset,
    )
}

/**
 * How short a picker row is allowed to be.
 *
 * Pulled out of the row composable for the same reason [pickerListPlan] was
 * pulled out of the list composable: this module has no Robolectric and no
 * Compose test rule, so a decision left inside a `@Composable` is a decision no
 * test can reach. A co-verify lens proved that literally — it deleted the
 * `!voiceMark` half of this condition, the whole module's unit tests stayed
 * BUILD SUCCESSFUL with zero failures, and the row it broke is the one the
 * comment says must not break.
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
