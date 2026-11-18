package tech.vauldex.dekita.presentation.store

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
import tech.vauldex.dekita.data.mock.FakeCartRepository
import tech.vauldex.dekita.data.mock.FakeMenuRepository
import tech.vauldex.dekita.data.mock.FakeStoreRepository
import tech.vauldex.dekita.data.mock.MockConfig
import tech.vauldex.dekita.data.mock.SeedStoreIds
import tech.vauldex.dekita.data.mock.seedItem
import tech.vauldex.dekita.data.mock.seedMenus
import tech.vauldex.dekita.domain.CartLine
import tech.vauldex.dekita.domain.FulfillmentMode
import tech.vauldex.dekita.domain.Money
import tech.vauldex.dekita.presentation.ITEM_SOLD_OUT_MESSAGE
import tech.vauldex.dekita.presentation.STORE_NOT_TAKING_ORDERS_MESSAGE

@OptIn(ExperimentalCoroutinesApi::class)
class StoreDetailViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(mainDispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private class Fixture(val viewModel: StoreDetailViewModel)

    /** A store screen with its stores and menu loaded. The fakes run in backgroundScope, so time is moved by hand. */
    private fun TestScope.loadedScreen(storeId: String, cart: FakeCartRepository = FakeCartRepository()): Fixture {
        val menus = FakeMenuRepository(backgroundScope, MockConfig())
        val viewModel = StoreDetailViewModel(storeId, FakeStoreRepository(backgroundScope, MockConfig()), menus, cart, menus)
        backgroundScope.launch { viewModel.state.collect {} }
        advanceTimeBy(MockConfig.DEFAULT_LOADING_DELAY)
        runCurrent()
        return Fixture(viewModel)
    }

    @Test
    fun addingACustomizedItemUpdatesTheCartCount() = runTest(mainDispatcher) {
        val viewModel = loadedScreen(SeedStoreIds.RAMEN_BAR).viewModel
        viewModel.openItem("item-shoyu-ramen")
        viewModel.toggleOption("ramen-spice", "ramen-spice-medium")
        viewModel.toggleOption("ramen-toppings", "topping-egg")
        runCurrent()
        assertTrue(viewModel.state.value.sheet!!.canAdd)

        assertEquals(AddResult.ADDED, viewModel.addToCart())
        runCurrent()
        val state = viewModel.state.value
        assertNull(state.sheet, "The sheet closes after adding")
        assertEquals(1, state.cart?.itemCount)
        assertEquals(Money.yen(1_100), state.cart?.total)
    }

    @Test
    fun soldOutItemsDoNotOpen() = runTest(mainDispatcher) {
        val viewModel = loadedScreen(SeedStoreIds.RAMEN_BAR).viewModel
        viewModel.openItem("item-tonkotsu-ramen")
        runCurrent()
        assertNull(viewModel.state.value.sheet)
    }

    @Test
    fun sellingOutWhileTheSheetIsOpenBlocksAdd() = runTest(mainDispatcher) {
        val viewModel = loadedScreen(SeedStoreIds.RAMEN_BAR).viewModel
        viewModel.openItem("item-gyoza")
        runCurrent()
        assertTrue(viewModel.state.value.sheet!!.canAdd)

        viewModel.stopSellingOpenItem()
        runCurrent()
        val sheet = viewModel.state.value.sheet!!
        assertFalse(sheet.canAdd)
        assertEquals(ITEM_SOLD_OUT_MESSAGE, sheet.blockedReason)
        assertEquals(AddResult.NOT_ADDED, viewModel.addToCart())
    }

    @Test
    fun aStoreThatIsNotTakingOrdersBlocksAdd() = runTest(mainDispatcher) {
        val viewModel = loadedScreen(SeedStoreIds.BENTO_KITCHEN).viewModel // seeded Full
        val firstItem = seedItemIdOf(SeedStoreIds.BENTO_KITCHEN)
        viewModel.openItem(firstItem)
        runCurrent()
        assertEquals(STORE_NOT_TAKING_ORDERS_MESSAGE, viewModel.state.value.sheet!!.blockedReason)
        assertEquals(AddResult.NOT_ADDED, viewModel.addToCart())
    }

    @Test
    fun anotherStoresCartAsksBeforeStartingANewOne() = runTest(mainDispatcher) {
        val cart = FakeCartRepository()
        cart.addLine(FulfillmentMode.PICKUP, CartLine("tea", seedItem(SeedStoreIds.MART, "item-green-tea")))
        val viewModel = loadedScreen(SeedStoreIds.RAMEN_BAR, cart).viewModel

        viewModel.openItem("item-gyoza")
        assertEquals(AddResult.NEEDS_REPLACE, viewModel.addToCart())
        runCurrent()
        assertEquals("Test Mart", viewModel.state.value.pendingReplaceStoreName)

        viewModel.confirmReplace()
        runCurrent()
        assertEquals(SeedStoreIds.RAMEN_BAR, cart.cart.value?.storeId)
        assertNull(viewModel.state.value.pendingReplaceStoreName)
        assertEquals(1, viewModel.state.value.cart?.itemCount)
    }

    @Test
    fun anItemForTheOtherModeCannotBeAdded() = runTest(mainDispatcher) {
        val cart = FakeCartRepository().apply { setPreferredMode(FulfillmentMode.PICKUP) }
        val viewModel = loadedScreen(SeedStoreIds.RAMEN_BAR, cart).viewModel
        viewModel.openItem("item-kaedama") // in-store only
        runCurrent()
        assertEquals("Only for Eat-in orders",viewModel.state.value.sheet!!.blockedReason)
    }

    @Test
    fun theModeDefaultsToTheDiscoverPickThenEatIn() = runTest(mainDispatcher) {
        assertEquals(FulfillmentMode.IN_STORE, loadedScreen(SeedStoreIds.RAMEN_BAR).viewModel.state.value.mode)

        val cart = FakeCartRepository().apply { setPreferredMode(FulfillmentMode.PICKUP) }
        assertEquals(FulfillmentMode.PICKUP, loadedScreen(SeedStoreIds.RAMEN_BAR, cart).viewModel.state.value.mode)
    }

    @Test
    fun pickingAModeFiltersTheMenu() = runTest(mainDispatcher) {
        val viewModel = loadedScreen(SeedStoreIds.RAMEN_BAR).viewModel
        assertTrue(viewModel.state.value.canChooseMode)
        viewModel.selectTab("sides")
        runCurrent()
        assertEquals(listOf("Gyoza (6 pcs)", "Edamame"), viewModel.state.value.visibleItems.map { it.name })

        viewModel.selectMode(FulfillmentMode.PICKUP)
        runCurrent()
        assertEquals(FulfillmentMode.PICKUP, viewModel.state.value.mode)
        assertEquals(
            listOf("Gyoza (6 pcs)", "Edamame", "Take-home Ramen Kit"),
            viewModel.state.value.visibleItems.map { it.name },
        )

        viewModel.selectTab("ramen")
        runCurrent()
        assertEquals(listOf("Shoyu Ramen", "Tonkotsu Ramen"), viewModel.state.value.visibleItems.map { it.name })
    }

    @Test
    fun pickingAModeIsTheModeOfTheNextAdd() = runTest(mainDispatcher) {
        val cart = FakeCartRepository()
        val viewModel = loadedScreen(SeedStoreIds.RAMEN_BAR, cart).viewModel

        viewModel.selectMode(FulfillmentMode.PICKUP)
        viewModel.openItem("item-gyoza")
        assertEquals(AddResult.ADDED, viewModel.addToCart())
        assertEquals(FulfillmentMode.PICKUP, cart.cart.value?.mode)
    }

    @Test
    fun pickingAModeMovesThisStoresCart() = runTest(mainDispatcher) {
        val cart = FakeCartRepository()
        cart.addLine(FulfillmentMode.IN_STORE, CartLine("gyoza", seedItem(SeedStoreIds.RAMEN_BAR, "item-gyoza")))
        val viewModel = loadedScreen(SeedStoreIds.RAMEN_BAR, cart).viewModel
        assertEquals(FulfillmentMode.IN_STORE, viewModel.state.value.mode)

        viewModel.selectMode(FulfillmentMode.PICKUP)
        runCurrent()
        assertEquals(FulfillmentMode.PICKUP, cart.cart.value?.mode)
        assertEquals(FulfillmentMode.PICKUP, viewModel.state.value.mode)
    }

    @Test
    fun pickingAModeLeavesAnotherStoresCartAlone() = runTest(mainDispatcher) {
        val cart = FakeCartRepository()
        cart.addLine(FulfillmentMode.PICKUP, CartLine("tea", seedItem(SeedStoreIds.MART, "item-green-tea")))
        val viewModel = loadedScreen(SeedStoreIds.RAMEN_BAR, cart).viewModel

        viewModel.selectMode(FulfillmentMode.IN_STORE)
        runCurrent()
        assertEquals(SeedStoreIds.MART, cart.cart.value?.storeId)
        assertEquals(FulfillmentMode.PICKUP, cart.cart.value?.mode)
    }

    @Test
    fun pickingAModeDoesNotChangeTheDiscoverFilter() = runTest(mainDispatcher) {
        val cart = FakeCartRepository()
        val viewModel = loadedScreen(SeedStoreIds.RAMEN_BAR, cart).viewModel

        viewModel.selectMode(FulfillmentMode.PICKUP)
        runCurrent()
        assertNull(cart.preferredMode.value)
    }

    @Test
    fun singleModeStoresHaveNoSelectorAndIgnoreAPick() = runTest(mainDispatcher) {
        val viewModel = loadedScreen(SeedStoreIds.MART).viewModel // pickup only
        assertFalse(viewModel.state.value.canChooseMode)

        viewModel.selectMode(FulfillmentMode.IN_STORE)
        runCurrent()
        assertEquals(FulfillmentMode.PICKUP, viewModel.state.value.mode)
    }

    private fun seedItemIdOf(storeId: String): String =
        seedMenus.getValue(storeId).items.first { !it.isSoldOut && it.modifierGroups.isEmpty() }.id
}
