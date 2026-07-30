package com.codeboxlk.tranzlate.navigation

import com.codeboxlk.tranzlate.core.model.Engine
import com.codeboxlk.tranzlate.core.model.Translation
import com.codeboxlk.tranzlate.core.testing.FakeTranslationRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DrawerViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `recents cap at four, newest first`() =
        runTest {
            val repo = FakeTranslationRepository()
            repeat(6) { i ->
                repo.save(
                    Translation(
                        sourceLang = "en",
                        sourceText = "text $i",
                        targetLang = "fr",
                        targetText = "texte $i",
                        engine = Engine.ONLINE_GOOGLE,
                        createdAt = i.toLong(),
                    ),
                )
            }
            val vm = DrawerViewModel(repo)
            dispatcher.scheduler.runCurrent()

            val recents = vm.recents.first { it.isNotEmpty() }

            assertThat(recents).hasSize(4)
            assertThat(recents.first().sourceText).isEqualTo("text 5")
        }
}
