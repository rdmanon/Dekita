package tech.vauldex.dekita.domain

/** The ticket's stepper: what the customer sees, a simpler view of [OrderStatus]. */
enum class OrderStep { RECEIVED, COOKING, READY, SERVED }

/** The stepper for this order's mode: pickup ends at Ready, in-store at Served. */
val Order.steps: List<OrderStep>
    get() = when (mode) {
        FulfillmentMode.PICKUP -> listOf(OrderStep.RECEIVED, OrderStep.COOKING, OrderStep.READY)
        FulfillmentMode.IN_STORE -> listOf(OrderStep.RECEIVED, OrderStep.COOKING, OrderStep.SERVED)
    }

/** The step the order is on. Null for a draft, which has no ticket yet; the last step once completed. */
val Order.currentStep: OrderStep?
    get() = when (status) {
        OrderStatus.DRAFT -> null
        OrderStatus.PAID, OrderStatus.QUEUED -> OrderStep.RECEIVED
        OrderStatus.PREPARING -> OrderStep.COOKING
        OrderStatus.READY_FOR_PICKUP -> OrderStep.READY
        OrderStatus.SERVED -> OrderStep.SERVED
        OrderStatus.COMPLETED -> steps.last()
    }

/** Paid and not closed yet: the order has a ticket the customer follows. */
val Order.isActive: Boolean
    get() = status != OrderStatus.DRAFT && status != OrderStatus.COMPLETED

/** [Confirm] closes the order once it is ready or served. */
val Order.canConfirm: Boolean
    get() = status == OrderStatus.READY_FOR_PICKUP || status == OrderStatus.SERVED
