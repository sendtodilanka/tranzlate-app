package com.codeboxlk.tranzlate.feature.language

import androidx.compose.runtime.Immutable
import com.codeboxlk.tranzlate.core.model.LanguageTagResolver
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.codeboxlk.tranzlate.core.ui.languageAvatarCode
import java.util.Locale

/*
 * The pure heart of Manage packs (20b/20f · #130 PR-23). Every decision the
 * screen makes about WHAT to say is taken here, away from Compose, so a plain
 * unit test can drive the whole matrix — the module still has only a thin Compose
 * test runtime (#186), and the rev.3 ruling's fourth cause is a decision left
 * inside a `@Composable` that no test can reach.
 *
 * The governing rule of this whole feature (designer brief): the screen may not
 * state anything the app cannot know. Two places that bites here:
 * - a pack with no recorded translation-use has no date to show and is not stale
 *   — it says "no recorded use yet", never a fabricated month (ruling ⑧, brief
 *   §7b, risk R6);
 * - per-pack sizes do not exist, so a row says "On device" and the storage card
 *   is AGGREGATE only (brief §2/§3).
 */

/** How many days count as a week / a month for the relative-use buckets below. */
private const val DAYS_PER_WEEK = 7L
private const val DAYS_PER_MONTH = 30L

/** Milliseconds in a day — the unit the usage store's stamps are compared in. */
internal const val DAY_MILLIS: Long = 24L * 60L * 60L * 1000L

/**
 * How long a pack must sit unused before the hygiene nudge counts it (#130 PR-23).
 *
 * **90 days, decided rather than drawn.** The spec illustrates staleness as "not
 * used since April" / "3 months ago" — an example date, not a threshold. ML Kit's
 * own guidance is only *"avoid keeping too many models"*, with no number
 * (`DESIGNER-BRIEF.md` §6.1). 90 days is the conservative reading of "clearly not
 * in active use": a full quarter with no translation into or out of the language,
 * long enough that a monthly-cadence user is never nudged about a pack they still
 * reach for. It is an ENGINEERING choice with no owner ruling on it, so it lives
 * as a named constant with a boundary test (`ManagePacksModelTest`) rather than
 * buried in a `>=` — a change to it is then a decision, not a silent edit.
 *
 * A pack with no recorded use is **not** stale by this rule: staleness needs a
 * date to measure, and a pack nobody has translated with yet is not one the nudge
 * should push toward deletion (ruling ⑧).
 */
internal const val STALE_THRESHOLD_DAYS: Long = 90L

/**
 * How long ago a pack was last PROVEN in use (a translation success, #122), as
 * the row supporting line reads it.
 *
 * Bucketed elapsed time, never a named calendar month: "since April" is
 * unambiguous only within a year, and the store keeps stamps indefinitely, so a
 * month name with no year is exactly the kind of half-true figure this brief
 * exists to keep off the screen. Elapsed days from a stamp the app actually holds
 * is a figure it can always state correctly, in every locale — the same relative
 * form the i18n guide's `minutes_ago` plural endorses.
 */
@Immutable
sealed interface PackUsage {
    /** No translation-success stamp — the honest "we do not know" (ruling ⑧). */
    data object NoRecord : PackUsage

    /** Used within the last day. */
    data object Today : PackUsage

    /** 1..6 days ago. */
    data class DaysAgo(
        val days: Int,
    ) : PackUsage

    /** 1..4 weeks ago. */
    data class WeeksAgo(
        val weeks: Int,
    ) : PackUsage

    /** A month or more ago — the stale zone the nudge watches. */
    data class MonthsAgo(
        val months: Int,
    ) : PackUsage
}

/**
 * A last-used stamp (or its absence) → the relative bucket the row draws.
 *
 * [lastUsedMillis] is `null` for a pack with no recorded use, and a future or
 * skewed stamp (elapsed <= 0) reads as [PackUsage.Today] rather than a negative
 * count. Pure and now-in-the-signature so a test fixes "now" and the boundaries
 * are checkable without a clock.
 */
fun packUsage(
    lastUsedMillis: Long?,
    nowMillis: Long,
): PackUsage {
    if (lastUsedMillis == null) return PackUsage.NoRecord
    val elapsedDays = (nowMillis - lastUsedMillis) / DAY_MILLIS
    return when {
        elapsedDays <= 0L -> PackUsage.Today
        elapsedDays < DAYS_PER_WEEK -> PackUsage.DaysAgo(elapsedDays.toInt())
        elapsedDays < DAYS_PER_MONTH -> PackUsage.WeeksAgo((elapsedDays / DAYS_PER_WEEK).toInt())
        else -> PackUsage.MonthsAgo((elapsedDays / DAYS_PER_MONTH).toInt())
    }
}

/** True only for a pack with a real date at or beyond [STALE_THRESHOLD_DAYS]. Date-less packs are never stale. */
internal fun isStale(
    lastUsedMillis: Long?,
    nowMillis: Long,
): Boolean = lastUsedMillis != null && (nowMillis - lastUsedMillis) >= STALE_THRESHOLD_DAYS * DAY_MILLIS

/**
 * The exact packs the 20e "Free up space" cleanup sheet lists and the nudge counts
 * (#130 PR-25) — installed packs that are removable AND provably stale.
 *
 * Three exclusions, the same three the nudge has always applied (this is now their
 * one home, so the LIST 20e removes and the COUNT the nudge shows can never
 * disagree):
 * - the pivot (English, #224): removing it frees nothing, so it is never offered;
 * - a date-less pack ([PackUsage.NoRecord], `lastUsedMillis == null`): staleness
 *   needs a real date to measure, and a selection must never fabricate one
 *   (ruling ⑧, brief §7b) — so a pack nobody has translated with is NEVER listed;
 * - a pack used within [STALE_THRESHOLD_DAYS]: not yet stale.
 *
 * Order is inherited from [buildManagePacksSections]' on-device sort (most-recent
 * first), so within the stale set the least-stale sits at the top — the same
 * reading order the management list keeps.
 */
internal fun stalePacks(
    onDevice: List<PackRow>,
    nowMillis: Long,
): List<PackRow> = onDevice.filter { !it.isPivot && isStale(it.lastUsedMillis, nowMillis) }

/**
 * One Manage-packs row: an offline-capable language the user HAS, is fetching, or
 * a fetch that failed. Not the catalog — the picker is where new packs are
 * browsed; this screen shows only what is installed / in flight / failed.
 *
 * @property usage relative last-used, for the supporting line (on-device rows).
 * @property lastUsedMillis the raw stamp behind [usage], kept so the nudge's
 *   staleness is measured precisely rather than re-derived from a display bucket.
 * @property inUse this pack's language is the current translation TARGET — the
 *   "IN USE" badge, and the only sense of in-use either drawn frame has.
 * @property isPivot the ML Kit English pivot (#224): included with every pack,
 *   removable by nothing, so it carries no overflow control and is never nudged.
 * @property hasOfflineVoice this device can also SPEAK this language offline — a
 *   voice, installed separately from the translate pack (device truth, the same
 *   `Language.hasOfflineVoice` the picker's speaker mark reads). Feeds the 20c
 *   pack-actions sheet's informational voice line ONLY; it never gates a row's
 *   presence, its removal, or its ordering. Defaulted so the many existing PackRow
 *   constructions (previews, tests) are untouched (#130 PR-24).
 */
@Immutable
data class PackRow(
    val id: String,
    val displayName: String,
    val state: OfflineModelState,
    val usage: PackUsage,
    val lastUsedMillis: Long?,
    val inUse: Boolean,
    val isPivot: Boolean,
    val hasOfflineVoice: Boolean = false,
)

/**
 * The three lists 20b draws, in the order it draws them: what is transferring,
 * what failed, and what is on the device. A [Downloadable]/[OnlineOnly] language
 * belongs to NONE of them — it is browsed and downloaded from the picker, not
 * managed here — and [downloadable] carries the offline-capable, not-yet-fetched
 * languages ONLY for the empty state's first-pack suggestions.
 */
@Immutable
data class ManagePacksSections(
    val downloading: List<PackRow>,
    val failed: List<PackRow>,
    val onDevice: List<PackRow>,
    val downloadable: List<PackRow>,
) {
    /** Any pack installed, in flight or failed — i.e. NOT the fresh-install 20f empty state. */
    val hasPacks: Boolean
        get() = downloading.isNotEmpty() || failed.isNotEmpty() || onDevice.isNotEmpty()
}

/**
 * Catalog rows + usage stamps → the four Manage-packs lists.
 *
 * Localization and the alphabetical order are delegated to [buildOfflineRows] —
 * the same tested transform Screen B already used — so a name reads and files the
 * way the picker's does, and this builder adds only the classification and the
 * on-device order on top.
 *
 * **On-device order (matches the 20b frame):** the pack in use first, then most
 * recently used, then alphabetical. A management list is read to find what to
 * remove, so the freshest sit at the top and the stale — the nudge's concern —
 * fall to the bottom. A pack with no recorded use has no recency to claim, so it
 * sorts as the oldest (never above a dated pack), yet is still excluded from the
 * nudge because "oldest for ordering" is not "provably stale for deleting".
 *
 * @param rows offline-capable catalog rows with their live model state.
 * @param usage id → last translation-success millis, already merged across roles.
 * @param targetId the current TARGET (canonical) — decides [PackRow.inUse].
 */
fun buildManagePacksSections(
    rows: List<OfflineLanguageRow>,
    usage: Map<String, Long>,
    targetId: String,
    nowMillis: Long,
    locale: Locale,
): ManagePacksSections {
    // Voice is looked up by id from the INPUT rows, exactly as `usage` is: the
    // localized `OfflinePackRow` [buildOfflineRows] returns carries only id/name/
    // state, and the id is preserved, so this reaches the device's per-language
    // voice truth without that row type having to carry it (#130 PR-24).
    val voiceById = rows.associate { it.id to it.hasOfflineVoice }
    val packRows =
        buildOfflineRows(rows, locale).map { row ->
            val lastUsed = usage[row.id]
            PackRow(
                id = row.id,
                displayName = row.displayName,
                state = row.state,
                usage = packUsage(lastUsed, nowMillis),
                lastUsedMillis = lastUsed,
                inUse = row.id == targetId,
                isPivot = isPivotLanguage(row.id),
                hasOfflineVoice = voiceById[row.id] == true,
            )
        }
    val downloading = packRows.filter { it.state == OfflineModelState.Downloading }
    val failed = packRows.filter { it.state is OfflineModelState.Failed }
    // Deleting stays "on device" — the pack is still on disk with a spinner until
    // the delete lands; calling it anything else would state a change not made yet.
    val onDevice =
        packRows
            .filter { it.state == OfflineModelState.Downloaded || it.state == OfflineModelState.Deleting }
            .sortedWith(onDeviceOrder)
    val downloadable =
        packRows.filter { it.state == OfflineModelState.NotDownloaded && !it.isPivot }
    return ManagePacksSections(
        downloading = downloading,
        failed = failed,
        onDevice = onDevice,
        downloadable = downloadable,
    )
}

/**
 * In use first, then most-recently-used, then alphabetical. `lastUsedMillis` is
 * `null` for a pack with no recorded use; it compares as the smallest value so it
 * lands after every dated pack — [buildManagePacksSections] explains why that is
 * ordering-only and not a staleness claim.
 */
private val onDeviceOrder: Comparator<PackRow> =
    compareByDescending<PackRow> { it.inUse }
        .thenByDescending { it.lastUsedMillis ?: Long.MIN_VALUE }
        .thenBy { it.displayName }

/**
 * The storage-hygiene nudge (20b · brief §6.1) — how many INSTALLED packs have sat
 * unused past [STALE_THRESHOLD_DAYS].
 *
 * Only [ManagePacksSections.onDevice] packs can be stale — a downloading or failed
 * pack is not one you are keeping unused. The pivot is excluded (removing it does
 * nothing), and a date-less pack is excluded (no date to call stale, ruling ⑧).
 * `null` when nothing qualifies, so the card is simply absent rather than a nudge
 * about zero packs — the same no-empty-header honesty the sections keep.
 */
@Immutable
data class HygieneNudge(
    val stalePackCount: Int,
)

fun hygieneNudge(
    onDevice: List<PackRow>,
    nowMillis: Long,
): HygieneNudge? {
    // The count IS the size of the exact list 20e will offer ([stalePacks]) — one
    // predicate, so the nudge can never say "3 packs" over a sheet that lists 2.
    val stale = stalePacks(onDevice, nowMillis)
    return if (stale.isNotEmpty()) HygieneNudge(stalePackCount = stale.size) else null
}

/**
 * The aggregate storage card (20b · brief §2/§3). AGGREGATE ONLY — ML Kit exposes
 * no per-pack size, so per-language bytes are never stated; a walk of the whole
 * model store is honest and is all this shows.
 */
@Immutable
sealed interface StorageCard {
    /** Packs on device — the counter's numerator, always available (it is a count, not a disk read). */
    val packCount: Int

    /**
     * The walk found the packs' bytes.
     *
     * @property packsBytes the measured aggregate, never an estimate (risk R3).
     * @property freeBytes free space on the volume the store sits on.
     * @property totalBytes that same volume's size — so the bar is one fraction of
     *   one disk (device-used vs free), the only honest bar at 110 MB against a
     *   whole device (brief §2, and 19b's own `deviceUsedFraction`).
     */
    data class Sized(
        override val packCount: Int,
        val packsBytes: Long,
        val freeBytes: Long,
        val totalBytes: Long,
    ) : StorageCard

    /**
     * No packs, or a size the walk could not read (store absent/renamed — the U-5
     * degrade). Free space is stated instead, because a fraction of the disk is
     * exactly what is not known. [packCount] separates the fresh-install "none
     * yet" copy from the rarer "packs counted, bytes unknown" degrade.
     */
    data class FreeOnly(
        override val packCount: Int,
        val freeBytes: Long,
    ) : StorageCard
}

/**
 * A storage snapshot + pack count → the card. Precedence:
 * 1. **A byte answer above zero → [StorageCard.Sized].** A real measured size.
 * 2. **Everything else → [StorageCard.FreeOnly].** `null` (no store directory
 *    where research measured one) and `0` are both "size not knowable, or nothing
 *    to size"; free space is the honest second figure, never "your packs take up
 *    no space" under a positive count.
 *
 * Unlike the picker's [offlineLibraryMeter], count-zero is NOT a distinct branch:
 * a zero-pack device has no bytes, so it degrades to [StorageCard.FreeOnly] with
 * `packCount == 0` and the composable draws the 20f "none yet" copy from that.
 */
fun storageCard(
    packCount: Int,
    packsBytes: Long?,
    freeBytes: Long,
    totalBytes: Long,
): StorageCard =
    if (packsBytes != null && packsBytes > 0L) {
        StorageCard.Sized(
            packCount = packCount,
            packsBytes = packsBytes,
            freeBytes = freeBytes,
            totalBytes = totalBytes,
        )
    } else {
        StorageCard.FreeOnly(packCount = packCount, freeBytes = freeBytes)
    }

/**
 * The empty state's first-pack suggestions (20f · brief §8), derived from the
 * device's preferred locales alone — the same 18a signal, never a platform figure
 * the app cannot source.
 *
 * A deliberate sibling of the picker's [firstRunSuggestions] rather than a reuse
 * of it: that one is entangled with `LanguagePickerRow`/`LanguageRowState`, which
 * this screen never builds, so forcing them together would mean running the whole
 * picker row pipeline in this ViewModel. This one reads the same `LanguageRole`?
 * no — it reads only offline capability, which is exactly [downloadable]
 * (offline-capable, nothing on disk, not the pivot). It CAN be empty (a
 * monolingual-English device has nothing offline-capable to offer), and the
 * caller then shows "Browse all languages" alone — no dead end (brief §8).
 *
 * @param preferredLocaleTags most-preferred first (`LocaleList.getAdjustedDefault()`).
 * @param downloadable the offline-capable, not-yet-fetched, non-pivot rows.
 */
fun manageEmptySuggestions(
    preferredLocaleTags: List<String>,
    downloadable: List<PackRow>,
    limit: Int = SUGGESTION_LIMIT,
): List<SuggestedLanguage> {
    val rowById = downloadable.associateBy(PackRow::id)
    val seen = HashSet<String>()
    val out = mutableListOf<SuggestedLanguage>()
    preferredLocaleTags.forEachIndexed { index, tag ->
        if (out.size >= limit) return out
        val id = downloadableCapableId(tag, rowById) ?: return@forEachIndexed
        if (!seen.add(id)) return@forEachIndexed
        val row = rowById.getValue(id)
        out +=
            SuggestedLanguage(
                id = id,
                displayName = row.displayName,
                avatar = LanguageAvatar.Code(languageAvatarCode(id)),
                reason = if (index == 0) SuggestionReason.DEVICE_LANGUAGE else SuggestionReason.COMMON_WHERE_YOU_ARE,
            )
    }
    return out
}

/**
 * The catalog id a locale [tag] should be OFFERED, most-specific first then the
 * base subtag, or null when none of its resolutions is a downloadable row. Same
 * resolution [firstRunSuggestions] uses — `fr-FR` reaches the base `fr` pack ML
 * Kit actually ships — kept in step by both routing through [LanguageTagResolver].
 */
private fun downloadableCapableId(
    tag: String,
    rowById: Map<String, PackRow>,
): String? {
    val candidates =
        listOfNotNull(
            LanguageTagResolver.canonicalId(tag),
            LanguageTagResolver.canonicalId(tag.substringBefore('-')),
        )
    return candidates.firstOrNull { id -> rowById.containsKey(id) }
}
