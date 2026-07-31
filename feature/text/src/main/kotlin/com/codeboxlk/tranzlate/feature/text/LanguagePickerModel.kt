package com.codeboxlk.tranzlate.feature.text

import androidx.compose.runtime.Immutable
import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.model.OfflineModelFailure
import com.codeboxlk.tranzlate.core.model.OfflineModelState
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
     * The choice this screen was opened to change. Wins over every other state:
     * "which one is mine" is the screen's primary question, and a language whose
     * model is mid-flight or failed is still perfectly usable online, so hiding
     * that detail here is not a dead end (model repair lives in the offline
     * manager — D-E2).
     *
     * @property onDevice the model is downloaded, so the row keeps its on-device line.
     * @property sizeBytes measured on-disk size, or null when we cannot measure it (R3).
     */
    data class Selected(
        val onDevice: Boolean,
        val sizeBytes: Long? = null,
    ) : LanguageRowState

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
    /** Folded `displayName + endonym + id`, ready for a plain `contains` scan. */
    val searchKey: String = "",
) {
    /** The letter this row files under in the A–Z rail. */
    val indexLetter: Char
        get() = displayName.firstOrNull()?.uppercaseChar() ?: '#'
}

/**
 * The one place a [Language] plus its live model state becomes a row state.
 *
 * Precedence, and why:
 * 1. **Selected** — see [LanguageRowState.Selected].
 * 2. **Failed** / **Downloading** — transient, actionable, and rarer than
 *    everything below them; they must not be masked by the resting state.
 * 3. **Downloaded** — [Language.offlineDownloaded] is already the live overlay
 *    `LanguageRepositoryImpl` applies, so it is trusted even before the raw
 *    state map arrives.
 * 4. **OnlineOnly** — a COMPILE-TIME fact ([Language.offlineAvailable]), never
 *    inferred from an absent map entry. That distinction is what stops the first
 *    frame from labelling 194 rows "Online only" and contradicting itself a
 *    moment later.
 * 5. **Downloadable** — capable, nothing on disk. Also where `Deleting` lands:
 *    the picker has no delete control of its own, the model is on its way out,
 *    and "not on device" is the true statement about it. Calling it
 *    "Downloading" would be a false one.
 */
fun rowStateOf(
    language: Language,
    modelState: OfflineModelState?,
    selected: Boolean,
    sizeBytes: Long? = null,
): LanguageRowState =
    when {
        selected -> LanguageRowState.Selected(onDevice = language.offlineDownloaded, sizeBytes = sizeBytes)
        modelState is OfflineModelState.Failed -> LanguageRowState.Failed(modelState.cause)
        modelState == OfflineModelState.Downloading -> LanguageRowState.Downloading
        language.offlineDownloaded -> LanguageRowState.Downloaded(sizeBytes = sizeBytes)
        !language.offlineAvailable -> LanguageRowState.OnlineOnly
        else -> LanguageRowState.Downloadable
    }

/**
 * Builds the picker's rows: localized name, avatar code, row state, search
 * haystack — then sorts with a locale-aware [Collator], because
 * `sortedBy(String)` orders by UTF-16 code unit and would file "Ålandic" and
 * "Österreichisch" after "Zulu" in every European locale.
 *
 * @param sizes measured on-disk bytes per tag. Empty today (see [LanguageRowState.Downloaded]);
 *   a row simply omits the number rather than guessing one.
 */
fun buildPickerRows(
    languages: List<Language>,
    modelStates: Map<String, OfflineModelState>,
    selectedId: String,
    locale: Locale,
    sizes: Map<String, Long> = emptyMap(),
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
                lastUsedAt = language.lastUsedAt,
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
                LanguageRowState.Selected(onDevice = false)
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

/** First index per rail letter, so a rail tap can scroll straight to it. */
fun List<LanguagePickerRow>.letterIndex(offset: Int): Map<Char, Int> =
    withIndex()
        .groupBy { (_, row) -> row.indexLetter }
        .mapValues { (_, entries) -> entries.first().index + offset }
