package tech.vauldex.dekita.presentation.discover

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import tech.vauldex.dekita.data.mock.FakeStoreRepository
import tech.vauldex.dekita.data.mock.MockConfig

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun showsLoadingThenSeedStores() = runTest(mainDispatcher) {
        val viewModel = DiscoverViewModel(FakeStoreRepository(backgroundScope, MockConfig()))
        backgroundScope.launch { viewModel.state.collect {} }

        runCurrent()
        assertTrue(viewModel.state.value.isLoading)

        // The fake runs in backgroundScope, which advanceUntilIdle() does not wait for.
        advanceTimeBy(MockConfig.DEFAULT_LOADING_DELAY)
        runCurrent()
        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(7, state.stores.size)
        assertEquals(4, state.favoritesCount)
    }
}
