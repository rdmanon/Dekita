package tech.vauldex.dekita.domain

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import tech.vauldex.dekita.domain.FulfillmentMode.IN_STORE
import tech.vauldex.dekita.domain.FulfillmentMode.PICKUP
import tech.vauldex.dekita.domain.OrderStatus.COMPLETED
import tech.vauldex.dekita.domain.OrderStatus.DRAFT
import tech.vauldex.dekita.domain.OrderStatus.PAID
import tech.vauldex.dekita.domain.OrderStatus.PREPARING
import tech.vauldex.dekita.domain.OrderStatus.QUEUED
import tech.vauldex.dekita.domain.OrderStatus.READY_FOR_PICKUP
import tech.vauldex.dekita.domain.OrderStatus.SERVED

class OrderStatusMachineTest {

    private val t0 = Instant.fromEpochSeconds(1_800_000_000)

    private val allowed = setOf(
        Triple(DRAFT, PAID, PICKUP),
        Triple(PAID, PREPARING, PICKUP),
        Triple(PREPARING, READY_FOR_PICKUP, PICKUP),
        Triple(READY_FOR_PICKUP, COMPLETED, PICKUP),
        Triple(DRAFT, QUEUED, IN_STORE),
        Triple(QUEUED, PREPARING, IN_STORE),
        Triple(PREPARING, SERVED, IN_STORE),
        Triple(SERVED, COMPLETED, IN_STORE),
    )

    private fun draft(mode: FulfillmentMode) =
        Order.draft("order-1", 1, "store-test", "Test Store", mode, emptyList(), null, t0)

    /** An order already at [status], built directly so every pair can be tested. */
    private fun orderAt(status: OrderStatus, mode: FulfillmentMode) =
        draft(mode).copy(status = status, history = listOf(StatusChange(status, t0)))

    private fun Order.moveTo(to: OrderStatus, reason: CompletionReason? = null): Order {
        val result = OrderStatusMachine.transition(this, to, lastUpdatedAt + 1.minutes, reason)
        return assertIs<TransitionResult.Accepted>(result, "Expected $status to $to to be accepted").order
    }

    @Test
    fun pickupOrderFollowsThePaidPath() {
        val order = draft(PICKUP)
            .moveTo(PAID)
            .moveTo(PREPARING)
            .moveTo(READY_FOR_PICKUP)
            .moveTo(COMPLETED, CompletionReason.CUSTOMER_CONFIRMED)

        assertEquals(COMPLETED, order.status)
        assertEquals(CompletionReason.CUSTOMER_CONFIRMED, order.completionReason)
        assertEquals(listOf(DRAFT, PAID, PREPARING, READY_FOR_PICKUP, COMPLETED), order.history.map { it.status })
    }

    @Test
    fun inStoreOrderFollowsTheQueuedPath() {
        val order = draft(IN_STORE)
            .moveTo(QUEUED)
            .moveTo(PREPARING)
            .moveTo(SERVED)
            .moveTo(COMPLETED, CompletionReason.AUTO_COMPLETED)

        assertEquals(COMPLETED, order.status)
        assertEquals(CompletionReason.AUTO_COMPLETED, order.completionReason)
        assertEquals(listOf(DRAFT, QUEUED, PREPARING, SERVED, COMPLETED), order.history.map { it.status })
    }

    @Test
    fun onlyListedTransitionsAreAllowed() {
        for (mode in FulfillmentMode.entries) {
            for (from in OrderStatus.entries) {
                for (to in OrderStatus.entries) {
                    val expected = Triple(from, to, mode) in allowed
                    assertEquals(expected, OrderStatusMachine.canTransition(from, to, mode), "$mode: $from to $to")

                    val reason = if (to == COMPLETED) CompletionReason.CUSTOMER_CONFIRMED else null
                    val result = OrderStatusMachine.transition(orderAt(from, mode), to, t0, reason)
                    if (expected) {
                        assertIs<TransitionResult.Accepted>(result, "$mode: $from to $to")
                    } else {
                        assertIs<TransitionResult.Rejected>(result, "$mode: $from to $to")
                    }
                }
            }
        }
    }

    @Test
    fun draftCannotJumpStraightToCompleted() {
        val order = draft(PICKUP)

        val result = OrderStatusMachine.transition(order, COMPLETED, t0 + 1.minutes, CompletionReason.CUSTOMER_CONFIRMED)

        val rejected = assertIs<TransitionResult.Rejected>(result)
        assertEquals(COMPLETED, rejected.to)
        assertSame(order, rejected.order)
        assertEquals(DRAFT, rejected.order.status)
        assertEquals(listOf(DRAFT), rejected.order.history.map { it.status })
    }

    @Test
    fun completingNeedsAReason() {
        val ready = draft(PICKUP).moveTo(PAID).moveTo(PREPARING).moveTo(READY_FOR_PICKUP)

        val result = OrderStatusMachine.transition(ready, COMPLETED, t0 + 10.minutes, reason = null)

        assertEquals(READY_FOR_PICKUP, assertIs<TransitionResult.Rejected>(result).order.status)
    }

    @Test
    fun reasonIsOnlyAcceptedForCompleted() {
        val result = OrderStatusMachine.transition(draft(PICKUP), PAID, t0, CompletionReason.AUTO_COMPLETED)

        assertIs<TransitionResult.Rejected>(result)
    }

    @Test
    fun readyStateDependsOnTheMode() {
        val pickupPreparing = draft(PICKUP).moveTo(PAID).moveTo(PREPARING)
        val inStorePreparing = draft(IN_STORE).moveTo(QUEUED).moveTo(PREPARING)

        assertIs<TransitionResult.Rejected>(OrderStatusMachine.transition(pickupPreparing, SERVED, t0))
        assertIs<TransitionResult.Rejected>(OrderStatusMachine.transition(inStorePreparing, READY_FOR_PICKUP, t0))
        assertIs<TransitionResult.Rejected>(OrderStatusMachine.transition(draft(PICKUP), QUEUED, t0))
        assertIs<TransitionResult.Rejected>(OrderStatusMachine.transition(draft(IN_STORE), PAID, t0))
    }

    @Test
    fun completedIsFinal() {
        val done = draft(IN_STORE).moveTo(QUEUED).moveTo(PREPARING).moveTo(SERVED)
            .moveTo(COMPLETED, CompletionReason.CUSTOMER_CONFIRMED)

        assertNull(OrderStatusMachine.nextStatus(done.status, done.mode))
        for (to in OrderStatus.entries) {
            assertIs<TransitionResult.Rejected>(OrderStatusMachine.transition(done, to, t0, CompletionReason.AUTO_COMPLETED))
        }
    }

    @Test
    fun historyRecordsTheTimeOfEachStep() {
        val paidAt = t0 + 2.minutes
        val result = OrderStatusMachine.transition(draft(PICKUP), PAID, paidAt)

        val order = assertIs<TransitionResult.Accepted>(result).order
        assertEquals(listOf(t0, paidAt), order.history.map { it.at })
    }
}
