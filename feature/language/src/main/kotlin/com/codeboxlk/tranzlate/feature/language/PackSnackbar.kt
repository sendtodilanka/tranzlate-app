package com.codeboxlk.tranzlate.feature.language

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme
import com.codeboxlk.tranzlate.domain.translate.PackEvent

/**
 * Which 20a snackbar a [PackEvent] becomes, and therefore which action it offers
 * (#130 PR-22). Four kinds, one per non-deferred 20a variant; the fifth 20a
 * ("Waiting for Wi-Fi") is E-W1-gated (#208) and has no kind here.
 */
enum class PackSnackbarKind {
    /** 20a-1 — a transfer started. Action: View (→ Manage packs). */
    STARTED,

    /** 20a-2 — the pack is on the device. Action: Use (→ set as target). */
    READY,

    /** 20a-3 — the pack was removed. Action: Download again (→ the gate). */
    REMOVED,

    /** 20a-4 — the transfer failed after starting. Action: Retry (→ the manager). */
    FAILED,
}

/**
 * A pack outcome resolved to the snackbar the app shell should raise: the [kind]
 * decides the copy and the action, [languageTag] names the pack. The shell turns
 * the tag into a display name and the kind into strings at show time.
 */
data class PackSnackbar(
    val kind: PackSnackbarKind,
    val languageTag: String,
)

/**
 * The one mapping from the manager's outcome channel to a shell snackbar — the
 * whole of what `DownloadEventsViewModel` does to a [PackEvent] before the shell
 * shows it. Pinned by `PackSnackbarMappingTest`, where the mutation is a swapped
 * arm (a "removed" event mapped to Retry): the `when` is exhaustive over the
 * sealed [PackEvent], so a NEW event added later fails to compile here rather than
 * silently mapping to nothing.
 */
fun PackEvent.toPackSnackbar(): PackSnackbar =
    when (this) {
        is PackEvent.DownloadStarted -> PackSnackbar(PackSnackbarKind.STARTED, languageTag)
        is PackEvent.DownloadSucceeded -> PackSnackbar(PackSnackbarKind.READY, languageTag)
        is PackEvent.Deleted -> PackSnackbar(PackSnackbarKind.REMOVED, languageTag)
        is PackEvent.DownloadFailed -> PackSnackbar(PackSnackbarKind.FAILED, languageTag)
    }

/**
 * The message resource for [kind]. Its own function so the resolver below and the
 * preview cannot drift onto different strings.
 */
@StringRes
internal fun packSnackbarMessageRes(kind: PackSnackbarKind): Int =
    when (kind) {
        PackSnackbarKind.STARTED -> R.string.lang_snackbar_downloading
        PackSnackbarKind.READY -> R.string.lang_snackbar_ready
        PackSnackbarKind.REMOVED -> R.string.lang_snackbar_removed
        PackSnackbarKind.FAILED -> R.string.lang_snackbar_failed
    }

/** The action-label resource for [kind]. */
@StringRes
internal fun packSnackbarActionRes(kind: PackSnackbarKind): Int =
    when (kind) {
        PackSnackbarKind.STARTED -> R.string.lang_snackbar_action_view
        PackSnackbarKind.READY -> R.string.lang_snackbar_action_use
        PackSnackbarKind.REMOVED -> R.string.lang_snackbar_action_download_again
        PackSnackbarKind.FAILED -> R.string.lang_snackbar_action_retry
    }

/**
 * The 20a message, resolved WITHOUT a composition (a `Context`, not
 * `stringResource`) because the shell calls `SnackbarHostState.showSnackbar` from
 * a coroutine, not a `@Composable`. [languageName] is the already-resolved display
 * name — the shell owns that lookup so this stays a pure string format.
 */
fun packSnackbarMessage(
    context: Context,
    kind: PackSnackbarKind,
    languageName: String,
): String = context.getString(packSnackbarMessageRes(kind), languageName)

/** The 20a action label, resolved for the shell's coroutine — see [packSnackbarMessage]. */
fun packSnackbarActionLabel(
    context: Context,
    kind: PackSnackbarKind,
): String = context.getString(packSnackbarActionRes(kind))

/**
 * Hosts a [SnackbarHost] ABOVE [content] so a 20a snackbar outlives a nav pop-out
 * (#130 PR-22). The app shell wraps its `NavDisplay` in this; because the host and its
 * [snackbarHostState] are a SIBLING of [content], not inside it, a nav push/pop — which
 * to the composition is only a swap of which destination [content] renders — leaves the
 * snackbar and its state untouched. Put the host inside a destination and the pop that
 * leaves it takes the snackbar too; that is the harm `PackSnackbarScaffoldTest` pins.
 *
 * A `Box`, not a `Scaffold`: each destination draws its own `Scaffold` with its own
 * insets, and a second here would double them. This only needs to sit on top and align
 * to the bottom. It lives in `:feature:language` (with the rest of the pack-snackbar UI)
 * rather than `:app` so a Compose test can render the REAL composable — the application
 * module cannot host a Robolectric Compose rule.
 */
@Composable
fun PackSnackbarScaffold(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        content()
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// ---- Previews (rule 7) ------------------------------------------------------

@PreviewLightDark
@Composable
private fun SnackbarVariantsPreview() {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            val spacing = LocalSpacing.current
            Column(
                verticalArrangement = Arrangement.spacedBy(spacing.sm8),
                modifier = Modifier.padding(spacing.md16),
            ) {
                // Literal fake data (rule 7): every 20a variant, so the owner reviews
                // the copy and the action of all four from one preview.
                PackSnackbarPreviewRow(PackSnackbarKind.STARTED, languageName = "English")
                PackSnackbarPreviewRow(PackSnackbarKind.READY, languageName = "English")
                PackSnackbarPreviewRow(PackSnackbarKind.REMOVED, languageName = "Spanish")
                PackSnackbarPreviewRow(PackSnackbarKind.FAILED, languageName = "Spanish")
            }
        }
    }
}

/** One rendered 20a snackbar for the preview — the default M3 `Snackbar`, message + action. */
@Composable
private fun PackSnackbarPreviewRow(
    kind: PackSnackbarKind,
    languageName: String,
) {
    Snackbar(
        modifier = Modifier.fillMaxWidth(),
        action = {
            TextButton(onClick = {}) {
                Text(stringResource(packSnackbarActionRes(kind)))
            }
        },
    ) {
        Text(stringResource(packSnackbarMessageRes(kind), languageName))
    }
}
