package com.codeboxlk.tranzlate.core.model

/**
 * Per-language offline-model state — 6-state sealed model
 * (spec 02 §3.3 lifecycle + §4.3 per-row states).
 *
 * MLKit's `RemoteModelManager.download()` returns `Task<Void>` — no progress %,
 * no true cancel (spec 02 §3.1, verified). [Downloading] is therefore
 * indeterminate; "stop" = delete-to-cancel.
 */
sealed interface OfflineModelState {
    /** Not in MLKit's `getAllLanguages()` — no download control, "Online only" badge. */
    data object OnlineOnly : OfflineModelState

    /** Offline-capable, not in `getDownloadedModels()` — download available. */
    data object NotDownloaded : OfflineModelState

    /** Indeterminate progress + stop (= delete-to-cancel). Never a fake %. */
    data object Downloading : OfflineModelState

    /** Downloaded — delete available, "~30MB" size hint. */
    data object Downloaded : OfflineModelState

    /** Transient spinner while deleting. */
    data object Deleting : OfflineModelState

    /** Failure with retry (spec 02 §4.3: network / wifi-required / storage). */
    data class Failed(
        val cause: OfflineModelFailure,
    ) : OfflineModelState
}

enum class OfflineModelFailure {
    NETWORK,
    WIFI_REQUIRED,
    STORAGE,
    UNKNOWN,
}
