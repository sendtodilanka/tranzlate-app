package com.codeboxlk.tranzlate.domain.translate

import com.codeboxlk.tranzlate.core.model.OfflineModelFailure
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import kotlinx.coroutines.flow.Flow

/**
 * Offline-model download manager ask-surface (Translation brain owns model state —
 * spec 02 §5.2). Constraint (verified, spec 02 §3.1): MLKit downloads expose no
 * progress % and no true cancel — states are the 6-state [OfflineModelState] model,
 * "stop" = delete-to-cancel.
 */
interface OfflineModelManager {
    /** Per-language model states keyed by BCP-47 tag. */
    fun modelStates(): Flow<Map<String, OfflineModelState>>

    /**
     * Ask for [languageTag]'s pack. Returns what was decided BEFORE anything was
     * enqueued; the transfer's own outcome arrives through [modelStates].
     */
    suspend fun download(languageTag: String): DownloadAttempt

    /** Also serves as delete-to-cancel while [OfflineModelState.Downloading]. */
    suspend fun delete(languageTag: String)
}

/**
 * What the manager decided the moment it was asked — the SYNCHRONOUS half of a
 * download, and the only half that can travel by return (issue **#234**).
 *
 * ## Why this is a return value and not a second outcome channel
 *
 * The manager publishes state through one conflating [Flow], and a
 * `MutableStateFlow` does not emit when the value written equals the value held.
 * `OfflineModelState.Failed` is a `data class`, so a pre-flight refusal repeated
 * for the same reason — the storage check refusing a Retry on a disk that is
 * still full — writes a map **equal** to the current one and is invisible
 * downstream. That is not a bug in the writer: a value channel structurally
 * cannot carry *"the same thing happened again"*. The user meanwhile has an
 * enabled, labelled, 48 dp Retry that produces nothing (#234), which is the dead
 * end `EDGE_CASES.md` §7 forbids.
 *
 * The answer travels on the call the caller already awaits. This is deliberately
 * NOT the U-1 `PackEvents` channel the rev3 ruling reserves for PR-22 and REJECT
 * §7.8 bounces third copies of: it adds no flow, no scope and no lifetime, and it
 * carries only the half that is already known at return. The ASYNCHRONOUS half
 * still arrives through [OfflineModelManager.modelStates], exactly as ruling 9
 * requires — `download()` hands the transfer to a process-lifetime scope so that
 * leaving the screen cannot strand it, and that is unchanged.
 */
sealed interface DownloadAttempt {
    /**
     * Enqueued. The row is `Downloading` and the outcome — `Downloaded`, or a
     * `Failed` — arrives through [OfflineModelManager.modelStates].
     */
    data object Started : DownloadAttempt

    /**
     * Refused before anything was enqueued, and the row already carries [cause].
     * Today the one refusal is the free-space pre-flight (issue #90's
     * `REQUIRED_FREE_BYTES`), which is sheet 19b's only trigger.
     */
    data class Refused(
        val cause: OfflineModelFailure,
    ) : DownloadAttempt

    /**
     * Nothing happened and nothing was written: the tag is not offline-capable,
     * or a download for it is already in flight.
     *
     * Named rather than folded into [Started], because a caller that watches the
     * state map for an outcome that will never come is a coroutine suspended for
     * the life of the screen — which is what both of `download()`'s early returns
     * used to cost.
     */
    data object Ignored : DownloadAttempt
}
