package com.codeboxlk.tranzlate.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Sheet ANATOMY primitives — language-screens rev3 spec §5 (issue #130, U-2).
 *
 * One skeleton for every rev3 sheet (19a–19n, 18a-confirm, 20c, 20e): modal
 * bottom sheet · drag handle · 44dp tonal icon slot · 20sp title · 13.5sp body
 * · at most TWO actions at 48dp with the likely intent filled. This module is
 * deliberately STRING-FREE: every user-facing word, testTag and
 * contentDescription arrives from the calling feature module, which keeps
 * string authority where the rev3 ruling put it (`:feature:language`).
 *
 * Colour discipline (spec §5): "Error colour is reserved for loss and for
 * stopping." [TranzlateSheetTone.Loss] paints the ICON SLOT with the error
 * container roles and may be carried by the one ACTION that is the loss/stop
 * (19f "Remove" is error-filled; 19d "Close" is error-text). The sheet surface,
 * title and body NEVER take error colour — the spec drawings keep the title
 * `onSurface` even on failure sheets ("the error colour only for the fact, not
 * the whole sheet", 19d) — so [sheetTitleColor]/[sheetBodyColor] have no tone
 * parameter at all: the API shape makes a red sheet unrepresentable.
 *
 * No-dead-end guarantee (EDGE_CASES, framework level): dismissal is ALWAYS
 * available — back press, scrim tap and handle drag are wired by
 * [ModalBottomSheet] and this wrapper does not expose the properties that could
 * disable them, and the drag handle is always rendered.
 */
enum class TranzlateSheetTone {
    /** Informational / decision sheets — icon slot on the primary container roles. */
    Neutral,

    /** Loss or stopping (failed download, remove pack) — icon slot on the error container roles. */
    Loss,
}

/**
 * One sheet action. At most two exist per sheet BY API SHAPE (a `primaryAction`
 * and an optional `secondaryAction` — there is no list to overfill).
 *
 * @param label caller-resolved text (this module names nothing user-facing).
 * @param testTag REQUIRED, no default — TEST_A11Y discipline: every sheet
 *   control ships a `tt_<feature>_<control>` tag from its feature module.
 * @param tone [TranzlateSheetTone.Loss] only when THIS action is the loss/stop
 *   (spec §5 reservation rule).
 */
@Immutable
class TranzlateSheetAction(
    val label: String,
    val testTag: String,
    val onClick: () -> Unit,
    val tone: TranzlateSheetTone = TranzlateSheetTone.Neutral,
    val enabled: Boolean = true,
)

/** Spec-§5 contract metrics + type styles, pinned by `TranzlateSheetContractTest`. */
object TranzlateSheetDefaults {
    /** Tonal icon slot diameter (spec §5: 44dp). */
    val IconSlotSize: Dp = 44.dp

    /** Glyph size inside the icon slot (export-measured 22px). */
    val IconSize: Dp = 22.dp

    /** Minimum action height (spec §5: 48dp; `heightIn` so large font scales may grow it). */
    val ActionMinHeight: Dp = 48.dp

    /**
     * Compact list-row metric for [TranzlateListSheet] rows (spec TOKENS block:
     * "compact 48dp"). The list slot cannot force a height onto caller-built
     * rows, so rows apply `Modifier.heightIn(min = ListRowMinHeight)` themselves.
     */
    val ListRowMinHeight: Dp = 48.dp
}

// ---- Pure tone/type resolvers (JVM-testable — the sheet contract in data form) ------------------

/** Icon-slot fill: primary container family, error container family only for [TranzlateSheetTone.Loss]. */
fun sheetIconContainerColor(
    tone: TranzlateSheetTone,
    scheme: ColorScheme,
): Color =
    when (tone) {
        TranzlateSheetTone.Neutral -> scheme.primaryContainer
        TranzlateSheetTone.Loss -> scheme.errorContainer
    }

/** Icon-slot glyph colour, paired with [sheetIconContainerColor]. */
fun sheetIconContentColor(
    tone: TranzlateSheetTone,
    scheme: ColorScheme,
): Color =
    when (tone) {
        TranzlateSheetTone.Neutral -> scheme.onPrimaryContainer
        TranzlateSheetTone.Loss -> scheme.onErrorContainer
    }

/** Filled (primary) action container — stock M3 `primary`, `error` only for a Loss action. */
fun sheetFilledActionContainerColor(
    tone: TranzlateSheetTone,
    scheme: ColorScheme,
): Color =
    when (tone) {
        TranzlateSheetTone.Neutral -> scheme.primary
        TranzlateSheetTone.Loss -> scheme.error
    }

/** Filled (primary) action content, paired with [sheetFilledActionContainerColor]. */
fun sheetFilledActionContentColor(
    tone: TranzlateSheetTone,
    scheme: ColorScheme,
): Color =
    when (tone) {
        TranzlateSheetTone.Neutral -> scheme.onPrimary
        TranzlateSheetTone.Loss -> scheme.onError
    }

/** Text (secondary) action content — stock M3 `primary`, `error` only for a Loss action (19d "Close"). */
fun sheetTextActionContentColor(
    tone: TranzlateSheetTone,
    scheme: ColorScheme,
): Color =
    when (tone) {
        TranzlateSheetTone.Neutral -> scheme.primary
        TranzlateSheetTone.Loss -> scheme.error
    }

/**
 * Sheet title colour. Deliberately NO tone parameter: spec §5 reserves error
 * for loss/stopping, and the drawings keep the title `onSurface` on every
 * sheet, failure sheets included — the sheet never turns red.
 */
fun sheetTitleColor(scheme: ColorScheme): Color = scheme.onSurface

/** Sheet body colour — `onSurfaceVariant` on every sheet; no tone parameter (see [sheetTitleColor]). */
fun sheetBodyColor(scheme: ColorScheme): Color = scheme.onSurfaceVariant

/** Spec-§5 title: 20sp/26sp REGULAR (`titleLarge` is 22sp Medium — the sheet title is drawn lighter). */
fun sheetTitleTextStyle(typography: Typography): TextStyle =
    typography.titleLarge.copy(
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
    )

/** Spec-§5 body: 13.5sp on the `bodyMedium` metrics (20sp line, 0.25sp tracking). */
fun sheetBodyTextStyle(typography: Typography): TextStyle = typography.bodyMedium.copy(fontSize = 13.5.sp)

/** Export-measured action label: `labelLarge` (Medium, 0.1sp) at 15sp. */
fun sheetActionTextStyle(typography: Typography): TextStyle = typography.labelLarge.copy(fontSize = 15.sp)

/**
 * At most two actions, and a secondary only ever accompanies a primary — a
 * lone "Cancel" with no drawn intent would be a dead end shape the spec never
 * draws. Called by [TranzlateListSheet] (its actions are optional; the
 * scaffold's primary is required by signature).
 */
internal fun validateSheetActions(
    primaryAction: TranzlateSheetAction?,
    secondaryAction: TranzlateSheetAction?,
) {
    require(secondaryAction == null || primaryAction != null) {
        "A secondary sheet action requires a primary action (spec §5: the likely intent is filled)"
    }
}

// ---- Export-measured internal rhythm (language-screens-spec.html §5 drawings) -------------------

private val TitleBodyGap = 6.dp
private val SupportingTopGap = 18.dp
private val ActionsTopGap = 22.dp
private val SheetBottomPadding = 20.dp
private val FilledActionPadding = PaddingValues(horizontal = 26.dp)
private val TextActionPadding = PaddingValues(horizontal = 20.dp)

/**
 * The modal skeleton every rev3 sheet stands on (spec §5 anatomy). Slots take
 * composables/strings from the CALLER; this module names nothing user-facing.
 *
 * Semantics contract: [title] is the sheet's accessibility title (`paneTitle`
 * on the content root + `heading()` on the title node); each action carries the
 * caller's [TranzlateSheetAction.testTag]; dismiss is always available (back +
 * scrim + drag handle — none of them can be disabled through this API).
 *
 * @param onDismissRequest ALWAYS reachable (back/scrim/drag). Per the rev3
 *   state-flow contract, dismiss must be a state-machine action in the raising
 *   ViewModel (request → null), never a dead end.
 * @param sheetState hoist to drive a graceful `hide()` before clearing the
 *   sheet request (PR-17+ SavedStateHandle flow).
 * @param icon optional glyph for the 44dp tonal slot; it inherits the tone's
 *   content colour via `LocalContentColor`. Decorative by default — the title
 *   carries the meaning, so callers normally pass `contentDescription = null`.
 * @param tone [TranzlateSheetTone.Loss] ONLY for loss/stopping sheets (19d/19f/19g).
 * @param supportingContent optional FULL-WIDTH region between header and
 *   actions (19a's standing-pref toggle, 19d's cause card, 19n's benefit list).
 * @param body the 13.5sp explanation, rendered beside the icon under the
 *   title; plain caller `Text` inherits style and colour.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranzlateSheetScaffold(
    onDismissRequest: () -> Unit,
    title: String,
    primaryAction: TranzlateSheetAction,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    tone: TranzlateSheetTone = TranzlateSheetTone.Neutral,
    icon: (@Composable () -> Unit)? = null,
    secondaryAction: TranzlateSheetAction? = null,
    supportingContent: (@Composable ColumnScope.() -> Unit)? = null,
    body: @Composable () -> Unit,
) {
    TranzlateSheetHost(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
    ) {
        TranzlateSheetLayout(
            title = title,
            tone = tone,
            icon = icon,
            primaryAction = primaryAction,
            secondaryAction = secondaryAction,
            midContent = supportingContent,
            body = body,
        )
    }
}

/**
 * Shared [ModalBottomSheet] shell — floating-surface container, outline drag
 * handle (export: `outline` in both themes), dismissal left fully enabled.
 * [TranzlateListSheet] reuses it so both variants stay one anatomy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TranzlateSheetHost(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = LocalFloatingSurface.current,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline) },
        content = content,
    )
}

/**
 * The anatomy itself, host-free so previews (and the two public variants) share
 * it: header row (icon slot? + title/body column) → optional full-width mid
 * region → end-aligned actions.
 */
@Composable
internal fun TranzlateSheetLayout(
    title: String,
    tone: TranzlateSheetTone,
    icon: (@Composable () -> Unit)?,
    primaryAction: TranzlateSheetAction?,
    secondaryAction: TranzlateSheetAction?,
    midContent: (@Composable ColumnScope.() -> Unit)?,
    body: @Composable () -> Unit,
) {
    validateSheetActions(primaryAction, secondaryAction)
    val spacing = LocalSpacing.current
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg24)
                .padding(bottom = SheetBottomPadding)
                .semantics { paneTitle = title },
    ) {
        Row(verticalAlignment = Alignment.Top) {
            if (icon != null) {
                Surface(
                    modifier = Modifier.size(TranzlateSheetDefaults.IconSlotSize),
                    shape = TranzlateShapeFull,
                    color = sheetIconContainerColor(tone, scheme),
                    contentColor = sheetIconContentColor(tone, scheme),
                ) {
                    Box(contentAlignment = Alignment.Center) { icon() }
                }
                Spacer(Modifier.size(spacing.md16))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = sheetTitleTextStyle(MaterialTheme.typography),
                    color = sheetTitleColor(scheme),
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(TitleBodyGap))
                CompositionLocalProvider(LocalContentColor provides sheetBodyColor(scheme)) {
                    ProvideTextStyle(sheetBodyTextStyle(MaterialTheme.typography)) { body() }
                }
            }
        }
        if (midContent != null) {
            Spacer(Modifier.height(SupportingTopGap))
            midContent()
        }
        if (primaryAction != null) {
            Spacer(Modifier.height(ActionsTopGap))
            Row(
                modifier = Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (secondaryAction != null) SheetTextAction(secondaryAction)
                SheetFilledAction(primaryAction)
            }
        }
    }
}

/** The filled action — the likely intent (spec §5); 48dp minimum, full-shape pill. */
@Composable
private fun SheetFilledAction(action: TranzlateSheetAction) {
    val scheme = MaterialTheme.colorScheme
    Button(
        onClick = action.onClick,
        enabled = action.enabled,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = sheetFilledActionContainerColor(action.tone, scheme),
                contentColor = sheetFilledActionContentColor(action.tone, scheme),
            ),
        contentPadding = FilledActionPadding,
        modifier =
            Modifier
                .heightIn(min = TranzlateSheetDefaults.ActionMinHeight)
                .testTag(action.testTag),
    ) {
        Text(text = action.label, style = sheetActionTextStyle(MaterialTheme.typography))
    }
}

/** The text action — never the likely intent, so never filled. */
@Composable
private fun SheetTextAction(action: TranzlateSheetAction) {
    TextButton(
        onClick = action.onClick,
        enabled = action.enabled,
        colors =
            ButtonDefaults.textButtonColors(
                contentColor = sheetTextActionContentColor(action.tone, MaterialTheme.colorScheme),
            ),
        contentPadding = TextActionPadding,
        modifier =
            Modifier
                .heightIn(min = TranzlateSheetDefaults.ActionMinHeight)
                .testTag(action.testTag),
    ) {
        Text(text = action.label, style = sheetActionTextStyle(MaterialTheme.typography))
    }
}

// ---- Previews (Rule 7) — the anatomy layout on the sheet surface, literal fake content ----------
// The ModalBottomSheet host cannot render inside a static preview (it opens a
// window), so previews exercise [TranzlateSheetLayout] on the same floating
// surface the host paints. Preview strings are literal fakes, never resources —
// the module itself stays string-free.

/**
 * The sheet anatomy on the sheet surface, WITHOUT the modal host — the only way
 * a feature module can put one of its sheets in front of the owner.
 *
 * Rule 7 asks every sheet for a `@PreviewLightDark` per meaningful state, and
 * [TranzlateSheetScaffold] cannot satisfy it: `ModalBottomSheet` opens a window,
 * and the tooling renders nothing for a window. The previews in this file get
 * around that by calling the internal [TranzlateSheetLayout] directly, which
 * `:feature:language` cannot do. So the same escape is public, named for what it
 * is for, rather than the two internals being opened up with no statement of
 * why. Added by #130 PR-17, the first sheet to be built outside this module.
 *
 * Preview-only by contract: it has no `onDismissRequest`, no `SheetState` and no
 * scrim, so it cannot stand in for a real sheet at runtime.
 */
@Composable
fun TranzlateSheetPreviewFrame(
    title: String,
    primaryAction: TranzlateSheetAction,
    tone: TranzlateSheetTone = TranzlateSheetTone.Neutral,
    icon: (@Composable () -> Unit)? = null,
    secondaryAction: TranzlateSheetAction? = null,
    supportingContent: (@Composable ColumnScope.() -> Unit)? = null,
    body: @Composable () -> Unit,
) {
    SheetPreviewSurface {
        TranzlateSheetLayout(
            title = title,
            tone = tone,
            icon = icon,
            primaryAction = primaryAction,
            secondaryAction = secondaryAction,
            midContent = supportingContent,
            body = body,
        )
    }
}

@Composable
internal fun SheetPreviewSurface(content: @Composable () -> Unit) {
    TranzlateTheme {
        Surface(
            color = LocalFloatingSurface.current,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column {
                Spacer(Modifier.height(20.dp))
                content()
            }
        }
    }
}

/** Icon + single filled action — the 19n "Got it" shape. */
@PreviewLightDark
@Composable
private fun SheetScaffoldIconPreview() {
    SheetPreviewSurface {
        TranzlateSheetLayout(
            title = "Everything runs on this device",
            tone = TranzlateSheetTone.Neutral,
            icon = {
                Icon(
                    painterResource(R.drawable.ic_download_for_offline),
                    null,
                    Modifier.size(TranzlateSheetDefaults.IconSize),
                )
            },
            primaryAction = TranzlateSheetAction("Got it", "tt_preview_primary", {}),
            secondaryAction = null,
            midContent = null,
            body = { Text("A pack is downloaded once. After that your text is translated on the phone.") },
        )
    }
}

/** No icon — title and body take the full width. */
@PreviewLightDark
@Composable
private fun SheetScaffoldNoIconPreview() {
    SheetPreviewSurface {
        TranzlateSheetLayout(
            title = "Spanish is already the source",
            tone = TranzlateSheetTone.Neutral,
            icon = null,
            primaryAction = TranzlateSheetAction("Swap languages", "tt_preview_primary", {}),
            secondaryAction = null,
            midContent = null,
            body = { Text("Pick a different target, or swap the two languages.") },
        )
    }
}

/** Two neutral actions + a full-width supporting region — the 19a decision shape. */
@PreviewLightDark
@Composable
private fun SheetScaffoldTwoActionPreview() {
    SheetPreviewSurface {
        TranzlateSheetLayout(
            title = "Download over mobile data?",
            tone = TranzlateSheetTone.Neutral,
            icon = {
                Icon(
                    painterResource(R.drawable.ic_download_for_offline),
                    null,
                    Modifier.size(TranzlateSheetDefaults.IconSize),
                )
            },
            primaryAction = TranzlateSheetAction("Download now", "tt_preview_primary", {}),
            secondaryAction = TranzlateSheetAction("Wait for Wi-Fi", "tt_preview_secondary", {}),
            midContent = {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Always ask before using mobile data",
                        style = sheetBodyTextStyle(MaterialTheme.typography),
                        modifier = Modifier.padding(14.dp),
                    )
                }
            },
            body = { Text("A language pack is usually 20–45 MB. Your plan may charge for it.") },
        )
    }
}

/** Loss tone: error icon slot + error-FILLED primary — the 19f "Remove" shape. Title stays onSurface. */
@PreviewLightDark
@Composable
private fun SheetScaffoldErrorFilledPreview() {
    SheetPreviewSurface {
        TranzlateSheetLayout(
            title = "Remove Spanish?",
            tone = TranzlateSheetTone.Loss,
            icon = {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    null,
                    Modifier.size(TranzlateSheetDefaults.IconSize),
                )
            },
            primaryAction = TranzlateSheetAction("Remove", "tt_preview_primary", {}, tone = TranzlateSheetTone.Loss),
            secondaryAction = TranzlateSheetAction("Cancel", "tt_preview_secondary", {}),
            midContent = null,
            body = {
                Text(
                    "Frees space on this device. Spanish will need a connection to translate until you download it again.",
                )
            },
        )
    }
}
