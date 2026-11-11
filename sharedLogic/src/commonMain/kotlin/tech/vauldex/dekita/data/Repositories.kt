package tech.vauldex.dekita.data

import kotlinx.coroutines.flow.StateFlow
import tech.vauldex.dekita.domain.Cart
import tech.vauldex.dekita.domain.CartLine
import tech.vauldex.dekita.domain.CompletionReason
import tech.vauldex.dekita.domain.FavoriteItem
import tech.vauldex.dekita.domain.FulfillmentMode
import tech.vauldex.dekita.domain.InboxMessage
import tech.vauldex.dekita.domain.Menu
import tech.vauldex.dekita.domain.Order
import tech.vauldex.dekita.domain.OrderStatus
import tech.vauldex.dekita.domain.PaymentMethod
import tech.vauldex.dekita.domain.Store
import tech.vauldex.dekita.domain.TransitionResult

interface StoreRepository {
    /** The store feed. Availability and queue changes are pushed here as they happen, with no refresh. */
    val stores: StateFlow<LoadState<List<Store>>>
    val favoriteStoreIds: StateFlow<Set<String>>

    /** Favorited menu items, newest first. */
    val favoriteItems: StateFlow<List<FavoriteItem>>

    /** Stores picked for this customer, best first. Ids may name stores that are not in the feed. */
    val recommendedStoreIds: StateFlow<List<String>>

    fun refresh()

    fun toggleFavorite(storeId: String)
}

interface MenuRepository {
    fun menu(storeId: String): StateFlow<LoadState<Menu>>
}

interface CartRepository {
    /** Null when there is no cart yet. */
    val cart: StateFlow<Cart?>

    /**
     * The mode picked on Discover before choosing a store, or null for no preference.
     * Cart Review starts in this mode when the store supports it.
     */
    val preferredMode: StateFlow<FulfillmentMode?>

    fun setPreferredMode(mode: FulfillmentMode?)

    /**
     * Adds a line, starting a cart for the item's store if there is none. [mode] is the new cart's
     * mode; an existing cart keeps its own. Returns false, and changes nothing, when the cart
     * belongs to another store.
     */
    fun addLine(mode: FulfillmentMode, line: CartLine): Boolean

    /** A line id no other line has. */
    fun newLineId(): String

    /** Puts an edited line in place of [lineId], in the same position. Does nothing if the line is gone. */
    fun replaceLine(lineId: String, line: CartLine)

    /** Eat-In or Takeout, for a store that offers both. Does nothing without a cart. */
    fun setMode(mode: FulfillmentMode)

    /** A quantity of 0 removes the line; removing the last line clears the cart. */
    fun updateQuantity(lineId: String, quantity: Int)

    fun clear()
}

interface OrderRepository {
    val orders: StateFlow<LoadState<List<Order>>>

    /** Returns null when the orders are not loaded or there is no order with [orderId]. */
    fun transition(orderId: String, to: OrderStatus, reason: CompletionReason? = null): TransitionResult?

    /**
     * Saves [cart] as a DRAFT order: an Eat-In order that is paid on arrival with [Start Cooking].
     * Returns the new order, which also appears in [orders].
     */
    fun createDraft(cart: Cart, storeName: String): Order
}

interface PaymentRepository {
    val methods: StateFlow<LoadState<List<PaymentMethod>>>
}

interface InboxRepository {
    val messages: StateFlow<LoadState<List<InboxMessage>>>

    fun markRead(messageId: String)
}
