package tech.vauldex.dekita.presentation.cart

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
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
import tech.vauldex.dekita.data.mock.seedItem
import tech.vauldex.dekita.data.mock.seedOption
import tech.vauldex.dekita.data.mock.seedSampleCart
import tech.vauldex.dekita.domain.CartLine
import tech.vauldex.dekita.domain.CheckoutAction
import tech.vauldex.dekita.domain.FulfillmentMode
import tech.vauldex.dekita.domain.ItemDraft
import tech.vauldex.dekita.domain.Money
import tech.vauldex.dekita.domain.OrderStatus
import tech.vauldex.dekita.domain.SsoProvider
import tech.vauldex.dekita.domain.StoreAvailability
import tech.vauldex.dekita.presentation.collectEvents
import tech.vauldex.dekita.presentation.store.AddResult
import tech.vauldex.dekita.presentation.store.StoreDetailViewModel

/** Cart Review: the checkout sign-in (AUTH-01) and CART-01's editing, mode routing and blocked states. */
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
        val config = MockConfig().apply { liveQueue.value = false }
        val stores = FakeStoreRepository(scope, config)
        val menus = FakeMenuRepository(scope, config)
        val cart = FakeCartRepository()
        val auth = FakeAuthRepository(config)
        val orders = FakeOrderRepository(scope, config, object : Clock {
            override fun now() = Instant.fromEpochSeconds(1_800_000_000)
        })
        val review = CartReviewViewModel(cart, stores, menus, orders, auth)

        fun line(storeId: String, itemId: String, vararg optionIds: String): CartLine {
            val item = seedItem(storeId, itemId)
            return CartLine("$itemId|${optionIds.sorted().joinToString("|")}", item, optionIds.map { item.seedOption(it) })
        }

        /** Shoyu Ramen (Mild) and Gyoza from Test Ramen Bar, which offers both modes. */
        fun fillRamenCart(mode: FulfillmentMode = FulfillmentMode.IN_STORE) {
            cart.addLine(mode, line(SeedStoreIds.RAMEN_BAR, "item-shoyu-ramen", "ramen-spice-mild"))
            cart.addLine(mode, line(SeedStoreIds.RAMEN_BAR, "item-gyoza"))
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

    // Checkout sign-in (AUTH-01)

    @Test
    fun guestIsSentToSignInAndTheCartSurvivesIt() = runTest(mainDispatcher) {
        val f = fixture()
        val sample = seedSampleCart()
        sample.lines.forEach { f.cart.addLine(sample.mode, it) }
        finishLoading()
        val events = collectEvents(f.review.events)
        assertEquals("Test Ramen Bar", f.review.state.value.store?.name)
        // The sample cart is Eat-In; Takeout makes the button "Pay now".
        f.review.selectMode(FulfillmentMode.PICKUP)
        runCurrent()

        f.review.checkout()
        runCurrent()
        assertEquals(listOf<CartReviewEvent>(CartReviewEvent.RequireSignIn), events)

        f.auth.signInWith(SsoProvider.APPLE)
        runCurrent()
        assertTrue(f.review.state.value.isSignedIn)
        assertEquals(sample.lines, f.review.state.value.cart?.lines)

        f.review.checkout()
        runCurrent()
        // ORDER-01: Pay now places a paid order with a mock payment, empties the cart and opens the ticket.
        val placed = events.last() as CartReviewEvent.OrderPlaced
        assertEquals(CartReviewEvent.RequireSignIn, events.first())
        assertEquals(2, events.size)
        val order = f.orders.orders.value.dataOrNull()?.first { it.id == placed.orderId }
        assertEquals(OrderStatus.PAID, order?.status)
        assertEquals(FulfillmentMode.PICKUP, order?.mode)
        assertEquals(sample.lines, order?.lines)
        assertTrue(f.review.state.value.isEmpty)

        // A second tap finds an empty cart and places nothing.
        f.review.checkout()
        runCurrent()
        assertEquals(2, events.size)
    }

    @Test
    fun emptyCartHasNothingToPay() = runTest(mainDispatcher) {
        val f = fixture()
        val events = collectEvents(f.review.events)

        assertNull(f.review.state.value.cart)
        assertTrue(f.review.state.value.isEmpty)
        assertFalse(f.review.state.value.isSignedIn)
        f.review.checkout()
        runCurrent()
        assertTrue(events.isEmpty())
    }

    // CART-01 scenarios

    @Test
    fun scenario1EditingALineUpdatesItInPlace() = runTest(mainDispatcher) {
        val f = fixture()
        f.fillRamenCart()
        finishLoading()
        val shoyu = f.review.state.value.lines.first { it.line.item.name == "Shoyu Ramen" }.line

        f.review.editLine(shoyu.lineId)
        runCurrent()
        val sheet = assertNotNull(f.review.state.value.editSheet)
        assertEquals(listOf("Mild"), sheet.draft.selected["ramen-spice"]?.map { it.name }, "Prefilled")

        f.review.toggleEditOption("ramen-spice", "ramen-spice-hot")
        assertTrue(f.review.saveEdit())
        runCurrent()

        val lines = f.review.state.value.lines.map { it.line }
        assertEquals(2, lines.size, "No duplicate line")
        assertEquals("Shoyu Ramen", lines.first().item.name, "Same position")
        assertEquals(listOf("Hot"), lines.first().selectedOptions.map { it.name })
        assertNull(f.review.state.value.editSheet)
    }

    @Test
    fun scenario2InStoreOnlyIsFixedAndPaysNow() = runTest(mainDispatcher) {
        val f = fixture()
        f.cart.addLine(FulfillmentMode.IN_STORE, f.line(SeedStoreIds.MASSAGE_STUDIO, "item-shoulder-30", "pressure-medium"))
        finishLoading()

        val state = f.review.state.value
        assertEquals(FulfillmentMode.IN_STORE, state.mode)
        assertFalse(state.canChooseMode, "No toggle")
        assertEquals(CheckoutAction.PAY_NOW, state.action)
    }

    @Test
    fun pickupOnlyIsFixedAndPaysNow() = runTest(mainDispatcher) {
        val f = fixture()
        f.cart.addLine(FulfillmentMode.PICKUP, f.line(SeedStoreIds.MART, "item-onigiri-salmon"))
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
        val store = StoreDetailViewModel(SeedStoreIds.RAMEN_BAR, f.stores, f.menus, f.cart, f.menus)
        backgroundScope.launch { store.state.collect {} }
        finishLoading()

        store.openItem("item-gyoza")
        assertEquals(AddResult.ADDED, store.addToCart())
        finishLoading()

        val state = f.review.state.value
        assertTrue(state.canChooseMode)
        assertEquals(FulfillmentMode.PICKUP, state.mode, "Takeout is preselected")
        assertEquals(CheckoutAction.PAY_NOW, state.action)
    }

    @Test
    fun scenario4EatInSavesADraftAndOpensCheckIn() = runTest(mainDispatcher) {
        val f = fixture()
        f.fillRamenCart(FulfillmentMode.PICKUP)
        f.auth.signInWith(SsoProvider.GOOGLE)
        finishLoading()
        val events = collectEvents(f.review.events)

        f.review.selectMode(FulfillmentMode.IN_STORE)
        runCurrent()
        assertEquals(CheckoutAction.HEAD_TO_STORE, f.review.state.value.action)
        f.review.checkout()
        runCurrent()

        val saved = assertIs<CartReviewEvent.DraftSaved>(events.single())
        val draft = f.orders.orders.value.dataOrNull().orEmpty().first { it.number == saved.orderNumber }
        assertEquals(draft.id, saved.orderId, "Check-in opens for the saved draft")
        assertEquals(OrderStatus.DRAFT, draft.status)
        assertEquals(FulfillmentMode.IN_STORE, draft.mode)
        assertEquals(Money.yen(980 + 450), draft.total)
        assertEquals("Test Ramen Bar", saved.storeName)
        assertNull(f.cart.cart.value, "The cart became the draft")
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
        val shoyu = blocked.lines.first { it.line.item.id == "item-shoyu-ramen" }
        assertEquals("Sold out", shoyu.unavailableReason)
        assertEquals(CheckoutBlock.UNAVAILABLE_LINES, blocked.block)
        assertFalse(blocked.canCheckout)

        f.review.remove(shoyu.line.lineId)
        runCurrent()
        assertNull(f.review.state.value.block)
        assertTrue(f.review.state.value.canCheckout)
    }

    @Test
    fun anEatInOnlyItemBlocksTakeout() = runTest(mainDispatcher) {
        val f = fixture()
        f.fillRamenCart()
        f.cart.addLine(FulfillmentMode.IN_STORE, f.line(SeedStoreIds.RAMEN_BAR, "item-kaedama"))
        finishLoading()
        assertNull(f.review.state.value.block)

        f.review.selectMode(FulfillmentMode.PICKUP)
        runCurrent()
        assertEquals("Eat-In only", f.review.state.value.lines.first { it.line.item.id == "item-kaedama" }.unavailableReason)
        assertEquals(CheckoutBlock.UNAVAILABLE_LINES, f.review.state.value.block)
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
        val tea = f.line(SeedStoreIds.MART, "item-green-tea")
        f.cart.addLine(FulfillmentMode.PICKUP, tea)
        finishLoading()

        f.review.decrease(tea.lineId)
        runCurrent()
        assertTrue(f.review.state.value.isEmpty)
        assertNull(f.cart.cart.value)
    }

    @Test
    fun quantityStepperChangesTheTotal() = runTest(mainDispatcher) {
        val f = fixture()
        val tea = f.line(SeedStoreIds.MART, "item-green-tea")
        f.cart.addLine(FulfillmentMode.PICKUP, tea)
        finishLoading()

        f.review.increase(tea.lineId)
        runCurrent()
        assertEquals(Money.yen(300), f.review.state.value.cart?.total)
    }

    @Test
    fun singleModeStoresIgnoreAModeChange() = runTest(mainDispatcher) {
        val f = fixture()
        f.cart.addLine(FulfillmentMode.PICKUP, f.line(SeedStoreIds.MART, "item-green-tea"))
        finishLoading()

        f.review.selectMode(FulfillmentMode.IN_STORE)
        runCurrent()
        assertEquals(FulfillmentMode.PICKUP, f.review.state.value.mode)
    }

    @Test
    fun editingCantSaveASoldOutItem() = runTest(mainDispatcher) {
        val f = fixture()
        f.fillRamenCart()
        finishLoading()
        val gyoza = f.review.state.value.lines.first { it.line.item.id == "item-gyoza" }.line

        f.review.editLine(gyoza.lineId)
        f.menus.setItemSoldOut(SeedStoreIds.RAMEN_BAR, "item-gyoza", soldOut = true)
        runCurrent()

        assertFalse(assertNotNull(f.review.state.value.editSheet).canAdd)
        assertFalse(f.review.saveEdit())
    }

    @Test
    fun theDraftFromALineMatchesItsPrice() {
        val shoyu = seedItem(SeedStoreIds.RAMEN_BAR, "item-shoyu-ramen")
        val line = CartLine("l", shoyu, listOf(shoyu.seedOption("ramen-spice-medium"), shoyu.seedOption("topping-egg")), quantity = 2)
        assertEquals(line.lineTotal, ItemDraft.fromLine(line).totalPrice)
    }
}
