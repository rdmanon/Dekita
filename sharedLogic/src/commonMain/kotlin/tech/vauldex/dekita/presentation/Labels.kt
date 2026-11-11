package tech.vauldex.dekita.presentation

import tech.vauldex.dekita.domain.BusinessType
import tech.vauldex.dekita.domain.CartLine
import tech.vauldex.dekita.domain.CheckoutAction
import tech.vauldex.dekita.domain.CheckIn
import tech.vauldex.dekita.domain.CheckInMethod
import tech.vauldex.dekita.domain.CompletionReason
import tech.vauldex.dekita.domain.FulfillmentMode
import tech.vauldex.dekita.domain.MenuItem
import tech.vauldex.dekita.domain.ModifierGroup
import tech.vauldex.dekita.domain.ModifierOption
import tech.vauldex.dekita.domain.Money
import tech.vauldex.dekita.domain.OrderStatus
import tech.vauldex.dekita.domain.QueueStatus
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

/** The store screen's banner when the store is paused or full. */
const val NOT_ACCEPTING_ORDERS_MESSAGE = "Currently at full capacity - Not accepting new orders"

/** The live queue card's second line. Empty when the store has no queue. */
fun Store.queueDetail(): String {
    val queue = queue ?: return ""
    return when {
        availability == StoreAvailability.FULL -> "Not taking new orders"
        queue.estimatedWaitMinutes == 0 -> "Updates live"
        else -> "About ${queue.estimatedWaitMinutes} min wait · updates live"
    }
}

/** A menu row's options line, for example "Spice level (required) · Toppings (up to 3), Corn sold out". */
fun MenuItem.optionsSummary(): String? {
    if (modifierGroups.isEmpty()) return null
    return modifierGroups.joinToString(" · ") { group ->
        val rule = if (group.isRequired) "required" else "up to ${group.maxSelections}"
        val soldOut = group.options.filter { it.isSoldOut }.joinToString { it.name }
        if (soldOut.isEmpty()) "${group.name} ($rule)" else "${group.name} ($rule), $soldOut sold out"
    }
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

/** How the customer checked in, for the seat check and the order ticket. */
val CheckIn.label: String
    get() = when (method) {
        CheckInMethod.GEOFENCE -> "Checked in by location"
        CheckInMethod.STORE_QR -> "Checked in with the store QR"
        CheckInMethod.TABLE_QR -> "Checked in at table $tableNumber"
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

/** The item sheet's message when the item sold out while it was open. */
const val ITEM_SOLD_OUT_MESSAGE = "This item just sold out, so it can't be added."

/** The item sheet's message when an option the customer picked sold out while it was open. */
const val OPTION_SOLD_OUT_MESSAGE = "A choice you picked just sold out. Pick another to add this item."

/** The item sheet's message when the store is paused or full. */
const val STORE_NOT_TAKING_ORDERS_MESSAGE = "This store isn't taking orders right now."

/** "Required" or "Choose up to 3", shown next to a modifier group's name. */
val ModifierGroup.ruleLabel: String
    get() = when {
        isRequired && maxSelections == 1 -> "Required"
        isRequired -> "Choose $minSelections to $maxSelections"
        maxSelections == 1 -> "Optional"
        else -> "Choose up to $maxSelections"
    }

/** "+¥120", or empty for a free option. */
val ModifierOption.priceLabel: String
    get() = if (priceDelta == Money.ZERO) "" else "+${priceDelta.formatted()}"

/** The item sheet's button text, for example "Add · ¥1,100". */
fun addButtonLabel(total: Money): String = "Add · ${total.formatted()}"

/** The item sheet's button text when editing a cart line, for example "Update · ¥1,100". */
fun updateButtonLabel(total: Money): String = "Update · ${total.formatted()}"

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
    CheckoutBlock.UNAVAILABLE_LINES -> "Remove or edit the marked items to check out."
    CheckoutBlock.CHECKING -> "Checking the store…"
}

/** A cart line's choices, as "Medium · Extra Egg". Empty when there are none. */
val CartLine.optionsLabel: String
    get() = selectedOptions.joinToString(" · ") { it.name }
