package tech.vauldex.dekita.presentation.orders

import kotlin.time.Clock
import kotlin.time.Instant
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
import tech.vauldex.dekita.data.mock.FakeAuthRepository
import tech.vauldex.dekita.data.mock.FakeInboxRepository
import tech.vauldex.dekita.data.mock.FakeOrderRepository
import tech.vauldex.dekita.data.mock.FakePaymentRepository
import tech.vauldex.dekita.data.mock.MockConfig
import tech.vauldex.dekita.data.mock.SeedStoreIds
import tech.vauldex.dekita.data.mock.seedItem
import tech.vauldex.dekita.domain.Cart
import tech.vauldex.dekita.domain.CartLine
import tech.vauldex.dekita.domain.CheckIn
import tech.vauldex.dekita.domain.CheckInMethod
import tech.vauldex.dekita.domain.CompletionReason
import tech.vauldex.dekita.domain.FulfillmentMode
import tech.vauldex.dekita.domain.InboxKind
import tech.vauldex.dekita.domain.OrderStatus
import tech.vauldex.dekita.domain.SsoProvider
import tech.vauldex.dekita.presentation.ORDER_PLACED_MESSAGE
import tech.vauldex.dekita.presentation.collectEvents

/** ORDER-01: the ticket's live stepper, [Confirm] and auto-complete. */
@OptIn(ExperimentalCoroutinesApi::class)
class OrderTicketViewModelTest {

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

    private class Fixture(
        val orders: FakeOrderRepository,
        val auth: FakeAuthRepository,
        val payments: FakePaymentRepository,
        val inbox: FakeInboxRepository,
    )

    /** Loaded orders and a signed-in customer, unless [signIn] is false. */
    private suspend fun TestScope.fixture(signIn: Boolean = true): Fixture {
        val fixture = Fixture(
            FakeOrderRepository(backgroundScope, MockConfig(), fixedClock),
            FakeAuthRepository(MockConfig()),
            FakePaymentRepository(backgroundScope, MockConfig()),
            FakeInboxRepository(backgroundScope, MockConfig(), fixedClock),
        )
        advanceTimeBy(MockConfig.DEFAULT_LOADING_DELAY)
        runCurrent()
        if (signIn) fixture.auth.signInWith(SsoProvider.APPLE)
        runCurrent()
        return fixture
    }

    private fun TestScope.ticket(f: Fixture, orderId: String) =
        OrderTicketViewModel(orderId, f.orders, f.auth, f.payments, f.inbox, f.orders).also { viewModel ->
            backgroundScope.launch { viewModel.state.collect {} }
            runCurrent()
        }

    private val pickupCart = Cart(
        storeId = SeedStoreIds.MART,
        mode = FulfillmentMode.PICKUP,
        lines = listOf(CartLine("line-tea", seedItem(SeedStoreIds.MART, "item-green-tea"))),
    )

    private val OrderTicketUiState.stepStates get() = steps.map { it.state }

    @Test
    fun pickupTicketFollowsTheOrderLiveAndConfirmClosesItOnce() = runTest(mainDispatcher) {
        val f = fixture()
        val order = f.orders.placeOrder(pickupCart, "Test Mart")
        val ticket = ticket(f, order.id)

        var state = ticket.state.value
        assertEquals("#${order.number}", state.numberLabel)
        assertEquals("Test Mart · Pickup", state.storeLine)
        assertEquals(listOf("Received", "Cooking", "Ready"), state.steps.map { it.label })
        assertEquals(listOf(StepState.CURRENT, StepState.TODO, StepState.TODO), state.stepStates)
        assertEquals(ORDER_PLACED_MESSAGE, state.message)
        assertFalse(state.canConfirm)
        assertFalse(ticket.confirm(), "Confirm is off before the order is ready")

        // The mock kitchen moves it on; the ticket updates without a refresh.
        advanceTimeBy(MockConfig.ORDER_STEP_INTERVAL)
        runCurrent()
        assertEquals(listOf(StepState.DONE, StepState.CURRENT, StepState.TODO), ticket.state.value.stepStates)
        advanceTimeBy(MockConfig.ORDER_STEP_INTERVAL)
        runCurrent()
        state = ticket.state.value
        assertEquals(listOf(StepState.DONE, StepState.DONE, StepState.CURRENT), state.stepStates)
        assertEquals("Your order #${order.number} is ready for pickup", state.message)
        assertTrue(state.canConfirm)

        assertTrue(ticket.confirm())
        assertFalse(ticket.confirm(), "A second tap closes nothing")
        runCurrent()
        state = ticket.state.value
        assertTrue(state.isCompleted)
        assertEquals(CompletionReason.CUSTOMER_CONFIRMED, state.order?.completionReason)
        assertEquals(List(3) { StepState.DONE }, state.stepStates)
        assertEquals("Completed · Confirmed by you", state.message)
        assertNull(state.confirmHint)
    }

    @Test
    fun eatInTicketShowsTheTableAndTheServedConfirmation() = runTest(mainDispatcher) {
        val f = fixture()
        f.orders.checkIn("order-44", CheckIn(CheckInMethod.TABLE_QR, tableNumber = "5"))
        f.orders.payAtCheckIn("order-44")
        val ticket = ticket(f, "order-44")

        assertEquals(listOf("Received", "Cooking", "Served"), ticket.state.value.steps.map { it.label })
        assertEquals("Table 5", ticket.state.value.tableLabel)
        assertEquals("The kitchen has your order. You'll be served shortly.", ticket.state.value.message)

        ticket.debugAdvance()
        ticket.debugAdvance()
        runCurrent()

        assertEquals(OrderStatus.SERVED, ticket.state.value.order?.status)
        assertEquals("Served at table 5. Enjoy!", ticket.state.value.message)
        assertTrue(ticket.state.value.canConfirm)
    }

    @Test
    fun autoCompleteWhileTheTicketIsOpen() = runTest(mainDispatcher) {
        val f = fixture()
        val ticket = ticket(f, "order-43")
        assertTrue(ticket.state.value.canConfirm, "Seed #43 is ready for pickup")

        ticket.debugAutoComplete()
        runCurrent()

        assertTrue(ticket.state.value.isCompleted)
        assertEquals("Completed · Auto-completed", ticket.state.value.message)
        assertFalse(ticket.confirm())
    }

    @Test
    fun noTicketForADraftAnUnknownOrderOrAGuest() = runTest(mainDispatcher) {
        val f = fixture()
        assertNull(ticket(f, "order-44").state.value.order, "A draft has no ticket until it is paid")
        assertNull(ticket(f, "no-such-order").state.value.order)
        assertFalse(ticket(f, "no-such-order").state.value.isLoading)

        val guest = fixture(signIn = false)
        val guestTicket = ticket(guest, "order-42")
        assertNull(guestTicket.state.value.order)
        assertFalse(guestTicket.state.value.isSignedIn)
        assertFalse(guestTicket.confirm())
    }

    // RCPT-01: the completed ticket as a receipt, and Rate after an auto-complete

    @Test
    fun aCompletedOrderShowsTheReceiptFields() = runTest(mainDispatcher) {
        val f = fixture()
        advanceTimeBy(MockConfig.DEFAULT_LOADING_DELAY)
        runCurrent()
        val rated = ticket(f, "order-18").state.value
        assertEquals("Rakuten Pay (test account)", rated.paymentLabel)
        assertEquals(fixedClock.now().epochSeconds - 6 * 24 * 3600 + 60, rated.paidAtEpochSeconds, "Queued a minute after it was placed")
        assertEquals("Rated 5 of 5 · Friendly staff", rated.ratingLabel)
        assertFalse(rated.canRate)

        val unrated = ticket(f, "order-37").state.value
        assertEquals("Test Visa •••• 4242", unrated.paymentLabel)
        assertNull(unrated.ratingLabel)
        assertTrue(unrated.canRate)
    }

    @Test
    fun rateOpensOnceForAnAutoCompletedOrderThatWasNeverRatedOrSkipped() = runTest(mainDispatcher) {
        val f = fixture()
        advanceTimeBy(MockConfig.DEFAULT_LOADING_DELAY)
        runCurrent()

        val autoCompleted = ticket(f, "order-29")
        val events = collectEvents(autoCompleted.events)
        runCurrent()
        assertEquals(listOf<OrderTicketEvent>(OrderTicketEvent.OpenRating), events)

        val confirmed = ticket(f, "order-37")
        val confirmedEvents = collectEvents(confirmed.events)
        runCurrent()
        assertTrue(confirmedEvents.isEmpty(), "Confirm opens Rate itself, so a confirmed order doesn't reopen it")

        // After a skip, the inbox message is the way back in.
        f.inbox.add(ratePromptId("order-29"), "Rate order #29", "How was it?", InboxKind.RATE_ORDER, "order-29")
        val skipped = ticket(f, "order-29")
        val skippedEvents = collectEvents(skipped.events)
        runCurrent()
        assertTrue(skippedEvents.isEmpty())
    }
}
