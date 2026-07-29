package com.codeboxlk.tranzlate.feature.history

import app.cash.turbine.test
import com.codeboxlk.tranzlate.core.model.Engine
import com.codeboxlk.tranzlate.core.model.Translation
import com.codeboxlk.tranzlate.core.testing.FakeTranslationRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
}
