package tech.vauldex.dekita.data

import kotlinx.coroutines.flow.StateFlow
import tech.vauldex.dekita.domain.Cart
import tech.vauldex.dekita.domain.CartLine
import tech.vauldex.dekita.domain.CompletionReason
import tech.vauldex.dekita.domain.FulfillmentMode
import tech.vauldex.dekita.domain.InboxMessage
import tech.vauldex.dekita.domain.Menu
import tech.vauldex.dekita.domain.Order
import tech.vauldex.dekita.domain.OrderStatus
import tech.vauldex.dekita.domain.PaymentMethod
import tech.vauldex.dekita.domain.Store
import tech.vauldex.dekita.domain.TransitionResult

interface StoreRepository {
    val stores: StateFlow<LoadState<List<Store>>>
    val favoriteStoreIds: StateFlow<Set<String>>

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
     * Adds a line, starting a cart for the item's store if there is none.
     * Returns false, and changes nothing, when the cart belongs to another store.
     */
    fun addLine(mode: FulfillmentMode, line: CartLine): Boolean

    /** A quantity of 0 removes the line; removing the last line clears the cart. */
    fun updateQuantity(lineId: String, quantity: Int)

    fun clear()
}

interface OrderRepository {
    val orders: StateFlow<LoadState<List<Order>>>

    /** Returns null when the orders are not loaded or there is no order with [orderId]. */
    fun transition(orderId: String, to: OrderStatus, reason: CompletionReason? = null): TransitionResult?
}

interface PaymentRepository {
    val methods: StateFlow<LoadState<List<PaymentMethod>>>
}

interface InboxRepository {
    val messages: StateFlow<LoadState<List<InboxMessage>>>

    fun markRead(messageId: String)
}
