package tech.vauldex.dekita.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import tech.vauldex.dekita.domain.Cart
import tech.vauldex.dekita.domain.CartLine
import tech.vauldex.dekita.domain.CheckIn
import tech.vauldex.dekita.domain.CompletionReason
import tech.vauldex.dekita.domain.FavoriteItem
import tech.vauldex.dekita.domain.FulfillmentMode
import tech.vauldex.dekita.domain.GeoPoint
import tech.vauldex.dekita.domain.InboxKind
import tech.vauldex.dekita.domain.InboxMessage
import tech.vauldex.dekita.domain.Menu
import tech.vauldex.dekita.domain.Order
import tech.vauldex.dekita.domain.OrderStatus
import tech.vauldex.dekita.domain.PaymentMethod
import tech.vauldex.dekita.domain.PhoneNumber
import tech.vauldex.dekita.domain.Session
import tech.vauldex.dekita.domain.SsoProvider
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

    /** Adds [item] to the favorite items, newest first, or removes it when it is already there. */
    fun toggleFavoriteItem(item: FavoriteItem)
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
     * Adds a line, starting a cart for the item's store if there is none.
     * Returns false, and changes nothing, when the cart belongs to another store.
     */
    fun addLine(mode: FulfillmentMode, line: CartLine): Boolean

    /** Drops the current cart, even one from another store, and starts a new one with [line]. */
    fun replaceWith(mode: FulfillmentMode, line: CartLine)

    /** Puts an edited line in place of [lineId]. Does nothing if the line is gone. See [Cart.replaceLine]. */
    fun replaceLine(lineId: String, line: CartLine)

    /** Eat-In or Takeout, for a store that offers both. Does nothing without a cart. */
    fun setMode(mode: FulfillmentMode)

    /** A quantity of 0 removes the line; removing the last line clears the cart. */
    fun updateQuantity(lineId: String, quantity: Int)

    /** Replaces any cart with [cart]. SEAT-01: a draft switched to Takeout becomes the cart again. */
    fun restore(cart: Cart)

    fun clear()
}

interface OrderRepository {
    val orders: StateFlow<LoadState<List<Order>>>

    /**
     * Each order right after its status changes, including payment. Not replayed, so a collector
     * only sees changes made while it collects. Drives the order notifications.
     */
    val statusChanges: Flow<Order>

    /** Returns null when the orders are not loaded or there is no order with [orderId]. */
    fun transition(orderId: String, to: OrderStatus, reason: CompletionReason? = null): TransitionResult?

    // Paid orders are made only by PaymentService (PAY-01), once a charge goes through, so they aren't on this
    // interface: a screen can't create a paid order without paying. The fake's placeOrder and payAtCheckIn do it.

    /**
     * Removes a DRAFT order and returns it, so its lines can go back into the cart (SEAT-01's Switch to
     * Takeout). Returns null, and changes nothing, when there is no such order or it is no longer a draft.
     */
    fun discardDraft(orderId: String): Order?

    /**
     * Rates a completed order once. Returns false, and changes nothing, when the order isn't completed,
     * is already rated, or the stars or tags are invalid, so a second tap records nothing.
     */
    fun rate(orderId: String, stars: Int, tags: Set<String>): Boolean

    /**
     * Saves [cart] as a DRAFT order: an Eat-In order that is paid on arrival with [Start Cooking].
     * Returns the new order, which also appears in [orders].
     */
    fun createDraft(cart: Cart, storeName: String): Order

    /**
     * Records arrival on an in-store draft, replacing an earlier check-in. Returns false, and changes
     * nothing, when the orders are not loaded or [orderId] is not an in-store draft.
     */
    fun checkIn(orderId: String, checkIn: CheckIn): Boolean
}

/** Where the customer is. GPS in production; the mockup never reads the real location. */
interface LocationRepository {
    /** The customer's position, or null when it is unknown. */
    val customerLocation: StateFlow<GeoPoint?>
}

/** The camera's QR reader. Emits each code's raw text once, while someone collects. */
interface QrScanner {
    val scans: Flow<String>
}

interface PaymentRepository {
    val methods: StateFlow<LoadState<List<PaymentMethod>>>

    /**
     * The demo add-card form's save (PAY-01). Accepts only a payment provider's published test number and
     * keeps just its brand and last four digits, in memory. Returns null for any other number.
     * Production never sees a card number: the provider's SDK or hosted fields return a saved method instead.
     */
    fun addTestCard(cardNumber: String): PaymentMethod?
}

/** What a payment is for. [checkoutId] is one payment sheet's idempotency key. */
sealed interface PaymentRequest {
    val checkoutId: String

    /** In-store-only, Pickup-only and Takeout orders: the cart is charged, then becomes a PAID or QUEUED order. */
    data class PayNow(override val checkoutId: String, val cart: Cart, val storeName: String) : PaymentRequest

    /** Eat-In: the checked-in DRAFT is charged after the seat check (SEAT-01) and moves to QUEUED. */
    data class PayDraft(override val checkoutId: String, val orderId: String) : PaymentRequest
}

sealed interface PaymentResult {
    /** The charge went through and [order] is PAID (pickup) or QUEUED (in store), with its order number. */
    data class Paid(val order: Order) : PaymentResult

    /** The method was declined. Nothing was charged and no order was made. */
    data object Declined : PaymentResult

    /** The draft is gone or isn't checked in, so there is nothing to charge. */
    data object OrderUnavailable : PaymentResult
}

/**
 * Charges a saved method. The mockup's fake simulates it; production goes through the backend to the payment
 * provider, and the order is only created once the provider confirms the payment.
 */
interface PaymentService {
    /**
     * Charges [methodId] for [request]. Idempotent on [PaymentRequest.checkoutId]: once a checkout is paid, calling
     * again returns the same order, so a double tap can't make a second order or order number.
     */
    suspend fun pay(request: PaymentRequest, methodId: String): PaymentResult
}

interface InboxRepository {
    val messages: StateFlow<LoadState<List<InboxMessage>>>

    fun markRead(messageId: String)

    /** Adds a message stamped with the current time. Does nothing when a message with [id] already exists. */
    fun add(id: String, title: String, body: String, kind: InboxKind, orderId: String?)
}

interface AuthRepository {
    /** Null while browsing as a guest. Held in memory only: nothing is written to disk and no token exists. */
    val session: StateFlow<Session?>

    /** Sends a 6-digit code to [phone]. */
    suspend fun requestOtp(phone: PhoneNumber)

    suspend fun verifyOtp(phone: PhoneNumber, code: String): AuthResult

    suspend fun signUpWithEmail(email: String, password: String): AuthResult

    suspend fun signInWithEmail(email: String, password: String): AuthResult

    /** Has the same outcome whether or not the account exists, so it can't reveal which emails are registered. */
    suspend fun requestPasswordReset(email: String)

    suspend fun signInWith(provider: SsoProvider): AuthResult

    fun signOut()
}

sealed interface AuthResult {
    data object Success : AuthResult

    data class Failure(val reason: AuthFailure) : AuthResult
}

enum class AuthFailure { INVALID_CODE, WRONG_CREDENTIALS, EMAIL_TAKEN, SSO_FAILED }
