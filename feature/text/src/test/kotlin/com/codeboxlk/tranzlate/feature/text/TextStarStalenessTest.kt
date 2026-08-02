package com.codeboxlk.tranzlate.feature.text

import com.codeboxlk.tranzlate.core.model.Translation
import com.codeboxlk.tranzlate.core.testing.FakeTranslationRepository
import com.codeboxlk.tranzlate.core.testing.TestDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * What the star does when a database call OUTLIVES the result it was made for
 * (issue #195 co-verify, findings F1/F2/F3).
 *
 * `TextStarFailureTest` covers the three failure points in sequence: tap, wait,
 * see. Every one of those tests settles the scheduler between the tap and the
 * assertion, so no test there can see the state the composer is actually in
 * while a slow disk is being waited on — and that state is the one this issue
 * exists for. The guard added for the crash published its answer unconditionally
 * when the write finished, whatever was on screen by then, so the fix for a
 * crash introduced a star that lies about a DIFFERENT translation.
 *
 * Its own class rather than more of `TextStarFailureTest`, for the reason that
 * class gives for existing at all: a second concern pushes it past detekt's
 * `LargeClass`. The harness is shared (`TextTestHarness.kt`).
 *
 * The slow disk is a [CompletableDeferred] awaited inside the repository's own
 * fault hooks, which is what makes the middle of a write reachable: the call is
 * held open, the test moves the composer on, and only then is the call let go.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TextStarStalenessTest {
    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val dispatcherRule = TestDispatcherRule(dispatcher)

    private fun settle() = dispatcher.scheduler.advanceUntilIdle()

    private fun diskFailure(): Nothing = error("disk I/O error (code 1802)")

    /** Result A — a settled en→fr translation (G1) whose history row is written. */
    private fun composerOnResultA(repository: FakeTranslationRepository): TextViewModel {
        val vm = textViewModel(dispatcher, repository = repository)
        settle()
        vm.onInputChange("Good morning")
        vm.onTranslate()
        settle()
        return vm
    }

    /**
     * Result B — the C-7 reverse of A (golden G12), so the composer is showing a
     * REAL second translation with its own C-8 tuple and its own history row,
     * not a hand-built state. B has never been starred, so its star is unfilled
     * and any filled star on this screen is a lie about B.
     */
    private fun TextViewModel.translateResultB() {
        assertThat(onReverse()).isTrue()
        settle()
    }

    private fun FakeTranslationRepository.row(sourceText: String): Translation =
        saved.single { it.sourceText == sourceText }

    /**
     * F1 — the star write that outlived its result.
     *
     * The user stars A while the disk is slow, which is EXACTLY the condition
     * this issue exists to survive, and translates B before it lands. B's star
     * is correctly unfilled — B has no row at all. A's write then finishes and
     * paints its answer onto B, and the user is looking at a filled star over a
     * translation that is saved nowhere.
     *
     * Both halves are asserted, because either one alone passes on a wrong fix:
     * the ICON must stay B's, and the ROW must still be A's — a fix that
     * "solved" this by cancelling or dropping the write would leave the star the
     * user tapped unsaved, which is the same silent loss one level down.
     */
    @Test
    fun `a star write that lands after the composer moved on never touches the new result's icon`() {
        val repository = FakeTranslationRepository()
        val vm = composerOnResultA(repository)
        val slowDisk = CompletableDeferred<Unit>()
        repository.beforeSetFavourite = { slowDisk.await() }

        vm.onToggleFavourite() // the user stars A…
        settle() // …and the write is now parked inside the database call
        vm.translateResultB()
        assertThat(vm.resultFavourite.value).isFalse() // B has no row — correct

        slowDisk.complete(Unit) // A's write finally lands
        settle()

        assertThat(vm.resultFavourite.value).isFalse() // still B's star, not A's
        assertThat(repository.row("Bonjour (fake)").favourite).isFalse() // B really is unsaved
        assertThat(repository.row("Good morning").favourite).isTrue() // …and A's save was NOT lost
    }

    /**
     * F2 — the star failure that outlived its result.
     *
     * `state`'s retirement effect clears a failure that already EXISTS when the
     * composer moves on. It cannot do anything about one created afterwards, and
     * that is the one the slow disk produces: the message names A, arrives while
     * B is on screen, and its Retry is refused by `retryStar`'s staleness guard
     * without a word — the dead end EDGE_CASES forbids.
     *
     * There is nothing to say about a translation the user can no longer see, so
     * the correct count of messages is zero. Retry cannot dead-end on a message
     * that was never shown, which is a stronger answer than making Retry explain
     * itself would be.
     */
    @Test
    fun `a star write that fails after the composer moved on says nothing about the result that is gone`() {
        val repository = FakeTranslationRepository()
        val vm = composerOnResultA(repository)
        val slowDisk = CompletableDeferred<Unit>()
        repository.beforeSetFavourite = {
            slowDisk.await()
            diskFailure()
        }

        vm.onToggleFavourite()
        settle()
        vm.translateResultB()
        assertThat(vm.starFailure.value).isNull()

        slowDisk.complete(Unit) // the write fails, about a result nobody is looking at
        settle()

        assertThat(vm.starFailure.value).isNull()
        assertThat(vm.resultFavourite.value).isFalse() // B's star is not moved by A's failure either
    }

    /**
     * The invariant behind F2, asserted where it can be seen rather than left
     * implied: a failure only ever exists while the composer is still showing
     * the result it names. A message about anything else has no Retry that can
     * act, so it is noise the user has to dismiss.
     */
    @Test
    fun `a star failure that IS published always names the result on screen`() {
        val repository = FakeTranslationRepository()
        val vm = composerOnResultA(repository)
        repository.beforeSetFavourite = { diskFailure() }

        vm.onToggleFavourite()
        settle()

        val failure = checkNotNull(vm.starFailure.value)
        assertThat(failure.result).isEqualTo(vm.uiState.value)
    }

    /**
     * F3 — the rapid double tap.
     *
     * Both taps read the same unmoved icon, so both ask to SAVE, and with the
     * write open both lookups answer "no row". The second insert then loses the
     * C-8 tuple race and the DAO answers `-1` exactly as Room's `IGNORE` does —
     * so the star reports unfilled for a row that is, in the database, saved and
     * favourited. Icon and row disagree and nothing is said.
     *
     * One write at a time is the answer, not a queued second write: on a slow
     * disk the icon has not moved yet, so both taps meant the same thing, and
     * running the pair would undo the save the user could see they asked for.
     * It is not a dead end — the first write still finishes visibly, as a filled
     * star or as a failure with Retry, and the star stays live throughout.
     */
    @Test
    fun `a second star tap while the first write is still open never lands a write that lies`() =
        runTest(dispatcher) {
            val repository = FakeTranslationRepository()
            val vm = composerOnResultA(repository)
            repository.delete(repository.row("Good morning").id) // deleted from History → the insert path
            val slowDisk = CompletableDeferred<Unit>()
            repository.beforeSave = { slowDisk.await() }

            vm.onToggleFavourite()
            settle()
            vm.onToggleFavourite() // the impatient second tap, first write still open
            settle()
            slowDisk.complete(Unit)
            settle()

            assertThat(repository.saved).hasSize(1)
            assertThat(repository.saved.single().favourite).isTrue()
            assertThat(vm.resultFavourite.value).isTrue() // the icon agrees with the row
            assertThat(vm.starFailure.value).isNull()
        }

    /**
     * The READ half has the identical defect, and it was not in the brief — it
     * was found by enumerating what writes `_resultFavourite` rather than by
     * following the finding: FOUR places, of which three are asynchronous. A
     * lookup started for one result and finishing after the composer moved on
     * paints the old translation's bookmark onto the new one, which is F1's harm
     * reached without touching the star at all.
     */
    @Test
    fun `a favourite lookup that lands after the composer moved on never paints the new result`() {
        val repository = FakeTranslationRepository()
        val vm = composerOnResultA(repository)
        vm.onToggleFavourite() // A is genuinely saved, so its stored flag is TRUE
        settle()
        assertThat(vm.resultFavourite.value).isTrue()
        val rowA = repository.row("Good morning")
        val slowDisk = CompletableDeferred<Unit>()
        repository.beforeCached = { slowDisk.await() }

        vm.onHistoryPick(rowA) // re-opening A starts a fresh lookup…
        settle() // …which is now parked
        repository.beforeCached = null
        vm.translateResultB()
        assertThat(vm.resultFavourite.value).isFalse()

        slowDisk.complete(Unit) // A's lookup answers "favourited" — about A
        settle()

        assertThat(vm.resultFavourite.value).isFalse() // B is still unsaved, and says so
    }
}
