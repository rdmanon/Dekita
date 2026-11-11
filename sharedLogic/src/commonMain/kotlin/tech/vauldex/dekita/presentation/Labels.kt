package tech.vauldex.dekita.presentation

import tech.vauldex.dekita.domain.BusinessType
import tech.vauldex.dekita.domain.CompletionReason
import tech.vauldex.dekita.domain.FulfillmentMode
import tech.vauldex.dekita.domain.OrderStatus
import tech.vauldex.dekita.domain.QueueStatus
import tech.vauldex.dekita.domain.Store
import tech.vauldex.dekita.domain.StoreAvailability
import tech.vauldex.dekita.domain.StoreBadge
import tech.vauldex.dekita.presentation.discover.WalkLimit

// User-facing text shared by the Compose UI and the SwiftUI app, so both platforms say the same thing.
// Swift sees these extensions as members, for example `store.queueLabel()` and `badge.label`.

val StoreBadge.label: String
    get() = when (this) {
        StoreBadge.OPEN_FOR_LUNCH -> "Open for Lunch"
        StoreBadge.PICKUP_ONLY -> "Pickup Only"
        StoreBadge.IN_STORE_ONLY -> "In-store Only"
        StoreBadge.PAUSED -> "Paused"
        StoreBadge.FULL -> "Full"
    }

/** The live queue card's headline. */
fun QueueStatus.headline(): String = when (ordersAhead) {
    0 -> "No wait"
    1 -> "1 order ahead"
    else -> "$ordersAhead orders ahead"
}

/** The queue line on a store card: "3 ahead · ~8 min", "No wait", "Not accepting orders", or empty. */
fun Store.queueLabel(): String {
    val queue = queue
    return when {
        availability == StoreAvailability.PAUSED -> "Not accepting orders"
        queue == null -> ""
        availability == StoreAvailability.FULL -> "${queue.ordersAhead} ahead · not taking new orders"
        queue.ordersAhead == 0 -> "No wait"
        else -> "${queue.ordersAhead} ahead · ~${queue.estimatedWaitMinutes} min"
    }
}

/** Which modes a store offers, for the iOS card subtitle. Null for single-mode stores, whose badge already says it. */
val BusinessType.modesLabel: String?
    get() = when (this) {
        BusinessType.BOTH -> "In-store & Pickup"
        BusinessType.IN_STORE_ONLY, BusinessType.PICKUP_ONLY -> null
    }

val FulfillmentMode.label: String
    get() = when (this) {
        FulfillmentMode.IN_STORE -> "In-store"
        FulfillmentMode.PICKUP -> "Pickup"
    }

val OrderStatus.label: String
    get() = when (this) {
        OrderStatus.DRAFT -> "Draft"
        OrderStatus.PAID -> "Paid"
        OrderStatus.QUEUED -> "In queue"
        OrderStatus.PREPARING -> "Preparing"
        OrderStatus.READY_FOR_PICKUP -> "Ready for pickup"
        OrderStatus.SERVED -> "Served"
        OrderStatus.COMPLETED -> "Completed"
    }

val CompletionReason.label: String
    get() = when (this) {
        CompletionReason.CUSTOMER_CONFIRMED -> "Confirmed by you"
        CompletionReason.AUTO_COMPLETED -> "Auto-completed"
    }

/** The status filter's chip text. */
val StoreAvailability.filterLabel: String
    get() = when (this) {
        StoreAvailability.OPEN -> "Open"
        StoreAvailability.FULL -> "Full"
        StoreAvailability.PAUSED -> "Paused"
    }

val WalkLimit.label: String
    get() = "≤ $maxMinutes min"
