package com.codeboxlk.tranzlate.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Pins the U-5 walk contract behind [StorageProbe.packsBytes] (issue #130
 * ruling, PR-11): correct recursive sum, and the honest degrade — absent or
 * non-directory store → `null`, never 0-as-fact.
 *
 * E-S1 (ruling risk R8) — the on-device pin (real pack download → sum > 0;
 * dir-rename simulation) — RAN on 2026-08-02 and passed both halves, on
 * `emulator-5554` under PR-15: 30 files, 44,169,505 bytes for one af↔en pack,
 * and a renamed store yielding nothing rather than zero
 * (`docs/research/issue-130-e-s1-storage-walk.md`). It stays an emulator
 * experiment and is deliberately not faked here — the cases below are the
 * CONTRACT, and the experiment is what says the contract is pointed at the
 * right directory.
 *
 * E-S1c (co-verify, same device, same day) added the scratch-directory cases at
 * the foot of this file: the walk used to count an interrupted download's
 * leftovers as pack bytes, forever, and one stray model file overstated the
 * drawn card by 34%.
 */
class StorageProbeWalkTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `nested files sum to one aggregate byte count`() {
        val store = tmp.newFolder("com.google.mlkit.translate.models")
        // Shape mirrors research E3: per-pair dirs holding model + resource files.
        File(store, "de_en").mkdirs()
        File(store, "de_en/translate_deen.bin").writeBytes(ByteArray(7))
        File(store, "de_en/nested").mkdirs()
        File(store, "de_en/nested/resources.bin").writeBytes(ByteArray(11))
        File(store, "manifest.json").writeBytes(ByteArray(5))

        assertThat(packsBytesOf(store)).isEqualTo(23L)
    }

    @Test
    fun `directories themselves add no bytes`() {
        val store = tmp.newFolder("models")
        File(store, "en_fr").mkdirs()
        File(store, "en_fr/only.bin").writeBytes(ByteArray(3))

        assertThat(packsBytesOf(store)).isEqualTo(3L)
    }

    @Test
    fun `absent store dir degrades to null - not zero`() {
        val absent = File(tmp.root, "com.google.mlkit.translate.models")

        assertThat(packsBytesOf(absent)).isNull()
    }

    @Test
    fun `a plain file where the store dir should be degrades to null`() {
        val impostor = tmp.newFile("com.google.mlkit.translate.models")

        assertThat(packsBytesOf(impostor)).isNull()
    }

    @Test
    fun `existing but empty store is a real zero`() {
        val store = tmp.newFolder("com.google.mlkit.translate.models")

        assertThat(packsBytesOf(store)).isEqualTo(0L)
    }

    // ---- the scratch directory (co-verify F3 / E-S1c) -------------------------

    /**
     * **An interrupted download's leftovers are not pack bytes, and nothing
     * sweeps them up.**
     *
     * E-S1 recorded `temp/af_en/` as an empty sibling of the pack directory once
     * a download had settled, and filed the mid-download over-report as bounded
     * to "a few seconds". Co-verify showed it is not bounded at all: copying one
     * real 14,779,264-byte model file into `temp/af_en/` — the shape an
     * interrupted download leaves behind — moved the drawn card from 44 MB to
     * 59 MB, and stayed there, while the catalogue still correctly read "2 of 59
     * packs". The numbers below are those measurements.
     */
    @Test
    fun `an interrupted download's temp debris is not counted as pack bytes`() {
        val store = tmp.newFolder("com.google.mlkit.translate.models")
        File(store, "af_en").mkdirs()
        File(store, "af_en/merged_dict_af_en_25_from_en.bin").writeBytes(ByteArray(PACK_FILE))
        File(store, "temp/af_en").mkdirs()
        File(store, "temp/af_en/merged_dict_af_en_25_from_en.bin").writeBytes(ByteArray(PACK_FILE))

        assertThat(packsBytesOf(store)).isEqualTo(PACK_FILE.toLong())
    }

    /**
     * …and ONLY that one directory. The exclusion is the store-root scratch area
     * ML Kit was observed to use, not the name `temp` wherever it appears: a
     * folder of that name inside a pack is the pack's own layout and its bytes
     * are on the disk, and a plain FILE called `temp` at the root is a file.
     * Both would vanish from the total under a name filter, which is the
     * plausible way to write this and the wrong one.
     */
    @Test
    fun `only the store-root scratch dir is skipped`() {
        val store = tmp.newFolder("com.google.mlkit.translate.models")
        File(store, "af_en/temp").mkdirs()
        File(store, "af_en/temp/resources.bin").writeBytes(ByteArray(11))
        File(store, "temp").writeBytes(ByteArray(5))

        assertThat(packsBytesOf(store)).isEqualTo(16L)
    }

    private companion object {
        /** E-S1's largest single file — the one co-verify copied into `temp/`. */
        const val PACK_FILE = 14_779_264
    }
}
