package com.codeboxlk.tranzlate.feature.text

import com.codeboxlk.tranzlate.core.model.AttemptCause

/**
 * Sheet 19h's trigger (#130 PR-20): the target language the composer could not
 * translate to offline, or null.
 *
 * It is the target of a [TextUiState.Error] whose cause is [AttemptCause.OFFLINE]
 * — that cause is the deepest attempt's, and it is `OFFLINE` only when EVERY
 * engine was unreachable, which means the target has no on-device pack AND there
 * is no connection to reach the online tiers. An offline device WITH the pack
 * translates through ML Kit and never reaches an error, so this reliably names
 * the "you are offline and this language has no pack" case 19h is for.
 *
 * A pure function, deliberately: the composer calls it in a `LaunchedEffect` and
 * HOISTS the result up to the app shell (`onOfflinePackMissing`), which gates it
 * on `ConnectivityMonitor.online` and on there being other packs to offer. Pure,
 * so the "which state raises 19h" decision is tested here without a Compose rule
 * or the shell.
 */
internal fun offlinePackMissingLang(uiState: TextUiState): String? =
    (uiState as? TextUiState.Error)
        ?.takeIf { it.cause == AttemptCause.OFFLINE }
        ?.request
        ?.targetLang
