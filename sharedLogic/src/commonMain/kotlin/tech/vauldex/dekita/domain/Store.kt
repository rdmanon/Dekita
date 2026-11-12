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

    /** True when the store is busy enough to warn the customer before they order. */
    val isHigh: Boolean get() = ordersAhead >= HIGH_QUEUE_THRESHOLD
}

// shortcut: the PO has not confirmed the threshold (QUEUE-01 open question); 15 matches the seed's busy stores, change it when they answer.
const val HIGH_QUEUE_THRESHOLD = 15

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
    /** Where the store is, for the check-in map. */
    val location: GeoPoint,
    /** Names of the store's menu items, sent with the feed so Discover search can match dishes. */
    val itemNames: List<String> = emptyList(),
    /**
     * SEAT-01: every seat is taken. Only Eat-In cares: the seat check offers Takeout, waiting or leaving
     * instead of cooking. The kitchen can still take orders, so this is separate from [StoreAvailability.FULL].
     */
    val seatsFull: Boolean = false,
) {
    val isAcceptingOrders: Boolean get() = availability == StoreAvailability.OPEN

    fun supports(mode: FulfillmentMode): Boolean = mode in businessType.supportedModes

    /**
     * The one status badge a store card shows. Not taking orders wins over everything,
     * then a single-mode store says which mode it has, and a store with both modes is open for lunch.
     */
    val badge: StoreBadge
        get() = when {
            availability == StoreAvailability.PAUSED -> StoreBadge.PAUSED
            availability == StoreAvailability.FULL -> StoreBadge.FULL
            businessType == BusinessType.PICKUP_ONLY -> StoreBadge.PICKUP_ONLY
            businessType == BusinessType.IN_STORE_ONLY -> StoreBadge.IN_STORE_ONLY
            else -> StoreBadge.OPEN_FOR_LUNCH
        }
}

enum class StoreBadge { OPEN_FOR_LUNCH, PICKUP_ONLY, IN_STORE_ONLY, PAUSED, FULL }

/** A favorited menu item. Tapping it opens [storeId]. */
data class FavoriteItem(
    val storeId: String,
    val itemId: String,
    val itemName: String,
    val price: Money,
)
