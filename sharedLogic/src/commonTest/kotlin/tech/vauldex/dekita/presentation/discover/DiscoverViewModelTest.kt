package tech.vauldex.dekita.presentation.discover

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
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
import tech.vauldex.dekita.data.mock.FakeAuthRepository
import tech.vauldex.dekita.data.mock.FakeCartRepository
import tech.vauldex.dekita.data.mock.FakeOrderRepository
import tech.vauldex.dekita.data.mock.FakeStoreRepository
import tech.vauldex.dekita.data.mock.MockConfig
import tech.vauldex.dekita.data.mock.SeedStoreIds
import tech.vauldex.dekita.data.mock.seedStores
import tech.vauldex.dekita.domain.FulfillmentMode
import tech.vauldex.dekita.domain.SsoProvider
import tech.vauldex.dekita.domain.StoreAvailability
import tech.vauldex.dekita.domain.StoreBadge

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

    private class Fixture(scope: CoroutineScope, modeNeedsCategory: Boolean = false) {
        val stores = FakeStoreRepository(scope, MockConfig())
        val orders = FakeOrderRepository(scope, MockConfig(), object : Clock {
            override fun now() = Instant.fromEpochSeconds(1_800_000_000)
        })
        val cart = FakeCartRepository()
        val auth = FakeAuthRepository(MockConfig())
        val viewModel = DiscoverViewModel(stores, orders, cart, auth, modeNeedsCategory)
    }

    /** Builds the view model, subscribes to it, and waits out the simulated loading delay. */
    private fun TestScope.loadedFixture(modeNeedsCategory: Boolean = false): Fixture {
        val fixture = Fixture(backgroundScope, modeNeedsCategory)
        backgroundScope.launch { fixture.viewModel.state.collect {} }
        finishLoading()
        return fixture
    }

    /** The fakes run in backgroundScope, which advanceUntilIdle() does not wait for. */
    private fun TestScope.finishLoading() {
        runCurrent()
        advanceTimeBy(MockConfig.DEFAULT_LOADING_DELAY)
        runCurrent()
    }

    @Test
    fun showsLoadingThenSeedStores() = runTest(mainDispatcher) {
        val fixture = Fixture(backgroundScope)
        backgroundScope.launch { fixture.viewModel.state.collect {} }

        runCurrent()
        assertTrue(fixture.viewModel.state.value.isLoading)

        advanceTimeBy(MockConfig.DEFAULT_LOADING_DELAY)
        runCurrent()
        val state = fixture.viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(seedStores.size, state.stores.size)
        assertEquals(4, state.favoriteItemCount)
        assertEquals("Test Ramen Bar", state.lastOrderStoreName, "The design's History tile: Last: Test Ramen Bar")
        assertTrue(state.recommended.isNotEmpty())
        assertFalse(state.showsResults)
    }

    @Test
    fun categoriesIncludeNonRestaurantBusinesses() = runTest(mainDispatcher) {
        val categories = loadedFixture().viewModel.state.value.categories
        assertTrue(listOf("Ramen", "Curry", "Salon", "Convenience store").all { it in categories }, "Got $categories")
    }

    @Test
    fun selectingPickupFiltersStoresAndBecomesTheCartMode() = runTest(mainDispatcher) {
        val fixture = loadedFixture()

        fixture.viewModel.selectMode(FulfillmentMode.PICKUP)
        runCurrent()

        val names = fixture.viewModel.state.value.stores.map { it.store.name }
        assertTrue("Sample Massage Studio" !in names)
        assertTrue("Test Ramen Bar" in names)
        assertEquals(FulfillmentMode.PICKUP, fixture.cart.preferredMode.value)
        // The mode alone keeps the home sections.
        assertFalse(fixture.viewModel.state.value.showsResults)
    }

    @Test
    fun theSearchTabKeepsTheModeFilterOnScreenAndApplied() = runTest(mainDispatcher) {
        val fixture = loadedFixture()
        assertTrue(fixture.viewModel.state.value.showsModeFilter)

        fixture.viewModel.selectMode(FulfillmentMode.PICKUP)
        runCurrent()
        assertTrue(fixture.viewModel.state.value.showsModeFilter)
        assertEquals(FulfillmentMode.PICKUP, fixture.viewModel.state.value.filters.mode)
        assertTrue("Sample Massage Studio" !in fixture.viewModel.state.value.stores.map { it.store.name })
    }

    @Test
    fun discoverShowsTheModeFilterOnlyUnderACategoryWithAStoreOfferingBoth() = runTest(mainDispatcher) {
        val fixture = loadedFixture(modeNeedsCategory = true)
        assertFalse(fixture.viewModel.state.value.showsModeFilter, "No category picked yet")

        fixture.viewModel.toggleCategory("Ramen")
        runCurrent()
        assertTrue(fixture.viewModel.state.value.showsModeFilter, "Test Ramen Bar offers both")

        fixture.viewModel.toggleCategory("Ramen")
        for (category in listOf("Curry", "Salon", "Convenience store")) {
            fixture.viewModel.toggleCategory(category)
            runCurrent()
            assertFalse(fixture.viewModel.state.value.showsModeFilter, "$category has no store offering both")
            fixture.viewModel.toggleCategory(category)
        }
    }

    @Test
    fun aModeLeftOverFromAnotherTabDoesNotHideStoresBehindAHiddenFilter() = runTest(mainDispatcher) {
        val fixture = loadedFixture(modeNeedsCategory = true)
        // The Search tab shares the cart's mode, so Discover can start with one that it isn't showing.
        fixture.cart.setPreferredMode(FulfillmentMode.PICKUP)
        runCurrent()

        val state = fixture.viewModel.state.value
        assertNull(state.filters.mode)
        assertEquals(seedStores.size, state.stores.size)
    }

    @Test
    fun pickingAnotherCategoryPutsTheModeBackToAll() = runTest(mainDispatcher) {
        val fixture = loadedFixture(modeNeedsCategory = true)
        fixture.viewModel.toggleCategory("Ramen")
        fixture.viewModel.selectMode(FulfillmentMode.PICKUP)
        runCurrent()
        assertEquals(FulfillmentMode.PICKUP, fixture.viewModel.state.value.filters.mode)

        fixture.viewModel.toggleCategory("Soba")
        runCurrent()
        assertNull(fixture.cart.preferredMode.value)
        assertNull(fixture.viewModel.state.value.filters.mode)
    }

    @Test
    fun clearingTheModeShowsAllStores() = runTest(mainDispatcher) {
        val fixture = loadedFixture()
        fixture.viewModel.selectMode(FulfillmentMode.IN_STORE)
        fixture.viewModel.selectMode(null)
        runCurrent()

        assertEquals(seedStores.size, fixture.viewModel.state.value.stores.size)
        assertNull(fixture.cart.preferredMode.value)
    }

    @Test
    fun tappingACategoryFiltersTheHomeSectionsInPlace() = runTest(mainDispatcher) {
        val fixture = loadedFixture()
        fixture.viewModel.toggleCategory("Curry")
        runCurrent()

        val state = fixture.viewModel.state.value
        assertFalse(state.showsResults, "UI-01: a chip filters Recommended and Nearby, it doesn't switch to results")
        assertEquals(listOf("Sample Curry Stand"), state.stores.map { it.store.name })
        assertTrue(state.recommended.all { it.store.category == "Curry" }, "Recommended is filtered too")
        assertEquals(0, state.filters.sheetFilterCount, "The Filters button doesn't count the chip")
    }

    @Test
    fun categoryChipsAreMultiSelect() = runTest(mainDispatcher) {
        val fixture = loadedFixture()
        fixture.viewModel.toggleCategory("Curry")
        fixture.viewModel.toggleCategory("Ramen")
        runCurrent()
        val state = fixture.viewModel.state.value
        assertEquals(setOf("Curry", "Ramen"), state.filters.categories, "DISC-01: chips add up")
        assertEquals(listOf("Test Ramen Bar", "Sample Curry Stand"), state.stores.map { it.store.name })

        fixture.viewModel.toggleCategory("Ramen")
        fixture.viewModel.toggleCategory("Curry")
        runCurrent()
        assertTrue(fixture.viewModel.state.value.filters.categories.isEmpty(), "Tapping a selected chip turns it off")
        assertEquals(seedStores.size, fixture.viewModel.state.value.stores.size)
    }

    @Test
    fun sheetClearAllKeepsTheChipsAndMode() = runTest(mainDispatcher) {
        val fixture = loadedFixture()
        fixture.viewModel.selectMode(FulfillmentMode.PICKUP)
        fixture.viewModel.toggleCategory("Curry")
        fixture.viewModel.selectWalkLimit(WalkLimit.FIVE)
        fixture.viewModel.toggleStatus(StoreAvailability.OPEN)
        runCurrent()

        fixture.viewModel.clearSheetFilters()
        runCurrent()
        val filters = fixture.viewModel.state.value.filters
        assertNull(filters.walkLimit)
        assertTrue(filters.statuses.isEmpty())
        assertEquals(setOf("Curry"), filters.categories, "Chips aren't in the sheet, so its Clear all leaves them")
        assertEquals(FulfillmentMode.PICKUP, filters.mode)
    }

    @Test
    fun searchStillShowsTheResultList() = runTest(mainDispatcher) {
        val fixture = loadedFixture()
        fixture.viewModel.setQuery("curry")
        runCurrent()
        assertTrue(fixture.viewModel.state.value.showsResults)
    }

    @Test
    fun noMatchesThenClearFiltersRestoresTheFullList() = runTest(mainDispatcher) {
        val fixture = loadedFixture()
        fixture.viewModel.selectMode(FulfillmentMode.PICKUP)
        fixture.viewModel.toggleCategory("Salon")
        runCurrent()
        assertTrue(fixture.viewModel.state.value.hasNoMatches, "Salons are in-store only, so Pickup + Salon matches nothing")

        fixture.viewModel.clearFilters()
        runCurrent()
        val state = fixture.viewModel.state.value
        assertFalse(state.hasNoMatches)
        assertEquals(seedStores.size, state.stores.size)
        assertTrue(state.filters.isCleared)
        assertNull(fixture.cart.preferredMode.value)
    }

    @Test
    fun walkLimitTogglesOffWhenPickedAgain() = runTest(mainDispatcher) {
        val fixture = loadedFixture()
        fixture.viewModel.selectWalkLimit(WalkLimit.FIVE)
        fixture.viewModel.selectWalkLimit(WalkLimit.FIVE)
        runCurrent()
        assertNull(fixture.viewModel.state.value.filters.walkLimit)
    }

    @Test
    fun badgeUpdatesLiveWhenAStoreStopsAcceptingOrders() = runTest(mainDispatcher) {
        val fixture = loadedFixture()
        fun ramenBadge() = fixture.viewModel.state.value.stores.first { it.store.id == SeedStoreIds.RAMEN_BAR }.store.badge
        assertEquals(StoreBadge.OPEN_FOR_LUNCH, ramenBadge())

        fixture.stores.setAvailability(SeedStoreIds.RAMEN_BAR, StoreAvailability.PAUSED)
        runCurrent()

        assertEquals(StoreBadge.PAUSED, ramenBadge(), "Scenario 5: no refresh needed")
        assertFalse(fixture.viewModel.state.value.isLoading)
    }

    @Test
    fun errorStateRetriesToTheFullList() = runTest(mainDispatcher) {
        val fixture = loadedFixture()
        fixture.stores.failNextLoad()
        fixture.stores.refresh()
        finishLoading()
        assertNotNull(fixture.viewModel.state.value.error)
        assertTrue(fixture.viewModel.state.value.stores.isEmpty())

        fixture.viewModel.retry()
        finishLoading()
        val state = fixture.viewModel.state.value
        assertNull(state.error)
        assertEquals(seedStores.size, state.stores.size)
    }

    @Test
    fun modeIsRestoredFromTheCartWhenTheScreenIsRebuilt() = runTest(mainDispatcher) {
        val fixture = loadedFixture()
        fixture.viewModel.selectMode(FulfillmentMode.PICKUP)

        val rebuilt = DiscoverViewModel(fixture.stores, fixture.orders, fixture.cart, fixture.auth)
        assertEquals(FulfillmentMode.PICKUP, rebuilt.state.value.filters.mode)
    }

    // ORDER-01: the active-order banner

    @Test
    fun activeOrderBannerShowsTheLatestPaidOrderToSignedInCustomersOnly() = runTest(mainDispatcher) {
        val fixture = loadedFixture()
        assertNull(fixture.viewModel.state.value.activeOrder, "Guests see no order banners")
        assertNull(fixture.viewModel.state.value.draftToResume)

        fixture.auth.signInWith(SsoProvider.APPLE)
        runCurrent()
        // Seed #43 (ready, updated 5 minutes ago) is more recent than #42; the draft #44 isn't paid.
        assertEquals("order-43", fixture.viewModel.state.value.activeOrder?.id)
        assertEquals("order-44", fixture.viewModel.state.value.draftToResume?.id)

        fixture.orders.advance("order-42")
        runCurrent()
        assertEquals("order-42", fixture.viewModel.state.value.activeOrder?.id, "The banner follows the latest update")

        fixture.orders.autoComplete("order-42")
        fixture.orders.autoComplete("order-43")
        runCurrent()
        assertNull(fixture.viewModel.state.value.activeOrder, "Completed orders leave the banner")
    }
}
