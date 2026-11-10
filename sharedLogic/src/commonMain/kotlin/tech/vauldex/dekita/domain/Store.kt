package tech.vauldex.dekita.domain

/** How the customer gets the order: eaten or served in the store, or picked up at the counter. */
enum class FulfillmentMode { IN_STORE, PICKUP }

enum class BusinessType(val supportedModes: Set<FulfillmentMode>) {
    IN_STORE_ONLY(setOf(FulfillmentMode.IN_STORE)),
    PICKUP_ONLY(setOf(FulfillmentMode.PICKUP)),
    BOTH(setOf(FulfillmentMode.IN_STORE, FulfillmentMode.PICKUP)),
}

enum class StoreAvailability {
    OPEN,

    /** The store has stopped taking orders for now. */
    PAUSED,

    /** The store is open but at capacity, so new orders wait until the queue drops. */
    FULL,
}

data class QueueStatus(val ordersAhead: Int, val estimatedWaitMinutes: Int) {
    init {
        require(ordersAhead >= 0) { "ordersAhead must not be negative" }
        require(estimatedWaitMinutes >= 0) { "estimatedWaitMinutes must not be negative" }
    }
}

data class Store(
    val id: String,
    val name: String,
    val category: String,
    val businessType: BusinessType,
    val availability: StoreAvailability,
    val walkMinutes: Int,
    val openLabel: String?,
    /** Null when the store is not taking orders, so there is no queue to show. */
    val queue: QueueStatus?,
    /** Picks a local placeholder illustration. The mockup loads no remote images. */
    val artKey: String,
) {
    val isAcceptingOrders: Boolean get() = availability == StoreAvailability.OPEN
}
