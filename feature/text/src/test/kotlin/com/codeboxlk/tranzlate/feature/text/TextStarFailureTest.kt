package com.codeboxlk.tranzlate.feature.text

import androidx.lifecycle.ViewModelStore
import com.codeboxlk.tranzlate.core.model.Engine
import com.codeboxlk.tranzlate.core.model.Translation
import com.codeboxlk.tranzlate.core.testing.FakeTranslationRepository
import com.codeboxlk.tranzlate.core.testing.TestDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * What the composer's star does when the database refuses (issue #195).
 *
 * Its own class rather than another section of `TextViewModelTest`, for the
 * reason `TextSpeakerLifetimeTest` gives: a second concern in that class puts it
 * past detekt's `LargeClass`. The harness is shared (`TextTestHarness.kt`).
 *
 * Reproduced before the fix: with the repository throwing on each of the three
 * calls in turn, the unguarded `viewModelScope.launch` let the exception out
 * entirely unhandled — 4 of 4 harness runs failed carrying the raw
 * `IllegalStateException: disk I/O error (code 1802)`, which on a device is the
 * process-level handler, i.e. the app disappearing while the user reads their
 * translation. The same harness passed 4 of 4 after the fix.
 *
 * Each of the three write points gets its OWN test, and each asserts a DIFFERENT
 * star face, because a failure lands with three different amounts already done.
 * One test covering the insert would have left the lookup and the update exactly
 * as they were — which is how this survived on all three at once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TextStarFailureTest {
    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val dispatcherRule = TestDispatcherRule(dispatcher)

    private fun settle() = dispatcher.scheduler.advanceUntilIdle()

    /** SQLite's real failures — disk full, locked, corrupt — all arrive as unchecked throws. */
    private fun diskFailure(): Nothing = error("disk I/O error (code 1802)")

    /** Truth's `isNotNull()` does not smart-cast, and every assertion below needs the value. */
    private fun TextViewModel.surfacedStarFailure(): StarFailure =
        checkNotNull(starFailure.value) { "no failure was surfaced — this write path has no error handling" }

    /** A settled en→fr result (G1) whose history row is already written. */
    private fun starredComposer(repository: FakeTranslationRepository): TextViewModel {
        val vm = textViewModel(dispatcher, repository = repository)
        settle()
        vm.onInputChange("Good morning")
        vm.onTranslate()
        settle()
        return vm
    }

    /**
     * POINT 1 — the LOOKUP. Nothing was written and nothing was learned, so the
     * star may not move: it still shows the bookmark the user is looking at.
     */
    @Test
    fun `REPRO 195 - a failing lookup surfaces a retryable failure and leaves the star alone`() {
        val repository = FakeTranslationRepository()
        val vm = starredComposer(repository)
        vm.onToggleFavourite() // saves for real first, so the icon is FILLED
        settle()
        assertThat(vm.resultFavourite.value).isTrue()
        repository.beforeCached = { diskFailure() }

        vm.onToggleFavourite()
        settle()

        val failure = vm.surfacedStarFailure()
        assertThat(failure.intent).isEqualTo(StarIntent.REMOVE) // the tap was on a FILLED star
        assertThat(failure.result).isEqualTo(vm.uiState.value)
        assertThat(vm.resultFavourite.value).isTrue() // unmoved — we learned nothing
        assertThat(repository.saved.single().favourite).isTrue()
    }

    /**
     * POINT 2 — the INSERT. The lookup just proved there is no row, so the star
     * is unfilled: this translation genuinely is not saved anywhere.
     */
    @Test
    fun `REPRO 195 - a failing insert surfaces a retryable failure and leaves the star unfilled`() =
        runTest(dispatcher) {
            val repository = FakeTranslationRepository()
            val vm = starredComposer(repository)
            // The user deleted this row from History, so the star's lookup finds
            // nothing and the tap becomes an insert (the KDoc's "row the history
            // write skipped" path, reached the way a user actually reaches it).
            repository.delete(repository.saved.single().id)
            repository.beforeSave = { diskFailure() }

            vm.onToggleFavourite()
            settle()

            val failure = vm.surfacedStarFailure()
            assertThat(failure.intent).isEqualTo(StarIntent.SAVE)
            assertThat(vm.resultFavourite.value).isFalse()
            assertThat(repository.saved).isEmpty() // nothing landed, and the star says so
        }

    /**
     * POINT 3 — the UPDATE. The lookup DID answer, so the star shows the row's
     * stored flag rather than the stale one it was painting or the one the tap
     * asked for. Set up with the row starred behind the composer's back (the
     * History screen writes the same table), so "what the icon showed", "what
     * the tap asked for" and "what is stored" are three different values and
     * only the stored one passes.
     */
    @Test
    fun `REPRO 195 - a failing update surfaces a retryable failure and the star shows what is stored`() =
        runTest(dispatcher) {
            val repository = FakeTranslationRepository()
            val vm = starredComposer(repository)
            assertThat(vm.resultFavourite.value).isFalse()
            repository.setFavourite(repository.saved.single().id, favourite = true)
            repository.beforeSetFavourite = { diskFailure() }

            vm.onToggleFavourite()
            settle()

            val failure = vm.surfacedStarFailure()
            assertThat(failure.intent).isEqualTo(StarIntent.SAVE) // what the user's tap meant
            assertThat(vm.resultFavourite.value).isTrue() // …but the truth is that it IS saved
            assertThat(repository.saved.single().favourite).isTrue() // the flip never happened
        }

    /**
     * The reason the catch is `Throwable` and not `Exception`, verified rather
     * than inherited: `Room.databaseBuilder` with no driver override is Room over
     * framework SQLite, and every statement ends in a `native` method on
     * `android.database.sqlite.SQLiteConnection` (SDK sources `:138-167` declare
     * them, `:754` calls one). A JNI link that cannot be satisfied raises
     * `UnsatisfiedLinkError` — a `LinkageError`, so an `Error`, so NOT an
     * `Exception`. Narrowing the catch hands it straight back to the handler-less
     * scope this whole fix exists to close, and every other test here stays green
     * while it does (measured: mutation M5 killed nothing before this test).
     */
    @Test
    fun `an Error from the database is surfaced, not allowed to escape`() {
        val repository = FakeTranslationRepository()
        val vm = starredComposer(repository)
        repository.beforeCached = { throw UnsatisfiedLinkError("libsqlite.so") }

        vm.onToggleFavourite()
        settle()

        assertThat(vm.surfacedStarFailure().intent).isEqualTo(StarIntent.SAVE)
    }

    /**
     * #142's defect, guarded directly: a `catch (e: Exception)` that swallows
     * `CancellationException` looks correct and breaks structured cancellation.
     * Widening to `Throwable` does nothing about it — cancellation is an
     * `Exception` — so only the re-throw protects it. Cancelling is not failing,
     * and it must never be reported to the user as one.
     */
    @Test
    fun `a cancelled star write is never reported as a failure`() {
        val repository = FakeTranslationRepository()
        val vm = starredComposer(repository)
        repository.beforeCached = { throw CancellationException("scope is going away") }

        vm.onToggleFavourite()
        settle()

        assertThat(vm.starFailure.value).isNull()
    }

    /**
     * The navigate-away case a screen-scoped snackbar could not cover: this
     * ViewModel is cleared with the Activity, which cancels the write in flight.
     * There is nobody left to tell, so nothing may be queued for a screen that
     * has gone.
     */
    @Test
    fun `a star write cancelled by the ViewModel being cleared surfaces nothing`() {
        val repository = FakeTranslationRepository()
        val vm = starredComposer(repository)
        val store = ViewModelStore()
        store.put("text", vm)
        repository.beforeCached = { awaitCancellation() } // the write is still open

        vm.onToggleFavourite()
        settle()
        store.clear() // == the composer's host going away
        settle()

        assertThat(vm.starFailure.value).isNull()
    }

    /** Retry re-runs the write that failed, and when the disk recovers it lands. */
    @Test
    fun `Retry runs the star write that failed, on the result it failed for`() {
        val repository = FakeTranslationRepository()
        val vm = starredComposer(repository)
        repository.beforeSetFavourite = { diskFailure() }
        vm.onToggleFavourite()
        settle()
        val failure = vm.surfacedStarFailure()
        repository.beforeSetFavourite = null

        vm.retryStar(failure)
        settle()

        assertThat(repository.saved.single().favourite).isTrue()
        assertThat(vm.resultFavourite.value).isTrue()
    }

    /**
     * The snackbar outlives the translation it is about — it stays up long
     * enough for the user to open a History row — so Retry must check the result
     * is still the one the message named. Starring whatever happens to be on
     * screen instead is a write nobody asked for.
     */
    @Test
    fun `Retry writes nothing once the composer has moved to another result`() {
        val repository = FakeTranslationRepository()
        val vm = starredComposer(repository)
        repository.beforeSetFavourite = { diskFailure() }
        vm.onToggleFavourite()
        settle()
        val failure = vm.surfacedStarFailure()
        repository.beforeSetFavourite = null
        val other =
            Translation(
                id = 99,
                sourceLang = "de",
                sourceText = "Hallo Welt",
                targetLang = "en",
                targetText = "Hello world",
                engine = Engine.ONLINE_GOOGLE,
                createdAt = 5L,
            )
        vm.onHistoryPick(other) // the user opened something else while the message was up
        settle()

        vm.retryStar(failure)
        settle()

        assertThat(repository.saved.single().favourite).isFalse() // the named row is untouched
        assertThat(vm.resultFavourite.value).isFalse()
    }

    /**
     * Retry that fails the same way must announce itself. Two identical failures
     * are equal as data and a `StateFlow` conflates equal values — so without a
     * distinguishing id the second one is dropped and the user, having just
     * tapped Retry, is told nothing at all by the fix for being told nothing.
     */
    @Test
    fun `the same star write failing twice produces two distinct failures`() {
        val repository = FakeTranslationRepository()
        val vm = starredComposer(repository)
        repository.beforeSetFavourite = { diskFailure() }
        vm.onToggleFavourite()
        settle()
        val first = vm.surfacedStarFailure()

        vm.onStarFailureShown(first)
        vm.retryStar(first)
        settle()

        val second = vm.surfacedStarFailure()
        assertThat(second).isNotEqualTo(first)
        assertThat(second.intent).isEqualTo(first.intent)
        assertThat(second.result).isEqualTo(first.result)
    }

    /** Consuming the message on screen must not swallow one that arrived behind it. */
    @Test
    fun `a newer star failure survives an older one being shown`() {
        val repository = FakeTranslationRepository()
        val vm = starredComposer(repository)
        repository.beforeSetFavourite = { diskFailure() }
        vm.onToggleFavourite()
        settle()
        val shown = vm.surfacedStarFailure()
        vm.onToggleFavourite() // fails again while the first message is still up
        settle()

        vm.onStarFailureShown(shown)

        val pending = vm.surfacedStarFailure()
        assertThat(pending).isNotEqualTo(shown)
    }

    /**
     * A message is only worth showing while its subject is on screen. This
     * ViewModel outlives the composer's composition, so a failure can still be
     * waiting when the user walks away — and arriving then, about a translation
     * they can no longer see and with a Retry that has nothing to act on, is
     * noise rather than an account of what happened.
     */
    @Test
    fun `leaving the composer retires a star failure that was still waiting to be shown`() {
        val repository = FakeTranslationRepository()
        val vm = starredComposer(repository)
        repository.beforeSetFavourite = { diskFailure() }
        vm.onToggleFavourite()
        settle()
        assertThat(vm.starFailure.value).isNotNull() // held, never shown

        vm.onComposerDismissed() // Back to Home — the result is gone
        settle()

        assertThat(vm.starFailure.value).isNull()
    }

    /** Same rule for the other way a result is left behind: translating something else. */
    @Test
    fun `a new result retires a star failure the user never saw`() {
        val repository = FakeTranslationRepository()
        val vm = starredComposer(repository)
        repository.beforeSetFavourite = { diskFailure() }
        vm.onToggleFavourite()
        settle()
        assertThat(vm.starFailure.value).isNotNull()

        vm.onHistoryPick(
            Translation(
                id = 99,
                sourceLang = "de",
                sourceText = "Hallo Welt",
                targetLang = "en",
                targetText = "Hello world",
                engine = Engine.ONLINE_GOOGLE,
                createdAt = 5L,
            ),
        )
        settle()

        assertThat(vm.starFailure.value).isNull()
    }

    /**
     * The star's READ half had the identical defect and runs on EVERY result, so
     * an unguarded lookup ends the process before the user can reach the star at
     * all — fixing only the tap would have left the issue's own harm alive on the
     * path nobody checked. Nothing was asked for here, so nothing is reported:
     * the icon shows unfilled, and the tap that follows is what speaks.
     */
    @Test
    fun `a failing favourite refresh leaves the star unfilled instead of ending the process`() {
        val repository = FakeTranslationRepository()
        val vm = starredComposer(repository)
        vm.onToggleFavourite()
        settle()
        assertThat(vm.resultFavourite.value).isTrue()
        val row = repository.saved.single()
        repository.beforeCached = { diskFailure() }

        vm.onHistoryPick(row) // re-opening it re-reads the star
        settle()

        assertThat(vm.resultFavourite.value).isFalse()
        assertThat(vm.starFailure.value).isNull() // the user asked for nothing
    }
}
