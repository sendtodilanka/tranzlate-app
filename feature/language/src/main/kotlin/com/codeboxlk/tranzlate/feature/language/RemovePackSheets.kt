package com.codeboxlk.tranzlate.feature.language

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.codeboxlk.tranzlate.core.designsystem.Dimensions
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing
import com.codeboxlk.tranzlate.core.designsystem.TranzlateSheetAction
import com.codeboxlk.tranzlate.core.designsystem.TranzlateSheetDefaults
import com.codeboxlk.tranzlate.core.designsystem.TranzlateSheetPreviewFrame
import com.codeboxlk.tranzlate.core.designsystem.TranzlateSheetScaffold
import com.codeboxlk.tranzlate.core.designsystem.TranzlateSheetTone
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme
import com.codeboxlk.tranzlate.core.designsystem.sheetBodyTextStyle

/** Sheet roots + controls — `tt_lang_sheet_*`, the rev3 ruling's sheet namespace (C-1). */
internal const val TT_SHEET_REMOVE = "tt_lang_sheet_remove"
internal const val TT_SHEET_REMOVE_CONFIRM = "tt_lang_sheet_remove_confirm"
internal const val TT_SHEET_REMOVE_CANCEL = "tt_lang_sheet_remove_cancel"
internal const val TT_SHEET_REMOVE_IN_USE = "tt_lang_sheet_remove_inuse"
internal const val TT_SHEET_REMOVE_IN_USE_CONFIRM = "tt_lang_sheet_remove_inuse_confirm"
internal const val TT_SHEET_REMOVE_IN_USE_CANCEL = "tt_lang_sheet_remove_inuse_cancel"
internal const val TT_SHEET_REMOVE_IN_USE_SAVED = "tt_lang_sheet_remove_inuse_saved"

/**
 * Sheet **19f** — "Remove pack" (#130 PR-19).
 *
 * The 🗑 on the offline manager used to delete on the tap. It now asks, and this
 * is what it asks with: what is freed, and what stops working, in one line — the
 * export's own caption for the frame.
 *
 * Every word of this sheet is drawn, and every word of it is TRUE of this app,
 * which is not something the sibling sheet could say (see [RemoveInUseSheet]).
 * Removing a pack takes away OFFLINE capability and nothing else: the language
 * stays in the catalogue, stays selectable, and keeps translating through the
 * AUTO waterfall's online tiers — `RealTranslator.waterfall` falls from ML Kit
 * to GOT to GCT, and its own trace spells the case out ("MLKit: fr not
 * downloaded · GOT: offline"). So *"will need a connection to translate until
 * you download it again"* is exactly what happens.
 *
 * **Loss tone, and only where the spec reserves it** (§5: "error colour is
 * reserved for loss and for stopping"): the icon slot and the one action that IS
 * the loss. The title and body stay `onSurface` — the scaffold has no parameter
 * that could turn them red, by design.
 *
 * Stateless and screen-local, following [MobileDataSheet]: the pending request
 * lives in the raising ViewModel's `SavedStateHandle`, and the default
 * `SheetState` is taken rather than hoisted (a hoisted one is `rememberSaveable`
 * underneath, which is what `PickerHostAgnosticTest` bans by name).
 *
 * The `@OptIn` is for the scaffold's DEFAULT `sheetState` argument, whose
 * expression is evaluated at this call site. Nothing here names it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RemovePackSheet(
    visible: Boolean,
    languageName: String,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    TranzlateSheetScaffold(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.lang_sheet_remove_title, languageName),
        primaryAction =
            TranzlateSheetAction(
                label = stringResource(R.string.lang_sheet_remove_confirm),
                testTag = TT_SHEET_REMOVE_CONFIRM,
                onClick = onRemove,
                tone = TranzlateSheetTone.Loss,
            ),
        modifier = Modifier.testTag(TT_SHEET_REMOVE),
        tone = TranzlateSheetTone.Loss,
        icon = { RemoveIcon() },
        secondaryAction =
            TranzlateSheetAction(
                label = stringResource(R.string.lang_sheet_remove_cancel),
                testTag = TT_SHEET_REMOVE_CANCEL,
                onClick = onDismiss,
            ),
        body = { Text(stringResource(R.string.lang_sheet_remove_body, languageName)) },
    )
}

/**
 * Sheet **19g** — "Removing what is in use" (#130 PR-19).
 *
 * Raised instead of [RemovePackSheet] when the pack being removed belongs to the
 * language the user is translating INTO right now. What it adds over 19f is
 * immediacy: this is not a capability the user might miss one day, it is the
 * next translation they make.
 *
 * ## The drawn body is not shipped, because it is false about this app
 *
 * The export draws: *"It is your target language. Removing it switches the
 * target to English."* **Nothing switches.** Enumerated two ways before this
 * sheet was written — `grep -rn 'setTargetLang|setSourceLang|setLanguagePair'`
 * over every module, and separately every writer of the DataStore keys
 * themselves in `TranzlatePreferencesDataSource` — and the language selection
 * has exactly four production writers: `LanguagePickerViewModel.select` (the
 * user picking a language) and `TextViewModel`'s three `setLanguagePair` calls
 * (swap, and reopening a history row). The remove path is none of them:
 * `OfflineLanguagesViewModel` reaches `OfflineModelManager.delete`, and
 * `RealOfflineModelManager` is constructed with a `ModelStore` and a
 * `StorageProbe` — it has no access to a preference at all, so it could not
 * change a selection if it tried.
 *
 * The owner said the same thing in plainer words when the drawn sentence was put
 * to him: *"If you delete a language pack in the app, nothing happens. It just
 * stops working offline. You can download it again and make it offline.
 * Otherwise it can work online."*
 *
 * That also settles rev3 ruling ③ ("which language becomes the target when an
 * in-use pack is removed — the device language if catalog-capable, else `en`").
 * The ruling was approved by the owner on 2026-08-01 along with the other seven,
 * so it is a real decision and not an open recommendation — but it answers a
 * question the app does not ask. **No fallback is implemented**, because there
 * is no switch to fall back from.
 *
 * ## The saved line, corrected the same way
 *
 * The export draws: *"3 saved phrases use Spanish. They stay saved and will need
 * a connection to reopen."* The first sentence is true and is the useful half —
 * a user removing "Spanish" may reasonably fear their saved Spanish phrases go
 * with it, and they do not: saved rows live in Room's `translation` table, which
 * nothing on the delete path can reach.
 *
 * The second sentence is false. Reopening a saved phrase needs no connection and
 * no pack: `TextViewModel.onHistoryPick` puts `translation.targetText` — the
 * STORED answer — straight into the result state without calling an engine, and
 * even Retry short-circuits on `TranslationRepository.cachedAny`, a database
 * read, before any tier runs. So the line says what is true instead: they stay
 * saved and still open without a connection.
 *
 * ## Zero is drawn as absence, not as a zero
 *
 * A user with no saved phrases in this language is told nothing about saved
 * phrases. "0 saved phrases use Spanish" is a sentence about something that does
 * not exist, and this project already draws that decision the same way
 * elsewhere — an empty recents section is absent, never a header over nothing.
 *
 * The count itself is a plural resource, so one phrase reads "1 saved phrase
 * uses …" rather than the "1 saved phrases" that a `%1$d` in a plain string
 * would produce.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RemoveInUseSheet(
    visible: Boolean,
    languageName: String,
    savedCount: Int,
    onRemoveAnyway: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    TranzlateSheetScaffold(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.lang_sheet_remove_inuse_title, languageName),
        primaryAction =
            TranzlateSheetAction(
                label = stringResource(R.string.lang_sheet_remove_inuse_confirm),
                testTag = TT_SHEET_REMOVE_IN_USE_CONFIRM,
                onClick = onRemoveAnyway,
                tone = TranzlateSheetTone.Loss,
            ),
        modifier = Modifier.testTag(TT_SHEET_REMOVE_IN_USE),
        tone = TranzlateSheetTone.Loss,
        icon = { InUseIcon() },
        secondaryAction =
            TranzlateSheetAction(
                label = stringResource(R.string.lang_sheet_remove_cancel),
                testTag = TT_SHEET_REMOVE_IN_USE_CANCEL,
                onClick = onDismiss,
            ),
        supportingContent = { SavedPhrasesLine(languageName = languageName, savedCount = savedCount) },
        body = { Text(stringResource(R.string.lang_sheet_remove_inuse_body, languageName)) },
    )
}

/** 19f's tonal glyph — decorative; the title carries the meaning (scaffold KDoc). */
@Composable
private fun RemoveIcon() {
    Icon(
        imageVector = Icons.Outlined.Delete,
        contentDescription = null,
        modifier = Modifier.size(TranzlateSheetDefaults.IconSize),
    )
}

/**
 * 19g's tonal glyph. The export draws a `warning` here where 19f draws `delete`,
 * and the distinction survives the copy correction: the two sheets differ by
 * whether the user is about to change the thing they are using right now, which
 * is what a warning glyph means. Decorative, like 19f's.
 */
@Composable
private fun InUseIcon() {
    Icon(
        imageVector = Icons.Filled.Warning,
        contentDescription = null,
        modifier = Modifier.size(TranzlateSheetDefaults.IconSize),
    )
}

/**
 * The `bookmark` line — reassurance, and the only part of this sheet that costs
 * a database read (`TranslationDao.savedCountUsing`, index-backed by U-10).
 *
 * Absent at zero: see [RemoveInUseSheet]'s KDoc. The glyph is silent to
 * TalkBack and the sentence carries the whole fact, which is the same rule the
 * picker's voice mark follows — a decorative node announced separately reads as
 * a second, tappable thing.
 */
@Composable
private fun SavedPhrasesLine(
    languageName: String,
    savedCount: Int,
) {
    if (savedCount <= 0) return
    val spacing = LocalSpacing.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.md16, vertical = spacing.sm8)
                    .testTag(TT_SHEET_REMOVE_IN_USE_SAVED),
        ) {
            Icon(
                imageVector = Icons.Outlined.Bookmark,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimensions.iconSm),
            )
            Text(
                text =
                    pluralStringResource(
                        R.plurals.lang_sheet_remove_inuse_saved,
                        savedCount,
                        savedCount,
                        languageName,
                    ),
                style = sheetBodyTextStyle(MaterialTheme.typography),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = spacing.md16),
            )
        }
    }
}

// ---- Previews (rule 7 — one per meaningful STATE) -----------------------------------------------
// `ModalBottomSheet` opens a window and the tooling renders nothing for a
// window, so these draw the same anatomy on the same floating surface the host
// paints — `TranzlateSheetPreviewFrame`, which exists for exactly this
// (`MobileDataSheet.kt:203`, PR-17).

/** 19f: the pack is not the one in use. One state — the sheet has no variants. */
@PreviewLightDark
@Composable
private fun RemovePackSheetPreview() {
    TranzlateSheetPreviewFrame(
        title = stringResource(R.string.lang_sheet_remove_title, "Spanish"),
        primaryAction =
            TranzlateSheetAction(
                label = stringResource(R.string.lang_sheet_remove_confirm),
                testTag = TT_SHEET_REMOVE_CONFIRM,
                onClick = {},
                tone = TranzlateSheetTone.Loss,
            ),
        tone = TranzlateSheetTone.Loss,
        icon = { RemoveIcon() },
        secondaryAction =
            TranzlateSheetAction(
                label = stringResource(R.string.lang_sheet_remove_cancel),
                testTag = TT_SHEET_REMOVE_CANCEL,
                onClick = {},
            ),
        body = { Text(stringResource(R.string.lang_sheet_remove_body, "Spanish")) },
    )
}

/** 19g with several saved phrases — the state the export draws. */
@PreviewLightDark
@Composable
private fun RemoveInUseSheetPreview() {
    RemoveInUseSheetPreviewBody(savedCount = 3)
}

/** 19g with exactly one — the plural rule, where "1 saved phrases" would show. */
@PreviewLightDark
@Composable
private fun RemoveInUseSheetOneSavedPreview() {
    RemoveInUseSheetPreviewBody(savedCount = 1)
}

/** 19g with none — the whole line is absent rather than reading zero. */
@PreviewLightDark
@Composable
private fun RemoveInUseSheetNoSavedPreview() {
    RemoveInUseSheetPreviewBody(savedCount = 0)
}

// ---- Item-level previews (#243) ---------------------------------------------
// The line renders inside the three 19g previews above, so nothing was invisible
// — rule 7's LETTER names items built from standard M3 parts and this is one: a
// Surface holding a Row holding a glyph and a plural. Previewed alone because
// its wrap is the thing to look at, and inside a sheet it never gets long enough
// to wrap. The zero case has no item preview by design: at zero the composable
// returns before drawing anything, so an item preview of it is a blank frame.
// `RemoveInUseSheetNoSavedPreview` above shows the absence in the place the
// absence means something.

/** Several — the plural arm, and the length that decides the wrap. */
@PreviewLightDark
@Composable
private fun SavedPhrasesLineManyPreview() {
    SavedPhrasesLinePreviewSurface(savedCount = 12)
}

/** Exactly one — the arm where "1 saved phrases" would show if the plural were a string. */
@PreviewLightDark
@Composable
private fun SavedPhrasesLineOnePreview() {
    SavedPhrasesLinePreviewSurface(savedCount = 1)
}

@Composable
private fun SavedPhrasesLinePreviewSurface(savedCount: Int) {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Box(modifier = Modifier.padding(LocalSpacing.current.md16)) {
                SavedPhrasesLine(languageName = "Spanish", savedCount = savedCount)
            }
        }
    }
}

@Composable
private fun RemoveInUseSheetPreviewBody(savedCount: Int) {
    TranzlateSheetPreviewFrame(
        title = stringResource(R.string.lang_sheet_remove_inuse_title, "Spanish"),
        primaryAction =
            TranzlateSheetAction(
                label = stringResource(R.string.lang_sheet_remove_inuse_confirm),
                testTag = TT_SHEET_REMOVE_IN_USE_CONFIRM,
                onClick = {},
                tone = TranzlateSheetTone.Loss,
            ),
        tone = TranzlateSheetTone.Loss,
        icon = { InUseIcon() },
        secondaryAction =
            TranzlateSheetAction(
                label = stringResource(R.string.lang_sheet_remove_cancel),
                testTag = TT_SHEET_REMOVE_IN_USE_CANCEL,
                onClick = {},
            ),
        supportingContent = { SavedPhrasesLine(languageName = "Spanish", savedCount = savedCount) },
        body = { Text(stringResource(R.string.lang_sheet_remove_inuse_body, "Spanish")) },
    )
}
