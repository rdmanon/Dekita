package tech.vauldex.dekita.presentation.checkin

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
import tech.vauldex.dekita.data.mock.FakeInboxRepository
import tech.vauldex.dekita.data.mock.FakeLocationRepository
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
import tech.vauldex.dekita.domain.CheckIn
import tech.vauldex.dekita.domain.CheckInMethod
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

/** SEAT-01: the seat question after check-in, the store-full fallbacks, and resuming a kept draft. */
@OptIn(ExperimentalCoroutinesApi::class)
class SeatCheckViewModelTest {

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
        val inbox = FakeInboxRepository(scope, config, object : Clock {
            override fun now() = Instant.fromEpochSeconds(1_800_000_000)
        })
        val location = FakeLocationRepository()
        val orders = FakeOrderRepository(scope, config, object : Clock {
            override fun now() = Instant.fromEpochSeconds(1_800_000_000)
        })

        fun seatCheck(orderId: String, wallet: PaymentType = PaymentType.APPLE_PAY) =
            SeatCheckViewModel(orderId, orders, stores, cart, payments, wallet)

        fun order(id: String): Order? = orders.orders.value.dataOrNull()?.firstOrNull { it.id == id }
    }

    /** The fakes run in backgroundScope, which advanceUntilIdle() does not wait for. */
    private fun TestScope.finishLoading() {
        runCurrent()
        advanceTimeBy(MockConfig.DEFAULT_LOADING_DELAY)
        runCurrent()
    }

    /**
     * Signed in, with the sample cart (Test Ramen Bar, 2 items) saved as an Eat-In draft and checked in at
     * "Test Ramen Bar" (CHECKIN-01), then the seat check opened by Start Cooking.
     */
    private fun TestScope.checkedIn(wallet: PaymentType = PaymentType.APPLE_PAY): Triple<Fixture, Order, SeatCheckViewModel> {
        val f = Fixture(backgroundScope)
        finishLoading()
        backgroundScope.launch { f.auth.signInWith(SsoProvider.APPLE) }
        finishLoading()
        val draft = f.orders.createDraft(seedSampleCart(), "Test Ramen Bar")
        assertTrue(f.orders.checkIn(draft.id, CheckIn(CheckInMethod.GEOFENCE)))
        val viewModel = f.seatCheck(draft.id, wallet)
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        return Triple(f, draft, viewModel)
    }

    // Gherkin scenarios

    @Test
    fun scenario1_seatConfirmedOpensOneTapPayment() = runTest(mainDispatcher) {
        val (f, draft, seat) = checkedIn()
        assertEquals("Test Ramen Bar", seat.state.value.store?.name)
        assertEquals(SeatCheckStep.QUESTION, seat.state.value.step)

        seat.answerSeat(hasSeat = true)
        runCurrent()

        val state = seat.state.value
        assertEquals(SeatCheckStep.PAYMENT, state.step)
        // 1-tap: this platform's wallet is listed first and already picked.
        assertEquals(PaymentType.APPLE_PAY, state.paymentMethods.first().type)
        assertEquals(PaymentType.APPLE_PAY, state.selectedMethod?.type)
        assertTrue(state.paymentMethods.none { it.type == PaymentType.GOOGLE_PAY })
        assertTrue(state.canPay)

        val events = collectEvents(seat.events)
        seat.pay()
        runCurrent()
        // Paying opens the order's ticket (ORDER-01).
        assertEquals(listOf<SeatCheckEvent>(SeatCheckEvent.Paid(draft.id)), events)
        assertEquals(draft.id, seat.state.value.paidOrderId)
        val sent = assertNotNull(f.order(draft.id))
        assertEquals(OrderStatus.QUEUED, sent.status)
        assertEquals(state.selectedMethodId, sent.paymentMethodId)
        // The check-in is kept on the order for the ticket.
        assertEquals(CheckInMethod.GEOFENCE, sent.checkIn?.method)
        assertFalse(seat.state.value.isUnavailable)
    }

    @Test
    fun scenario2_storeFullSwitchToTakeoutOpensCartReviewWithTheSameItems() = runTest(mainDispatcher) {
        val (f, draft, seat) = checkedIn()
        val events = collectEvents(seat.events)
        f.stores.setSeatsFull(SeedStoreIds.RAMEN_BAR, true)
        runCurrent()

        // A full store skips the question.
        assertEquals(SeatCheckStep.NO_SEAT, seat.state.value.step)
        assertTrue(seat.state.value.isStoreFull)
        assertTrue(seat.state.value.canSwitchToTakeout)

        seat.switchToTakeout()
        runCurrent()
        assertEquals(listOf<SeatCheckEvent>(SeatCheckEvent.SwitchedToTakeout(SeedStoreIds.RAMEN_BAR)), events)
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
        val (f, draft, seat) = checkedIn()
        val events = collectEvents(seat.events)
        f.stores.setSeatsFull(SeedStoreIds.RAMEN_BAR, true)
        runCurrent()

        seat.exit()
        runCurrent()
        assertEquals(listOf<SeatCheckEvent>(SeatCheckEvent.Exited), events)
        assertEquals(OrderStatus.DRAFT, f.order(draft.id)?.status)

        val ordersList = OrdersViewModel(f.orders, f.inbox, f.auth)
        val discover = DiscoverViewModel(f.stores, f.orders, f.cart, f.auth)
        backgroundScope.launch { ordersList.state.collect {} }
        backgroundScope.launch { discover.state.collect {} }
        runCurrent()
        assertTrue(ordersList.state.value.active.any { it.id == draft.id && it.status == OrderStatus.DRAFT })
        assertEquals(draft.id, discover.state.value.draftToResume?.id)

        // Resuming opens check-in on the same draft, still checked in, so Start Cooking leads back here.
        val checkIn = CheckInViewModel(draft.id, f.orders, f.stores, f.location)
        backgroundScope.launch { checkIn.state.collect {} }
        runCurrent()
        assertEquals(draft.lines, checkIn.state.value.draft?.lines)
        assertTrue(checkIn.state.value.canStartCooking)
        assertTrue(checkIn.startCooking())
    }

    // Fallbacks

    @Test
    fun notYetThenWaitInLineKeepsTheCheckedInDraft() = runTest(mainDispatcher) {
        val (f, draft, seat) = checkedIn()
        val events = collectEvents(seat.events)

        seat.answerSeat(hasSeat = false)
        // state is a stateIn of the sources, so it catches up on the next dispatch.
        runCurrent()
        assertEquals(SeatCheckStep.NO_SEAT, seat.state.value.step)
        assertFalse(seat.state.value.isStoreFull)

        seat.waitInLine()
        runCurrent()
        assertEquals(listOf<SeatCheckEvent>(SeatCheckEvent.WaitInLine), events)
        val kept = assertNotNull(f.order(draft.id))
        assertEquals(OrderStatus.DRAFT, kept.status)
        assertNotNull(kept.checkIn)

        // Coming back later asks again.
        val again = f.seatCheck(draft.id)
        backgroundScope.launch { again.state.collect {} }
        runCurrent()
        assertEquals(SeatCheckStep.QUESTION, again.state.value.step)
    }

    @Test
    fun closingPaymentGoesBackToTheQuestion() = runTest(mainDispatcher) {
        val (_, _, seat) = checkedIn()
        seat.answerSeat(hasSeat = true)
        seat.closePayment()
        runCurrent()
        assertEquals(SeatCheckStep.QUESTION, seat.state.value.step)
    }

    @Test
    fun switchToTakeoutAsksBeforeReplacingAnotherCart() = runTest(mainDispatcher) {
        val (f, draft, seat) = checkedIn()
        val other = CartLine("curry", seedItem(SeedStoreIds.CURRY_STAND, "item-beef-curry"))
        f.cart.addLine(FulfillmentMode.PICKUP, other)
        val events = collectEvents(seat.events)

        seat.answerSeat(hasSeat = false)
        seat.switchToTakeout()
        runCurrent()
        assertEquals("Sample Curry Stand", seat.state.value.pendingReplaceCartStoreName)
        assertTrue(events.isEmpty())

        seat.dismissReplaceCart()
        runCurrent()
        assertNull(seat.state.value.pendingReplaceCartStoreName)
        assertEquals(listOf(other), f.cart.cart.value?.lines)
        assertEquals(OrderStatus.DRAFT, f.order(draft.id)?.status)

        seat.switchToTakeout()
        seat.confirmReplaceCart()
        runCurrent()
        assertEquals(draft.lines, f.cart.cart.value?.lines)
        assertEquals(listOf<SeatCheckEvent>(SeatCheckEvent.SwitchedToTakeout(SeedStoreIds.RAMEN_BAR)), events)
    }

    @Test
    fun takeoutIsOffWhenTheKitchenIsFullAndTheDraftIsKept() = runTest(mainDispatcher) {
        val (f, draft, seat) = checkedIn()
        f.stores.setAvailability(SeedStoreIds.RAMEN_BAR, StoreAvailability.FULL)
        runCurrent()

        assertEquals(SeatCheckStep.NO_SEAT, seat.state.value.step)
        assertFalse(seat.state.value.canSwitchToTakeout)
        assertNotNull(seat.state.value.takeoutUnavailableReason)

        seat.switchToTakeout()
        runCurrent()
        assertNull(f.cart.cart.value)
        assertEquals(OrderStatus.DRAFT, f.order(draft.id)?.status)
    }

    @Test
    fun pausedStoreCantPay() = runTest(mainDispatcher) {
        val (f, draft, seat) = checkedIn()
        seat.answerSeat(hasSeat = true)
        f.stores.setAvailability(SeedStoreIds.RAMEN_BAR, StoreAvailability.PAUSED)
        runCurrent()

        assertFalse(seat.state.value.canPay)
        seat.pay()
        runCurrent()
        assertEquals(OrderStatus.DRAFT, f.order(draft.id)?.status)
    }

    @Test
    fun androidListsGooglePayFirstAndNotApplePay() = runTest(mainDispatcher) {
        val (_, _, seat) = checkedIn(wallet = PaymentType.GOOGLE_PAY)

        val methods = seat.state.value.paymentMethods
        assertEquals(PaymentType.GOOGLE_PAY, methods.first().type)
        assertTrue(methods.none { it.type == PaymentType.APPLE_PAY })
        assertEquals(PaymentType.GOOGLE_PAY, seat.state.value.selectedMethod?.type)
    }

    @Test
    fun aDraftThatIsNotCheckedInHasNothingToSeatCheck() = runTest(mainDispatcher) {
        val f = Fixture(backgroundScope)
        finishLoading()
        val draft = f.orders.createDraft(seedSampleCart(), "Test Ramen Bar")
        val seat = f.seatCheck(draft.id)
        backgroundScope.launch { seat.state.collect {} }
        runCurrent()
        assertTrue(seat.state.value.isUnavailable)
        seat.answerSeat(hasSeat = true)
        runCurrent()
        assertFalse(seat.state.value.canPay)
    }

    @Test
    fun anOrderAlreadySentIsNotSeatCheckedAgain() = runTest(mainDispatcher) {
        val (f, draft, _) = checkedIn()
        assertNotNull(f.orders.payAtCheckIn(draft.id))

        val again = f.seatCheck(draft.id)
        backgroundScope.launch { again.state.collect {} }
        runCurrent()
        assertTrue(again.state.value.isUnavailable)
    }
}
