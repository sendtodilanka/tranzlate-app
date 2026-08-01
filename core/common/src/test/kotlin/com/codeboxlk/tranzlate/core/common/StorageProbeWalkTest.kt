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
 * dir-rename simulation) — is PENDING; it is an emulator experiment and is
 * deliberately not faked here.
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
}
