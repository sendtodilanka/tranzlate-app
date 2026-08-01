package com.codeboxlk.tranzlate.feature.history

import androidx.lifecycle.ViewModelStore
import app.cash.turbine.test
import com.codeboxlk.tranzlate.core.model.Engine
import com.codeboxlk.tranzlate.core.model.Translation
import com.codeboxlk.tranzlate.core.testing.FakeTranslationRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun row(
        id: Long,
        text: String,
        favourite: Boolean = false,
        at: Long = id,
    ) = Translation(
        id = id,
        sourceLang = "en",
        sourceText = text,
        targetLang = "fr",
        targetText = "$text (fr)",
        engine = Engine.ONLINE_GOOGLE,
        favourite = favourite,
        createdAt = at,
    )

    @Test
    fun `history is newest-first and favourites filter to starred rows`() =
        runTest {
            val repo = FakeTranslationRepository()
            repo.save(row(1, "older"))
            repo.save(row(2, "newer", favourite = true))
            val vm = HistoryViewModel(repo)

            vm.history.test {
                skipItems(1) // stateIn initial emptyList
                assertThat(awaitItem().map(Translation::sourceText)).containsExactly("newer", "older").inOrder()
            }
            vm.favourites.test {
                skipItems(1)
                assertThat(awaitItem().single().sourceText).isEqualTo("newer")
            }
        }

    @Test
    fun `delete removes the row and undo restores the SAME content`() =
        runTest {
            val repo = FakeTranslationRepository()
            repo.save(row(1, "keep me", favourite = true, at = 42))
            val saved = repo.saved.single()
            val vm = HistoryViewModel(repo)

            vm.delete(saved)
            dispatcher.scheduler.advanceUntilIdle()
            assertThat(repo.saved).isEmpty()

            vm.undoDelete(saved)
            dispatcher.scheduler.advanceUntilIdle()

            val restored = repo.saved.single()
            assertThat(restored.sourceText).isEqualTo("keep me")
            assertThat(restored.favourite).isTrue() // star survives the round trip
            assertThat(restored.createdAt).isEqualTo(42)
        }

    @Test
    fun `REPRO 179 - undo onto an occupied tuple keeps the star and the stamp`() =
        runTest {
            val repo = FakeTranslationRepository()
            repo.save(row(1, "raced", favourite = true, at = 42))
            val saved = repo.saved.single()
            val vm = HistoryViewModel(repo)
            vm.delete(saved)
            dispatcher.scheduler.advanceUntilIdle()
            repo.save(row(9, "raced", at = 99)) // the same tuple came back before Undo

            vm.undoDelete(saved)

            dispatcher.scheduler.advanceUntilIdle()
            val survivor = repo.saved.single() // still exactly one row for the tuple
            assertThat(survivor.favourite).isTrue()
            assertThat(survivor.createdAt).isEqualTo(42)
        }

    @Test
    fun `toggle flips the row's favourite in the repository`() =
        runTest {
            val repo = FakeTranslationRepository()
            repo.save(row(1, "keep me"))
            val saved = repo.saved.single()
            val vm = HistoryViewModel(repo)

            vm.toggleFavourite(saved)
            dispatcher.scheduler.advanceUntilIdle()

            assertThat(repo.saved.single().favourite).isTrue()

            vm.toggleFavourite(repo.saved.single())
            dispatcher.scheduler.advanceUntilIdle()

            assertThat(repo.saved.single().favourite).isFalse()
        }

    // ── Issue #190: a write that fails must say so, not end the process. ──────
    //
    // Reproduced before the fix: with the repository throwing, each of the three
    // launches let the exception out of `viewModelScope` entirely unhandled — the
    // repro test failed carrying the raw `IllegalStateException`, which on a
    // device is the process-level handler, i.e. the app disappearing. Each path
    // gets its OWN test on purpose: one test covering `delete` would have left
    // the other two exactly as they were, which is how this survived in all three.

    /** SQLite's real failures — disk full, locked, corrupt — all arrive as unchecked throws. */
    private fun diskFailure(): Nothing = error("disk I/O error (code 1802)")

    /** Truth's `isNotNull()` does not smart-cast, and every assertion below needs the value. */
    private fun HistoryViewModel.surfacedFailure(): HistoryFailure =
        checkNotNull(failure.value) { "no failure was surfaced — this write path has no error handling" }

    @Test
    fun `REPRO 190 - a failing delete surfaces a retryable failure instead of escaping`() =
        runTest {
            val repo = FakeTranslationRepository()
            repo.save(row(1, "keep me"))
            val saved = repo.saved.single()
            val vm = HistoryViewModel(repo)
            repo.beforeDelete = { diskFailure() }

            vm.delete(saved)
            dispatcher.scheduler.advanceUntilIdle()

            val failure = vm.surfacedFailure()
            assertThat(failure.write).isEqualTo(HistoryWrite.DELETE)
            assertThat(failure.translation).isEqualTo(saved)
            // …and the row is still there, which is what the message must not contradict.
            assertThat(repo.saved).hasSize(1)
        }

    @Test
    fun `REPRO 190 - a failing undo surfaces a retryable failure instead of escaping`() =
        runTest {
            val repo = FakeTranslationRepository()
            repo.save(row(1, "keep me"))
            val saved = repo.saved.single()
            val vm = HistoryViewModel(repo)
            vm.delete(saved)
            dispatcher.scheduler.advanceUntilIdle()
            // #189 routed Undo through `restore()` -> a @Transaction DAO method, so
            // this path runs MORE sql than when #190 was filed, not less.
            repo.beforeRestore = { diskFailure() }

            vm.undoDelete(saved)
            dispatcher.scheduler.advanceUntilIdle()

            val failure = vm.surfacedFailure()
            assertThat(failure.write).isEqualTo(HistoryWrite.RESTORE)
            assertThat(failure.translation).isEqualTo(saved)
            assertThat(repo.saved).isEmpty() // the row really is gone — Undo is the only way back
        }

    @Test
    fun `REPRO 190 - a failing star surfaces a retryable failure instead of escaping`() =
        runTest {
            val repo = FakeTranslationRepository()
            repo.save(row(1, "keep me"))
            val saved = repo.saved.single()
            val vm = HistoryViewModel(repo)
            repo.beforeSetFavourite = { diskFailure() }

            vm.toggleFavourite(saved)
            dispatcher.scheduler.advanceUntilIdle()

            val failure = vm.surfacedFailure()
            assertThat(failure.write).isEqualTo(HistoryWrite.FAVOURITE)
            assertThat(failure.translation).isEqualTo(saved)
            assertThat(repo.saved.single().favourite).isFalse() // the star did not move
        }

    /**
     * The reason the catch is `Throwable` and not `Exception`.
     *
     * `android.database.sqlite` is a thin wrapper over native code, so a broken
     * install surfaces as `UnsatisfiedLinkError` — a `LinkageError`, which is an
     * `Error` and not an `Exception`. Narrowing the catch to `Exception` lets that
     * one straight back out to the handler-less scope this whole fix exists to
     * close, and every other test in this class stays green while it does
     * (measured: mutation M7 in the register turned zero tests red before this
     * test existed).
     */
    @Test
    fun `an Error from the database is surfaced, not allowed to escape`() =
        runTest {
            val repo = FakeTranslationRepository()
            repo.save(row(1, "keep me"))
            val saved = repo.saved.single()
            val vm = HistoryViewModel(repo)
            repo.beforeDelete = { throw UnsatisfiedLinkError("libsqlite.so") }

            vm.delete(saved)
            dispatcher.scheduler.advanceUntilIdle()

            assertThat(vm.surfacedFailure().write).isEqualTo(HistoryWrite.DELETE)
        }

    /**
     * #142's defect, guarded directly: a `catch (e: Exception)` that swallows
     * `CancellationException` looks correct and breaks structured cancellation.
     * Cancelling is not failing, and it must never be reported to the user as one.
     */
    @Test
    fun `a cancelled write is never reported as a failure`() =
        runTest {
            val repo = FakeTranslationRepository()
            repo.save(row(1, "keep me"))
            val saved = repo.saved.single()
            val vm = HistoryViewModel(repo)
            repo.beforeDelete = { throw CancellationException("scope is going away") }
            repo.beforeRestore = { throw CancellationException("scope is going away") }
            repo.beforeSetFavourite = { throw CancellationException("scope is going away") }

            vm.delete(saved)
            vm.undoDelete(saved)
            vm.toggleFavourite(saved)
            dispatcher.scheduler.advanceUntilIdle()

            assertThat(vm.failure.value).isNull()
        }

    /**
     * The navigate-away case the screen's snackbar scope cannot cover: popping
     * History clears this ViewModel, which cancels the write in flight. There is
     * nobody left to tell, so nothing may be queued for a screen that has gone.
     */
    @Test
    fun `a write cancelled by the ViewModel being cleared surfaces nothing`() =
        runTest {
            val repo = FakeTranslationRepository()
            repo.save(row(1, "keep me"))
            val saved = repo.saved.single()
            val vm = HistoryViewModel(repo)
            val store = ViewModelStore()
            store.put("history", vm)
            repo.beforeDelete = { awaitCancellation() } // the write is still open

            vm.delete(saved)
            dispatcher.scheduler.advanceUntilIdle()
            store.clear() // == the user pressing Back off History
            dispatcher.scheduler.advanceUntilIdle()

            assertThat(vm.failure.value).isNull()
        }

    @Test
    fun `Retry runs the write that failed, on the row it failed for`() =
        runTest {
            val repo = FakeTranslationRepository()
            repo.save(row(1, "keep me", favourite = true, at = 42))
            val saved = repo.saved.single()
            val vm = HistoryViewModel(repo)

            // 1. star
            repo.beforeSetFavourite = { diskFailure() }
            vm.toggleFavourite(saved)
            dispatcher.scheduler.advanceUntilIdle()
            repo.beforeSetFavourite = null
            vm.retry(vm.surfacedFailure())
            dispatcher.scheduler.advanceUntilIdle()
            assertThat(repo.saved.single().favourite).isFalse() // un-starred, as asked

            // 2. delete
            repo.beforeDelete = { diskFailure() }
            vm.delete(saved)
            dispatcher.scheduler.advanceUntilIdle()
            repo.beforeDelete = null
            vm.retry(vm.surfacedFailure())
            dispatcher.scheduler.advanceUntilIdle()
            assertThat(repo.saved).isEmpty()

            // 3. undo
            repo.beforeRestore = { diskFailure() }
            vm.undoDelete(saved)
            dispatcher.scheduler.advanceUntilIdle()
            repo.beforeRestore = null
            vm.retry(vm.surfacedFailure())
            dispatcher.scheduler.advanceUntilIdle()
            assertThat(repo.saved.single().createdAt).isEqualTo(42) // the SAME row came back
        }

    /**
     * Retry that fails the same way must announce itself. Two identical failures
     * are equal as data, and a `StateFlow` conflates equal values — so without a
     * distinguishing id the second one is dropped and the user, having tapped
     * Retry, is told nothing at all.
     */
    @Test
    fun `the same write failing twice produces two distinct failures`() =
        runTest {
            val repo = FakeTranslationRepository()
            repo.save(row(1, "keep me"))
            val saved = repo.saved.single()
            val vm = HistoryViewModel(repo)
            repo.beforeDelete = { diskFailure() }

            vm.delete(saved)
            dispatcher.scheduler.advanceUntilIdle()
            val first = vm.surfacedFailure()
            vm.onFailureShown(first)
            vm.retry(first)
            dispatcher.scheduler.advanceUntilIdle()
            val second = vm.surfacedFailure()

            assertThat(second).isNotEqualTo(first)
            assertThat(second.write).isEqualTo(first.write)
        }

    /** Consuming the message on screen must not swallow one that arrived behind it. */
    @Test
    fun `a newer failure survives an older one being shown`() =
        runTest {
            val repo = FakeTranslationRepository()
            repo.save(row(1, "keep me"))
            val saved = repo.saved.single()
            val vm = HistoryViewModel(repo)
            repo.beforeDelete = { diskFailure() }
            vm.delete(saved)
            dispatcher.scheduler.advanceUntilIdle()
            val shown = vm.surfacedFailure()

            repo.beforeSetFavourite = { diskFailure() }
            vm.toggleFavourite(saved) // fails while the delete message is still up
            dispatcher.scheduler.advanceUntilIdle()
            vm.onFailureShown(shown)

            val pending = vm.surfacedFailure()
            assertThat(pending.write).isEqualTo(HistoryWrite.FAVOURITE)
        }
}
