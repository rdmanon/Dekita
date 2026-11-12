package tech.vauldex.dekita.presentation.orders

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import tech.vauldex.dekita.data.mock.FakeAuthRepository
import tech.vauldex.dekita.data.mock.FakeOrderRepository
import tech.vauldex.dekita.data.mock.MockConfig
import tech.vauldex.dekita.data.mock.SeedStoreIds
import tech.vauldex.dekita.data.mock.seedItem
import tech.vauldex.dekita.domain.Cart
import tech.vauldex.dekita.domain.CartLine
import tech.vauldex.dekita.domain.CheckIn
import tech.vauldex.dekita.domain.CheckInMethod
import tech.vauldex.dekita.domain.FulfillmentMode
import tech.vauldex.dekita.domain.SsoProvider
import tech.vauldex.dekita.presentation.ORDER_PLACED_MESSAGE

/** ORDER-01: which status changes become system notifications, and only while signed in. */
@OptIn(ExperimentalCoroutinesApi::class)
class OrderNoticesTest {

    private val fixedClock = object : Clock {
        override fun now() = Instant.fromEpochSeconds(1_800_000_000)
    }

    private class Fixture(val orders: FakeOrderRepository, val auth: FakeAuthRepository) {
        val notifications = OrderNotifications(orders, auth)
        val notices = mutableListOf<OrderNotice>()
    }

    /** Loaded orders, with every notice collected from now on. */
    private fun TestScope.fixture(): Fixture {
        val fixture = Fixture(FakeOrderRepository(backgroundScope, MockConfig(), fixedClock), FakeAuthRepository(MockConfig()))
        backgroundScope.launch { fixture.notifications.notices.collect { fixture.notices += it } }
        advanceTimeBy(MockConfig.DEFAULT_LOADING_DELAY)
        runCurrent()
        return fixture
    }

    private val pickupCart = Cart(
        storeId = SeedStoreIds.MART,
        mode = FulfillmentMode.PICKUP,
        lines = listOf(CartLine("line-tea", seedItem(SeedStoreIds.MART, "item-green-tea"))),
    )

    private val inStoreOnlyCart = Cart(
        storeId = SeedStoreIds.MASSAGE_STUDIO,
        mode = FulfillmentMode.IN_STORE,
        lines = listOf(CartLine("line-massage", seedItem(SeedStoreIds.MASSAGE_STUDIO, "item-shoulder-30"))),
    )

    @Test
    fun pickupGetsTheComeToTheStoreNoticeThenTheReadyNotice() = runTest {
        val f = fixture()
        f.auth.signInWith(SsoProvider.APPLE)
        runCurrent()

        val order = f.orders.placeOrder(pickupCart, "Test Mart")
        f.orders.advance(order.id) // Cooking: no notice
        f.orders.advance(order.id) // Ready
        runCurrent()

        val title = "Order #${order.number} · Test Mart"
        assertEquals(
            listOf(
                OrderNotice(order.id, title, ORDER_PLACED_MESSAGE),
                OrderNotice(order.id, title, "Your order #${order.number} is ready for pickup"),
            ),
            f.notices,
        )
    }

    @Test
    fun inStoreOnlyIsToldToComeInButGetsNoReadyNotice() = runTest {
        val f = fixture()
        f.auth.signInWith(SsoProvider.APPLE)
        runCurrent()

        val order = f.orders.placeOrder(inStoreOnlyCart, "Sample Massage Studio")
        f.orders.advance(order.id)
        f.orders.advance(order.id) // Served
        runCurrent()

        assertEquals(listOf(ORDER_PLACED_MESSAGE), f.notices.map { it.body })
    }

    @Test
    fun eatInPaidAtCheckInGetsNoComeToTheStoreNotice() = runTest {
        val f = fixture()
        f.auth.signInWith(SsoProvider.APPLE)
        runCurrent()

        f.orders.checkIn("order-44", CheckIn(CheckInMethod.GEOFENCE))
        f.orders.payAtCheckIn("order-44")
        runCurrent()

        assertTrue(f.notices.isEmpty(), "The customer is already at the store")
    }

    @Test
    fun noNoticesWhileSignedOut() = runTest {
        val f = fixture()

        f.orders.placeOrder(pickupCart, "Test Mart")
        runCurrent()

        assertTrue(f.notices.isEmpty())
    }

    @Test
    fun signedOutFiresOnlyWhenACustomerSignsOut() = runTest {
        val f = fixture()
        var signOuts = 0
        backgroundScope.launch { f.notifications.signedOut.collect { signOuts++ } }
        runCurrent()
        assertEquals(0, signOuts, "Starting as a guest isn't a sign-out")

        f.auth.signInWith(SsoProvider.APPLE)
        runCurrent()
        assertEquals(0, signOuts)

        f.auth.signOut()
        runCurrent()
        assertEquals(1, signOuts)
    }

    @Test
    fun aNotificationTapOpensOnlyAPaidOrderWhileSignedIn() = runTest {
        val f = fixture()
        assertFalse(f.notifications.canOpenTicket("order-42"), "Guests can't open a ticket")

        f.auth.signInWith(SsoProvider.APPLE)
        runCurrent()

        assertTrue(f.notifications.canOpenTicket("order-42"))
        assertFalse(f.notifications.canOpenTicket("order-44"), "A draft has no ticket")
        assertFalse(f.notifications.canOpenTicket("order-999"))
        assertFalse(f.notifications.canOpenTicket(""))
    }
}
