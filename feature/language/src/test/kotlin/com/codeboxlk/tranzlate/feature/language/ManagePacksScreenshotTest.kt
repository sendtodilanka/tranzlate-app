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
 * The Roborazzi regression lock for Manage packs (#333 harness, #338 20d golden).
 *
 * TWO goldens, both captured through the same `createComposeRule` the module's render
 * tests already use — so the screenshot harness sits on the existing Robolectric
 * compose-test foundation, not beside it:
 *  - `ManagePacksContent` at COMPACT width (`w411dp-h891dp`), the phone frame (#333);
 *  - `ManagePacksContent` at EXPANDED width (`w1280dp-h800dp`), the 20d two-pane (#338).
 *
 * **The two-pane golden locks the FIXED 20d.** #337 deliberately DROPPED it: on `main`
 * the two-pane was still the pre-fix `ManagePacksDetailPane` (avatar+name, a bare "Usage"
 * header, a duplicated storage card), and a golden of that would have locked the exact
 * defect #338 fixes. Recorded here against the rebuilt, conformance-verified detail pane,
 * `managePacksTwoPaneExpanded` locks the design #338 ships: identity + on-device status,
 * the Text/Voice capability cards, "Where this pack is used" with the saved-phrases line,
 * the per-role usage, and the Remove action.
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

    /**
     * The 20d two-pane at EXPANDED width (`w1280dp-h800dp`, the ruling's tablet frame and
     * the same gate `ManagePacksListDetailRenderTest` drives), locking the FIXED detail
     * pane (#338). Method-level `@Config` overrides the class's compact qualifier so this
     * one test draws `ManagePacksTwoPane`.
     *
     * The fixture ids are REAL (`fr`, `de`): `displayName` derives from the id via
     * `languageDisplayName(id, …, fallback = row.name)` (`OfflinePackRow.kt`), so `name`
     * alone would not render "French"/"German". French is the current target
     * (`targetId = "fr"`) → the default selection the detail draws, and it carries an
     * on-device voice, a saved count, and a target-role stamp, so every non-ruled block —
     * the two capability cards, "Where this pack is used" + the saved-phrases line, the
     * per-role usage, the Remove action — is exercised in one frame. Only downloaded rows,
     * kept STATIC like the compact fixture: no `Downloading` row whose indeterminate
     * progress would animate the captured frame.
     */
    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun managePacksTwoPaneExpanded() {
        val rows =
            listOf(
                OfflineLanguageRow("fr", "French", OfflineModelState.Downloaded, hasOfflineVoice = true),
                OfflineLanguageRow("de", "German", OfflineModelState.Downloaded),
            )
        val sections =
            buildManagePacksSections(rows, usage = mapOf("fr" to NOW), targetId = "fr", locale = Locale.ENGLISH)
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
                    usageAsSource = emptyMap(),
                    usageAsTarget = mapOf("fr" to NOW),
                    nowMillis = NOW,
                    onBack = {},
                    onGet = {},
                    onStopDownload = {},
                    onRetry = {},
                    onRemove = {},
                    onDismissNudge = {},
                    onBrowseAll = {},
                    // The detail's saved-phrases line reads this for the selected pack (#332).
                    onSavedCount = { 4 },
                )
            }
        }
        compose.waitForIdle()

        compose.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/ManagePacksTwoPane_expanded_w1280dp_h800dp.png",
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

        /** A fixed "now", and the French target-role stamp, so "used today" is stable across renders. */
        const val NOW = 1_000_000_000_000L
    }
}
