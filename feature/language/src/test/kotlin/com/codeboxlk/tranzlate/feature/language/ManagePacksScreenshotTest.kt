package com.codeboxlk.tranzlate.feature.language

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * The Roborazzi proof-of-concept + regression lock for Manage packs (#333).
 *
 * ONE golden: the SHIPPED `ManagePacksContent` at COMPACT width (`w411dp-h891dp`), the
 * phone frame, captured through the same `createComposeRule` the module's render tests
 * already use — so the screenshot harness sits on the existing Robolectric compose-test
 * foundation, not beside it.
 *
 * **Why only the compact pane, not the 20d two-pane.** The two-pane at expanded width is
 * still the PRE-FIX 20d on `main` (`ManagePacksDetailPane` — avatar+name, a bare "Usage"
 * header, a duplicated storage card); a golden of it would lock the exact defect PR #338
 * fixes. The two-pane golden lands with #338, recorded against the FIXED + conformance-
 * verified 20d. This PR ships only the compact pane, which a conformance review has
 * confirmed correct.
 *
 * Native graphics (`@GraphicsMode(NATIVE)`) is what makes `captureRoboImage()` produce
 * real pixels under Robolectric; the path names the committed golden under the module's
 * `src/test/screenshots` (the dir `tranzlate.compose-test` pins as
 * `RoborazziExtension.outputDir`). Record with
 * `./gradlew :feature:language:recordRoborazziDebug`; `./gradlew build` verifies.
 *
 * **Comparison threshold — a deliberate 1%, not the Roborazzi default.** Roborazzi's
 * default validator is `ThresholdValidator(0F)` — pixel-exact (RoborazziOptions.common.kt
 * at tag 1.70.0). Pixel-exact is right only when goldens are recorded and verified in one
 * environment; these are recorded on macOS (Android Studio JBR) and verified by CI on
 * Linux, and Robolectric-native-graphics font antialiasing can differ by a handful of
 * sub-pixels across OSes (roborazzi#180 is a thread of exactly this macOS-record /
 * Linux-verify split at threshold 0). So the SET value is `0.01` (1% of pixels) — this is
 * the REGRESSION lock, and 1% sits far below the smallest real regression (a
 * missing/changed element is a large-area diff — the self-test below moves ~8% of the
 * phone frame) while absorbing cross-OS AA noise. The first Linux CI run is the empirical
 * calibration: if a conformant golden still reddens there, read the differing-pixel
 * fraction from the `*_compare.png` and either nudge the value just above that floor or
 * record the goldens on Linux/CI. Initial DESIGN conformance is the cross-model
 * co-verify's job, not this threshold's.
 *
 * **Self-test (the harness must be shown to FIRE, #333):** flip [twoPacks]' second pack
 * from `Downloaded` to `OfflineModelState.Failed(OfflineModelFailure.NETWORK)`. That moves
 * it into a failed row with its own error treatment and a Retry control — a whole
 * differently-styled row, far past the 1% threshold — so `verifyRoborazziDebug` reddens
 * with a `*_compare.png` diff, and reverting greens it. Evidence is in the PR.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class ManagePacksScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    /**
     * Two downloaded packs — German, Spanish (alphabetical, both date-less). The same
     * literal fixture `ManagePacksListDetailRenderTest` renders, kept deliberately
     * STATIC: no `Downloading` row, whose indeterminate progress would animate and make
     * the captured frame non-deterministic.
     *
     * The self-test mutation lives on the marked line below.
     */
    private fun twoPacks() =
        listOf(
            OfflineLanguageRow("de", "German", OfflineModelState.Downloaded),
            // ── self-test mutation point (#333): Downloaded -> Failed(NETWORK) ──
            OfflineLanguageRow("es", "Spanish", OfflineModelState.Downloaded),
        )

    private fun show(rows: List<OfflineLanguageRow>) {
        val sections =
            buildManagePacksSections(rows, usage = emptyMap(), targetId = "", locale = Locale.ENGLISH)
        compose.setContent {
            TranzlateTheme {
                ManagePacksContent(
                    loading = false,
                    sections = sections,
                    storage = StorageCard.FreeOnly(packCount = rows.size, freeBytes = 8L * 1024 * 1024 * 1024),
                    nudge = null,
                    suggestions = emptyList(),
                    capable = 59,
                    total = 194,
                    onBack = {},
                    onGet = {},
                    onStopDownload = {},
                    onRetry = {},
                    onRemove = {},
                    onDismissNudge = {},
                    onBrowseAll = {},
                )
            }
        }
        compose.waitForIdle()
    }

    /** The single phone pane at compact width — no detail pane. */
    @Test
    fun managePacksContentCompact() {
        show(twoPacks())

        compose.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/ManagePacksContent_compact_w411dp_h891dp.png",
            roborazziOptions =
                RoborazziOptions(
                    compareOptions = RoborazziOptions.CompareOptions(changeThreshold = COMPARE_THRESHOLD),
                ),
        )
    }

    private companion object {
        /**
         * 1% of pixels — see the class KDoc. The regression lock's tolerance for cross-OS
         * font-AA noise, well below the smallest real UI regression.
         */
        const val COMPARE_THRESHOLD = 0.01f
    }
}
