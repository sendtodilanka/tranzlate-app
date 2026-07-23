package com.codeboxlk.tranzlate.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.codeboxlk.tranzlate.R
import com.codeboxlk.tranzlate.core.designsystem.Dimensions
import com.codeboxlk.tranzlate.core.designsystem.LocalFloatingSurface
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing
import com.codeboxlk.tranzlate.core.designsystem.TranzlateShapeFull
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme
import com.codeboxlk.tranzlate.core.model.Engine
import com.codeboxlk.tranzlate.core.model.Translation

/** Drawer sheet width — the push/scale motion in [TranzlateApp] derives from this. */
val DrawerSheetWidth: Dp = 300.dp

/** UI_SPEC §2.3 drawer sections (secondary destinations — D-5). */
enum class DrawerDestination {
    SEARCH,
    HISTORY,
    SAVED,
    OFFLINE_LANGUAGES,
    SETTINGS,
}

/**
 * UI_SPEC §2.3 drawer sheet (Claude-app structure): wordmark → section rows →
 * RECENTS (last 4 from the Room history — read-only rows, not action
 * affordances) → account row pinned bottom (no account system yet: static
 * Guest + "Free" tier chip). No "+ New translation" pill (owner decision).
 */
@Composable
fun DrawerContent(
    drawerState: DrawerState,
    recents: List<Translation>,
    onDestinationClick: (DrawerDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    // ModalDrawerSheet, not a bare Surface, and specifically the drawerState
    // overload: back handling for the drawer lives ONLY there, via
    // DrawerPredictiveBackHandler (NavigationDrawer.kt:643). ModalNavigationDrawer
    // installs none of its own, so with a raw Surface the system back button fell
    // through to the nav host and closed the app instead of the drawer.
    //
    // windowInsets: DrawerDefaults.windowInsets is systemBarsForVisualComponents
    // .only(Vertical + Start) — it drops the IME. Under edge-to-edge + adjustResize
    // the window does not physically shrink, so a screen must consume ime itself or
    // bottom content sits under the keyboard. The drawer opens from the same screen
    // as the composer text field, so if it opens with the keyboard up the pinned
    // AccountRow would be hidden behind it. safeDrawing carries the ime; keep the
    // same Vertical+Start shape as the default so nothing else changes.
    // (HomeScreen makes the same choice for the same reason.)
    //
    // Width stays pinned at 300dp rather than letting the sheet size itself: it is
    // inside M3's own 240-360dp range, and the push/scale motion in TranzlateApp
    // reads this same constant to compute its fraction.
    ModalDrawerSheet(
        drawerState = drawerState,
        drawerContainerColor = LocalFloatingSurface.current,
        drawerContentColor = MaterialTheme.colorScheme.onSurface,
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start),
        modifier =
            modifier
                .width(DrawerSheetWidth)
                .testTag("tt_app_drawer"),
    ) {
        Column(
            modifier = Modifier.padding(vertical = spacing.md16),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm8),
                modifier = Modifier.padding(horizontal = spacing.md16),
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Dimensions.iconMd),
                )
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Spacer(modifier = Modifier.heightIn(min = spacing.md16))
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
            ) {
                DrawerRow(Icons.Outlined.Search, R.string.drawer_search, DrawerDestination.SEARCH, onDestinationClick)
                DrawerRow(
                    Icons.Outlined.History,
                    R.string.drawer_history,
                    DrawerDestination.HISTORY,
                    onDestinationClick,
                )
                DrawerRow(Icons.Outlined.Bookmark, R.string.drawer_saved, DrawerDestination.SAVED, onDestinationClick)
                DrawerRow(
                    Icons.Outlined.Language,
                    R.string.drawer_offline_languages,
                    DrawerDestination.OFFLINE_LANGUAGES,
                    onDestinationClick,
                )
                DrawerRow(
                    Icons.Outlined.Settings,
                    R.string.drawer_settings,
                    DrawerDestination.SETTINGS,
                    onDestinationClick,
                )
                Spacer(modifier = Modifier.heightIn(min = spacing.md16))
                Text(
                    text = stringResource(R.string.drawer_recents_header),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = spacing.md16, vertical = spacing.sm8),
                )
                if (recents.isEmpty()) {
                    Text(
                        text = stringResource(R.string.drawer_recents_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier =
                            Modifier
                                .padding(horizontal = spacing.md16, vertical = spacing.xs4)
                                .testTag("tt_app_drawer_recents_empty"),
                    )
                } else {
                    recents.forEach { translation ->
                        RecentRow(translation)
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            AccountRow()
        }
    }
}

@Composable
private fun DrawerRow(
    icon: ImageVector,
    @StringRes labelRes: Int,
    destination: DrawerDestination,
    onDestinationClick: (DrawerDestination) -> Unit,
) {
    val spacing = LocalSpacing.current
    Surface(
        onClick = { onDestinationClick(destination) },
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("tt_app_drawer_${destination.name.lowercase()}"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md16),
            modifier =
                Modifier
                    .heightIn(min = Dimensions.touchTargetMin)
                    .padding(horizontal = spacing.md16),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimensions.iconMd),
            )
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

/** RECENTS row: source line + translation line (UI_SPEC §2.3) — read-only. */
@Composable
private fun RecentRow(translation: Translation) {
    val spacing = LocalSpacing.current
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md16, vertical = spacing.xs4),
    ) {
        Text(
            text = translation.sourceText,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = translation.targetText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AccountRow() {
    val spacing = LocalSpacing.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm8),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md16)
                .heightIn(min = Dimensions.touchTargetMin)
                .padding(top = spacing.sm8),
    ) {
        val guest = stringResource(R.string.drawer_account_guest)
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(Dimensions.iconChip)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        ) {
            Text(
                text = guest.take(1),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(
            text = guest,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Surface(
            shape = TranzlateShapeFull,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Text(
                text = stringResource(R.string.drawer_tier_free),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = spacing.sm8, vertical = spacing.xxs2),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun DrawerContentPreview() {
    TranzlateTheme {
        DrawerContent(
            recents =
                listOf(
                    Translation(
                        id = 1,
                        sourceLang = "en",
                        sourceText = "Good morning",
                        targetLang = "fr",
                        targetText = "Bonjour (fake)",
                        engine = Engine.OFFLINE_MLKIT,
                        createdAt = 0L,
                    ),
                ),
            onDestinationClick = {},
            // Open: the sheet is the whole subject of the preview.
            drawerState = rememberDrawerState(DrawerValue.Open),
        )
    }
}

@PreviewLightDark
@Composable
private fun DrawerContentEmptyPreview() {
    TranzlateTheme {
        DrawerContent(
            recents = emptyList(),
            onDestinationClick = {},
            drawerState = rememberDrawerState(DrawerValue.Open),
        )
    }
}
