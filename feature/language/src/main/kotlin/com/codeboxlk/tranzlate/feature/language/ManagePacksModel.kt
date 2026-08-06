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
 * A pack's last translation-use (a success, #122) — the honest SINGLE source the
 * row, the detail pane and the nudge all read.
 *
 * Two states, and only two, so the illegal ones cannot be written (#325): a pack
 * either has a real stamp ([Used]) or it has none ([NoRecord]). The relative bucket
 * a row draws and whether a pack is stale are DERIVED from [Used.lastUsedMillis]
 * ([PackUsage.Used.bucket], [isStale]) at a given "now" — never a second field stored
 * beside the stamp. So "used 4 months ago" built from no date, and "no recorded use
 * yet" on a pack the cleanup sweeps as stale, are each unrepresentable by
 * construction, not merely avoided by the one factory that builds a row.
 */
@Immutable
sealed interface PackUsage {
    /** No translation-success stamp — the honest "we do not know" (ruling ⑧). */
    data object NoRecord : PackUsage

    /** A real translation-success stamp. Bucket and staleness DERIVE from [lastUsedMillis]. */
    data class Used(
        val lastUsedMillis: Long,
    ) : PackUsage
}

/**
 * The relative last-used bucket a row or the detail DRAWS — "used today", "5 days
 * ago", "4 months ago". Derived from a real [PackUsage.Used] stamp at a given "now",
 * never stored: a [PackUsage.NoRecord] pack has NO bucket (it draws the honest
 * date-less line instead), so only a [PackUsage.Used] can be asked for one (#325).
 *
 * Bucketed elapsed time, never a named calendar month: "since April" is unambiguous
 * only within a year, and the store keeps stamps indefinitely, so a month name with
 * no year is exactly the half-true figure this brief keeps off the screen. Elapsed
 * days from a stamp the app holds is a figure it can always state correctly, in every
 * locale — the same relative form the i18n guide's `minutes_ago` plural endorses.
 */
@Immutable
sealed interface UsageBucket {
    /** Used within the last day. */
    data object Today : UsageBucket

    /** 1..6 days ago. */
    data class DaysAgo(
        val days: Int,
    ) : UsageBucket

    /** 1..4 weeks ago. */
    data class WeeksAgo(
        val weeks: Int,
    ) : UsageBucket

    /** A month or more ago — the stale zone the nudge watches. */
    data class MonthsAgo(
        val months: Int,
    ) : UsageBucket
}

/** A last-used stamp (or its absence) → the honest usage. `null` → [PackUsage.NoRecord], else [PackUsage.Used]. */
fun packUsage(lastUsedMillis: Long?): PackUsage =
    if (lastUsedMillis == null) PackUsage.NoRecord else PackUsage.Used(lastUsedMillis)

/**
 * A real stamp's relative [UsageBucket] at [nowMillis]. A future or skewed stamp
 * (elapsed <= 0) reads as [UsageBucket.Today] rather than a negative count. Pure and
 * now-in-the-signature so a test fixes "now" and the boundaries are checkable without
 * a clock.
 */
internal fun PackUsage.Used.bucket(nowMillis: Long): UsageBucket {
    val elapsedDays = (nowMillis - lastUsedMillis) / DAY_MILLIS
    return when {
        elapsedDays <= 0L -> UsageBucket.Today
        elapsedDays < DAYS_PER_WEEK -> UsageBucket.DaysAgo(elapsedDays.toInt())
        elapsedDays < DAYS_PER_MONTH -> UsageBucket.WeeksAgo((elapsedDays / DAYS_PER_WEEK).toInt())
        else -> UsageBucket.MonthsAgo((elapsedDays / DAYS_PER_MONTH).toInt())
    }
}

/**
 * A pack's last-used, split by the role it was used IN — the 20d detail pane's
 * "source" line (#130 PR-26, ruling :144/:82). The list ROW shows one merged
 * bucket (used INTO or OUT OF, whichever is fresher); the detail is where the two
 * roles are told apart, because a language you translate FROM often is not the
 * same as one you translate INTO, and the detail is the room to say so.
 *
 * Each side is the SAME honest [PackUsage] the row carries ([packUsage]): a role with
 * no translation-success stamp is [PackUsage.NoRecord] — "no recorded use yet", never
 * a fabricated date (ruling ⑧, brief §7b). So a pack used only as a source reads a
 * real date on the source line and the date-less line on the target line, and both
 * are true.
 */
@Immutable
data class PackRoleUsage(
    val asSource: PackUsage,
    val asTarget: PackUsage,
)

/**
 * The selected pack's per-role last-used, for the 20d detail pane.
 *
 * Pure and the same shape as [packUsage] it delegates to, so the honesty (a role with
 * no stamp → [PackUsage.NoRecord]) is checkable directly. Each role reads its OWN map
 * — [usageAsSource] for the source line, [usageAsTarget] for the target line — so a
 * swap of the two is a reddening mutation, not a silent lie about which way the
 * language was used. The relative date each line prints derives from the stamp at the
 * detail pane's clock ([PackUsage.Used.bucket]), never stored here.
 *
 * @param usageAsSource canonical-id → last-used-as-source millis (#122 SOURCE role).
 * @param usageAsTarget canonical-id → last-used-as-target millis (#122 TARGET role).
 */
fun packRoleUsage(
    id: String,
    usageAsSource: Map<String, Long>,
    usageAsTarget: Map<String, Long>,
): PackRoleUsage =
    PackRoleUsage(
        asSource = packUsage(usageAsSource[id]),
        asTarget = packUsage(usageAsTarget[id]),
    )

/** True only for a [PackUsage.Used] at or beyond [STALE_THRESHOLD_DAYS]. A [PackUsage.NoRecord] pack is never stale. */
internal fun isStale(
    usage: PackUsage,
    nowMillis: Long,
): Boolean = usage is PackUsage.Used && (nowMillis - usage.lastUsedMillis) >= STALE_THRESHOLD_DAYS * DAY_MILLIS

/**
 * The exact packs the 20e "Free up space" cleanup sheet lists and the nudge counts
 * (#130 PR-25) — installed packs that are removable AND provably stale.
 *
 * Three exclusions, the same three the nudge has always applied (this is now their
 * one home, so the LIST 20e removes and the COUNT the nudge shows can never
 * disagree):
 * - the pivot (English, #224): removing it frees nothing, so it is never offered —
 *   asked of [isPivotLanguage] by id, so a row and its pivot-ness cannot disagree;
 * - a date-less pack ([PackUsage.NoRecord]): staleness needs a real date to measure,
 *   and a selection must never fabricate one (ruling ⑧, brief §7b) — so a pack nobody
 *   has translated with is NEVER listed, which [isStale] enforces by type;
 * - a pack used within [STALE_THRESHOLD_DAYS]: not yet stale.
 *
 * Order is inherited from [buildManagePacksSections]' on-device sort (most-recent
 * first), so within the stale set the least-stale sits at the top — the same
 * reading order the management list keeps.
 */
internal fun stalePacks(
    onDevice: List<PackRow>,
    nowMillis: Long,
): List<PackRow> = onDevice.filter { !isPivotLanguage(it.id) && isStale(it.usage, nowMillis) }

/**
 * One Manage-packs row: an offline-capable language the user HAS, is fetching, or
 * a fetch that failed. Not the catalog — the picker is where new packs are
 * browsed; this screen shows only what is installed / in flight / failed.
 *
 * @property usage the honest last-used ([PackUsage]) — the SINGLE source for the
 *   supporting line's relative date, the on-device order AND staleness alike. A
 *   [PackUsage.Used] carries the raw stamp; a [PackUsage.NoRecord] carries none, so a
 *   date-less pack cannot be asked "how many months" nor be called stale (#325).
 * @property inUse this pack's language is the current translation TARGET — the
 *   "IN USE" badge, and the only sense of in-use either drawn frame has.
 * @property hasOfflineVoice this device can also SPEAK this language offline — a
 *   voice, installed separately from the translate pack (device truth, the same
 *   `Language.hasOfflineVoice` the picker's speaker mark reads). Feeds the 20c
 *   pack-actions sheet's informational voice line ONLY; it never gates a row's
 *   presence, its removal, or its ordering. Defaulted so the many existing PackRow
 *   constructions (previews, tests) are untouched (#130 PR-24).
 *
 * The pivot-ness the row once stored (`isPivot`) is gone: it is asked of
 * [isPivotLanguage] by [id] at the few read sites, so a row and its pivot-ness can
 * never disagree (#224, #325 finding 2).
 */
@Immutable
data class PackRow(
    val id: String,
    val displayName: String,
    val state: OfflineModelState,
    val usage: PackUsage,
    val inUse: Boolean,
    val hasOfflineVoice: Boolean = false,
)

/**
 * Whether one offline capability WORKS for the selected pack right now, or needs a
 * connection. The 20d detail pane's capability cards draw one of these per capability
 * — a filled "supported" tint and its own positive subtitle, or a muted tint and the
 * shared "needs a connection" subtitle (#332). Two states, and never a bare Boolean,
 * so the card cannot be drawn "supported" by mistaking `true`-means-what for `false`.
 */
@Immutable
enum class CapabilityState {
    /** The capability runs on-device — draw it filled, with its own subtitle. */
    Supported,

    /** The capability needs a connection here — draw it muted, "Needs a connection". */
    Unavailable,
}

/**
 * The 20d detail pane's derived, string-free view of the SELECTED pack (#332): which
 * offline capabilities work, and whether the pack is one the user can remove. Pure,
 * so every "what the pane may claim" decision is unit-tested away from Compose — the
 * module still has only a thin Compose test runtime (#186), and a decision left inside
 * a `@Composable` is the rev.3 ruling's fourth cause.
 *
 * The two capabilities read the SAME device truths the rest of the screen does, never
 * a new source: text-offline is exactly "the pack is on the device" (a downloaded pack
 * translates full sentences offline), and voice-offline is that AND [PackRow.hasOfflineVoice]
 * — a voice is a separate install, so a downloaded pack without one still needs a
 * connection to speak. A pack that is not on the device (mid-download, or a failed
 * fetch) supports NEITHER offline yet, so both read [CapabilityState.Unavailable]; the
 * card's honesty then holds for every row the detail can select, not only a settled one.
 *
 * @property onDevice the pack is on disk (Downloaded, or mid-delete and still on disk)
 *   — the identity subtitle's "On device" and both capabilities turn on this.
 * @property removable a settled, non-pivot on-device pack: only then does the Remove
 *   block draw. A mid-delete pack already has a delete in flight, and the English pivot
 *   is non-actionable (#224, removing it frees nothing) — asked of [isPivotLanguage] by
 *   id, never a stored flag, so a row and its pivot-ness cannot disagree (#325).
 */
@Immutable
data class PackDetail(
    val onDevice: Boolean,
    val textOffline: CapabilityState,
    val voiceOffline: CapabilityState,
    val removable: Boolean,
)

/** Derive the [PackDetail] for the selected [row] — the single home of the pane's capability + remove decisions. */
fun packDetail(row: PackRow): PackDetail {
    val onDevice = row.state == OfflineModelState.Downloaded || row.state == OfflineModelState.Deleting
    return PackDetail(
        onDevice = onDevice,
        textOffline = if (onDevice) CapabilityState.Supported else CapabilityState.Unavailable,
        voiceOffline = if (onDevice && row.hasOfflineVoice) CapabilityState.Supported else CapabilityState.Unavailable,
        removable = row.state == OfflineModelState.Downloaded && !isPivotLanguage(row.id),
    )
}

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
    locale: Locale,
): ManagePacksSections {
    // Voice is looked up by id from the INPUT rows, exactly as `usage` is: the
    // localized `OfflinePackRow` [buildOfflineRows] returns carries only id/name/
    // state, and the id is preserved, so this reaches the device's per-language
    // voice truth without that row type having to carry it (#130 PR-24).
    val voiceById = rows.associate { it.id to it.hasOfflineVoice }
    val packRows =
        buildOfflineRows(rows, locale).map { row ->
            PackRow(
                id = row.id,
                displayName = row.displayName,
                state = row.state,
                // The stamp travels INSIDE [PackUsage] now: `Used(stamp)` or `NoRecord`,
                // the single source the row's date, the order and staleness all read (#325).
                usage = packUsage(usage[row.id]),
                inUse = row.id == targetId,
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
        packRows.filter { it.state == OfflineModelState.NotDownloaded && !isPivotLanguage(it.id) }
    return ManagePacksSections(
        downloading = downloading,
        failed = failed,
        onDevice = onDevice,
        downloadable = downloadable,
    )
}

/**
 * In use first, then most-recently-used, then alphabetical. A [PackUsage.NoRecord]
 * pack has no stamp; it compares as the smallest value so it lands after every dated
 * pack — [buildManagePacksSections] explains why that is ordering-only and not a
 * staleness claim.
 */
private val onDeviceOrder: Comparator<PackRow> =
    compareByDescending<PackRow> { it.inUse }
        .thenByDescending { (it.usage as? PackUsage.Used)?.lastUsedMillis ?: Long.MIN_VALUE }
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
