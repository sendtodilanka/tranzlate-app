package com.codeboxlk.tranzlate.feature.language

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme
import com.codeboxlk.tranzlate.core.model.OfflineModelFailure
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
 * **The two-pane golden locks the CONFORMANCE 20d against `specs/20d.json`.** Recorded against
 * the rebuilt, golden-spec-conformed frame, `managePacksTwoPaneExpanded` locks the whole 20d:
 * on the LEFT the sized storage card, the green hygiene nudge, the downloading Arabic card, the
 * dense on-device rows (Afrikaans selected, Spanish IN USE, German/Polish stale-grey) and the
 * failed Hindi row; on the RIGHT the identity + on-device status, the THREE capability cards
 * (Text/Voice/Camera — Camera GREEN per the owner override), "Where this pack is used" with the
 * saved-phrases line, the single last-used line and the pair-share line, and the Remove action.
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
     * The exact 20d frame fixture (make-or-break data, owner 2026-08-06): the seven packs in the
     * frame's hand-arranged order — a downloading Arabic, a failed Hindi, and on-device Afrikaans
     * (a week ago), Spanish (IN USE, today), English (today), German + Polish (stale since April).
     */
    private fun twentyDSections(now: Long): ManagePacksSections {
        fun pack(
            id: String,
            name: String,
            state: OfflineModelState,
            usage: PackUsage,
            inUse: Boolean = false,
            voice: Boolean = false,
        ) = PackRow(id = id, displayName = name, state = state, usage = usage, inUse = inUse, hasOfflineVoice = voice)
        return ManagePacksSections(
            downloading = listOf(pack("ar", "Arabic", OfflineModelState.Downloading, PackUsage.NoRecord)),
            failed =
                listOf(pack("hi", "Hindi", OfflineModelState.Failed(OfflineModelFailure.NETWORK), PackUsage.NoRecord)),
            onDevice =
                listOf(
                    pack(
                        "af",
                        "Afrikaans",
                        OfflineModelState.Downloaded,
                        PackUsage.Used(now - 7 * DAY_MILLIS),
                        voice = true,
                    ),
                    pack("es", "Spanish", OfflineModelState.Downloaded, PackUsage.Used(now), inUse = true),
                    pack("en", "English", OfflineModelState.Downloaded, PackUsage.Used(now)),
                    pack("de", "German", OfflineModelState.Downloaded, PackUsage.Used(now - 120 * DAY_MILLIS)),
                    pack("pl", "Polish", OfflineModelState.Downloaded, PackUsage.Used(now - 120 * DAY_MILLIS)),
                ),
            downloadable = emptyList(),
        )
    }

    /**
     * The 20d two-pane at EXPANDED width (`w1280dp-h800dp`, the ruling's tablet frame and the
     * same gate `ManagePacksListDetailRenderTest` drives), locking the CONFORMANCE build against
     * the golden spec `docs/design/language-screens/specs/20d.json` — the browser-computed
     * layout of the rev5 20d frame. The fixture is the exact frame data (make-or-break test,
     * owner 2026-08-06): the detail pane on Afrikaans; the list = Afrikaans (selected, used a
     * week ago), Spanish (IN USE, today), English (today), German + Polish (stale since April,
     * grey avatars), a downloading Arabic card, a failed Hindi row; the 110 MB sized storage
     * card and the 2-packs hygiene nudge.
     *
     * The sections are built DIRECTLY (not through `buildManagePacksSections`) so the on-device
     * order matches the frame's hand-arranged rows (Afrikaans first). The shipped sort pins the
     * IN-USE pack first (Spanish) and orders by recency — a deliberate difference from this
     * design mock, flagged for the owner; the sort itself is locked by `ManagePacksModelTest`,
     * not this render. Afrikaans is SELECTED by a tap (the frame's selected pack) because the
     * screen's default selection is the first row of downloading+failed+on-device — a transient
     * Arabic here — which is itself flagged as a UX follow-up.
     *
     * The Arabic row's indeterminate progress bar renders at its initial animation frame under
     * Robolectric (no running clock), so the capture stays deterministic within the 1% threshold.
     */
    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun managePacksTwoPaneExpanded() {
        val now = NOW
        compose.setContent {
            TranzlateTheme {
                ManagePacksContent(
                    loading = false,
                    sections = twentyDSections(now),
                    // SI byte values so Formatter.formatShortFileSize renders the frame's exact
                    // "110 MB" / "1.4 GB" (the formatter divides by 1000, not 1024); the total
                    // gives the device-used bar ≈ 88%, matching the frame's fill.
                    storage =
                        StorageCard.Sized(
                            packCount = 5,
                            packsBytes = 110_000_000L,
                            freeBytes = 1_400_000_000L,
                            totalBytes = 12_000_000_000L,
                        ),
                    nudge = HygieneNudge(stalePackCount = 2),
                    suggestions = emptyList(),
                    capable = 59,
                    total = 194,
                    usageAsSource = mapOf("af" to now - 7 * DAY_MILLIS),
                    usageAsTarget = emptyMap(),
                    nowMillis = now,
                    onBack = {},
                    onGet = {},
                    onStopDownload = {},
                    onRetry = {},
                    onRemove = {},
                    onDismissNudge = {},
                    onBrowseAll = {},
                    // The detail's saved-phrases line reads this for the selected pack (frame: 3).
                    onSavedCount = { 3 },
                )
            }
        }
        compose.waitForIdle()
        // Select Afrikaans — the frame's selected pack — the first on-device selectable row.
        compose.onAllNodesWithTag("tt_manage_select_row")[0].performClick()
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
