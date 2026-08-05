package com.codeboxlk.tranzlate.navigation

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.codeboxlk.tranzlate.feature.language.PackSnackbar
import kotlinx.coroutines.flow.Flow

/**
 * Collects [snackbars] only while [lifecycle] is at least STARTED, restarting the
 * collection on each STARTED and cancelling it on each STOP (#130 PR-22). This is the
 * consuming half of the U-1 contract: the manager's channel is `replay = 0`, so a
 * STOP tears this subscription down, notices emitted while stopped are dropped, and a
 * later STARTED re-subscribes to a channel that replays nothing — the state map is the
 * truth on return, never a burst of stale snackbars.
 *
 * Extracted from the shell's `LaunchedEffect` so the lifecycle gate is reachable from
 * a JVM test: `DownloadSnackbarCollectionTest` drives a `TestLifecycleOwner` through
 * CREATED → STARTED → CREATED → STARTED and asserts nothing is handled below STARTED
 * and nothing replays on return. Its mutation is a plain `snackbars.collect { … }`
 * with no `repeatOnLifecycle` — which delivers while stopped and reddens the test.
 *
 * The visual half — hosting the `SnackbarHost` ABOVE the nav content so a snackbar
 * survives a nav pop-out — is `PackSnackbarScaffold` in `:feature:language`, where a
 * Compose test can render it (the app module cannot host a Robolectric Compose rule:
 * `testOptions.targetSdk` is rejected for an application module).
 */
internal suspend fun collectPackSnackbars(
    lifecycle: Lifecycle,
    snackbars: Flow<PackSnackbar>,
    handle: suspend (PackSnackbar) -> Unit,
) {
    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        snackbars.collect { handle(it) }
    }
}
