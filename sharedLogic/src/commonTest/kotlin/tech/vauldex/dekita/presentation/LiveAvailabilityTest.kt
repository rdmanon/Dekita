package tech.vauldex.dekita.presentation

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
import tech.vauldex.dekita.data.dataOrNull
import tech.vauldex.dekita.data.mock.FakeAuthRepository
import tech.vauldex.dekita.data.mock.FakeCartRepository
import tech.vauldex.dekita.data.mock.FakeMenuRepository
import tech.vauldex.dekita.data.mock.FakeOrderRepository
import tech.vauldex.dekita.data.mock.FakeStoreRepository
import tech.vauldex.dekita.data.mock.MockConfig
import tech.vauldex.dekita.data.mock.SeedStoreIds
import tech.vauldex.dekita.data.mock.seedSampleCart
import tech.vauldex.dekita.domain.BusinessType
import tech.vauldex.dekita.domain.CheckoutAction
import tech.vauldex.dekita.domain.FulfillmentMode
import tech.vauldex.dekita.domain.StoreAvailability
import tech.vauldex.dekita.domain.StoreBadge
import tech.vauldex.dekita.presentation.cart.CartReviewViewModel
import tech.vauldex.dekita.presentation.cart.CheckoutBlock
import tech.vauldex.dekita.presentation.discover.DiscoverViewModel
import tech.vauldex.dekita.presentation.store.AddResult
import tech.vauldex.dekita.presentation.store.StoreDetailViewModel

/**
 * AVAIL-01: debug-panel events reach every open screen at once. All four screens share one set of fakes
 * and stay subscribed, as they do when the debug panel opens over them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveAvailabilityTest {

    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(mainDispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private class Screens(scope: CoroutineScope) {
        // Queue ticks off, so only the debug panel changes the data.
        val config = MockConfig().apply { liveQueue.value = false }
        val stores = FakeStoreRepository(scope, config)
        val menus = FakeMenuRepository(scope, config)
        val cart = FakeCartRepository()
        val orders = FakeOrderRepository(scope, config, object : Clock {
            override fun now() = Instant.fromEpochSeconds(1_800_000_000)
        })
        val auth = FakeAuthRepository(config)
        val discover = DiscoverViewModel(stores, orders, cart, auth)
        val store = StoreDetailViewModel(SeedStoreIds.RAMEN_BAR, stores, menus, cart, menus)
        val review = CartReviewViewModel(cart, stores, menus, orders, auth)

        fun badge(): StoreBadge? = discover.state.value.stores.firstOrNull { it.store.id == SeedStoreIds.RAMEN_BAR }?.store?.badge

        /** The design's cart: Shoyu Ramen (Medium · Extra Egg) and Gyoza, Eat-In. */
        fun fillSampleCart() {
            val sample = seedSampleCart()
            sample.lines.forEach { cart.addLine(sample.mode, it) }
        }
    }

    private fun TestScope.openScreens(): Screens {
        val screens = Screens(backgroundScope)
        backgroundScope.launch { screens.discover.state.collect {} }
        backgroundScope.launch { screens.store.state.collect {} }
        backgroundScope.launch { screens.review.state.collect {} }
        runCurrent()
        advanceTimeBy(MockConfig.DEFAULT_LOADING_DELAY)
        runCurrent()
        return screens
    }

    @Test
    fun stopAcceptingOrdersPausesEveryScreenAndResumeRestoresThem() = runTest(mainDispatcher) {
        val s = openScreens()
        s.fillSampleCart()
        s.store.openItem("item-gyoza")
        runCurrent()
        assertEquals(StoreBadge.OPEN_FOR_LUNCH, s.badge())
        assertTrue(s.store.state.value.canOrder)
        assertTrue(s.store.state.value.sheet!!.canAdd)

        // Scenario 1: the store stops accepting orders while the menu is open.
        s.stores.setAvailability(SeedStoreIds.RAMEN_BAR, StoreAvailability.PAUSED)
        runCurrent()
        assertEquals(StoreBadge.PAUSED, s.badge())
        val paused = s.store.state.value
        assertFalse(paused.canOrder, "Add is off for every menu row")
        assertEquals(STORE_NOT_TAKING_ORDERS_MESSAGE, paused.sheet!!.blockedReason)
        assertEquals(AddResult.NOT_ADDED, s.store.addToCart())
        assertEquals(CheckoutBlock.STORE_PAUSED, s.review.state.value.block)
        assertFalse(s.review.state.value.canCheckout)

        // Scenario 3: accepting orders again restores normal behavior, with nothing left over.
        s.stores.setAvailability(SeedStoreIds.RAMEN_BAR, StoreAvailability.OPEN)
        runCurrent()
        assertEquals(StoreBadge.OPEN_FOR_LUNCH, s.badge())
        assertTrue(s.store.state.value.canOrder)
        assertNull(s.store.state.value.sheet!!.blockedReason)
        assertNotNull(s.store.state.value.store?.queue, "The live queue comes back")
        assertNull(s.review.state.value.block)
        assertTrue(s.review.state.value.canCheckout)
    }

    @Test
    fun stopSellingAnItemFlagsItEverywhereAndResumeClearsIt() = runTest(mainDispatcher) {
        val s = openScreens()
        s.fillSampleCart()
        s.store.openItem("item-shoyu-ramen")
        runCurrent()

        // Scenario 2: Shoyu Ramen stops selling while it is in the cart.
        s.menus.setItemSoldOut(SeedStoreIds.RAMEN_BAR, "item-shoyu-ramen", soldOut = true)
        runCurrent()
        val menuItem = s.store.state.value.menu.dataOrNull()!!.items.first { it.id == "item-shoyu-ramen" }
        assertTrue(menuItem.isSoldOut, "The menu row shows Sold out")
        assertEquals(ITEM_SOLD_OUT_MESSAGE, s.store.state.value.sheet!!.blockedReason)
        val review = s.review.state.value
        assertEquals("Sold out", review.lines.first { it.line.item.id == "item-shoyu-ramen" }.unavailableReason)
        assertEquals(CheckoutBlock.UNAVAILABLE_LINES, review.block)
        assertFalse(review.canCheckout)

        s.menus.setItemSoldOut(SeedStoreIds.RAMEN_BAR, "item-shoyu-ramen", soldOut = false)
        runCurrent()
        assertTrue(s.review.state.value.lines.all { it.isAvailable })
        assertTrue(s.review.state.value.canCheckout)
        // Resuming leaves the open sheet usable again (the required spice is still unpicked, so pick it).
        s.store.toggleOption("ramen-spice", "ramen-spice-medium")
        runCurrent()
        assertTrue(s.store.state.value.sheet!!.canAdd)
    }

    @Test
    fun stopSellingAnOptionDisablesItInTheSheetAndFlagsTheCartLine() = runTest(mainDispatcher) {
        val s = openScreens()
        s.fillSampleCart()
        s.store.openItem("item-shoyu-ramen")
        s.store.toggleOption("ramen-spice", "ramen-spice-medium")
        s.store.toggleOption("ramen-toppings", "topping-egg")
        runCurrent()
        assertTrue(s.store.state.value.sheet!!.canAdd)

        s.menus.setOptionSoldOut(SeedStoreIds.RAMEN_BAR, "topping-egg", soldOut = true)
        runCurrent()
        val sheet = s.store.state.value.sheet!!
        val egg = sheet.draft.item.modifierGroups.flatMap { it.options }.first { it.id == "topping-egg" }
        assertTrue(egg.isSoldOut, "The open sheet shows the option as sold out")
        assertEquals(OPTION_SOLD_OUT_MESSAGE, sheet.blockedReason)
        assertFalse(sheet.canAdd)
        val review = s.review.state.value
        assertEquals("Extra Egg sold out", review.lines.first { it.line.item.id == "item-shoyu-ramen" }.unavailableReason)
        assertFalse(review.canCheckout)

        s.menus.setOptionSoldOut(SeedStoreIds.RAMEN_BAR, "topping-egg", soldOut = false)
        runCurrent()
        assertTrue(s.store.state.value.sheet!!.canAdd)
        assertTrue(s.review.state.value.canCheckout)
    }

    @Test
    fun switchingToPickupOnlyRelocksCartReviewAndLeavesTheInStoreFilter() = runTest(mainDispatcher) {
        val s = openScreens()
        s.fillSampleCart()
        s.discover.selectMode(FulfillmentMode.IN_STORE)
        runCurrent()
        assertNotNull(s.badge(), "Test Ramen Bar offers Eat-In")
        assertTrue(s.review.state.value.canChooseMode)
        assertEquals(CheckoutAction.HEAD_TO_STORE, s.review.state.value.action)

        s.stores.setBusinessType(SeedStoreIds.RAMEN_BAR, BusinessType.PICKUP_ONLY)
        runCurrent()
        assertNull(s.badge(), "The store drops out of the In-store filter")
        val pickupOnly = s.review.state.value
        assertFalse(pickupOnly.canChooseMode, "Eat-In is hidden")
        assertEquals(FulfillmentMode.PICKUP, pickupOnly.mode)
        assertEquals(CheckoutAction.PAY_NOW, pickupOnly.action)
        s.review.selectMode(FulfillmentMode.IN_STORE)
        runCurrent()
        assertEquals(FulfillmentMode.PICKUP, s.review.state.value.mode, "The locked mode can't be changed")

        // Adding from the menu now uses Pickup, never the old Eat-In mode.
        s.store.openItem("item-gyoza")
        assertEquals(AddResult.ADDED, s.store.addToCart())
        runCurrent()
        assertEquals(FulfillmentMode.PICKUP, s.cart.cart.value?.mode)

        s.stores.setBusinessType(SeedStoreIds.RAMEN_BAR, BusinessType.BOTH)
        runCurrent()
        assertNotNull(s.badge())
        assertTrue(s.review.state.value.canChooseMode, "The toggle comes back")
    }

    @Test
    fun queueChangesReachTheOpenScreens() = runTest(mainDispatcher) {
        val s = openScreens()
        s.fillSampleCart()
        val before = s.store.state.value.store!!.queue!!.ordersAhead

        s.stores.adjustQueue(SeedStoreIds.RAMEN_BAR, 5)
        runCurrent()
        assertEquals(before + 5, s.store.state.value.store!!.queue!!.ordersAhead)
        assertEquals(before + 5, s.review.state.value.store!!.queue!!.ordersAhead)
        assertEquals(
            before + 5,
            s.discover.state.value.stores.first { it.store.id == SeedStoreIds.RAMEN_BAR }.store.queue!!.ordersAhead,
        )
    }
}
