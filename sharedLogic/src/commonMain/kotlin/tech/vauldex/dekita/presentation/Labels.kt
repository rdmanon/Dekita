package tech.vauldex.dekita.presentation

import tech.vauldex.dekita.domain.BusinessType
import tech.vauldex.dekita.domain.CartLine
import tech.vauldex.dekita.domain.CheckoutAction
import tech.vauldex.dekita.domain.CompletionReason
import tech.vauldex.dekita.domain.FulfillmentMode
import tech.vauldex.dekita.domain.MenuItem
import tech.vauldex.dekita.domain.ModifierGroup
import tech.vauldex.dekita.domain.ModifierOption
import tech.vauldex.dekita.domain.Money
import tech.vauldex.dekita.domain.Order
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

// SEAT-01: check-in, the seat question and its fallbacks.

/** "2 items · ¥1,550" for an order. */
val Order.itemsLabel: String
    get() = "${if (itemCount == 1) "1 item" else "$itemCount items"} · ${total.formatted()}"

/** The check-in queue row: "3 orders ahead · ~8 min", or "No wait". */
fun QueueStatus.summary(): String =
    if (ordersAhead == 0 || estimatedWaitMinutes == 0) headline() else "${headline()} · ~$estimatedWaitMinutes min"

const val SEAT_QUESTION = "Have you found a seat?"

const val SEAT_QUESTION_DETAIL = "Cooking starts when you pay, so make sure you have a seat first."

/** The fallbacks' title: the store is full, or the customer said they have no seat yet. */
fun noSeatTitle(storeName: String, storeIsFull: Boolean): String =
    if (storeIsFull) "$storeName is full right now" else "No seat yet?"

const val NO_SEAT_DETAIL = "Your order is saved. Nothing is cooked or charged until you start cooking."

const val SWITCH_TO_TAKEOUT = "Switch to Takeout"
const val SWITCH_TO_TAKEOUT_DETAIL = "Pay now and take your food with you."
const val WAIT_IN_LINE = "Wait in line"
const val WAIT_IN_LINE_DETAIL = "Stay here and start cooking once you have a seat."
const val CANCEL_EXIT = "Cancel / Exit"
const val CANCEL_EXIT_DETAIL = "Back to Discover. Your order stays in Orders as a draft."

/** Shown on check-in after "Wait in line". */
const val WAITING_FOR_SEAT_MESSAGE = "Waiting for a seat. Your order is kept until you start cooking."

const val FOUND_SEAT_BUTTON = "I've found a seat"

/** Shown instead of Start Cooking while the store has paused orders. */
fun checkInPausedMessage(storeName: String): String =
    "$storeName has paused orders. Your order is kept, so you can start cooking when it reopens."

/** The payment sheet's button, for example "Pay ¥1,550". */
fun payButtonLabel(total: Money): String = "Pay ${total.formatted()}"

/** Shown after paying at check-in. */
fun sentToKitchenTitle(orderNumber: Int): String = "Order #$orderNumber sent to the kitchen"

const val SENT_TO_KITCHEN_DETAIL = "Cooking starts now. Follow your order in the Orders tab."

/** Discover's banner for a kept draft. */
fun draftBannerTitle(storeName: String): String = "Draft order at $storeName"

const val RESUME_CHECK_IN = "Resume check-in"
