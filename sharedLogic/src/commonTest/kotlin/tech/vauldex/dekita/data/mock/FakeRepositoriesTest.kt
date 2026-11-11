package tech.vauldex.dekita.data.mock

import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import tech.vauldex.dekita.data.LoadState
import tech.vauldex.dekita.data.dataOrNull
import tech.vauldex.dekita.domain.CartLine
import tech.vauldex.dekita.domain.FulfillmentMode
import tech.vauldex.dekita.domain.OrderStatus
import tech.vauldex.dekita.domain.QueueStatus
import tech.vauldex.dekita.domain.StoreAvailability
import tech.vauldex.dekita.domain.TransitionResult

@OptIn(ExperimentalCoroutinesApi::class)
class FakeRepositoriesTest {

    private val fixedClock = object : Clock {
        override fun now() = Instant.fromEpochSeconds(1_800_000_000)
    }

    /** Fakes run in backgroundScope, which advanceUntilIdle() does not wait for, so move time by hand. */
    private fun TestScope.finishLoading() {
        advanceTimeBy(MockConfig.DEFAULT_LOADING_DELAY)
        runCurrent()
    }

    @Test
    fun storesLoadAfterTheSimulatedDelay() = runTest {
        val repository = FakeStoreRepository(backgroundScope, MockConfig(800.milliseconds))
        runCurrent()
        assertEquals(LoadState.Loading, repository.stores.value)

        advanceTimeBy(799.milliseconds)
        assertEquals(LoadState.Loading, repository.stores.value)

        advanceTimeBy(1.milliseconds)
        runCurrent()
        assertEquals(800L, testScheduler.currentTime)
        assertEquals(seedStores, assertIs<LoadState.Success<*>>(repository.stores.value).data)
    }

    @Test
    fun toggleFavoriteUpdatesTheFlow() = runTest {
        val repository = FakeStoreRepository(backgroundScope, MockConfig())

        repository.toggleFavorite(SeedStoreIds.UDON_HALL)
        assertTrue(SeedStoreIds.UDON_HALL in repository.favoriteStoreIds.value)

        repository.toggleFavorite(SeedStoreIds.UDON_HALL)
        assertFalse(SeedStoreIds.UDON_HALL in repository.favoriteStoreIds.value)
    }

    @Test
    fun resetRestoresSeedFavoritesAndReloads() = runTest {
        val repository = FakeStoreRepository(backgroundScope, MockConfig())
        finishLoading()
        repository.toggleFavorite(SeedStoreIds.RAMEN_BAR)

        repository.reset()
        runCurrent()
        assertEquals(seedFavoriteStoreIds, repository.favoriteStoreIds.value)
        assertEquals(LoadState.Loading, repository.stores.value)

        finishLoading()
        assertIs<LoadState.Success<*>>(repository.stores.value)
    }

    private fun FakeStoreRepository.queueOf(storeId: String) =
        stores.value.dataOrNull()!!.first { it.id == storeId }.queue

    @Test
    fun openQueuesMoveEachTickAndFreezeWhenLiveQueueIsOff() = runTest {
        val config = MockConfig()
        val repository = FakeStoreRepository(backgroundScope, config)
        finishLoading()

        advanceTimeBy(MockConfig.QUEUE_TICK_INTERVAL)
        runCurrent()
        assertEquals(QueueStatus(4, 11), repository.queueOf(SeedStoreIds.RAMEN_BAR))
        assertEquals(QueueStatus(0, 0), repository.queueOf(SeedStoreIds.MART))
        assertNull(repository.queueOf(SeedStoreIds.SOBA_HOUSE))
        assertEquals(QueueStatus(18, 35), repository.queueOf(SeedStoreIds.BENTO_KITCHEN))

        config.liveQueue.value = false
        advanceTimeBy(MockConfig.QUEUE_TICK_INTERVAL * 3)
        runCurrent()
        assertEquals(QueueStatus(4, 11), repository.queueOf(SeedStoreIds.RAMEN_BAR))
    }

    @Test
    fun udonHallCrossesTheHighQueueThreshold() = runTest {
        val repository = FakeStoreRepository(backgroundScope, MockConfig())
        finishLoading()

        val ahead = List(6) {
            advanceTimeBy(MockConfig.QUEUE_TICK_INTERVAL)
            runCurrent()
            repository.queueOf(SeedStoreIds.UDON_HALL)!!.ordersAhead
        }
        assertEquals(listOf(16, 17, 16, 15, 14, 15), ahead)
    }

    @Test
    fun availabilityChangesArePushedWithoutAReload() = runTest {
        val repository = FakeStoreRepository(backgroundScope, MockConfig())
        finishLoading()

        repository.setAvailability(SeedStoreIds.RAMEN_BAR, StoreAvailability.PAUSED)
        val paused = repository.stores.value.dataOrNull().orEmpty().first { it.id == SeedStoreIds.RAMEN_BAR }
        assertEquals(StoreAvailability.PAUSED, paused.availability)
        assertNull(paused.queue, "A paused store has no queue")

        repository.setAvailability(SeedStoreIds.RAMEN_BAR, StoreAvailability.OPEN)
        val reopened = repository.stores.value.dataOrNull().orEmpty().first { it.id == SeedStoreIds.RAMEN_BAR }
        assertEquals(StoreAvailability.OPEN, reopened.availability)
        assertEquals(seedStores.first { it.id == SeedStoreIds.RAMEN_BAR }.queue, reopened.queue)
    }

    @Test
    fun availabilityChangesSurviveARefresh() = runTest {
        val repository = FakeStoreRepository(backgroundScope, MockConfig())
        finishLoading()
        repository.setAvailability(SeedStoreIds.MART, StoreAvailability.PAUSED)

        repository.refresh()
        finishLoading()
        val mart = repository.stores.value.dataOrNull().orEmpty().first { it.id == SeedStoreIds.MART }
        assertEquals(StoreAvailability.PAUSED, mart.availability)
    }

    @Test
    fun failNextLoadFailsOnlyOnce() = runTest {
        val repository = FakeStoreRepository(backgroundScope, MockConfig())
        finishLoading()

        repository.failNextLoad()
        repository.refresh()
        finishLoading()
        assertIs<LoadState.Error>(repository.stores.value)

        repository.refresh()
        finishLoading()
        assertIs<LoadState.Success<*>>(repository.stores.value)
    }

    @Test
    fun preferredModeIsClearedOnReset() {
        val repository = FakeCartRepository()
        repository.setPreferredMode(FulfillmentMode.PICKUP)
        assertEquals(FulfillmentMode.PICKUP, repository.preferredMode.value)

        repository.reset()
        assertNull(repository.preferredMode.value)
    }

    @Test
    fun unknownStoreMenuIsAnError() = runTest {
        val repository = FakeMenuRepository(backgroundScope, MockConfig())
        assertIs<LoadState.Error>(repository.menu("store-missing").value)
    }

    @Test
    fun orderTransitionGoesThroughTheStateMachine() = runTest {
        val repository = FakeOrderRepository(backgroundScope, MockConfig(), fixedClock)
        assertNull(repository.transition("order-42", OrderStatus.SERVED), "Orders are not loaded yet")
        finishLoading()

        val rejected = repository.transition("order-42", OrderStatus.COMPLETED)
        assertIs<TransitionResult.Rejected>(rejected)

        val accepted = repository.transition("order-42", OrderStatus.SERVED)
        assertIs<TransitionResult.Accepted>(accepted)
        val stored = repository.orders.value.dataOrNull().orEmpty().first { it.id == "order-42" }
        assertEquals(OrderStatus.SERVED, stored.status)
    }

    @Test
    fun cartIsScopedToOneStore() {
        val repository = FakeCartRepository()
        val gyoza = seedItem(SeedStoreIds.RAMEN_BAR, "item-gyoza")
        val tea = seedItem(SeedStoreIds.MART, "item-green-tea")

        assertTrue(repository.addLine(FulfillmentMode.IN_STORE, CartLine("l1", gyoza)))
        assertFalse(repository.addLine(FulfillmentMode.PICKUP, CartLine("l2", tea)))
        assertEquals(1, repository.cart.value?.itemCount)

        repository.updateQuantity("l1", 0)
        assertNull(repository.cart.value, "Removing the last line clears the cart")
    }
}
