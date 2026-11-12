package tech.vauldex.dekita.presentation.payment

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
import tech.vauldex.dekita.data.mock.FakeLocationRepository
import tech.vauldex.dekita.data.mock.FakeMenuRepository
import tech.vauldex.dekita.data.mock.FakeOrderRepository
import tech.vauldex.dekita.data.mock.FakePaymentRepository
import tech.vauldex.dekita.data.mock.FakePaymentService
import tech.vauldex.dekita.data.mock.FakeStoreRepository
import tech.vauldex.dekita.data.mock.MockConfig
import tech.vauldex.dekita.data.mock.SeedPaymentIds
import tech.vauldex.dekita.data.mock.SeedStoreIds
import tech.vauldex.dekita.data.mock.seedItem
import tech.vauldex.dekita.data.mock.seedOption
import tech.vauldex.dekita.domain.CartLine
import tech.vauldex.dekita.domain.CheckIn
import tech.vauldex.dekita.domain.CheckInMethod
import tech.vauldex.dekita.domain.ClientPlatform
import tech.vauldex.dekita.domain.FulfillmentMode
import tech.vauldex.dekita.domain.Order
import tech.vauldex.dekita.domain.OrderStatus
import tech.vauldex.dekita.presentation.cart.CartReviewViewModel
import tech.vauldex.dekita.presentation.checkin.CheckInViewModel
import tech.vauldex.dekita.presentation.collectEvents

/**
 * PAY-02: a failed or cancelled payment keeps the order exactly as it was. Every way out of the payment sheet is
 * checked by comparing the cart, and what Cart Review shows, before and after.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PaymentRetryTest {

    private val mainDispatcher = StandardTestDispatcher()

    /** The seed in-store draft at Test Ramen Bar, waiting for check-in. */
    private val draftId = "order-44"

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
        val payments = FakePaymentRepository(scope, config)
        val service = FakePaymentService(orders, payments, config)
        val location = FakeLocationRepository()
        val review = CartReviewViewModel(cart, stores, menus, orders, auth)

        fun line(itemId: String, vararg optionIds: String, quantity: Int = 1): CartLine {
            val item = seedItem(SeedStoreIds.RAMEN_BAR, itemId)
            return CartLine("$itemId|${optionIds.sorted().joinToString("|")}", item, optionIds.map { item.seedOption(it) }, quantity)
        }

        /** Scenario 2's cart: 3 items with modifiers, Takeout at Test Ramen Bar. */
        fun fillThreeItemTakeoutCart() {
            cart.addLine(FulfillmentMode.PICKUP, line("item-shoyu-ramen", "ramen-spice-mild", "topping-egg", quantity = 2))
            cart.addLine(FulfillmentMode.PICKUP, line("item-gyoza"))
        }

        fun orderList(): List<Order> = orders.orders.value.dataOrNull().orEmpty()
    }

    /** What the customer sees on Cart Review: the lines (items, modifiers, quantities) and the mode. */
    private data class CartReviewSnapshot(val lines: List<CartLine>, val mode: FulfillmentMode?, val total: String?)

    private fun Fixture.cartReview(): CartReviewSnapshot {
        val state = review.state.value
        return CartReviewSnapshot(state.lines.map { it.line }, state.mode, state.cart?.total?.formatted())
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

    private fun TestScope.payment(
        f: Fixture,
        target: PaymentTarget = PaymentTarget.Cart,
        platform: ClientPlatform = ClientPlatform.ANDROID,
    ): PaymentViewModel =
        PaymentViewModel(target, platform, f.service, f.payments, f.cart, f.orders, f.stores).also { viewModel ->
            backgroundScope.launch { viewModel.state.collect {} }
            runCurrent()
        }

    private fun TestScope.declineOn(sheet: PaymentViewModel) {
        sheet.selectMethod(SeedPaymentIds.DECLINE_CARD)
        sheet.pay()
        finishLoading()
        assertTrue(sheet.state.value.isDeclined)
    }

    // Scenario 1: declined card with retry

    @Test
    fun declineShowsRetryAndRetryWithTheGoodCardCreatesExactlyOneOrder() = runTest(mainDispatcher) {
        val f = fixture()
        f.fillThreeItemTakeoutCart()
        val sheet = payment(f)
        val events = collectEvents(sheet.events)
        val before = f.orderList()

        declineOn(sheet)
        assertNotNull(sheet.state.value.errorMessage)
        assertTrue(sheet.state.value.canPay, "Retry is on")
        assertTrue(sheet.state.value.canChangeMethod, "Another method can be picked")
        assertEquals(before, f.orderList(), "A decline makes no order")

        sheet.selectMethod(SeedPaymentIds.TEST_CARD)
        sheet.pay() // Retry
        sheet.pay() // a second tap on Retry
        finishLoading()

        val created = f.orderList() - before.toSet()
        assertEquals(1, created.size)
        assertEquals(SeedPaymentIds.TEST_CARD, created.single().paymentMethodId)
        assertEquals(listOf<PaymentEvent>(PaymentEvent.Paid(created.single().id, created.single().number)), events)
    }

    @Test
    fun retryWithTheSameDeclinedCardStillDeclinesAndMakesNoOrder() = runTest(mainDispatcher) {
        val f = fixture()
        f.fillThreeItemTakeoutCart()
        val sheet = payment(f)
        val before = f.orderList()

        declineOn(sheet)
        sheet.pay()
        finishLoading()

        assertTrue(sheet.state.value.isDeclined)
        assertEquals(before, f.orderList())
    }

    @Test
    fun declineMessageIsPlainLanguageWithNoInternalCodes() = runTest(mainDispatcher) {
        for (target in listOf(PaymentTarget.Cart, PaymentTarget.Draft(draftId))) {
            val f = fixture()
            f.fillThreeItemTakeoutCart()
            f.orders.checkIn(draftId, CheckIn(CheckInMethod.GEOFENCE))
            val sheet = payment(f, target)
            declineOn(sheet)

            val message = assertNotNull(sheet.state.value.errorMessage)
            assertTrue("weren't charged" in message, message)
            assertFalse(Regex("""\d|_|DECLINE|[Ee]rror|[Cc]ode""").containsMatchIn(message), "Looks like a code: $message")
        }
    }

    @Test
    fun useAnotherMethodClearsTheErrorAndMovesOffTheDeclinedCard() = runTest(mainDispatcher) {
        val f = fixture()
        f.fillThreeItemTakeoutCart()
        val sheet = payment(f)

        declineOn(sheet)
        sheet.useAnotherMethod()
        runCurrent()

        assertEquals(PaymentPhase.READY, sheet.state.value.phase)
        assertEquals(SeedPaymentIds.TEST_CARD, sheet.state.value.selectedMethod?.id, "The saved default, not the declined card")
    }

    // Scenarios 2 and 3: every exit returns to Cart Review with the cart intact

    /** One way out of the sheet, and whether the sheet itself asks to close (the others are closed by the screen). */
    private class Exit(val name: String, val sendsClosed: Boolean, val run: TestScope.(PaymentViewModel) -> Unit)

    private val exits = listOf(
        Exit("Cancel after a decline", sendsClosed = true) { sheet ->
            declineOn(sheet)
            sheet.cancel()
        },
        Exit("PayPay cancel", sendsClosed = true) { sheet ->
            sheet.selectMethod(SeedPaymentIds.PAYPAY)
            sheet.pay()
            sheet.cancelApproval()
        },
        Exit("Rakuten Pay cancel", sendsClosed = true) { sheet ->
            sheet.selectMethod(SeedPaymentIds.RAKUTEN_PAY)
            sheet.pay()
            sheet.cancelApproval()
        },
        // Android Back, iOS swipe-down and the close button all dismiss the sheet, which resets it.
        Exit("Back or swipe-down before paying", sendsClosed = false) { sheet -> sheet.reset() },
        Exit("Back or swipe-down after a decline", sendsClosed = false) { sheet ->
            declineOn(sheet)
            sheet.reset()
        },
        Exit("Use another method, then Back or swipe-down", sendsClosed = false) { sheet ->
            declineOn(sheet)
            sheet.useAnotherMethod()
            sheet.reset()
        },
        Exit("Google Pay sheet closed, then Back", sendsClosed = false) { sheet ->
            sheet.selectMethod(SeedPaymentIds.GOOGLE_PAY)
            sheet.pay()
            sheet.cancelApproval()
            sheet.reset()
        },
    )

    @Test
    fun everyExitKeepsTheCartExactlyAsItWas() = runTest(mainDispatcher) {
        for (exit in exits) {
            val f = fixture()
            f.fillThreeItemTakeoutCart()
            finishLoading()
            val cartBefore = f.cart.cart.value
            val reviewBefore = f.cartReview()
            assertEquals(3, cartBefore?.itemCount, exit.name)
            val ordersBefore = f.orderList()
            val sheet = payment(f)
            val events = collectEvents(sheet.events)

            exit.run(this, sheet)
            finishLoading()

            assertEquals(cartBefore, f.cart.cart.value, "${exit.name}: cart")
            assertEquals(reviewBefore, f.cartReview(), "${exit.name}: Cart Review's items, modifiers, quantities and mode")
            assertEquals(FulfillmentMode.PICKUP, f.cartReview().mode, "${exit.name}: still Takeout")
            assertEquals(ordersBefore, f.orderList(), "${exit.name}: no order")
            val expected = if (exit.sendsClosed) listOf<PaymentEvent>(PaymentEvent.Closed) else emptyList()
            assertEquals(expected, events, "${exit.name}: only Closed, never Paid")
            assertTrue(f.review.state.value.canCheckout, "${exit.name}: Pay now works again")
        }
    }

    @Test
    fun cancelIsIgnoredWhileCharging() = runTest(mainDispatcher) {
        val f = fixture()
        f.fillThreeItemTakeoutCart()
        val sheet = payment(f)
        val events = collectEvents(sheet.events)

        sheet.pay()
        runCurrent()
        sheet.cancel()
        finishLoading()

        assertEquals(PaymentPhase.PAID, sheet.state.value.phase)
        assertTrue(events.none { it == PaymentEvent.Closed })
    }

    @Test
    fun payingAgainAfterLeavingUsesTheSameCheckoutAndMakesOneOrder() = runTest(mainDispatcher) {
        val f = fixture()
        f.fillThreeItemTakeoutCart()
        val sheet = payment(f)
        val before = f.orderList()

        declineOn(sheet)
        sheet.cancel()
        // Back on Cart Review, the customer taps Pay now again.
        sheet.selectMethod(SeedPaymentIds.TEST_CARD)
        sheet.pay()
        finishLoading()

        assertEquals(before.size + 1, f.orderList().size)
    }

    // Eat-In: a failed payment keeps the check-in

    @Test
    fun failedEatInPaymentKeepsTheCheckIn() = runTest(mainDispatcher) {
        val f = fixture()
        val tableCheckIn = CheckIn(CheckInMethod.TABLE_QR, "12")
        assertTrue(f.orders.checkIn(draftId, tableCheckIn))
        val checkInScreen = CheckInViewModel(draftId, f.orders, f.stores, f.location)
        backgroundScope.launch { checkInScreen.state.collect {} }
        val draftBefore = f.orderList().first { it.id == draftId }
        val sheet = payment(f, PaymentTarget.Draft(draftId))
        val events = collectEvents(sheet.events)

        declineOn(sheet)
        sheet.cancel()
        finishLoading()

        val draft = f.orderList().first { it.id == draftId }
        assertEquals(draftBefore, draft, "The draft is untouched")
        assertEquals(OrderStatus.DRAFT, draft.status)
        assertEquals(tableCheckIn, draft.checkIn)
        assertEquals(listOf<PaymentEvent>(PaymentEvent.Closed), events)

        // Back on check-in, the customer is out of the geofence, yet Start Cooking still works: no second check-in.
        assertFalse(checkInScreen.state.value.isInRange)
        assertTrue(checkInScreen.state.value.canStartCooking)
        assertTrue(checkInScreen.startCooking(), "Start Cooking opens the seat check, and its payment sheet, again")
        assertEquals(tableCheckIn, f.orderList().first { it.id == draftId }.checkIn, "The table is kept")

        sheet.selectMethod(SeedPaymentIds.TEST_CARD)
        sheet.pay()
        finishLoading()
        assertEquals(OrderStatus.QUEUED, f.orderList().first { it.id == draftId }.status)
    }
}
