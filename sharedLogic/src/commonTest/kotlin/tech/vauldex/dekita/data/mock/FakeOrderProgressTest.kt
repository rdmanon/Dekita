package tech.vauldex.dekita.data.mock

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import tech.vauldex.dekita.data.dataOrNull
import tech.vauldex.dekita.domain.Cart
import tech.vauldex.dekita.domain.CartLine
import tech.vauldex.dekita.domain.CheckIn
import tech.vauldex.dekita.domain.CheckInMethod
import tech.vauldex.dekita.domain.CompletionReason
import tech.vauldex.dekita.domain.FulfillmentMode
import tech.vauldex.dekita.domain.Order
import tech.vauldex.dekita.domain.OrderStatus
import tech.vauldex.dekita.domain.TransitionResult

/** ORDER-01: mock payment, the mock kitchen's timer, auto-complete and the debug moves. */
@OptIn(ExperimentalCoroutinesApi::class)
class FakeOrderProgressTest {

    private val fixedClock = object : Clock {
        override fun now() = Instant.fromEpochSeconds(1_800_000_000)
    }

    private fun TestScope.loadedRepository(config: MockConfig = MockConfig()): FakeOrderRepository {
        val repository = FakeOrderRepository(backgroundScope, config, fixedClock)
        advanceTimeBy(MockConfig.DEFAULT_LOADING_DELAY)
        runCurrent()
        return repository
    }

    /** Moves virtual time and runs what is due at the end, so a timer due exactly then has fired. */
    private fun TestScope.wait(duration: Duration) {
        advanceTimeBy(duration)
        runCurrent()
    }

    private fun FakeOrderRepository.order(id: String): Order = orders.value.dataOrNull().orEmpty().first { it.id == id }

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
    fun payNowPlacesAPaidOrderWithTheNextNumberAndTheDefaultMethod() = runTest {
        val repository = loadedRepository()
        val changes = mutableListOf<Order>()
        backgroundScope.launch { repository.statusChanges.collect { changes += it } }
        runCurrent()

        val pickup = repository.placeOrder(pickupCart, "Test Mart")
        val inStore = repository.placeOrder(inStoreOnlyCart, "Sample Massage Studio")
        runCurrent()

        assertEquals(OrderStatus.PAID, pickup.status)
        assertEquals(45, pickup.number, "The seed's highest number is 44")
        assertEquals(SeedPaymentIds.TEST_CARD, pickup.paymentMethodId)
        assertEquals(OrderStatus.QUEUED, inStore.status, "In-store orders join the queue when paid")
        assertEquals(46, inStore.number)
        assertEquals(listOf(OrderStatus.DRAFT, OrderStatus.PAID), pickup.history.map { it.status })
        assertEquals(pickup, repository.order(pickup.id))
        assertEquals(listOf(pickup.id, inStore.id), changes.map { it.id })
    }

    @Test
    fun anEatInDraftIsPaidOnlyAfterCheckIn() = runTest {
        val repository = loadedRepository()

        assertNull(repository.payAtCheckIn("order-44"), "Not checked in yet")
        assertNull(repository.payAtCheckIn("order-42"), "Already paid")
        assertNull(repository.payAtCheckIn("no-such-order"))

        repository.checkIn("order-44", CheckIn(CheckInMethod.TABLE_QR, tableNumber = "5"))
        val paid = repository.payAtCheckIn("order-44")

        assertEquals(OrderStatus.QUEUED, paid?.status)
        assertEquals(SeedPaymentIds.TEST_CARD, paid?.paymentMethodId)
        assertEquals("5", paid?.checkIn?.tableNumber, "The table from check-in stays on the order")
        assertNull(repository.payAtCheckIn("order-44"), "A second tap pays nothing")
    }

    @Test
    fun aLiveOrderMovesOnEveryStepAndAutoCompletesAfterReady() = runTest {
        val repository = loadedRepository()
        val id = repository.placeOrder(pickupCart, "Test Mart").id

        wait(MockConfig.ORDER_STEP_INTERVAL)
        assertEquals(OrderStatus.PREPARING, repository.order(id).status)
        wait(MockConfig.ORDER_STEP_INTERVAL)
        assertEquals(OrderStatus.READY_FOR_PICKUP, repository.order(id).status)

        wait(MockConfig.AUTO_COMPLETE_AFTER - 1.seconds)
        assertEquals(OrderStatus.READY_FOR_PICKUP, repository.order(id).status, "Still waiting for Confirm")
        wait(1.seconds)
        assertEquals(OrderStatus.COMPLETED, repository.order(id).status)
        assertEquals(CompletionReason.AUTO_COMPLETED, repository.order(id).completionReason)
    }

    @Test
    fun anInStoreOrderEndsAtServed() = runTest {
        val repository = loadedRepository()
        val id = repository.placeOrder(inStoreOnlyCart, "Sample Massage Studio").id

        wait(MockConfig.ORDER_STEP_INTERVAL * 2)

        assertEquals(OrderStatus.SERVED, repository.order(id).status)
    }

    @Test
    fun confirmBeforeAutoCompleteClosesTheOrderOnce() = runTest {
        val repository = loadedRepository()
        val id = repository.placeOrder(pickupCart, "Test Mart").id
        wait(MockConfig.ORDER_STEP_INTERVAL * 2)

        val confirmed = repository.transition(id, OrderStatus.COMPLETED, CompletionReason.CUSTOMER_CONFIRMED)
        wait(MockConfig.AUTO_COMPLETE_AFTER * 2)

        assertTrue(confirmed is TransitionResult.Accepted)
        assertEquals(CompletionReason.CUSTOMER_CONFIRMED, repository.order(id).completionReason)
        assertEquals(1, repository.order(id).history.count { it.status == OrderStatus.COMPLETED })
    }

    @Test
    fun liveOrdersOffHoldsTheStatusUntilTurnedBackOn() = runTest {
        val config = MockConfig()
        val repository = loadedRepository(config)
        config.liveOrders.value = false
        val id = repository.placeOrder(pickupCart, "Test Mart").id

        wait(5.minutes)
        assertEquals(OrderStatus.PAID, repository.order(id).status)

        config.liveOrders.value = true
        runCurrent()
        assertEquals(OrderStatus.PREPARING, repository.order(id).status)
    }

    @Test
    fun aDebugMoveDuringAWaitDoesNotSkipTheNextStep() = runTest {
        val repository = loadedRepository()
        val id = repository.placeOrder(pickupCart, "Test Mart").id

        wait(10.seconds)
        assertTrue(repository.advance(id))
        assertEquals(OrderStatus.PREPARING, repository.order(id).status)

        // The timer notices the move when its wait ends at 15 s, then waits a full step from there.
        wait(10.seconds)
        assertEquals(OrderStatus.PREPARING, repository.order(id).status)
        wait(10.seconds)
        assertEquals(OrderStatus.READY_FOR_PICKUP, repository.order(id).status)
    }

    @Test
    fun seedOrdersStayWhereTheyAre() = runTest {
        val repository = loadedRepository()

        wait(10.minutes)

        assertEquals(OrderStatus.PREPARING, repository.order("order-42").status)
        assertEquals(OrderStatus.READY_FOR_PICKUP, repository.order("order-43").status)
        assertEquals(OrderStatus.DRAFT, repository.order("order-44").status)
    }

    @Test
    fun debugAdvanceStopsAtServedAndAutoCompleteNeedsIt() = runTest {
        val repository = loadedRepository()

        assertFalse(repository.autoComplete("order-42"), "Still cooking")
        assertFalse(repository.advance("order-43"), "Ready orders close by Confirm or auto-complete only")
        assertFalse(repository.advance("order-44"), "A draft isn't paid")
        assertTrue(repository.advance("order-42"))
        assertEquals(OrderStatus.SERVED, repository.order("order-42").status)
        assertFalse(repository.advance("order-42"))

        assertTrue(repository.autoComplete("order-42"))
        assertEquals(CompletionReason.AUTO_COMPLETED, repository.order("order-42").completionReason)
        assertFalse(repository.autoComplete("order-42"), "Already completed")
    }
}
