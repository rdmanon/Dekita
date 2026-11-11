package tech.vauldex.dekita.presentation.discover

import kotlin.time.Clock
import kotlin.time.Instant
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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import tech.vauldex.dekita.data.mock.FakeOrderRepository
import tech.vauldex.dekita.data.mock.FakeStoreRepository
import tech.vauldex.dekita.data.mock.MockConfig

/** Favorites and History, the decided customer's paths into a store. */
@OptIn(ExperimentalCoroutinesApi::class)
class DecidedViewModelsTest {

    private val mainDispatcher = StandardTestDispatcher()

    private val fixedClock = object : Clock {
        override fun now() = Instant.fromEpochSeconds(1_800_000_000)
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.finishLoading() {
        runCurrent()
        advanceTimeBy(MockConfig.DEFAULT_LOADING_DELAY)
        runCurrent()
    }

    @Test
    fun favoritesListItemsWithTheirStore() = runTest(mainDispatcher) {
        val viewModel = FavoritesViewModel(FakeStoreRepository(backgroundScope, MockConfig()))
        backgroundScope.launch { viewModel.state.collect {} }
        finishLoading()

        val shoyu = viewModel.state.value.items.first { it.item.itemName == "Shoyu Ramen" }
        // Scenario 2: tapping Shoyu Ramen opens this store.
        assertEquals("Test Ramen Bar", shoyu.store.name)
        assertEquals(4, viewModel.state.value.items.size)
        assertFalse(viewModel.state.value.isEmpty)
    }

    @Test
    fun favoritesHaveAnEmptyStateOfTheirOwn() = runTest(mainDispatcher) {
        val stores = FakeStoreRepository(backgroundScope, MockConfig())
        val viewModel = FavoritesViewModel(stores)
        backgroundScope.launch { viewModel.state.collect {} }
        finishLoading()

        stores.clearFavorites()
        runCurrent()
        assertTrue(viewModel.state.value.isEmpty)
    }

    @Test
    fun historyListsOrdersNewestFirstAndEachStoreOnce() = runTest(mainDispatcher) {
        val viewModel = HistoryViewModel(
            FakeStoreRepository(backgroundScope, MockConfig()),
            FakeOrderRepository(backgroundScope, MockConfig(), fixedClock),
        )
        backgroundScope.launch { viewModel.state.collect {} }
        finishLoading()

        val state = viewModel.state.value
        val created = state.recentOrders.map { it.order.createdAt }
        assertEquals(created.sortedDescending(), created)
        assertEquals("Test Ramen Bar", state.recentStores.first().name)
        assertEquals(state.recentStores.map { it.id }.distinct(), state.recentStores.map { it.id })
    }

    @Test
    fun historyHasAnEmptyStateOfItsOwn() = runTest(mainDispatcher) {
        val orders = FakeOrderRepository(backgroundScope, MockConfig(), fixedClock)
        val viewModel = HistoryViewModel(FakeStoreRepository(backgroundScope, MockConfig()), orders)
        backgroundScope.launch { viewModel.state.collect {} }
        finishLoading()

        orders.clearAll()
        runCurrent()
        assertTrue(viewModel.state.value.isEmpty)
    }
}
