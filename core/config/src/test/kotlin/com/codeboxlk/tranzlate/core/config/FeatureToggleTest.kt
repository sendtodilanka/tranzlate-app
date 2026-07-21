package com.codeboxlk.tranzlate.core.config

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class FeatureToggleTest {
    @Test
    fun `parseCsv maps names case-insensitively and trims whitespace`() {
        val parsed = FeatureToggle.parseCsv(" text , CAMERA,history,settings ")

        assertThat(parsed).containsExactly(
            FeatureToggle.TEXT,
            FeatureToggle.CAMERA,
            FeatureToggle.HISTORY,
            FeatureToggle.SETTINGS,
        )
    }

    @Test
    fun `parseCsv collapses duplicates into a set`() {
        assertThat(FeatureToggle.parseCsv("text,text,TEXT")).containsExactly(FeatureToggle.TEXT)
    }

    @Test
    fun `parseCsv of blank csv is an empty set`() {
        assertThat(FeatureToggle.parseCsv("")).isEmpty()
        assertThat(FeatureToggle.parseCsv(" , ,")).isEmpty()
    }

    @Test
    fun `parseCsv rejects unknown feature names`() {
        assertThrows(IllegalArgumentException::class.java) {
            FeatureToggle.parseCsv("text,teleport")
        }
    }
}
