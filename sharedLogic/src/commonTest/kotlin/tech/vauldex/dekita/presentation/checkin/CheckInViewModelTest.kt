package tech.vauldex.dekita.presentation.checkin

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
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
import tech.vauldex.dekita.data.mock.FakePaymentRepository
import tech.vauldex.dekita.data.mock.FakeStoreRepository
import tech.vauldex.dekita.data.mock.MockConfig
import tech.vauldex.dekita.data.mock.SeedStoreIds
import tech.vauldex.dekita.data.mock.seedItem
import tech.vauldex.dekita.data.mock.seedSampleCart
import tech.vauldex.dekita.domain.Cart
import tech.vauldex.dekita.domain.CartLine
import tech.vauldex.dekita.domain.CheckoutAction
import tech.vauldex.dekita.domain.FulfillmentMode
import tech.vauldex.dekita.domain.Order
import tech.vauldex.dekita.domain.OrderStatus
import tech.vauldex.dekita.domain.PaymentType
import tech.vauldex.dekita.domain.SsoProvider
import tech.vauldex.dekita.domain.StoreAvailability
import tech.vauldex.dekita.presentation.cart.CartReviewViewModel
import tech.vauldex.dekita.presentation.collectEvents
import tech.vauldex.dekita.presentation.discover.DiscoverViewModel
import tech.vauldex.dekita.presentation.orders.OrdersViewModel

/** SEAT-01: the seat question before cooking, the store-full fallbacks, and resuming a kept draft. */
@OptIn(ExperimentalCoroutinesApi::class)
class CheckInViewModelTest {

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
        val payments = FakePaymentRepository(scope, config)
        val orders = FakeOrderRepository(scope, config, object : Clock {
            override fun now() = Instant.fromEpochSeconds(1_800_000_000)
        })

        fun checkIn(orderId: String, wallet: PaymentType = PaymentType.APPLE_PAY) =
            CheckInViewModel(orderId, orders, stores, cart, payments, wallet)

        fun order(id: String): Order? = orders.orders.value.dataOrNull()?.firstOrNull { it.id == id }
    }

    /** The fakes run in backgroundScope, which advanceUntilIdle() does not wait for. */
    private fun TestScope.finishLoading() {
        runCurrent()
        advanceTimeBy(MockConfig.DEFAULT_LOADING_DELAY)
        runCurrent()
    }

    /** Signed in, with the sample cart (Test Ramen Bar, 2 items) saved as an Eat-In draft, as "Head to store" does. */
    private fun TestScope.checkedIn(wallet: PaymentType = PaymentType.APPLE_PAY): Triple<Fixture, Order, CheckInViewModel> {
        val f = Fixture(backgroundScope)
        finishLoading()
        backgroundScope.launch { f.auth.signInWith(SsoProvider.APPLE) }
        finishLoading()
        val draft = f.orders.createDraft(seedSampleCart(), "Test Ramen Bar")
        val viewModel = f.checkIn(draft.id, wallet)
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        return Triple(f, draft, viewModel)
    }

    // Gherkin scenarios

    @Test
    fun scenario1_seatConfirmedOpensOneTapPayment() = runTest(mainDispatcher) {
        val (f, draft, checkIn) = checkedIn()
        assertEquals("Test Ramen Bar", checkIn.state.value.store?.name)
        assertTrue(checkIn.state.value.canStartCooking)

        checkIn.startCooking()
        assertEquals(CheckInPrompt.SEAT_QUESTION, checkIn.state.value.prompt)
        checkIn.answerSeat(hasSeat = true)
        runCurrent()

        val state = checkIn.state.value
        assertEquals(CheckInPrompt.PAYMENT, state.prompt)
        // 1-tap: this platform's wallet is listed first and already picked.
        assertEquals(PaymentType.APPLE_PAY, state.paymentMethods.first().type)
        assertEquals(PaymentType.APPLE_PAY, state.selectedMethod?.type)
        assertTrue(state.paymentMethods.none { it.type == PaymentType.GOOGLE_PAY })
        assertTrue(state.canPay)

        checkIn.pay()
        runCurrent()
        assertEquals(draft.number, checkIn.state.value.sentOrderNumber)
        assertNull(checkIn.state.value.prompt)
        val sent = assertNotNull(f.order(draft.id))
        assertEquals(OrderStatus.QUEUED, sent.status)
        assertEquals(state.selectedMethodId, sent.paymentMethodId)
        assertFalse(checkIn.state.value.isUnavailable)
    }

    @Test
    fun scenario2_storeFullSwitchToTakeoutOpensCartReviewWithTheSameItems() = runTest(mainDispatcher) {
        val (f, draft, checkIn) = checkedIn()
        val events = collectEvents(checkIn.events)
        f.stores.setSeatsFull(SeedStoreIds.RAMEN_BAR, true)
        runCurrent()

        checkIn.startCooking()
        assertEquals(CheckInPrompt.NO_SEAT, checkIn.state.value.prompt)
        assertTrue(checkIn.state.value.isStoreFull)
        assertTrue(checkIn.state.value.canSwitchToTakeout)

        checkIn.switchToTakeout()
        runCurrent()
        assertEquals(listOf<CheckInEvent>(CheckInEvent.SwitchedToTakeout(SeedStoreIds.RAMEN_BAR)), events)
        assertEquals(Cart(SeedStoreIds.RAMEN_BAR, FulfillmentMode.PICKUP, draft.lines), f.cart.cart.value)
        // The draft became the cart, so it isn't left behind as a second copy.
        assertNull(f.order(draft.id))

        val review = CartReviewViewModel(f.cart, f.stores, f.menus, f.orders, f.auth)
        backgroundScope.launch { review.state.collect {} }
        finishLoading()
        val cartState = review.state.value
        assertEquals(FulfillmentMode.PICKUP, cartState.mode)
        assertEquals(CheckoutAction.PAY_NOW, cartState.action)
        assertEquals(draft.lines, cartState.cart?.lines)
        // Seats being full doesn't stop takeout.
        assertTrue(cartState.canCheckout)
    }

    @Test
    fun scenario3_cancelReturnsToDiscoverAndTheDraftCanBeResumed() = runTest(mainDispatcher) {
        val (f, draft, checkIn) = checkedIn()
        val events = collectEvents(checkIn.events)
        f.stores.setSeatsFull(SeedStoreIds.RAMEN_BAR, true)
        runCurrent()

        checkIn.startCooking()
        checkIn.exit()
        runCurrent()
        assertEquals(listOf<CheckInEvent>(CheckInEvent.Exited), events)
        assertEquals(OrderStatus.DRAFT, f.order(draft.id)?.status)

        val ordersList = OrdersViewModel(f.orders, f.auth)
        val discover = DiscoverViewModel(f.stores, f.orders, f.cart, f.auth)
        backgroundScope.launch { ordersList.state.collect {} }
        backgroundScope.launch { discover.state.collect {} }
        runCurrent()
        assertTrue(ordersList.state.value.active.any { it.id == draft.id && it.status == OrderStatus.DRAFT })
        assertEquals(draft.id, discover.state.value.draft?.id)

        // Resuming opens check-in on the same draft, ready to start cooking once seats free up.
        f.stores.setSeatsFull(SeedStoreIds.RAMEN_BAR, false)
        val resumed = f.checkIn(draft.id)
        backgroundScope.launch { resumed.state.collect {} }
        runCurrent()
        assertEquals(draft.lines, resumed.state.value.order?.lines)
        assertTrue(resumed.state.value.canStartCooking)
        resumed.startCooking()
        assertEquals(CheckInPrompt.SEAT_QUESTION, resumed.state.value.prompt)
    }

    // Fallbacks

    @Test
    fun noSeatYetThenWaitInLineKeepsTheDraftAndConfirmsTheSeatLater() = runTest(mainDispatcher) {
        val (f, draft, checkIn) = checkedIn()

        checkIn.startCooking()
        checkIn.answerSeat(hasSeat = false)
        assertEquals(CheckInPrompt.NO_SEAT, checkIn.state.value.prompt)
        assertFalse(checkIn.state.value.isStoreFull)

        checkIn.waitInLine()
        runCurrent()
        assertNull(checkIn.state.value.prompt)
        assertTrue(checkIn.state.value.isWaitingForSeat)
        assertEquals(OrderStatus.DRAFT, f.order(draft.id)?.status)

        // "I've found a seat" is the answer, so it goes straight to payment.
        checkIn.startCooking()
        assertEquals(CheckInPrompt.PAYMENT, checkIn.state.value.prompt)
    }

    @Test
    fun waitingWhileTheStoreIsStillFullOffersTheFallbacksAgain() = runTest(mainDispatcher) {
        val (f, draft, checkIn) = checkedIn()
        f.stores.setSeatsFull(SeedStoreIds.RAMEN_BAR, true)
        runCurrent()

        checkIn.startCooking()
        checkIn.waitInLine()
        checkIn.startCooking()
        assertEquals(CheckInPrompt.NO_SEAT, checkIn.state.value.prompt)
        assertEquals(OrderStatus.DRAFT, f.order(draft.id)?.status)
    }

    @Test
    fun switchToTakeoutAsksBeforeReplacingAnotherCart() = runTest(mainDispatcher) {
        val (f, draft, checkIn) = checkedIn()
        val other = CartLine("curry", seedItem(SeedStoreIds.CURRY_STAND, "item-beef-curry"))
        f.cart.addLine(FulfillmentMode.PICKUP, other)
        val events = collectEvents(checkIn.events)

        checkIn.startCooking()
        checkIn.answerSeat(hasSeat = false)
        checkIn.switchToTakeout()
        runCurrent()
        assertEquals("Sample Curry Stand", checkIn.state.value.pendingReplaceCartStoreName)
        assertTrue(events.isEmpty())

        checkIn.dismissReplaceCart()
        runCurrent()
        assertNull(checkIn.state.value.pendingReplaceCartStoreName)
        assertEquals(listOf(other), f.cart.cart.value?.lines)
        assertEquals(OrderStatus.DRAFT, f.order(draft.id)?.status)

        checkIn.switchToTakeout()
        checkIn.confirmReplaceCart()
        runCurrent()
        assertEquals(draft.lines, f.cart.cart.value?.lines)
        assertEquals(listOf<CheckInEvent>(CheckInEvent.SwitchedToTakeout(SeedStoreIds.RAMEN_BAR)), events)
    }

    @Test
    fun takeoutIsOffWhenTheKitchenIsFullAndTheDraftIsKept() = runTest(mainDispatcher) {
        val (f, draft, checkIn) = checkedIn()
        f.stores.setAvailability(SeedStoreIds.RAMEN_BAR, StoreAvailability.FULL)
        runCurrent()

        checkIn.startCooking()
        assertEquals(CheckInPrompt.NO_SEAT, checkIn.state.value.prompt)
        assertFalse(checkIn.state.value.canSwitchToTakeout)
        assertNotNull(checkIn.state.value.takeoutUnavailableReason)

        checkIn.switchToTakeout()
        runCurrent()
        assertNull(f.cart.cart.value)
        assertEquals(OrderStatus.DRAFT, f.order(draft.id)?.status)
    }

    @Test
    fun pausedStoreCantStartCooking() = runTest(mainDispatcher) {
        val (f, _, checkIn) = checkedIn()
        f.stores.setAvailability(SeedStoreIds.RAMEN_BAR, StoreAvailability.PAUSED)
        runCurrent()

        assertTrue(checkIn.state.value.isPaused)
        assertFalse(checkIn.state.value.canStartCooking)
        checkIn.startCooking()
        assertNull(checkIn.state.value.prompt)
    }

    @Test
    fun androidListsGooglePayFirstAndNotApplePay() = runTest(mainDispatcher) {
        val (_, _, checkIn) = checkedIn(wallet = PaymentType.GOOGLE_PAY)
        finishLoading()

        val methods = checkIn.state.value.paymentMethods
        assertEquals(PaymentType.GOOGLE_PAY, methods.first().type)
        assertTrue(methods.none { it.type == PaymentType.APPLE_PAY })
        assertEquals(PaymentType.GOOGLE_PAY, checkIn.state.value.selectedMethod?.type)
    }

    @Test
    fun anOrderAlreadySentIsNotCheckedInAgain() = runTest(mainDispatcher) {
        val (f, draft, _) = checkedIn()
        f.orders.startCooking(draft.id, "pm-test-card-4242")

        val again = f.checkIn(draft.id)
        backgroundScope.launch { again.state.collect {} }
        runCurrent()
        assertTrue(again.state.value.isUnavailable)
        assertFalse(again.state.value.canStartCooking)
    }

    @Test
    fun guestsDontSeeTheDraftBanner() = runTest(mainDispatcher) {
        val (f, _, _) = checkedIn()
        f.auth.signOut()
        val discover = DiscoverViewModel(f.stores, f.orders, f.cart, f.auth)
        backgroundScope.launch { discover.state.collect {} }
        runCurrent()
        assertNull(discover.state.value.draft)
    }
}
