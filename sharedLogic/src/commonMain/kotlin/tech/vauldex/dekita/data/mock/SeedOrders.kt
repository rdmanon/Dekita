package tech.vauldex.dekita.data.mock

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import tech.vauldex.dekita.domain.CartLine
import tech.vauldex.dekita.domain.CompletionReason
import tech.vauldex.dekita.domain.FulfillmentMode
import tech.vauldex.dekita.domain.Order
import tech.vauldex.dekita.domain.OrderStatus
import tech.vauldex.dekita.domain.OrderStatusMachine
import tech.vauldex.dekita.domain.TransitionResult
import tech.vauldex.dekita.domain.defaultMode

/** A status change in a seed order, timed relative to the order's creation. */
private class Step(val status: OrderStatus, val after: Duration, val reason: CompletionReason? = null)

/** Builds a seed order through [OrderStatusMachine], so seed data can only hold valid states. */
private fun seedOrder(
    id: String,
    number: Int,
    storeId: String,
    mode: FulfillmentMode,
    lines: List<CartLine>,
    paymentMethodId: String,
    createdAt: Instant,
    steps: List<Step>,
): Order {
    val store = seedStores.first { it.id == storeId }
    val draft = Order.draft(id, number, storeId, store.name, mode, lines, paymentMethodId, createdAt)
    return steps.fold(draft) { order, step ->
        when (val result = OrderStatusMachine.transition(order, step.status, createdAt + step.after, step.reason)) {
            is TransitionResult.Accepted -> result.order
            is TransitionResult.Rejected -> error("Invalid seed order $id: ${result.reason}")
        }
    }
}

internal fun seedOrders(now: Instant): List<Order> {
    val shoyu = seedItem(SeedStoreIds.RAMEN_BAR, "item-shoyu-ramen")
    val curry = seedItem(SeedStoreIds.CURRY_STAND, "item-beef-curry")
    val massage = seedItem(SeedStoreIds.MASSAGE_STUDIO, "item-shoulder-30")
    return listOf(
        // Active in-store order, matches the design's ticket: #42, 2 items, ¥1,550.
        seedOrder(
            id = "order-42", number = 42, storeId = SeedStoreIds.RAMEN_BAR, mode = FulfillmentMode.IN_STORE,
            lines = listOf(
                CartLine("line-42-1", shoyu, listOf(shoyu.seedOption("ramen-spice-medium"), shoyu.seedOption("topping-egg"))),
                CartLine("line-42-2", seedItem(SeedStoreIds.RAMEN_BAR, "item-gyoza")),
            ),
            paymentMethodId = SeedPaymentIds.TEST_CARD,
            createdAt = now - 12.minutes,
            steps = listOf(Step(OrderStatus.QUEUED, 2.minutes), Step(OrderStatus.PREPARING, 6.minutes)),
        ),
        // Active pickup order waiting at the counter.
        seedOrder(
            id = "order-43", number = 43, storeId = SeedStoreIds.MART, mode = FulfillmentMode.PICKUP,
            lines = listOf(
                CartLine("line-43-1", seedItem(SeedStoreIds.MART, "item-onigiri-salmon"), quantity = 2),
                CartLine("line-43-2", seedItem(SeedStoreIds.MART, "item-green-tea")),
            ),
            paymentMethodId = SeedPaymentIds.PAYPAY,
            createdAt = now - 25.minutes,
            steps = listOf(
                Step(OrderStatus.PAID, 30.seconds),
                Step(OrderStatus.PREPARING, 5.minutes),
                Step(OrderStatus.READY_FOR_PICKUP, 20.minutes),
            ),
        ),
        // History: completed orders, with both completion reasons.
        seedOrder(
            id = "order-37", number = 37, storeId = SeedStoreIds.CURRY_STAND, mode = FulfillmentMode.PICKUP,
            lines = listOf(CartLine("line-37-1", curry, listOf(curry.seedOption("curry-spice-hot")))),
            paymentMethodId = SeedPaymentIds.TEST_CARD,
            createdAt = now - 1.days,
            steps = listOf(
                Step(OrderStatus.PAID, 20.seconds),
                Step(OrderStatus.PREPARING, 2.minutes),
                Step(OrderStatus.READY_FOR_PICKUP, 9.minutes),
                Step(OrderStatus.COMPLETED, 15.minutes, CompletionReason.CUSTOMER_CONFIRMED),
            ),
        ),
        seedOrder(
            id = "order-29", number = 29, storeId = SeedStoreIds.RAMEN_BAR, mode = FulfillmentMode.IN_STORE,
            lines = listOf(CartLine("line-29-1", shoyu, listOf(shoyu.seedOption("ramen-spice-mild")))),
            paymentMethodId = SeedPaymentIds.TEST_CARD,
            createdAt = now - 3.days,
            steps = listOf(
                Step(OrderStatus.QUEUED, 1.minutes),
                Step(OrderStatus.PREPARING, 4.minutes),
                Step(OrderStatus.SERVED, 11.minutes),
                Step(OrderStatus.COMPLETED, 41.minutes, CompletionReason.AUTO_COMPLETED),
            ),
        ),
        seedOrder(
            id = "order-18", number = 18, storeId = SeedStoreIds.MASSAGE_STUDIO, mode = FulfillmentMode.IN_STORE,
            lines = listOf(CartLine("line-18-1", massage, listOf(massage.seedOption("pressure-medium")))),
            paymentMethodId = SeedPaymentIds.RAKUTEN_PAY,
            createdAt = now - 6.days,
            steps = listOf(
                Step(OrderStatus.QUEUED, 1.minutes),
                Step(OrderStatus.PREPARING, 20.minutes),
                Step(OrderStatus.SERVED, 50.minutes),
                Step(OrderStatus.COMPLETED, 52.minutes, CompletionReason.CUSTOMER_CONFIRMED),
            ),
        ),
    )
}

/** The design's cart: Shoyu Ramen (Mild) and Gyoza from Test Ramen Bar, which offers both modes. */
internal fun sampleCartLines(newLineId: () -> String): List<CartLine> {
    val shoyu = seedItem(SeedStoreIds.RAMEN_BAR, "item-shoyu-ramen")
    return listOf(
        CartLine(newLineId(), shoyu, listOf(shoyu.seedOption("ramen-spice-mild"))),
        CartLine(newLineId(), seedItem(SeedStoreIds.RAMEN_BAR, "item-gyoza")),
    )
}

internal fun sampleCartMode(preferred: FulfillmentMode?): FulfillmentMode =
    seedStores.first { it.id == SeedStoreIds.RAMEN_BAR }.businessType.defaultMode(preferred)
