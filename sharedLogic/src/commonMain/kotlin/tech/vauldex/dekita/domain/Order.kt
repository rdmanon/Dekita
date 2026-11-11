package tech.vauldex.dekita.domain

import kotlin.time.Instant

enum class OrderStatus {
    DRAFT,

    /** Pickup orders are paid in the app before the store starts on them. */
    PAID,

    /** In-store orders join the store's queue and are paid at check-in. */
    QUEUED,
    PREPARING,
    READY_FOR_PICKUP,
    SERVED,
    COMPLETED,
}

enum class CompletionReason { CUSTOMER_CONFIRMED, AUTO_COMPLETED }

data class StatusChange(val status: OrderStatus, val at: Instant)

/**
 * The constructor is internal so code outside this module can only change [status]
 * through [OrderStatusMachine]. Start a new order with [Order.draft].
 */
@ConsistentCopyVisibility
data class Order internal constructor(
    val id: String,
    val number: Int,
    val storeId: String,
    val storeName: String,
    val mode: FulfillmentMode,
    val lines: List<CartLine>,
    val total: Money,
    val paymentMethodId: String?,
    val status: OrderStatus,
    val history: List<StatusChange>,
    val completionReason: CompletionReason?,
) {
    val itemCount: Int get() = lines.sumOf { it.quantity }

    val createdAt: Instant get() = history.first().at

    val lastUpdatedAt: Instant get() = history.last().at

    companion object {
        fun draft(
            id: String,
            number: Int,
            storeId: String,
            storeName: String,
            mode: FulfillmentMode,
            lines: List<CartLine>,
            paymentMethodId: String?,
            at: Instant,
        ): Order = Order(
            id = id,
            number = number,
            storeId = storeId,
            storeName = storeName,
            mode = mode,
            lines = lines,
            total = lines.fold(Money.ZERO) { total, line -> total + line.lineTotal },
            paymentMethodId = paymentMethodId,
            status = OrderStatus.DRAFT,
            history = listOf(StatusChange(OrderStatus.DRAFT, at)),
            completionReason = null,
        )
    }
}
