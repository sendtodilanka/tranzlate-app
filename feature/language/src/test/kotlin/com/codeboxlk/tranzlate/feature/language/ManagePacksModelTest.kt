package com.codeboxlk.tranzlate.feature.language

import com.codeboxlk.tranzlate.core.model.OfflineModelFailure
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

/**
 * The pure heart of Manage packs (20b/20f · #130 PR-23): the section builder, the
 * relative-use buckets, the honest date-less case, the nudge threshold, the
 * storage card, the empty-state suggestions, and the on-device order. No Compose,
 * no ViewModel — every honesty rule the screen keeps is decided here and pinned
 * here, with the mutation each test would fail under written down first (rule 11).
 *
 * "Now" is fixed at [NOW] and stamps are [NOW] minus a whole number of days, so
 * the boundaries are exact and nothing reads a wall clock.
 */
class ManagePacksModelTest {
    private val en = Locale.ENGLISH

    private fun capable(
        id: String,
        name: String,
        state: OfflineModelState,
    ) = OfflineLanguageRow(id, name, state)

    private fun daysAgo(days: Long): Long = NOW - days * DAY_MILLIS

    // ── packUsage: relative buckets, and the honest date-less case ────────────

    /**
     * The honesty rule this whole screen exists to keep (ruling ⑧): a pack with no
     * recorded translation-use has NO date, and says so — never a fabricated one.
     *
     * Mutation: `packUsage` drops the `null -> NoRecord` line and lets a null stamp
     * fall through to `Today`. Then a pack nobody has ever translated with would
     * read "used today", which is exactly the invented figure the brief forbids.
     */
    @Test
    fun `a pack with no stamp is NoRecord, never a date`() {
        assertThat(packUsage(lastUsedMillis = null, nowMillis = NOW)).isEqualTo(PackUsage.NoRecord)
    }

    /** Mutation: `elapsedDays <= 0` → `< 0`, so a stamp from earlier today becomes DaysAgo(0). */
    @Test
    fun `a stamp from earlier today is Today`() {
        assertThat(packUsage(NOW - 3 * 60 * 60 * 1000L, NOW)).isEqualTo(PackUsage.Today)
    }

    /**
     * The three bucket boundaries, chosen so each is one day off a neighbour:
     * mutations that turn a `<` into `<=` (or shift a divisor) move exactly one of
     * these across a line.
     */
    @Test
    fun `elapsed days bucket into today, days, weeks and months at their boundaries`() {
        assertThat(packUsage(daysAgo(6), NOW)).isEqualTo(PackUsage.DaysAgo(6))
        assertThat(packUsage(daysAgo(7), NOW)).isEqualTo(PackUsage.WeeksAgo(1))
        assertThat(packUsage(daysAgo(29), NOW)).isEqualTo(PackUsage.WeeksAgo(4))
        assertThat(packUsage(daysAgo(30), NOW)).isEqualTo(PackUsage.MonthsAgo(1))
        assertThat(packUsage(daysAgo(90), NOW)).isEqualTo(PackUsage.MonthsAgo(3))
    }

    /** A stamp in the future (clock skew) is not a negative age. Mutation: report DaysAgo(negative). */
    @Test
    fun `a future stamp reads as Today, not a negative age`() {
        assertThat(packUsage(NOW + 5 * DAY_MILLIS, NOW)).isEqualTo(PackUsage.Today)
    }

    // ── isStale: the nudge threshold ──────────────────────────────────────────

    /**
     * The 90-day threshold, pinned at the boundary. Mutation: `>= STALE_THRESHOLD_DAYS`
     * → `>` makes exactly 90 days not-stale; the first assertion reddens.
     */
    @Test
    fun `staleness turns on at exactly 90 days`() {
        assertThat(isStale(daysAgo(90), NOW)).isTrue()
        assertThat(isStale(daysAgo(89), NOW)).isFalse()
        assertThat(isStale(daysAgo(120), NOW)).isTrue()
    }

    /** Mutation: drop the `!= null` guard so a date-less pack is treated as stale (ruling ⑧). */
    @Test
    fun `a date-less pack is never stale`() {
        assertThat(isStale(lastUsedMillis = null, nowMillis = NOW)).isFalse()
    }

    // ── buildManagePacksSections: classification ──────────────────────────────

    /**
     * Every state lands in the right list, and a NotDownloaded language lands in
     * NONE of the three drawn sections — it is browsed from the picker, not managed
     * here. Mutations: classify Downloading/Failed into onDevice, or let
     * NotDownloaded leak into it.
     */
    @Test
    fun `each state is classified into its own section`() {
        val rows =
            listOf(
                capable("de", "German", OfflineModelState.Downloaded),
                capable("ar", "Arabic", OfflineModelState.Downloading),
                capable("hi", "Hindi", OfflineModelState.Failed(OfflineModelFailure.NETWORK)),
                capable("es", "Spanish", OfflineModelState.NotDownloaded),
                capable("it", "Italian", OfflineModelState.Deleting),
            )
        val sections = buildManagePacksSections(rows, emptyMap(), targetId = "", nowMillis = NOW, locale = en)

        assertThat(sections.downloading.map { it.id }).containsExactly("ar")
        assertThat(sections.failed.map { it.id }).containsExactly("hi")
        // Deleting is still on disk, so it stays "on device" with a spinner.
        assertThat(sections.onDevice.map { it.id }).containsExactly("de", "it")
        assertThat(sections.downloadable.map { it.id }).containsExactly("es")
    }

    /**
     * THE no-date honest case, at the section level (ruling ⑧ · risk R6): a
     * downloaded pack with no usage stamp carries [PackUsage.NoRecord], not a date
     * conjured from thin air. Mutation: `packUsage` folds null to `Today` — this
     * row would then claim "used today" for a language never translated with.
     */
    @Test
    fun `a downloaded pack with no usage stamp shows no recorded use, not a date`() {
        val rows = listOf(capable("pl", "Polish", OfflineModelState.Downloaded))
        val sections = buildManagePacksSections(rows, usage = emptyMap(), targetId = "", nowMillis = NOW, locale = en)

        assertThat(sections.onDevice.single().usage).isEqualTo(PackUsage.NoRecord)
        assertThat(sections.onDevice.single().lastUsedMillis).isNull()
    }

    /** A stamp that exists reaches the row as its relative bucket. Mutation: ignore the usage map. */
    @Test
    fun `a downloaded pack with a stamp shows its relative age`() {
        val rows = listOf(capable("de", "German", OfflineModelState.Downloaded))
        val sections =
            buildManagePacksSections(
                rows,
                usage = mapOf("de" to daysAgo(3)),
                targetId = "",
                nowMillis = NOW,
                locale = en,
            )

        assertThat(sections.onDevice.single().usage).isEqualTo(PackUsage.DaysAgo(3))
    }

    /** The current target's pack is flagged in use; no other is. Mutation: read the wrong side / a constant. */
    @Test
    fun `only the target pack is marked in use`() {
        val rows =
            listOf(
                capable("es", "Spanish", OfflineModelState.Downloaded),
                capable("de", "German", OfflineModelState.Downloaded),
            )
        val sections = buildManagePacksSections(rows, emptyMap(), targetId = "es", nowMillis = NOW, locale = en)

        assertThat(sections.onDevice.single { it.id == "es" }.inUse).isTrue()
        assertThat(sections.onDevice.single { it.id == "de" }.inUse).isFalse()
    }

    /** English is flagged as the pivot; it is never nudged and carries no overflow. Mutation: drop the pivot check. */
    @Test
    fun `the English pivot is flagged`() {
        val rows = listOf(capable("en", "English", OfflineModelState.Downloaded))
        val sections = buildManagePacksSections(rows, emptyMap(), targetId = "", nowMillis = NOW, locale = en)

        assertThat(sections.onDevice.single().isPivot).isTrue()
    }

    /**
     * The device's offline-voice truth threads from the source row to the built pack
     * row, so the 20c sheet can gate its voice line on it (#130 PR-24). Mutation decided
     * first (rule 11): hardcode `hasOfflineVoice = false` in `buildManagePacksSections`
     * and the Spanish assertion reddens; hardcode it `true` and the German one does. The
     * two rows in one call also prove it is looked up per id, not applied blanket.
     */
    @Test
    fun `offline voice threads per-pack from the source row to the pack row`() {
        val rows =
            listOf(
                OfflineLanguageRow("es", "Spanish", OfflineModelState.Downloaded, hasOfflineVoice = true),
                OfflineLanguageRow("de", "German", OfflineModelState.Downloaded, hasOfflineVoice = false),
            )
        val sections = buildManagePacksSections(rows, emptyMap(), targetId = "", nowMillis = NOW, locale = en)

        assertThat(sections.onDevice.single { it.id == "es" }.hasOfflineVoice).isTrue()
        assertThat(sections.onDevice.single { it.id == "de" }.hasOfflineVoice).isFalse()
    }

    // ── on-device order: in use first, then most-recent, then alphabetical ─────

    /**
     * The order the 20b frame draws, and the one that matters for a management
     * list: the pack in use first, then most-recently used, then alphabetical.
     *
     * The in-use pack is deliberately the OLDEST here (`es`, months ago) while a
     * fresh non-target pack (`de`, today) sits behind it — so the fixture is
     * discriminating: an ordering that dropped the in-use key would float `de`
     * above `es`. `pl` has no date and sorts last (ordering-only, never a
     * staleness claim), and `af`/`ar` split a same-day tie by name.
     */
    @Test
    fun `on-device packs sort in use first, then most recent, then by name`() {
        val rows =
            listOf(
                capable("de", "German", OfflineModelState.Downloaded),
                capable("es", "Spanish", OfflineModelState.Downloaded),
                capable("pl", "Polish", OfflineModelState.Downloaded),
                capable("ar", "Arabic", OfflineModelState.Downloaded),
                capable("af", "Afrikaans", OfflineModelState.Downloaded),
            )
        val usage =
            mapOf(
                "es" to daysAgo(120),
                "de" to daysAgo(0),
                "af" to daysAgo(0),
                "ar" to daysAgo(0),
                // pl: no stamp
            )
        val sections = buildManagePacksSections(rows, usage, targetId = "es", nowMillis = NOW, locale = en)

        assertThat(sections.onDevice.map { it.id })
            .containsExactly("es", "af", "ar", "de", "pl")
            .inOrder()
    }

    // ── hygieneNudge ──────────────────────────────────────────────────────────

    /**
     * The nudge counts ONLY packs that are provably stale AND removable. The
     * fixture carries every trap: a stale-but-pivot pack, a date-less pack, a fresh
     * pack, and one genuinely stale dated pack. Only the last counts.
     *
     * Three mutations this reddens: drop the `!isPivot` guard (pivot counted, 2),
     * drop the date-less exclusion (`pl` counted, 2), or drop the staleness test
     * (fresh `de` counted).
     */
    @Test
    fun `the nudge counts only stale, dated, non-pivot packs`() {
        val onDevice =
            listOf(
                packRow("en", OfflineModelState.Downloaded, lastUsedMillis = daysAgo(200), isPivot = true),
                packRow("pl", OfflineModelState.Downloaded, lastUsedMillis = null),
                packRow("de", OfflineModelState.Downloaded, lastUsedMillis = daysAgo(1)),
                packRow("ru", OfflineModelState.Downloaded, lastUsedMillis = daysAgo(120)),
            )

        assertThat(hygieneNudge(onDevice, NOW)).isEqualTo(HygieneNudge(stalePackCount = 1))
    }

    /** No stale pack → no nudge at all (absent card, never "0 packs"). Mutation: return a zero-count nudge. */
    @Test
    fun `no stale packs means no nudge`() {
        val onDevice =
            listOf(
                packRow("de", OfflineModelState.Downloaded, lastUsedMillis = daysAgo(3)),
                packRow("es", OfflineModelState.Downloaded, lastUsedMillis = null),
            )

        assertThat(hygieneNudge(onDevice, NOW)).isNull()
    }

    // ── storageCard ───────────────────────────────────────────────────────────

    /** A measured positive byte answer → Sized, carrying the real figures. Mutation: read free as used. */
    @Test
    fun `a positive byte answer builds a Sized card`() {
        val card = storageCard(packCount = 5, packsBytes = 110L, freeBytes = 900L, totalBytes = 1000L)

        assertThat(card).isInstanceOf(StorageCard.Sized::class.java)
        val sized = card as StorageCard.Sized
        assertThat(sized.packCount).isEqualTo(5)
        assertThat(sized.packsBytes).isEqualTo(110L)
        assertThat(sized.freeBytes).isEqualTo(900L)
    }

    /**
     * A null byte answer (store absent/renamed) degrades to free-only — never a
     * "0 MB used" the walk did not measure. Mutation: substitute 0 for null and
     * build a Sized card claiming zero bytes.
     */
    @Test
    fun `a null byte answer degrades to a FreeOnly card`() {
        val card = storageCard(packCount = 3, packsBytes = null, freeBytes = 900L, totalBytes = 1000L)

        assertThat(card).isInstanceOf(StorageCard.FreeOnly::class.java)
        assertThat((card as StorageCard.FreeOnly).packCount).isEqualTo(3)
    }

    /** Zero bytes is also "not a size to state" → FreeOnly. Mutation: `> 0` → `>= 0` makes this Sized(0). */
    @Test
    fun `zero bytes degrades to a FreeOnly card, not Sized with a zero`() {
        assertThat(storageCard(packCount = 0, packsBytes = 0L, freeBytes = 900L, totalBytes = 1000L))
            .isInstanceOf(StorageCard.FreeOnly::class.java)
    }

    // ── manageEmptySuggestions ────────────────────────────────────────────────

    /**
     * The device's first locale is offered as the device language, the rest as
     * "common where you are". Mutation: assign the reason from a constant, or off
     * the wrong index — the first suggestion then stops reading DEVICE_LANGUAGE.
     */
    @Test
    fun `the first suggestion is the device language, the rest are local`() {
        val downloadable =
            listOf(
                packRow("es", OfflineModelState.NotDownloaded),
                packRow("fr", OfflineModelState.NotDownloaded),
            )
        val suggestions = manageEmptySuggestions(listOf("es-ES", "fr-FR"), downloadable)

        assertThat(suggestions.map { it.id }).containsExactly("es", "fr").inOrder()
        assertThat(suggestions.first().reason).isEqualTo(SuggestionReason.DEVICE_LANGUAGE)
        assertThat(suggestions[1].reason).isEqualTo(SuggestionReason.COMMON_WHERE_YOU_ARE)
    }

    /** A locale with no downloadable pack is skipped, not offered a download that cannot happen. */
    @Test
    fun `a locale with no offline-capable pack is skipped`() {
        val downloadable = listOf(packRow("fr", OfflineModelState.NotDownloaded))
        // `yue` (Cantonese) is online-only, so it is never in the downloadable set.
        val suggestions = manageEmptySuggestions(listOf("yue", "fr-FR"), downloadable)

        assertThat(suggestions.map { it.id }).containsExactly("fr")
    }

    /** Two locales resolving to one base id (fr-FR, fr-CA → fr) suggest it once. Mutation: drop the dedupe. */
    @Test
    fun `locales resolving to the same base language are deduplicated`() {
        val downloadable = listOf(packRow("fr", OfflineModelState.NotDownloaded))
        val suggestions = manageEmptySuggestions(listOf("fr-FR", "fr-CA"), downloadable)

        assertThat(suggestions.map { it.id }).containsExactly("fr")
    }

    /** A monolingual-English device offers nothing — the caller then shows Browse all alone (no dead end). */
    @Test
    fun `an English-only device yields no suggestions`() {
        val downloadable = listOf(packRow("es", OfflineModelState.NotDownloaded))
        assertThat(manageEmptySuggestions(listOf("en-US"), downloadable)).isEmpty()
    }

    // ── mergeLatestUse (VM helper) ────────────────────────────────────────────

    /**
     * A language is "used" if translated INTO or OUT OF, whichever is more recent.
     * Mutation: take the min, or only the source — a pack used as a target last
     * week would then look stale on its older source stamp.
     */
    @Test
    fun `latest use is the more recent of the two roles`() {
        val merged = mergeLatestUse(source = mapOf("es" to daysAgo(90)), target = mapOf("es" to daysAgo(2)))
        assertThat(merged["es"]).isEqualTo(daysAgo(2))
    }

    /** A language used in only one role keeps that stamp. */
    @Test
    fun `a language used in one role keeps that stamp`() {
        val merged = mergeLatestUse(source = mapOf("de" to daysAgo(5)), target = mapOf("fr" to daysAgo(1)))
        assertThat(merged).containsEntry("de", daysAgo(5))
        assertThat(merged).containsEntry("fr", daysAgo(1))
    }

    private fun packRow(
        id: String,
        state: OfflineModelState,
        lastUsedMillis: Long? = null,
        isPivot: Boolean = false,
    ) = PackRow(
        id = id,
        displayName = id,
        state = state,
        usage = packUsage(lastUsedMillis, NOW),
        lastUsedMillis = lastUsedMillis,
        inUse = false,
        isPivot = isPivot,
    )

    private companion object {
        /** A fixed "now" so every relative bucket and threshold is exact. */
        const val NOW: Long = 1_800_000_000_000L
    }
}
