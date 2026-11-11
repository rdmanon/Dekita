package tech.vauldex.dekita.presentation.cart

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
import tech.vauldex.dekita.data.mock.FakeCartRepository
import tech.vauldex.dekita.data.mock.FakeMenuRepository
import tech.vauldex.dekita.data.mock.FakeOrderRepository
import tech.vauldex.dekita.data.mock.FakeStoreRepository
import tech.vauldex.dekita.data.mock.MockConfig
import tech.vauldex.dekita.data.mock.SeedStoreIds
import tech.vauldex.dekita.data.mock.sampleCartLines
import tech.vauldex.dekita.data.mock.seedItem
import tech.vauldex.dekita.data.mock.seedOption
import tech.vauldex.dekita.domain.CartLine
import tech.vauldex.dekita.domain.CheckoutAction
import tech.vauldex.dekita.domain.FulfillmentMode
import tech.vauldex.dekita.domain.Money
import tech.vauldex.dekita.domain.OrderStatus
import tech.vauldex.dekita.domain.StoreAvailability
import tech.vauldex.dekita.presentation.menu.ItemSheetViewModel
import tech.vauldex.dekita.presentation.menu.SaveResult

/** CART-01's Gherkin scenarios, run against the in-memory fakes. */
@OptIn(ExperimentalCoroutinesApi::class)
class CartReviewViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Fixture(scope: CoroutineScope) {
        val stores = FakeStoreRepository(scope, MockConfig())
        val menus = FakeMenuRepository(scope, MockConfig())
        val cart = FakeCartRepository()
        val orders = FakeOrderRepository(scope, MockConfig(), object : Clock {
            override fun now() = Instant.fromEpochSeconds(1_800_000_000)
        })
        val review = CartReviewViewModel(cart, stores, menus, orders)

        fun sheet(storeId: String, itemId: String, editingLineId: String? = null) =
            ItemSheetViewModel(storeId, itemId, editingLineId, menus, stores, cart)

        /** The design's cart from Test Ramen Bar: Shoyu Ramen (Mild) and Gyoza. */
        fun fillRamenCart(mode: FulfillmentMode = FulfillmentMode.IN_STORE) {
            sampleCartLines(cart::newLineId).forEach { cart.addLine(mode, it) }
        }
    }

    /** The fakes run in backgroundScope, which advanceUntilIdle() does not wait for. */
    private fun TestScope.finishLoading() {
        runCurrent()
        advanceTimeBy(MockConfig.DEFAULT_LOADING_DELAY)
        runCurrent()
    }

    private fun TestScope.fixture(): Fixture {
        val fixture = Fixture(backgroundScope)
        backgroundScope.launch { fixture.review.state.collect {} }
        finishLoading()
        return fixture
    }

    private fun TestScope.watch(sheet: ItemSheetViewModel): ItemSheetViewModel {
        backgroundScope.launch { sheet.state.collect {} }
        finishLoading()
        return sheet
    }

    @Test
    fun scenario1EditingALineUpdatesItInPlace() = runTest(mainDispatcher) {
        val f = fixture()
        f.fillRamenCart()
        finishLoading()
        val shoyuLine = f.cart.cart.value!!.lines.first { it.item.name == "Shoyu Ramen" }

        val sheet = watch(f.sheet(SeedStoreIds.RAMEN_BAR, shoyuLine.item.id, editingLineId = shoyuLine.lineId))
        assertEquals(setOf("ramen-spice-mild"), sheet.state.value.selection?.selected?.get("ramen-spice"), "Prefilled")
        sheet.toggle("ramen-spice", "ramen-spice-hot")
        assertEquals(SaveResult.SAVED, sheet.save())
        runCurrent()

        val lines = f.review.state.value.lines.map { it.line }
        assertEquals(2, lines.size, "No duplicate line")
        val edited = lines.first { it.lineId == shoyuLine.lineId }
        assertEquals(listOf("Hot"), edited.selectedOptions.map { it.name })
    }

    @Test
    fun scenario2InStoreOnlyIsFixedAndPaysNow() = runTest(mainDispatcher) {
        val f = fixture()
        val massage = seedItem(SeedStoreIds.MASSAGE_STUDIO, "item-shoulder-30")
        f.cart.addLine(FulfillmentMode.IN_STORE, CartLine("l1", massage, listOf(massage.seedOption("pressure-medium"))))
        finishLoading()

        val state = f.review.state.value
        assertEquals(FulfillmentMode.IN_STORE, state.mode)
        assertFalse(state.canChooseMode, "No toggle")
        assertEquals(CheckoutAction.PAY_NOW, state.action)
        assertTrue(f.review.canStartPayment())
    }

    @Test
    fun pickupOnlyIsFixedAndPaysNow() = runTest(mainDispatcher) {
        val f = fixture()
        f.cart.addLine(FulfillmentMode.PICKUP, CartLine("l1", seedItem(SeedStoreIds.MART, "item-onigiri-salmon")))
        finishLoading()

        val state = f.review.state.value
        assertEquals(FulfillmentMode.PICKUP, state.mode)
        assertFalse(state.canChooseMode)
        assertEquals(CheckoutAction.PAY_NOW, state.action)
    }

    @Test
    fun scenario3ModeDefaultsFromDiscover() = runTest(mainDispatcher) {
        val f = fixture()
        f.cart.setPreferredMode(FulfillmentMode.PICKUP)

        val sheet = watch(f.sheet(SeedStoreIds.RAMEN_BAR, "item-gyoza"))
        assertEquals(SaveResult.SAVED, sheet.save())
        finishLoading()

        val state = f.review.state.value
        assertTrue(state.canChooseMode)
        assertEquals(FulfillmentMode.PICKUP, state.mode, "Takeout is preselected")
        assertEquals(CheckoutAction.PAY_NOW, state.action)
    }

    @Test
    fun noDiscoverChoiceDefaultsToEatIn() = runTest(mainDispatcher) {
        val f = fixture()
        val sheet = watch(f.sheet(SeedStoreIds.RAMEN_BAR, "item-gyoza"))
        sheet.save()
        finishLoading()

        assertEquals(FulfillmentMode.IN_STORE, f.review.state.value.mode)
        assertEquals(CheckoutAction.HEAD_TO_STORE, f.review.state.value.action)
    }

    @Test
    fun scenario4EatInSavesADraftAndEmptiesTheCart() = runTest(mainDispatcher) {
        val f = fixture()
        f.fillRamenCart(FulfillmentMode.PICKUP)
        finishLoading()
        f.review.selectMode(FulfillmentMode.IN_STORE)
        runCurrent()
        assertEquals(CheckoutAction.HEAD_TO_STORE, f.review.state.value.action)

        val draft = assertNotNull(f.review.headToStore())
        runCurrent()

        assertEquals(OrderStatus.DRAFT, draft.status)
        assertEquals(FulfillmentMode.IN_STORE, draft.mode)
        assertEquals(Money.yen(980 + 450), draft.total)
        assertTrue(f.orders.orders.value.dataOrNull().orEmpty().any { it.id == draft.id }, "Saved with the orders")
        assertNull(f.cart.cart.value, "The cart became the draft")
        assertTrue(f.review.state.value.isEmpty)
    }

    @Test
    fun headToStoreDoesNothingForTakeout() = runTest(mainDispatcher) {
        val f = fixture()
        f.fillRamenCart(FulfillmentMode.PICKUP)
        finishLoading()
        assertNull(f.review.headToStore())
        assertNotNull(f.cart.cart.value)
    }

    @Test
    fun scenario5SoldOutLineBlocksCheckoutUntilRemoved() = runTest(mainDispatcher) {
        val f = fixture()
        f.fillRamenCart()
        finishLoading()
        assertTrue(f.review.state.value.canCheckout)

        f.menus.setItemSoldOut(SeedStoreIds.RAMEN_BAR, "item-shoyu-ramen", soldOut = true)
        runCurrent()
        val blocked = f.review.state.value
        assertTrue(blocked.lines.first { it.line.item.id == "item-shoyu-ramen" }.isSoldOut)
        assertEquals(CheckoutBlock.SOLD_OUT_LINES, blocked.block)
        assertFalse(blocked.canCheckout)
        assertNull(f.review.headToStore(), "Checkout is disabled")

        f.review.remove(blocked.lines.first { it.isSoldOut }.line.lineId)
        runCurrent()
        assertNull(f.review.state.value.block)
        assertTrue(f.review.state.value.canCheckout)
    }

    @Test
    fun aPausedStoreBlocksCheckoutAndKeepsTheCart() = runTest(mainDispatcher) {
        val f = fixture()
        f.fillRamenCart()
        finishLoading()

        f.stores.setAvailability(SeedStoreIds.RAMEN_BAR, StoreAvailability.PAUSED)
        runCurrent()
        assertEquals(CheckoutBlock.STORE_PAUSED, f.review.state.value.block)
        assertFalse(f.review.state.value.canCheckout)
        assertEquals(2, f.review.state.value.lines.size, "The cart is kept")
    }

    @Test
    fun theQueueUpdatesLive() = runTest(mainDispatcher) {
        val f = fixture()
        f.fillRamenCart()
        finishLoading()
        assertEquals(3, f.review.state.value.store?.queue?.ordersAhead)

        f.stores.adjustQueue(SeedStoreIds.RAMEN_BAR, 2)
        runCurrent()
        assertEquals(5, f.review.state.value.store?.queue?.ordersAhead)
    }

    @Test
    fun removingTheLastLineShowsTheEmptyState() = runTest(mainDispatcher) {
        val f = fixture()
        f.cart.addLine(FulfillmentMode.PICKUP, CartLine("l1", seedItem(SeedStoreIds.MART, "item-green-tea")))
        finishLoading()

        f.review.decrease("l1")
        runCurrent()
        assertTrue(f.review.state.value.isEmpty)
        assertNull(f.cart.cart.value)
    }

    @Test
    fun quantityStepperChangesTheTotal() = runTest(mainDispatcher) {
        val f = fixture()
        f.cart.addLine(FulfillmentMode.PICKUP, CartLine("l1", seedItem(SeedStoreIds.MART, "item-green-tea")))
        finishLoading()

        f.review.increase("l1")
        runCurrent()
        assertEquals(Money.yen(300), f.review.state.value.total)
    }

    @Test
    fun singleModeStoresIgnoreAModeChange() = runTest(mainDispatcher) {
        val f = fixture()
        f.cart.addLine(FulfillmentMode.PICKUP, CartLine("l1", seedItem(SeedStoreIds.MART, "item-green-tea")))
        finishLoading()

        f.review.selectMode(FulfillmentMode.IN_STORE)
        runCurrent()
        assertEquals(FulfillmentMode.PICKUP, f.review.state.value.mode)
    }

    @Test
    fun addingFromAnotherStoreAsksFirst() = runTest(mainDispatcher) {
        val f = fixture()
        f.fillRamenCart()
        val sheet = watch(f.sheet(SeedStoreIds.MART, "item-green-tea"))

        assertEquals("Test Ramen Bar", sheet.state.value.otherCartStoreName)
        assertEquals(SaveResult.CART_FROM_OTHER_STORE, sheet.save())
        assertEquals(SeedStoreIds.RAMEN_BAR, f.cart.cart.value?.storeId, "Nothing changed yet")

        assertEquals(SaveResult.SAVED, sheet.startNewCartAndSave())
        assertEquals(SeedStoreIds.MART, f.cart.cart.value?.storeId)
        assertEquals(1, f.cart.cart.value?.lines?.size)
    }
}
