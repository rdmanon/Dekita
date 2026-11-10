package tech.vauldex.dekita.domain

import kotlin.time.Instant
import tech.vauldex.dekita.domain.FulfillmentMode.IN_STORE
import tech.vauldex.dekita.domain.FulfillmentMode.PICKUP
import tech.vauldex.dekita.domain.OrderStatus.COMPLETED
import tech.vauldex.dekita.domain.OrderStatus.DRAFT
import tech.vauldex.dekita.domain.OrderStatus.PAID
import tech.vauldex.dekita.domain.OrderStatus.PREPARING
import tech.vauldex.dekita.domain.OrderStatus.QUEUED
import tech.vauldex.dekita.domain.OrderStatus.READY_FOR_PICKUP
import tech.vauldex.dekita.domain.OrderStatus.SERVED

sealed interface TransitionResult {
    /** The order after the attempt: the updated order, or the unchanged one when rejected. */
    val order: Order

    data class Accepted(override val order: Order) : TransitionResult

    data class Rejected(override val order: Order, val to: OrderStatus, val reason: String) : TransitionResult
}

/**
 * The only allowed order status changes. Each mode moves one step at a time along its own path,
 * and COMPLETED is reached by the customer's [Confirm] or by auto-complete.
 */
object OrderStatusMachine {

    private val paths: Map<FulfillmentMode, List<OrderStatus>> = mapOf(
        PICKUP to listOf(DRAFT, PAID, PREPARING, READY_FOR_PICKUP, COMPLETED),
        IN_STORE to listOf(DRAFT, QUEUED, PREPARING, SERVED, COMPLETED),
    )

    fun canTransition(from: OrderStatus, to: OrderStatus, mode: FulfillmentMode): Boolean {
        val path = paths.getValue(mode)
        val index = path.indexOf(from)
        return index >= 0 && path.getOrNull(index + 1) == to
    }

    fun nextStatus(from: OrderStatus, mode: FulfillmentMode): OrderStatus? {
        val path = paths.getValue(mode)
        val index = path.indexOf(from)
        return if (index >= 0) path.getOrNull(index + 1) else null
    }

    fun transition(
        order: Order,
        to: OrderStatus,
        at: Instant,
        reason: CompletionReason? = null,
    ): TransitionResult {
        if (!canTransition(order.status, to, order.mode)) {
            return TransitionResult.Rejected(order, to, "${order.status} to $to is not allowed for ${order.mode} orders")
        }
        if (to == COMPLETED && reason == null) {
            return TransitionResult.Rejected(order, to, "COMPLETED needs a completion reason")
        }
        if (to != COMPLETED && reason != null) {
            return TransitionResult.Rejected(order, to, "A completion reason only applies to COMPLETED")
        }
        return TransitionResult.Accepted(
            order.copy(
                status = to,
                history = order.history + StatusChange(to, at),
                completionReason = reason,
            ),
        )
    }
}
