package tech.vauldex.dekita.presentation

import tech.vauldex.dekita.domain.BusinessType
import tech.vauldex.dekita.domain.CartLine
import tech.vauldex.dekita.domain.CheckoutAction
import tech.vauldex.dekita.domain.CompletionReason
import tech.vauldex.dekita.domain.FulfillmentMode
import tech.vauldex.dekita.domain.OrderStatus
import tech.vauldex.dekita.domain.Store
import tech.vauldex.dekita.domain.StoreAvailability
import tech.vauldex.dekita.domain.StoreBadge
import tech.vauldex.dekita.presentation.cart.CheckoutBlock
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

/** The Eat-In / Takeout toggle's words, for stores that offer both modes. */
val FulfillmentMode.choiceLabel: String
    get() = when (this) {
        FulfillmentMode.IN_STORE -> "Eat-In"
        FulfillmentMode.PICKUP -> "Takeout"
    }

val CheckoutAction.label: String
    get() = when (this) {
        CheckoutAction.PAY_NOW -> "Pay now"
        CheckoutAction.HEAD_TO_STORE -> "Head to store"
    }

/** The line under the mode on Cart Review: when the customer pays and what happens next. */
fun checkoutInfo(action: CheckoutAction, mode: FulfillmentMode): String = when {
    action == CheckoutAction.HEAD_TO_STORE ->
        "You pay when you check in at the store. Cooking starts then, so your food is fresh when you sit down."
    mode == FulfillmentMode.PICKUP ->
        "You pay now. We start preparing right away and tell you when it's ready for pickup."
    else -> "You pay now, then show your order when you arrive."
}

/** Why checkout is off, for the message above the button. */
fun CheckoutBlock.message(storeName: String): String = when (this) {
    CheckoutBlock.STORE_PAUSED -> "$storeName has paused orders. Your cart is saved, so you can check out when it reopens."
    CheckoutBlock.STORE_FULL -> "$storeName is full right now. Your cart is saved, so you can check out when the queue drops."
    CheckoutBlock.SOLD_OUT_LINES -> "Remove or edit the sold-out items to check out."
    CheckoutBlock.CHECKING -> "Checking the store…"
}

/** The live queue line on Cart Review. */
fun Store.queueSummary(): String {
    val queue = queue
    return when {
        availability == StoreAvailability.PAUSED -> "Not accepting orders"
        queue == null -> ""
        queue.ordersAhead == 0 -> "No wait"
        queue.ordersAhead == 1 -> "1 order ahead · ~${queue.estimatedWaitMinutes} min wait"
        else -> "${queue.ordersAhead} orders ahead · ~${queue.estimatedWaitMinutes} min wait"
    }
}

/** A cart line's choices, as "Medium · Extra Egg". Empty when there are none. */
val CartLine.optionsLabel: String
    get() = selectedOptions.joinToString(" · ") { it.name }
