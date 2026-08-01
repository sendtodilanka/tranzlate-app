package com.codeboxlk.tranzlate.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeboxlk.tranzlate.core.model.Translation
import com.codeboxlk.tranzlate.domain.repository.TranslationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

private const val SUBSCRIBE_TIMEOUT_MS = 5_000L

/** The three writes History owns. The screen turns each into copy; C-3 keeps wording in resources. */
enum class HistoryWrite {
    DELETE,
    RESTORE,
    FAVOURITE,
}

/**
 * A write that did not land, held until the screen has shown it (issue #190).
 *
 * @property id makes two consecutive identical failures DIFFERENT values. Without
 *   it, "delete failed → Retry → delete failed the same way" produces an equal
 *   `HistoryFailure`, `StateFlow` conflates the repeat away, and the second
 *   failure is never announced — a silent failure introduced by the fix for
 *   silent failures.
 * @property translation the row the write was asked for, so Retry can run the
 *   SAME operation on the SAME row instead of guessing from the visible list.
 */
data class HistoryFailure(
    val id: Long,
    val write: HistoryWrite,
    val translation: Translation,
)

/**
 * History/Saved state holder (issue #68) — reads the SAME Room flows the
 * drawer's Recents already uses (the write side lives in the use case; this
 * screen only ASKS, per APP_STRUCTURE).
 */
@HiltViewModel
class HistoryViewModel
    @Inject
    constructor(
        private val repository: TranslationRepository,
    ) : ViewModel() {
        val history: StateFlow<List<Translation>> =
            repository
                .history()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyList())

        val favourites: StateFlow<List<Translation>> =
            repository
                .favourites()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyList())

        private val failureIds = AtomicLong()
        private val _failure = MutableStateFlow<HistoryFailure?>(null)

        /**
         * The write that did not land, or null. Issue #190: all three writes used
         * to throw straight out of a scope with no handler, so a locked or full
         * database took the whole app down and the user never learned whether
         * their delete happened. EDGE_CASES §94 forbids exactly that — "no crash
         * instead of a message" — and requires a way forward, which is the
         * snackbar's Retry.
         *
         * A retained StateFlow, NOT a one-shot event flow. The History entry stays
         * on the back stack while the user is in the composer, so the ViewModel can
         * outlive its composition: an event emitted into that gap has no subscriber
         * and is dropped forever (the shape `MutableSharedFlow(replay = 0)` has in
         * `PaywallViewModel`, where every emitter runs while the screen is on top).
         * Held state survives the gap and is shown when History is next composed.
         * [onFailureShown] is how the screen says it no longer needs to.
         */
        val failure: StateFlow<HistoryFailure?> = _failure.asStateFlow()

        fun toggleFavourite(translation: Translation) {
            viewModelScope.launch {
                writeSafely(HistoryWrite.FAVOURITE, translation) {
                    repository.setFavourite(translation.id, !translation.favourite)
                }
            }
        }

        /** Swipe-to-delete (issue #80). Undo restores the SAME content. */
        fun delete(translation: Translation) {
            viewModelScope.launch {
                writeSafely(HistoryWrite.DELETE, translation) { repository.delete(translation.id) }
            }
        }

        /**
         * Issue #179 — this used to call `save(copy(id = 0L))` and throw the result
         * away. On a retaken C-8 tuple the DAO's `IGNORE` returned -1 and Undo did
         * nothing at all, silently, after the snackbar had already promised the
         * delete was reversible. `restore` owns that decision now (one home, per
         * APP_STRUCTURE) and merges instead of no-op'ing.
         *
         * It is also the longest of the three writes: `restore` runs a `@Transaction`
         * DAO method, so it has the most statements that can raise — and it is the
         * one whose failure LOSES data, because the delete it is undoing succeeded.
         */
        fun undoDelete(translation: Translation) {
            viewModelScope.launch {
                writeSafely(HistoryWrite.RESTORE, translation) { repository.restore(translation) }
            }
        }

        /**
         * The snackbar's Retry — runs the write that failed, on the row it failed
         * for. Each retry goes back through [writeSafely], so a second failure is
         * announced exactly like the first rather than falling silent.
         */
        fun retry(failure: HistoryFailure) {
            when (failure.write) {
                HistoryWrite.DELETE -> delete(failure.translation)
                HistoryWrite.RESTORE -> undoDelete(failure.translation)
                HistoryWrite.FAVOURITE -> toggleFavourite(failure.translation)
            }
        }

        /**
         * The screen has shown [shown] and does not need it again.
         *
         * Compare-and-set, not a plain clear: a second write can fail while the
         * first message is still on screen, and clearing blind would throw that
         * newer failure away unread.
         */
        fun onFailureShown(shown: HistoryFailure) {
            _failure.compareAndSet(shown, null)
        }

        /**
         * Every History write goes through here.
         *
         * `Throwable`, not `Exception`: a write that dies takes the process down
         * from a scope with no `CoroutineExceptionHandler`, and the point of this
         * guard is that nothing gets out. Cancellation is re-thrown FIRST and by
         * name — #142 shipped a worker that died permanently because a
         * `catch (e: Exception)` ate the `CancellationException` that was meant to
         * stop it cleanly. Widening the catch does not fix that (cancellation is a
         * `Throwable` too); only the re-throw does. Same shape as
         * `TranslateTextUseCase.stampSafely` (#141), for the same reason.
         *
         * Cancellation is also the navigate-away case: popping History clears this
         * ViewModel, which cancels the write in flight. Re-throwing means no
         * message is invented for a user who is no longer there — and no snackbar
         * is queued against a screen that has gone.
         */
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        private suspend fun writeSafely(
            write: HistoryWrite,
            translation: Translation,
            block: suspend () -> Unit,
        ) {
            try {
                block()
            } catch (rethrown: CancellationException) {
                throw rethrown // never break structured cancellation
            } catch (unused: Throwable) {
                // The cause is deliberately not shown: "database disk image is
                // malformed (code 11)" is not a sentence for a user. What they get
                // is which action failed, and a way to run it again.
                _failure.value = HistoryFailure(failureIds.incrementAndGet(), write, translation)
            }
        }
    }
