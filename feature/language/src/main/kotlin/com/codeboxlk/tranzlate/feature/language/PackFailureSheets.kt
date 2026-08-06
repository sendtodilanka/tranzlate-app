package com.codeboxlk.tranzlate.feature.language

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SdCardAlert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.codeboxlk.tranzlate.core.designsystem.Dimensions
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing
import com.codeboxlk.tranzlate.core.designsystem.TranzlateSheetAction
import com.codeboxlk.tranzlate.core.designsystem.TranzlateSheetDefaults
import com.codeboxlk.tranzlate.core.designsystem.TranzlateSheetPreviewFrame
import com.codeboxlk.tranzlate.core.designsystem.TranzlateSheetScaffold
import com.codeboxlk.tranzlate.core.designsystem.TranzlateSheetTone
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme
import com.codeboxlk.tranzlate.core.designsystem.sheetBodyTextStyle
import com.codeboxlk.tranzlate.core.model.OfflineModelFailure

/** Sheet roots and controls — `tt_lang_sheet_*`, the rev3 ruling's sheet namespace (C-1). */
internal const val TT_SHEET_FAILED = "tt_lang_sheet_failed"
internal const val TT_SHEET_FAILED_CAUSE = "tt_lang_sheet_failed_cause"
internal const val TT_SHEET_FAILED_CLOSE = "tt_lang_sheet_failed_close"
internal const val TT_SHEET_FAILED_RETRY = "tt_lang_sheet_failed_retry"
internal const val TT_SHEET_SPACE = "tt_lang_sheet_space"
internal const val TT_SHEET_SPACE_BAR = "tt_lang_sheet_space_bar"
internal const val TT_SHEET_SPACE_MANAGE = "tt_lang_sheet_space_manage"
internal const val TT_SHEET_SPACE_FREE_UP = "tt_lang_sheet_space_free_up"

/**
 * Sheet **19d — "Interrupted"** (#130 PR-18): the download the user just asked
 * for stopped, and nothing came of it.
 *
 * ## The one place the drawing and its own caption disagree
 *
 * The frame's caption says *"The important reassurance — **progress is kept** —
 * leads the copy."* The frame's BODY says *"…so **nothing is on the device
 * yet**."* Both cannot ship, and the repo already settled which:
 * `DESIGNER-BRIEF.md:73` — *"No resume control. The system may or may not resume
 * internally; we cannot observe it, so we must not promise it. A failed download
 * offers retry, not resume. **Do not claim kept progress.**"* — and
 * `README.md:73`, which lists resume among the things replaced by *"Nothing"*.
 * `RemoteModelManager.download()` hands back a `Task<Void>`; there is no handle
 * on a partial transfer to resume, and the app's own Retry starts a fresh
 * download. **The body ships as drawn and the caption is the half that is
 * wrong.**
 *
 * The body is also literally true rather than approximately so, which is worth
 * one sentence because the project has measured the exception: an interrupted
 * download does leave bytes behind, in ML Kit's store-root scratch directory
 * (`MLKIT_SCRATCH_DIR`, E-S1c — one real 14,779,264-byte file survived
 * indefinitely). Those bytes are not a pack, cannot be translated with, are
 * excluded from the library meter, and are exactly what "nothing is on the
 * device **yet**" is about. The sentence is about the pack.
 *
 * ## Why the copy is per cause and the title is not
 *
 * The drawn 19d is written for one cause — the connection dropped. ML Kit's
 * other failures do not report one, and reusing the connection sentence for them
 * would invent a reason the app does not have. [downloadFailureCopy] therefore
 * answers with the body and the cause line together, and this composable spells
 * whichever it was handed. The title is cause-free ("Spanish did not download")
 * and needs no branch.
 *
 * ## Actions
 *
 * `Close` is the text action and carries [TranzlateSheetTone.Loss] — error INK on
 * a sheet that is otherwise ordinary, which is the whole of the caption that is
 * right: *"Failure states earn the error colour only for the fact, not the whole
 * sheet."* The fact here is the icon slot, the cause card and that one label.
 * `Retry` is filled and primary, as drawn, because it is the likely intent.
 * Dismissal is never blocked, so there is no dead end either way.
 *
 * The `@OptIn` is for the scaffold's DEFAULT `SheetState` argument, whose
 * expression is evaluated at this call site. Nothing here names a `SheetState`,
 * and nothing hoists one: `rememberModalBottomSheetState` is `rememberSaveable`
 * underneath, so hoisting would tie the sheet to whichever `SaveableStateHolder`
 * draws the picker — the host-scoped state PR-13 removed and
 * `PickerHostAgnosticTest` bans by name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InterruptedSheet(
    languageName: String,
    sheet: DownloadFailureSheet.Interrupted,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    TranzlateSheetScaffold(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.lang_sheet_failed_title, languageName),
        primaryAction =
            TranzlateSheetAction(
                label = stringResource(R.string.lang_sheet_failed_retry),
                testTag = TT_SHEET_FAILED_RETRY,
                onClick = onRetry,
            ),
        modifier = Modifier.testTag(TT_SHEET_FAILED),
        tone = TranzlateSheetTone.Loss,
        icon = { InterruptedIcon() },
        secondaryAction =
            TranzlateSheetAction(
                label = stringResource(R.string.lang_sheet_failed_close),
                testTag = TT_SHEET_FAILED_CLOSE,
                onClick = onDismiss,
                tone = TranzlateSheetTone.Loss,
            ),
        supportingContent = { CauseCard(causeRes = sheet.cause) },
        body = { Text(stringResource(sheet.body)) },
    )
}

/** The tonal slot's glyph — decorative; the title carries the meaning (scaffold KDoc). */
@Composable
private fun InterruptedIcon() {
    Icon(
        imageVector = Icons.Filled.CloudOff,
        contentDescription = null,
        modifier = Modifier.size(TranzlateSheetDefaults.IconSize),
    )
}

/**
 * 19d's cause card: what happened, and what the filled action will do about it.
 *
 * It is the one region of the sheet drawn on the error container, which is the
 * spec's reservation rule applied to a REGION rather than to the surface — the
 * fact is red, the sheet is not. Merged into one semantics node so a screen
 * reader hears one sentence rather than a glyph followed by a sentence; the
 * glyph itself is silent, like every other decorative mark in this feature.
 */
@Composable
private fun CauseCard(causeRes: Int) {
    val spacing = LocalSpacing.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm8 + spacing.xs4),
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(horizontal = spacing.md16 - spacing.xs4 / 2, vertical = spacing.sm8 + spacing.xs4)
                .semantics(mergeDescendants = true) {}
                .testTag(TT_SHEET_FAILED_CAUSE),
    ) {
        Icon(
            imageVector = Icons.Filled.Download,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(Dimensions.iconSm),
        )
        Text(
            text = stringResource(causeRes),
            style = sheetBodyTextStyle(MaterialTheme.typography),
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

/**
 * Sheet **19b — "Not enough space"** (#130 PR-18): the download never started,
 * because the disk cannot hold a pack.
 *
 * ## It is raised by a check that already existed
 *
 * `RealOfflineModelManager.download()` refuses before it enqueues anything when
 * `StorageProbe.freeBytes()` is under `REQUIRED_FREE_BYTES` (150 MB — issue
 * #90's measured pack, times three, for the store plus unzip staging). The rev3
 * ruling settled that this const IS 19b's trigger and is not an owner question.
 * So the sheet reports a refusal that was already happening silently: before
 * this PR the row simply turned red and the user was left to read a supporting
 * line for it.
 *
 * ## The bar is used against free, and never packs against device
 *
 * The spec re-drew every storage bar in rev 5 for one reason, stated in five
 * places and once here: *"at 110 MB the library cannot be plotted against a
 * whole device without misstating either the library or the 12 MB"*
 * (`docs/design/language-screens/README.md:15`). So the fill is everything on
 * the volume that is not free — "Other apps and system" — and this app's own
 * packs are not broken out of it. The breakdown is 20e's job (PR-25).
 *
 * ## The second action, now that 20e exists (#130 PR-25)
 *
 * The frame draws `Manage packs` beside a filled `Free up space`. PR-18 shipped
 * only `Manage packs` because *"Free up space opens 20e, which does not exist"* —
 * a button that opens nothing is the dead end EDGE_CASES §7 forbids, and the rev3
 * ruling pre-decided the single-action case (PR-18 row). 20e exists now, so this
 * sheet can carry both: with [onFreeUpSpace] wired, `Free up space` is the filled
 * likely-intent action and `Manage packs` the text action beside it, exactly as
 * drawn.
 *
 * [onFreeUpSpace] is **optional** for a deliberate reason: the host decides whether
 * 20e is reachable from where it raised this sheet. A host that CAN open the 20e
 * cleanup sheet passes the callback and gets both actions; a host that cannot
 * (opening 20e from that surface would be a dead-end button, or need navigation it
 * does not own) omits it and gets the single filled `Manage packs` — the same
 * no-dead-end degrade PR-18 shipped, never a button that opens nothing. So the
 * two-action frame is honoured only where it can actually work.
 *
 * The tone is [TranzlateSheetTone.Neutral], as drawn: the export paints this
 * icon slot `#d3e3fd`/`#0842a0` — the primary container pair — where 19d's is
 * the error pair. Nothing has been lost here; a download has been declined.
 *
 * The `@OptIn` is the scaffold's default `SheetState` argument — see
 * [InterruptedSheet] for why none is hoisted.
 *
 * @param onFreeUpSpace opens the 20e "Free up space" batch-cleanup sheet. When
 *   `null`, the sheet degrades to the single `Manage packs` action (no dead end).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoSpaceSheet(
    freeBytes: Long,
    volumeBytes: Long,
    onManagePacks: () -> Unit,
    onDismiss: () -> Unit,
    onFreeUpSpace: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val free = Formatter.formatShortFileSize(context, freeBytes)
    val managePacksAction =
        TranzlateSheetAction(
            label = stringResource(R.string.lang_sheet_space_manage),
            testTag = TT_SHEET_SPACE_MANAGE,
            onClick = onManagePacks,
        )
    TranzlateSheetScaffold(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.lang_sheet_space_title),
        // Filled = likely intent (spec §5). With 20e reachable, that is "Free up
        // space"; "Manage packs" moves to the text slot. Without it, "Manage packs"
        // is the lone filled action — PR-18's no-dead-end single action.
        primaryAction =
            if (onFreeUpSpace != null) {
                TranzlateSheetAction(
                    label = stringResource(R.string.lang_sheet_space_free_up),
                    testTag = TT_SHEET_SPACE_FREE_UP,
                    onClick = onFreeUpSpace,
                )
            } else {
                managePacksAction
            },
        modifier = Modifier.testTag(TT_SHEET_SPACE),
        icon = { NoSpaceIcon() },
        secondaryAction = if (onFreeUpSpace != null) managePacksAction else null,
        supportingContent = { StorageBarCard(freeLabel = free, fraction = deviceUsedFraction(freeBytes, volumeBytes)) },
        body = { Text(stringResource(R.string.lang_sheet_space_body, free)) },
    )
}

/** The tonal slot's glyph — decorative; the title carries the meaning. */
@Composable
private fun NoSpaceIcon() {
    Icon(
        imageVector = Icons.Filled.SdCardAlert,
        contentDescription = null,
        modifier = Modifier.size(TranzlateSheetDefaults.IconSize),
    )
}

/**
 * How much of the volume is NOT free, 0..1 — the one number 19b's bar draws.
 *
 * Pure, and outside the composable, for the same reason [offlineLibraryMeter] is:
 * a fraction that decides what a user is told about their disk should be
 * checkable without a screen. It clamps rather than trusts, because `StatFs` can
 * report free space above the total on a volume with reserved blocks, and a
 * fill wider than its track draws as a full bar beside the words "12 MB free".
 *
 * A volume of zero (or less) yields `0f`: unknown is not "empty", and it is not
 * "full" either. The bar then draws as bare track, which claims nothing — the
 * same honest-degrade shape as the library meter's `Unsized`.
 */
internal fun deviceUsedFraction(
    freeBytes: Long,
    volumeBytes: Long,
): Float =
    if (volumeBytes <= 0L) {
        0f
    } else {
        ((volumeBytes - freeBytes).toFloat() / volumeBytes).coerceIn(0f, 1f)
    }

/**
 * 19b's storage block: the bar, then the two legends that name its two segments.
 *
 * `LinearProgressIndicator` for the same reason the library meter uses one — it
 * is a determinate bar and M3 already draws one, with the platform's own
 * reduced-motion behaviour. Its semantics are cleared: the body above it has
 * already said "There is 12 MB free on this device" in words, and a screen
 * reader announcing "96 percent" after that is the least useful phrasing of the
 * same fact.
 */
@Composable
private fun StorageBarCard(
    freeLabel: String,
    fraction: Float,
) {
    val spacing = LocalSpacing.current
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(scheme.surfaceContainerLow)
                .padding(spacing.md16),
    ) {
        LinearProgressIndicator(
            progress = { fraction },
            color = scheme.primaryContainer,
            trackColor = scheme.surfaceContainerHighest,
            gapSize = 0.dp,
            drawStopIndicator = {},
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(StorageBarHeight)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .testTag(TT_SHEET_SPACE_BAR)
                    .clearAndSetSemantics {},
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.md16),
            modifier = Modifier.padding(top = spacing.sm8 + spacing.xs4),
        ) {
            StorageLegend(swatch = scheme.primaryContainer, label = stringResource(R.string.lang_sheet_space_used))
            StorageLegend(
                swatch = scheme.surfaceContainerHighest,
                label = stringResource(R.string.lang_sheet_space_free, freeLabel),
            )
        }
    }
}

/** One dot plus its words. The dot is decorative — the words are the legend. */
@Composable
private fun StorageLegend(
    swatch: Color,
    label: String,
) {
    val spacing = LocalSpacing.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs4 + spacing.xs4 / 2),
    ) {
        Box(
            modifier =
                Modifier
                    .size(StorageLegendDot)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(swatch),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Export-measured: a 10px track and a 9px legend dot, inside 19b's storage block. */
private val StorageBarHeight = 10.dp
private val StorageLegendDot = 9.dp

// ---- Previews (rule 7 — one per meaningful STATE) ------------------------------------------
// `ModalBottomSheet` opens a window and the tooling renders nothing for a window,
// so these draw the same anatomy on the same floating surface the host paints —
// `TranzlateSheetPreviewFrame`, which `MobileDataSheet.kt` explains and PR-17
// added to `:core:designsystem` for exactly this.

/** 19d as drawn: the connection dropped, which is the cause the frame is written for. */
@PreviewLightDark
@Composable
private fun InterruptedSheetPreview() {
    InterruptedSheetPreviewBody(OfflineModelFailure.NETWORK)
}

/**
 * 19d for a failure ML Kit did not explain — the state the frame does not draw
 * and the app still reaches. Its whole difference from the preview above is the
 * two sentences, which is exactly what the owner needs to see side by side.
 */
@PreviewLightDark
@Composable
private fun InterruptedSheetUnexplainedPreview() {
    InterruptedSheetPreviewBody(OfflineModelFailure.UNKNOWN)
}

@Composable
private fun InterruptedSheetPreviewBody(cause: OfflineModelFailure) {
    val sheet = downloadFailureCopy(cause).sheet as DownloadFailureSheet.Interrupted
    TranzlateSheetPreviewFrame(
        title = stringResource(R.string.lang_sheet_failed_title, "Spanish"),
        primaryAction =
            TranzlateSheetAction(
                label = stringResource(R.string.lang_sheet_failed_retry),
                testTag = TT_SHEET_FAILED_RETRY,
                onClick = {},
            ),
        tone = TranzlateSheetTone.Loss,
        icon = { InterruptedIcon() },
        secondaryAction =
            TranzlateSheetAction(
                label = stringResource(R.string.lang_sheet_failed_close),
                testTag = TT_SHEET_FAILED_CLOSE,
                onClick = {},
                tone = TranzlateSheetTone.Loss,
            ),
        supportingContent = { CauseCard(causeRes = sheet.cause) },
        body = { Text(stringResource(sheet.body)) },
    )
}

/** 19b as drawn: 12 MB free on a nearly-full 64 GB device — the frame's own figures. */
@PreviewLightDark
@Composable
private fun NoSpaceSheetPreview() {
    NoSpaceSheetPreviewBody(freeBytes = 12L * 1024 * 1024, volumeBytes = 64L * 1024 * 1024 * 1024)
}

/**
 * 19b when the volume cannot be measured — the honest degrade.
 *
 * `StatFs` answering zero for the total is the same class of unknown as the
 * library meter's absent model store, and the card says nothing rather than
 * drawing a full bar or an empty one as if either were a fact. The free figure
 * beside it is still real, which is why the sheet is still worth showing.
 */
@PreviewLightDark
@Composable
private fun NoSpaceSheetUnmeasuredVolumePreview() {
    NoSpaceSheetPreviewBody(freeBytes = 12L * 1024 * 1024, volumeBytes = 0L)
}

/**
 * 19b with 20e reachable (#130 PR-25): both actions the export draws — "Free up
 * space" filled (the likely intent), "Manage packs" the text action beside it.
 */
@PreviewLightDark
@Composable
private fun NoSpaceSheetWithFreeUpPreview() {
    NoSpaceSheetPreviewBody(
        freeBytes = 12L * 1024 * 1024,
        volumeBytes = 64L * 1024 * 1024 * 1024,
        withFreeUp = true,
    )
}

@Composable
private fun NoSpaceSheetPreviewBody(
    freeBytes: Long,
    volumeBytes: Long,
    withFreeUp: Boolean = false,
) {
    val free = Formatter.formatShortFileSize(LocalContext.current, freeBytes)
    val managePacks =
        TranzlateSheetAction(
            label = stringResource(R.string.lang_sheet_space_manage),
            testTag = TT_SHEET_SPACE_MANAGE,
            onClick = {},
        )
    TranzlateSheetPreviewFrame(
        title = stringResource(R.string.lang_sheet_space_title),
        primaryAction =
            if (withFreeUp) {
                TranzlateSheetAction(
                    label = stringResource(R.string.lang_sheet_space_free_up),
                    testTag = TT_SHEET_SPACE_FREE_UP,
                    onClick = {},
                )
            } else {
                managePacks
            },
        secondaryAction = if (withFreeUp) managePacks else null,
        icon = { NoSpaceIcon() },
        supportingContent = { StorageBarCard(freeLabel = free, fraction = deviceUsedFraction(freeBytes, volumeBytes)) },
        body = { Text(stringResource(R.string.lang_sheet_space_body, free)) },
    )
}

// ---- Item-level previews (#243) ---------------------------------------------
// Rule 7 names "every custom item built from standard M3 parts" and these three
// are exactly that: a Row on an error container, a bar with a legend under it,
// and a dot beside its words. Each already renders inside a sheet preview above,
// so nothing here was invisible — this is rule 7 in LETTER, and the reason to
// close it anyway is that a sheet preview shows the item at ONE size, in ONE
// composition, and an item preview is where the owner sees it change.

/**
 * The cause card, both sentences it can carry, side by side and out of the
 * sheet. Their whole difference is the words — one names the connection and one
 * refuses to name anything — which is easiest to judge with nothing else drawn.
 */
@PreviewLightDark
@Composable
private fun CauseCardNetworkPreview() {
    CauseCardPreviewSurface(R.string.lang_sheet_failed_cause_network)
}

@PreviewLightDark
@Composable
private fun CauseCardGenericPreview() {
    CauseCardPreviewSurface(R.string.lang_sheet_failed_cause_generic)
}

@Composable
private fun CauseCardPreviewSurface(causeRes: Int) {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Box(modifier = Modifier.padding(LocalSpacing.current.md16)) { CauseCard(causeRes = causeRes) }
        }
    }
}

/**
 * The storage bar at the fraction the frame draws — nearly full, which is the
 * only state that raises 19b. The legend under it is the item below, previewed
 * separately because its swatch colours are the pair a reviewer checks for
 * contrast and they are easiest to see without a bar over them.
 */
@PreviewLightDark
@Composable
private fun StorageBarCardPreview() {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Box(modifier = Modifier.padding(LocalSpacing.current.md16)) {
                StorageBarCard(freeLabel = "12 MB free", fraction = 0.96f)
            }
        }
    }
}

/** Both swatches, the pair as drawn: used against free. */
@PreviewLightDark
@Composable
private fun StorageLegendPreview() {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.md16),
                modifier = Modifier.padding(LocalSpacing.current.md16),
            ) {
                StorageLegend(
                    swatch = MaterialTheme.colorScheme.primaryContainer,
                    label = stringResource(R.string.lang_sheet_space_used),
                )
                StorageLegend(
                    swatch = MaterialTheme.colorScheme.surfaceContainerHighest,
                    label = stringResource(R.string.lang_sheet_space_free, "12 MB"),
                )
            }
        }
    }
}
