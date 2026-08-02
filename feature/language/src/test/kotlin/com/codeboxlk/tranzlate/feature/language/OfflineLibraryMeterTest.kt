package com.codeboxlk.tranzlate.feature.language

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The offline-library meter (#130 PR-15, ruling U-5) — "meter formatting +
 * degrade states", which is the whole of what the ruling asks this PR to test.
 *
 * **What is actually at stake is a lie the app could tell.** The card prints a
 * size, and the size comes from walking a directory ML Kit has never promised
 * the name of. This project only knows the name because issue #90's research
 * measured it on 2026-07-30, and experiment E-S1 re-measured it for this PR
 * (`docs/research/issue-130-e-s1-storage-walk.md`). If ML Kit renames it in an
 * update, the walk returns null on every device at once — and the obvious thing
 * to draw for null is `0 MB`, which states as a fact that the packs occupy no
 * space. That is risk R8 in the ruling's register, and most of this file is
 * about refusing it.
 *
 * The numbers below are the E-S1 measurements rather than round ones: one af↔en
 * pack summed to 44,169,505 bytes across 30 files, on a volume `df` reported as
 * 10,411,143,168 bytes with 8,651,702,272 free.
 */
class OfflineLibraryMeterTest {
    // ---- the ordinary case ---------------------------------------------------

    @Test
    fun `packs with a measured size report that size`() {
        val meter =
            offlineLibraryMeter(
                downloaded = 1,
                capable = 59,
                packsBytes = ONE_PACK,
                volumeBytes = VOLUME,
                freeBytes = FREE,
            )

        assertThat(meter).isEqualTo(
            OfflineLibraryMeter.Sized(
                downloaded = 1,
                capable = 59,
                usedBytes = ONE_PACK,
                volumeBytes = VOLUME,
            ),
        )
    }

    /**
     * The bar is the packs' share of the WHOLE VOLUME, not of the free space.
     *
     * Those two differ by a lot on a real device — E-S1's emulator was 16% full
     * before a single pack landed — and free space is the wrong denominator for
     * a bar that is captioned "Offline library": it would grow as the user's
     * photos filled the disk, without a single pack being downloaded.
     */
    @Test
    fun `the bar is the packs' share of the whole volume`() {
        val meter =
            offlineLibraryMeter(
                downloaded = 1,
                capable = 59,
                packsBytes = ONE_PACK,
                volumeBytes = VOLUME,
                freeBytes = FREE,
            )

        assertThat(meter.fraction).isWithin(TOLERANCE).of(ONE_PACK.toFloat() / VOLUME)
        // …and had it divided by free space instead it would read 0.51%, which
        // is a different number for the same disk.
        assertThat(meter.fraction).isNotWithin(TOLERANCE).of(ONE_PACK.toFloat() / FREE)
    }

    /**
     * Not floored at a visible minimum, deliberately. On the E-S1 device one
     * pack was 0.42% of the volume, which draws as very nearly nothing — and
     * that is the true statement. A floor would draw a bar bigger than the fact
     * it reports, and the count beside it already says the packs are there.
     */
    @Test
    fun `a tiny library draws a tiny bar rather than a minimum one`() {
        val meter =
            offlineLibraryMeter(
                downloaded = 1,
                capable = 59,
                packsBytes = ONE_PACK,
                volumeBytes = VOLUME,
                freeBytes = FREE,
            )

        assertThat(meter.fraction).isLessThan(0.005f)
        assertThat(meter.fraction).isGreaterThan(0f)
    }

    // ---- the degrade states, which are the point -----------------------------

    /**
     * **The R8 case.** The store directory is not where research measured it, so
     * the size is unknown — and unknown must not render as zero. Free space is
     * reported instead: it is the number the user wanted the size for.
     */
    @Test
    fun `an absent model store degrades to free space, never to zero used`() {
        val meter =
            offlineLibraryMeter(
                downloaded = 5,
                capable = 59,
                packsBytes = null,
                volumeBytes = VOLUME,
                freeBytes = FREE,
            )

        assertThat(meter).isEqualTo(
            OfflineLibraryMeter.Unsized(downloaded = 5, capable = 59, freeBytes = FREE),
        )
        assertWithMessage(
            "A null walk rendered as a Sized(0) would print \"0 B used\" — a claim about the " +
                "disk that nothing checked (ruling risk R8).",
        ).that(meter)
            .isNotInstanceOf(OfflineLibraryMeter.Sized::class.java)
    }

    /**
     * The same finding arriving by the other road: the directory IS there, but
     * ML Kit has moved the models out of it, so the walk sums to zero while the
     * catalogue says five packs are installed. Zero is a *number* here rather
     * than an absence, which is exactly what makes it dangerous — it would print
     * "0 B used" beside "5 of 59 packs" and look like a rendering quirk instead
     * of a broken store path.
     */
    @Test
    fun `an empty store with packs installed is a degrade, not a zero`() {
        val meter =
            offlineLibraryMeter(
                downloaded = 5,
                capable = 59,
                packsBytes = 0L,
                volumeBytes = VOLUME,
                freeBytes = FREE,
            )

        assertThat(meter).isInstanceOf(OfflineLibraryMeter.Unsized::class.java)
    }

    /** A degraded card cannot draw a fill: a fraction of the disk is what is unknown. */
    @Test
    fun `a degraded meter has no bar`() {
        val meter =
            offlineLibraryMeter(
                downloaded = 5,
                capable = 59,
                packsBytes = null,
                volumeBytes = VOLUME,
                freeBytes = FREE,
            )

        assertThat(meter.fraction).isEqualTo(0f)
    }

    // ---- zero outranks both --------------------------------------------------

    /**
     * **A zero count says "nothing downloaded", whatever the disk says.**
     *
     * The COUNT decides this one, and it is knowable from the catalogue without
     * touching the disk — which matters because the byte answer beside it can be
     * anything at all: `null` when there is no store, `0` when there is an empty
     * one, and a positive number when a deleted pack has left files behind. None
     * of those is a size of anything the user has.
     *
     * The name this test used to carry was "a fresh install says nothing
     * downloaded", and that claim was false — see the pivot test below.
     */
    @Test
    fun `a zero count says nothing downloaded`() {
        val meter =
            offlineLibraryMeter(
                downloaded = 0,
                capable = 59,
                packsBytes = null,
                volumeBytes = VOLUME,
                freeBytes = FREE,
            )

        assertThat(meter).isEqualTo(OfflineLibraryMeter.Empty(capable = 59))
        assertThat(meter.downloaded).isEqualTo(0)
        assertThat(meter.fraction).isEqualTo(0f)
    }

    /**
     * …and it says so even when the store DOES exist and is measurable. A device
     * that downloaded a pack and deleted it again keeps the directory, so the
     * walk can succeed while the count is zero; "nothing downloaded" is still
     * the sentence, because it is the one about the user's packs.
     */
    @Test
    fun `zero packs is empty even when the store is readable`() {
        assertThat(
            offlineLibraryMeter(
                downloaded = 0,
                capable = 59,
                packsBytes = 0L,
                volumeBytes = VOLUME,
                freeBytes = FREE,
            ),
        ).isEqualTo(OfflineLibraryMeter.Empty(capable = 59))
    }

    /**
     * The same rule where it actually bites: files left under the store after the
     * packs were deleted. The walk sums them happily, and a meter that let the
     * BYTES decide would print a size beside a count of nought — "0 of 59 packs ·
     * 42.1 MB used", a size for something the user does not have.
     */
    @Test
    fun `zero packs stays empty even when the store still holds bytes`() {
        assertThat(
            offlineLibraryMeter(
                downloaded = 0,
                capable = 59,
                packsBytes = ONE_PACK,
                volumeBytes = VOLUME,
                freeBytes = FREE,
            ),
        ).isEqualTo(OfflineLibraryMeter.Empty(capable = 59))
    }

    /**
     * **What a first run actually draws — measured, after PR-15 got it wrong.**
     *
     * The PR argued that letting the count decide first kept a fresh install off
     * the free-space line. Co-verify ran the `pm clear` that claim needed
     * (E-S1b, `emulator-5554`, 2026-08-02) and the picker's first frame read
     * **"Offline library · 1 · of 59 packs · 8.6 GB free"**, with "1 of 59 on
     * device" in the top bar and the English row marked "On device" — while
     * `no_backup/` held nothing but a Firebase installation file and the model
     * store did not exist.
     *
     * So on hardware with Play Services the first card is [Unsized]: ML Kit
     * counts the English pivot from launch, and the walk still answers `null`
     * because nothing has been written. The tempting repair — treat a null walk
     * as "nothing downloaded" — would print "1 of 59 packs · nothing downloaded",
     * a card at war with the row three inches from it. Free space is what both
     * readings of a null walk support, and it is the number a user about to
     * download something wants.
     */
    @Test
    fun `a first run reports free space because the pivot pack already counts`() {
        val meter =
            offlineLibraryMeter(
                downloaded = 1,
                capable = 59,
                packsBytes = null,
                volumeBytes = VOLUME,
                freeBytes = FREE,
            )

        assertThat(meter).isEqualTo(
            OfflineLibraryMeter.Unsized(downloaded = 1, capable = 59, freeBytes = FREE),
        )
        assertWithMessage(
            "A first run has a pack ML Kit counts and no store to walk. Calling that " +
                "\"nothing downloaded\" contradicts the English row's own \"On device\" badge.",
        ).that(meter)
            .isNotInstanceOf(OfflineLibraryMeter.Empty::class.java)
    }

    // ---- arithmetic that must not blow up ------------------------------------

    /**
     * A volume that reports less total than the packs occupy is not a real disk,
     * but `StatFs` is a platform call over a mount that can be reported oddly,
     * and a progress bar handed 1.4 either throws or draws past its own end.
     */
    @Test
    fun `a volume that reports less total than used cannot draw past full`() {
        val meter =
            offlineLibraryMeter(
                downloaded = 59,
                capable = 59,
                packsBytes = VOLUME * 2,
                volumeBytes = VOLUME,
                freeBytes = 0L,
            )

        assertThat(meter.fraction).isEqualTo(1f)
    }

    /** A zero or negative volume is unusable as a denominator — no bar, no division. */
    @Test
    fun `a volume of zero yields no bar instead of a divide by zero`() {
        val meter =
            offlineLibraryMeter(
                downloaded = 1,
                capable = 59,
                packsBytes = ONE_PACK,
                volumeBytes = 0L,
                freeBytes = 0L,
            )

        assertThat(meter.fraction).isEqualTo(0f)
        assertThat(meter.fraction.isNaN()).isFalse()
    }

    // ---- the type itself -----------------------------------------------------

    /**
     * Every state carries the count, because every drawn line starts with it.
     * A state that could not answer would have to be special-cased at the draw
     * site, which is where a fourth, undocumented sentence would appear.
     */
    @Test
    fun `every state can state its own count`() {
        val states =
            listOf(
                OfflineLibraryMeter.Empty(capable = 59),
                OfflineLibraryMeter.Sized(5, 59, ONE_PACK, VOLUME),
                OfflineLibraryMeter.Unsized(5, 59, FREE),
            )

        assertThat(states.map { it.capable }).containsExactly(59, 59, 59)
        assertThat(states.map { it.downloaded }).containsExactly(0, 5, 5)
        assertThat(states.map { it.fraction }.all { it in 0f..1f }).isTrue()
    }

    /**
     * `Empty` cannot be handed a count, so no caller can construct the one
     * contradiction the type would otherwise allow — an "empty" library with
     * packs in it.
     */
    @Test
    fun `an empty library is empty by construction`() {
        assertThat(OfflineLibraryMeter.Empty(capable = 59).downloaded).isEqualTo(0)
    }

    /**
     * A negative count is not a smaller library, it is a bug upstream, and the
     * honest reading of "fewer than zero packs" is the same as zero.
     */
    @Test
    fun `a negative count reads as empty rather than as a library`() {
        assertThat(
            offlineLibraryMeter(
                downloaded = -1,
                capable = 59,
                packsBytes = ONE_PACK,
                volumeBytes = VOLUME,
                freeBytes = FREE,
            ),
        ).isInstanceOf(OfflineLibraryMeter.Empty::class.java)
    }

    /**
     * **Every state the card can draw has a preview that reviews it** (CLAUDE.md
     * rule 7), pinned by reading both files rather than by remembering.
     *
     * The owner reviews this card from previews and nowhere else — there is no
     * Compose test runtime in this module (#186) and CI compiles instrumented
     * tests without running them (#40) — so a state added without one is a state
     * that ships unlooked-at. The degrade case is precisely the one that would
     * be added quietly: it is rare, it is hard to reach on a device, and it is
     * the one whose wording is load-bearing.
     *
     * Read as source for the same reason `PickerHostAgnosticTest` is: reflection
     * over a sealed interface needs `kotlin-reflect`, which is not on this
     * module's test classpath, and adding it to check a naming convention would
     * be a dependency bought for one assertion.
     */
    @Test
    fun `every meter state has a preview`() {
        val declared =
            Regex("""data (?:class|object) (\w+)\s*\(?[^)]*\)?\s*:\s*OfflineLibraryMeter""")
                .findAll(readSource(MODEL_SOURCE))
                .map { it.groupValues[1] }
                .toList()

        // Never vacuous: a moved or renamed model file declares nothing and
        // would satisfy every "has a preview" below by having no states.
        assertWithMessage("no OfflineLibraryMeter states found in $MODEL_SOURCE")
            .that(declared)
            .containsExactly("Empty", "Sized", "Unsized")

        val screen = readSource(SCREEN_SOURCE)
        val previewed =
            Regex("""private fun (OfflineLibraryMeter\w+Preview)\(""")
                .findAll(screen)
                .map { it.groupValues[1] }
                .toList()

        // The ruling names three by hand: Zero, Packs, Degraded.
        assertWithMessage("PR-15's ruling entry names these three previews by name")
            .that(previewed)
            .containsAtLeast(
                "OfflineLibraryMeterZeroPreview",
                "OfflineLibraryMeterPacksPreview",
                "OfflineLibraryMeterDegradedPreview",
            )
        assertWithMessage(
            "$previewed previews for ${declared.size} states — every state the card can draw " +
                "needs one, because previews are the only place this card is reviewed.",
        ).that(previewed.size)
            .isAtLeast(declared.size)
        // …and each of them is a light/dark pair rather than a single frame.
        assertThat(Regex("""@PreviewLightDark""").findAll(screen).count())
            .isAtLeast(previewed.size)
    }

    private fun readSource(path: String): String {
        val checkoutRoot =
            generateSequence(java.io.File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
                .first { java.io.File(it, "settings.gradle.kts").isFile }
        return checkoutRoot.resolve(path).readText()
    }

    /** Guard against the two-leaf flag being settable without a second pane (M1's neighbour). */
    @Test
    fun `a leaf arrangement without a pane is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            PickerArrangement(twoPane = false, columns = 1, twoLeaf = true)
        }
    }

    private companion object {
        /** E-S1: one af↔en pack, 30 regular files, measured on `emulator-5554`. */
        const val ONE_PACK = 44_169_505L

        /** E-S1: `df /data` reported 10,167,132 KiB total, 8,448,928 KiB available. */
        const val VOLUME = 10_411_143_168L
        const val FREE = 8_651_702_272L

        const val TOLERANCE = 1e-6f

        const val MODEL_SOURCE =
            "feature/language/src/main/kotlin/com/codeboxlk/tranzlate/feature/language/LanguagePickerModel.kt"
        const val SCREEN_SOURCE =
            "feature/language/src/main/kotlin/com/codeboxlk/tranzlate/feature/language/LanguagePickerScreen.kt"
    }
}
