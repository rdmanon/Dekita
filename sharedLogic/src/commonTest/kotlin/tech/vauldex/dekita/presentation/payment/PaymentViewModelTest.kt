package tech.vauldex.dekita.presentation.payment

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
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import tech.vauldex.dekita.data.PaymentRequest
import tech.vauldex.dekita.data.PaymentResult
import tech.vauldex.dekita.data.dataOrNull
import tech.vauldex.dekita.data.mock.FakeAuthRepository
import tech.vauldex.dekita.data.mock.FakeCartRepository
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
import tech.vauldex.dekita.domain.CardFormError
import tech.vauldex.dekita.domain.CartLine
import tech.vauldex.dekita.domain.CheckIn
import tech.vauldex.dekita.domain.CheckInMethod
import tech.vauldex.dekita.domain.ClientPlatform
import tech.vauldex.dekita.domain.FulfillmentMode
import tech.vauldex.dekita.domain.Money
import tech.vauldex.dekita.domain.Order
import tech.vauldex.dekita.domain.OrderStatus
import tech.vauldex.dekita.domain.PaymentType
import tech.vauldex.dekita.domain.SsoProvider
import tech.vauldex.dekita.presentation.cart.CartReviewEvent
import tech.vauldex.dekita.presentation.cart.CartReviewViewModel
import tech.vauldex.dekita.presentation.collectEvents

/** PAY-01: the payment sheet, the mock approval screens, Eat-In payment on check-in, double submit and totals. */
@OptIn(ExperimentalCoroutinesApi::class)
class PaymentViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()

    /** The seed in-store draft at Test Ramen Bar: 2 items, ¥1,550, waiting for check-in. */
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
        val review = CartReviewViewModel(cart, stores, menus, orders, auth)

        fun line(storeId: String, itemId: String, vararg optionIds: String): CartLine {
            val item = seedItem(storeId, itemId)
            return CartLine("$itemId|${optionIds.sorted().joinToString("|")}", item, optionIds.map { item.seedOption(it) })
        }

        /** The ticket's Scenario 1: a ¥1,200 Takeout cart, the Test Ramen Bar's take-home ramen kit. */
        fun fillTakeoutCart() {
            cart.addLine(FulfillmentMode.PICKUP, line(SeedStoreIds.RAMEN_BAR, "item-ramen-kit"))
        }

        fun orderList(): List<Order> = orders.orders.value.dataOrNull().orEmpty()
    }

    /** The fakes run in backgroundScope, which advanceUntilIdle() does not wait for. */
    private fun TestScope.finishLoading() {
        runCurrent()
        advanceTimeBy(MockConfig.DEFAULT_LOADING_DELAY)
        runCurrent()
    }

    /** One simulated charge: the payment service waits the loading delay. */
    private fun TestScope.finishCharge() = finishLoading()

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

    // Scenario 1: Takeout by card

    @Test
    fun takeoutCardPaymentIssuesAPaidOrderAndOpensTheTicket() = runTest(mainDispatcher) {
        val f = fixture()
        f.fillTakeoutCart()
        val sheet = payment(f)
        val events = collectEvents(sheet.events)
        val before = f.orderList().size

        assertEquals("Test Visa •••• 4242", sheet.state.value.selectedMethod?.label)
        assertEquals("Pay ¥1,200", sheet.state.value.payButtonLabel)
        assertEquals("Test Ramen Bar · Takeout · 1 item", sheet.state.value.summary)

        sheet.pay()
        runCurrent()
        assertTrue(sheet.state.value.isProcessing)
        assertFalse(sheet.state.value.canPay)
        assertFalse(sheet.state.value.canDismiss)

        finishCharge()
        val order = f.orderList().first()
        assertEquals(before + 1, f.orderList().size)
        assertEquals(OrderStatus.PAID, order.status)
        assertEquals(Money.yen(1200), order.total)
        assertEquals(SeedPaymentIds.TEST_CARD, order.paymentMethodId)
        assertEquals(listOf<PaymentEvent>(PaymentEvent.Paid(order.id, order.number)), events)
        assertEquals(PaymentPhase.PAID, sheet.state.value.phase)
        assertNull(f.cart.cart.value, "A paid cart is emptied")
        assertEquals("Test Ramen Bar · Takeout · 1 item", sheet.state.value.summary, "The sheet keeps what was paid")
    }

    @Test
    fun inStoreOnlyPayNowJoinsTheQueue() = runTest(mainDispatcher) {
        val f = fixture()
        f.cart.addLine(FulfillmentMode.IN_STORE, f.line(SeedStoreIds.HAIR_SALON, "item-quick-cut"))
        val sheet = payment(f)

        sheet.pay()
        finishCharge()

        val order = f.orderList().first()
        assertEquals(OrderStatus.QUEUED, order.status)
        assertEquals("Test Hair & Nail Salon", order.storeName)
    }

    @Test
    fun payNowFromCartReviewOpensTheSheetWithTheSameTotal() = runTest(mainDispatcher) {
        val f = fixture()
        f.auth.signInWith(SsoProvider.GOOGLE)
        f.cart.addLine(FulfillmentMode.PICKUP, f.line(SeedStoreIds.RAMEN_BAR, "item-shoyu-ramen", "ramen-spice-mild", "topping-egg"))
        f.cart.addLine(FulfillmentMode.PICKUP, f.line(SeedStoreIds.RAMEN_BAR, "item-gyoza"))
        finishLoading()
        val reviewEvents = collectEvents(f.review.events)

        f.review.checkout()
        runCurrent()
        assertEquals(listOf<CartReviewEvent>(CartReviewEvent.OpenPayment), reviewEvents)

        val cartTotal = assertNotNull(f.review.state.value.cart).total
        val sheet = payment(f)
        assertEquals(cartTotal, sheet.state.value.total)
        assertEquals("Pay ${cartTotal.formatted()}", sheet.state.value.payButtonLabel)

        sheet.pay()
        finishCharge()
        assertEquals(cartTotal, f.orderList().first().total, "The order is charged Cart Review's total")
    }

    @Test
    fun sheetTotalFollowsCartReviewWhenTheCartChanges() = runTest(mainDispatcher) {
        val f = fixture()
        f.fillTakeoutCart()
        val sheet = payment(f)
        assertEquals(f.review.state.value.cart?.total, sheet.state.value.total)

        f.review.increase(f.cart.cart.value!!.lines.single().lineId)
        runCurrent()
        assertEquals(Money.yen(2400), f.review.state.value.cart?.total)
        assertEquals(f.review.state.value.cart?.total, sheet.state.value.total)
    }

    // Scenario 3: double submit

    @Test
    fun doubleTapCreatesExactlyOneOrderAndNumber() = runTest(mainDispatcher) {
        val f = fixture()
        f.fillTakeoutCart()
        val sheet = payment(f)
        val events = collectEvents(sheet.events)
        val before = f.orderList()

        sheet.pay()
        sheet.pay()
        runCurrent()
        sheet.pay()
        finishCharge()
        sheet.pay()
        finishCharge()

        val created = f.orderList() - before.toSet()
        assertEquals(1, created.size)
        assertEquals(1, events.size)
        assertEquals(1, f.orderList().count { it.number == created.single().number })
    }

    @Test
    fun doubleApproveCreatesExactlyOneOrder() = runTest(mainDispatcher) {
        val f = fixture()
        f.fillTakeoutCart()
        val sheet = payment(f)
        val before = f.orderList().size

        sheet.selectMethod(SeedPaymentIds.PAYPAY)
        sheet.pay()
        sheet.approve()
        sheet.approve()
        finishCharge()

        assertEquals(before + 1, f.orderList().size)
    }

    @Test
    fun serviceReturnsTheSameOrderForARepeatedCheckout() = runTest(mainDispatcher) {
        val f = fixture()
        f.fillTakeoutCart()
        val request = PaymentRequest.PayNow("checkout-test", f.cart.cart.value!!, "Test Ramen Bar")
        val before = f.orderList().size

        val first = async { f.service.pay(request, SeedPaymentIds.TEST_CARD) }
        val second = async { f.service.pay(request, SeedPaymentIds.TEST_CARD) }
        finishCharge()

        val a = first.await() as PaymentResult.Paid
        val b = second.await() as PaymentResult.Paid
        assertEquals(a.order, b.order)
        assertEquals(before + 1, f.orderList().size)
    }

    // Failure and cancel: each method reaches one (PAY-02's exits are in PaymentRetryTest)

    @Test
    fun declineCardChargesNothingAndKeepsTheCart() = runTest(mainDispatcher) {
        val f = fixture()
        f.fillTakeoutCart()
        val cart = f.cart.cart.value
        val sheet = payment(f)
        val before = f.orderList()

        sheet.selectMethod(SeedPaymentIds.DECLINE_CARD)
        sheet.pay()
        finishCharge()

        assertEquals(PaymentPhase.DECLINED, sheet.state.value.phase)
        assertNotNull(sheet.state.value.errorMessage)
        assertTrue(sheet.state.value.canPay, "The customer can try again")
        assertEquals(before, f.orderList())
        assertEquals(cart, f.cart.cart.value)

        // Another method then goes through on the same sheet; the error stays until Retry.
        sheet.selectMethod(SeedPaymentIds.TEST_CARD)
        runCurrent()
        assertTrue(sheet.state.value.isDeclined)
        sheet.pay()
        finishCharge()
        assertEquals(PaymentPhase.PAID, sheet.state.value.phase)
    }

    @Test
    fun forcedDeclineFailsEveryMethod() = runTest(mainDispatcher) {
        val f = fixture()
        f.fillTakeoutCart()
        f.config.forcePaymentDecline.value = true
        val sheet = payment(f)

        sheet.pay()
        finishCharge()
        assertEquals(PaymentPhase.DECLINED, sheet.state.value.phase)

        sheet.selectMethod(SeedPaymentIds.GOOGLE_PAY)
        sheet.pay()
        sheet.approve()
        finishCharge()
        assertEquals(PaymentPhase.DECLINED, sheet.state.value.phase)
    }

    @Test
    fun redirectAndWalletMethodsCanBeApprovedOrCancelled() = runTest(mainDispatcher) {
        val methods = listOf(
            ClientPlatform.ANDROID to SeedPaymentIds.PAYPAY,
            ClientPlatform.ANDROID to SeedPaymentIds.RAKUTEN_PAY,
            ClientPlatform.ANDROID to SeedPaymentIds.GOOGLE_PAY,
            ClientPlatform.IOS to SeedPaymentIds.APPLE_PAY,
        )
        for ((platform, methodId) in methods) {
            val f = fixture()
            f.fillTakeoutCart()
            val sheet = payment(f, platform = platform)
            val before = f.orderList()

            sheet.selectMethod(methodId)
            sheet.pay()
            runCurrent()
            assertEquals(methodId, sheet.state.value.approvingMethod?.id, methodId)
            sheet.cancelApproval()
            runCurrent()
            assertEquals(PaymentPhase.READY, sheet.state.value.phase, methodId)
            assertEquals(before, f.orderList(), "$methodId: cancel charges nothing")
            assertNotNull(f.cart.cart.value, "$methodId: cancel keeps the cart")

            sheet.pay()
            sheet.approve()
            finishCharge()
            assertEquals(PaymentPhase.PAID, sheet.state.value.phase, methodId)
            assertEquals(methodId, f.orderList().first().paymentMethodId)
        }
    }

    // Scenario 4: Rakuten Pay

    @Test
    fun rakutenPayApprovalPaysTheOrder() = runTest(mainDispatcher) {
        val f = fixture()
        f.cart.addLine(FulfillmentMode.IN_STORE, f.line(SeedStoreIds.HAIR_SALON, "item-quick-cut"))
        val sheet = payment(f)

        sheet.selectMethod(SeedPaymentIds.RAKUTEN_PAY)
        sheet.pay()
        runCurrent()
        assertEquals(PaymentType.RAKUTEN_PAY, sheet.state.value.approvingMethod?.type)
        assertEquals(0, f.orderList().count { it.paymentMethodId == SeedPaymentIds.RAKUTEN_PAY && it.storeName == "Test Hair & Nail Salon" })

        sheet.approve()
        finishCharge()
        assertEquals(OrderStatus.QUEUED, f.orderList().first().status)
    }

    // Methods per platform

    @Test
    fun walletsAreOfferedOnTheirOwnPlatformOnly() = runTest(mainDispatcher) {
        val f = fixture()
        f.fillTakeoutCart()
        val android = payment(f, platform = ClientPlatform.ANDROID).state.value.methods.map { it.type }
        val ios = payment(f, platform = ClientPlatform.IOS).state.value.methods.map { it.type }

        assertTrue(PaymentType.GOOGLE_PAY in android && PaymentType.APPLE_PAY !in android)
        assertTrue(PaymentType.APPLE_PAY in ios && PaymentType.GOOGLE_PAY !in ios)
        listOf(PaymentType.CARD, PaymentType.PAYPAY, PaymentType.RAKUTEN_PAY).forEach {
            assertTrue(it in android && it in ios, "$it is offered on both")
        }
    }

    @Test
    fun otherPlatformsWalletCantBeSelected() = runTest(mainDispatcher) {
        val f = fixture()
        f.fillTakeoutCart()
        val sheet = payment(f, platform = ClientPlatform.ANDROID)

        sheet.selectMethod(SeedPaymentIds.APPLE_PAY)
        runCurrent()
        assertEquals(SeedPaymentIds.TEST_CARD, sheet.state.value.selectedMethod?.id)
    }

    // Scenario 2: Eat-In, paid after the seat check

    @Test
    fun eatInChargesTheSavedMethodAndQueuesTheDraft() = runTest(mainDispatcher) {
        val f = fixture()
        assertTrue(f.orders.checkIn(draftId, CheckIn(CheckInMethod.GEOFENCE)))
        val draft = f.orderList().first { it.id == draftId }
        val confirm = payment(f, target = PaymentTarget.Draft(draftId))
        val events = collectEvents(confirm.events)

        assertEquals("Test Ramen Bar · Eat-In · 2 items", confirm.state.value.summary)
        assertEquals(Money.yen(1550), confirm.state.value.total)
        assertEquals(SeedPaymentIds.TEST_CARD, confirm.state.value.selectedMethod?.id, "The sheet starts on the saved default")

        confirm.pay()
        finishCharge()

        val paid = f.orderList().first { it.id == draftId }
        assertEquals(OrderStatus.QUEUED, paid.status)
        assertEquals(draft.number, paid.number)
        assertEquals(SeedPaymentIds.TEST_CARD, paid.paymentMethodId)
        assertEquals(draft.checkIn, paid.checkIn, "The table or check-in method stays for the ticket")
        assertEquals(listOf<PaymentEvent>(PaymentEvent.Paid(draftId, draft.number)), events)
    }

    @Test
    fun draftThatIsNotCheckedInCantBePaid() = runTest(mainDispatcher) {
        val f = fixture()
        val confirm = payment(f, target = PaymentTarget.Draft(draftId))

        assertFalse(confirm.state.value.hasSomethingToPay)
        assertFalse(confirm.state.value.canPay)
        confirm.pay()
        finishCharge()
        assertEquals(OrderStatus.DRAFT, f.orderList().first { it.id == draftId }.status)
    }

    @Test
    fun eatInDoubleTapQueuesOnce() = runTest(mainDispatcher) {
        val f = fixture()
        f.orders.checkIn(draftId, CheckIn(CheckInMethod.TABLE_QR, "12"))
        val confirm = payment(f, target = PaymentTarget.Draft(draftId))
        val events = collectEvents(confirm.events)

        confirm.pay()
        confirm.pay()
        finishCharge()

        assertEquals(1, events.size)
        assertEquals(2, f.orderList().first { it.id == draftId }.history.size, "DRAFT then QUEUED, once")
    }

    // Demo add-card form

    @Test
    fun addCardTakesPublishedTestNumbersOnly() = runTest(mainDispatcher) {
        val f = fixture()
        f.fillTakeoutCart()
        val sheet = payment(f)

        assertEquals(CardFormError.NUMBER, sheet.addTestCard("4242", "12/34", "123"))
        assertEquals(CardFormError.EXPIRY, sheet.addTestCard("4242 4242 4242 4242", "13/34", "123"))
        assertEquals(CardFormError.CVC, sheet.addTestCard("4242 4242 4242 4242", "12/34", "12"))
        assertEquals(CardFormError.NOT_A_TEST_CARD, sheet.addTestCard("4111 1111 1111 1112", "12/34", "123"))

        assertNull(sheet.addTestCard("5555 5555 5555 4444", "12/34", "123"))
        runCurrent()
        val added = assertNotNull(sheet.state.value.selectedMethod)
        assertEquals("Test Mastercard •••• 4444", added.label)
        assertTrue(sheet.state.value.methods.contains(added))
    }

    @Test
    fun addedDeclineTestCardIsDeclined() = runTest(mainDispatcher) {
        val f = fixture()
        f.fillTakeoutCart()
        val sheet = payment(f)

        assertNull(sheet.addTestCard("4000 0000 0000 0002", "12/34", "123"))
        sheet.pay()
        finishCharge()
        assertEquals(PaymentPhase.DECLINED, sheet.state.value.phase)
    }

    @Test
    fun addedCardKeepsOnlyItsLabel() = runTest(mainDispatcher) {
        val f = fixture()
        val method = assertNotNull(f.payments.addTestCard("4242424242424242"))
        assertFalse("4242424242424242" in method.toString(), "The full number is never kept")
        assertFalse("424242" in method.id)
    }

    @Test
    fun closingTheSheetClearsAnErrorButNeverAChargeInFlight() = runTest(mainDispatcher) {
        val f = fixture()
        f.fillTakeoutCart()
        val sheet = payment(f)

        sheet.selectMethod(SeedPaymentIds.DECLINE_CARD)
        sheet.pay()
        finishCharge()
        sheet.reset()
        runCurrent()
        assertEquals(PaymentPhase.READY, sheet.state.value.phase)

        sheet.selectMethod(SeedPaymentIds.TEST_CARD)
        sheet.pay()
        runCurrent()
        sheet.reset()
        runCurrent()
        assertEquals(PaymentPhase.PROCESSING, sheet.state.value.phase)
        finishCharge()
        assertEquals(PaymentPhase.PAID, sheet.state.value.phase)
    }
}
