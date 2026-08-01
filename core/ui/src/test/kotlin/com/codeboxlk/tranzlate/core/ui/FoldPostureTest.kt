package com.codeboxlk.tranzlate.core.ui

import androidx.compose.material3.adaptive.HingeInfo
import androidx.compose.material3.adaptive.Posture
import androidx.compose.ui.geometry.Rect
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The 17b-vs-17c discriminator (#130 rev.3 ruling §2, "Adaptive").
 *
 * Width cannot make this call: an unfolded inner display at 760dp and a tablet
 * at 800dp are both [WindowWidthClass.MEDIUM], so the branch between the
 * foldable two-leaf layout and the dialog host is posture or nothing.
 *
 * These are plain JVM tests because [foldPosture] is a plain function. `Posture`,
 * `HingeInfo` and `Rect` are all pure Kotlin — Material derives them from
 * WindowManager's `FoldingFeature`, but the derived form carries no Android type,
 * so the decision this file checks is reachable without a device. That matters
 * here: this repo has no Compose unit-test runtime (#186) and CI compiles
 * instrumented tests without running them (#40), so anything left inside
 * `rememberWindowInfo()` would be checked by nothing at all.
 *
 * Hinge shapes are written against
 * `androidx.compose.material3.adaptive:adaptive:1.2.0`,
 * `AndroidPosture.android.kt:29-51`: `isTabletop` is set for a HORIZONTAL hinge
 * in the HALF_OPENED state; `HingeInfo.isFlat` is `state == FLAT`;
 * `HingeInfo.isVertical` is `orientation == VERTICAL`.
 */
class FoldPostureTest {
    @Test
    fun `a device with no hinge at all is flat`() {
        assertThat(foldPosture(Posture())).isEqualTo(FoldPosture.FLAT)
    }

    /** A foldable opened right out is one surface — nothing to lay out in two leaves. */
    @Test
    fun `a fold opened out flat is flat`() {
        assertThat(foldPosture(posture(hinge(flat = true, vertical = true))))
            .isEqualTo(FoldPosture.FLAT)
    }

    @Test
    fun `a half-open vertical hinge is book posture`() {
        assertThat(foldPosture(posture(hinge(flat = false, vertical = true))))
            .isEqualTo(FoldPosture.BOOK)
    }

    /**
     * Tabletop comes from Material's own flag, not from re-deriving it: it is set
     * for a half-open HORIZONTAL hinge, and the `hingeList` entry carries the
     * same facts. Both are supplied here so the test would still be honest if the
     * implementation read either one.
     */
    @Test
    fun `a half-open horizontal hinge is tabletop posture`() {
        val posture = Posture(isTabletop = true, hingeList = listOf(hinge(flat = false, vertical = false)))

        assertThat(foldPosture(posture)).isEqualTo(FoldPosture.TABLETOP)
    }

    /**
     * The case that keeps `hinged` and [FoldPosture] as two separate questions: a
     * dual-screen device held fully open is FLAT, and still has a hinge that
     * splits the window into two logical areas. Layout must route content around
     * it (that is `hinged`) without treating the device as half-closed.
     */
    @Test
    fun `a flat but separating hinge is still flat posture`() {
        val posture = posture(hinge(flat = true, vertical = true, separating = true))

        assertThat(foldPosture(posture)).isEqualTo(FoldPosture.FLAT)
    }

    /**
     * A book fold whose crease does NOT separate the window is still book
     * posture. The two properties are independent in the platform, and reading
     * `isSeparating` as if it meant "folded" is the mistake this asserts against
     * — it is what `WindowInfo.hinged` already means, and posture would then be a
     * second name for it rather than the new answer 17b needs.
     */
    @Test
    fun `a half-open hinge that does not separate is still book posture`() {
        val posture = posture(hinge(flat = false, vertical = true, separating = false))

        assertThat(foldPosture(posture)).isEqualTo(FoldPosture.BOOK)
    }

    /** Both creases at once: the horizontal one is the one that crosses the content. */
    @Test
    fun `a tabletop hinge wins over a book one`() {
        val posture =
            Posture(
                isTabletop = true,
                hingeList =
                    listOf(
                        hinge(flat = false, vertical = true),
                        hinge(flat = false, vertical = false),
                    ),
            )

        assertThat(foldPosture(posture)).isEqualTo(FoldPosture.TABLETOP)
    }

    private fun posture(vararg hinges: HingeInfo) = Posture(isTabletop = false, hingeList = hinges.toList())

    private fun hinge(
        flat: Boolean,
        vertical: Boolean,
        separating: Boolean = true,
    ) = HingeInfo(
        bounds = Rect(0f, 0f, 1f, 1f),
        isFlat = flat,
        isVertical = vertical,
        isSeparating = separating,
        isOccluding = false,
    )
}
