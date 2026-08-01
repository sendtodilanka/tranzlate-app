package com.codeboxlk.tranzlate.feature.language

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The A–Z rail's sampler. These two cases arrived with `LanguageNamesTest` and
 * stayed behind when the rest of that suite followed `LanguageNames` into
 * `:core:ui`: the rail is picker UI, not language presentation, so `sampledTo`
 * belongs here with the screen that draws it. Bodies are unchanged.
 */
class AlphabetRailTest {
    @Test
    fun `the rail keeps its ends and never exceeds what fits`() {
        val alphabet = ('A'..'Z').mapIndexed { index, letter -> letter to index }

        val sampled = alphabet.sampledTo(10)

        assertThat(sampled).hasSize(10)
        assertThat(sampled.first()).isEqualTo('A' to 0)
        // Dropping the tail instead would make the rail lie by omission: an
        // index that stops at M in a list that runs to Z.
        assertThat(sampled.last()).isEqualTo('Z' to 25)
        assertThat(sampled.map { it.second }).isInOrder()
    }

    @Test
    fun `a rail that already fits is untouched`() {
        val five = ('A'..'E').mapIndexed { index, letter -> letter to index }

        assertThat(five.sampledTo(10)).isEqualTo(five)
    }
}
