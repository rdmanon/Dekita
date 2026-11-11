package tech.vauldex.dekita.data.mock

import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import tech.vauldex.dekita.data.CartRepository
import tech.vauldex.dekita.data.InboxRepository
import tech.vauldex.dekita.data.LoadState
import tech.vauldex.dekita.data.MenuRepository
import tech.vauldex.dekita.data.OrderRepository
import tech.vauldex.dekita.data.PaymentRepository
import tech.vauldex.dekita.data.StoreRepository
import tech.vauldex.dekita.domain.Cart
import tech.vauldex.dekita.domain.CartLine
import tech.vauldex.dekita.domain.CompletionReason
import tech.vauldex.dekita.domain.FavoriteItem
import tech.vauldex.dekita.domain.FulfillmentMode
import tech.vauldex.dekita.domain.InboxMessage
import tech.vauldex.dekita.domain.Menu
import tech.vauldex.dekita.domain.Order
import tech.vauldex.dekita.domain.OrderStatus
import tech.vauldex.dekita.domain.OrderStatusMachine
import tech.vauldex.dekita.domain.PaymentMethod
import tech.vauldex.dekita.domain.QueueStatus
import tech.vauldex.dekita.domain.Store
import tech.vauldex.dekita.domain.StoreAvailability
import tech.vauldex.dekita.domain.TransitionResult

// In-memory fakes over the seed data. They never touch the network or disk.
// Pass a test scope in tests so the simulated delay runs on virtual time.

/** Debug-panel hooks for the store feed. Only the fake implements this; a real feed changes on the server. */
interface StoreFeedSimulator {
    /** Changes one store's availability in the live feed, as the store's own app would. */
    fun setAvailability(storeId: String, availability: StoreAvailability)

    /** Makes the next load of the feed fail, so the error state and its Retry can be checked. */
    fun failNextLoad()

    /** Empties favorite stores and items, as for a customer who has none yet. */
    fun clearFavorites()

    /** Moves a store's queue by [delta] orders, as orders come in or are served. Never below zero. */
    fun adjustQueue(storeId: String, delta: Int)
}

class FakeStoreRepository(
    private val scope: CoroutineScope,
    private val config: MockConfig,
) : StoreRepository, StoreFeedSimulator, Resettable {

    private val _stores = MutableStateFlow<LoadState<List<Store>>>(LoadState.Loading)
    override val stores: StateFlow<LoadState<List<Store>>> = _stores.asStateFlow()

    private val _favoriteStoreIds = MutableStateFlow(seedFavoriteStoreIds)
    override val favoriteStoreIds: StateFlow<Set<String>> = _favoriteStoreIds.asStateFlow()

    private val _favoriteItems = MutableStateFlow(seedFavoriteItems)
    override val favoriteItems: StateFlow<List<FavoriteItem>> = _favoriteItems.asStateFlow()

    override val recommendedStoreIds: StateFlow<List<String>> = MutableStateFlow(seedRecommendedStoreIds).asStateFlow()

    /** What the "server" holds now. Loads read it, and live changes are applied to it first. */
    private val feed = MutableStateFlow(seedStores)
    private val failNext = MutableStateFlow(false)

    private var loadJob: Job? = null

    init {
        refresh()
    }

    override fun refresh() {
        loadJob?.cancel()
        loadJob = if (failNext.getAndUpdate { false }) {
            scope.simulateFailure(_stores, config, "We couldn't load stores. Check your connection and try again.")
        } else {
            scope.simulateLoad(_stores, config) { feed.value }
        }
    }

    override fun toggleFavorite(storeId: String) {
        _favoriteStoreIds.update { ids -> if (storeId in ids) ids - storeId else ids + storeId }
    }

    override fun setAvailability(storeId: String, availability: StoreAvailability) {
        val updated = feed.updateAndGet { stores ->
            stores.map { if (it.id == storeId) it.withAvailability(availability) else it }
        }
        // Push to anyone showing the feed. A load in flight reads the updated feed when it finishes.
        _stores.update { state -> if (state is LoadState.Success) LoadState.Success(updated) else state }
    }

    override fun adjustQueue(storeId: String, delta: Int) {
        val updated = feed.updateAndGet { stores ->
            stores.map { store ->
                val queue = store.queue
                if (store.id != storeId || queue == null) {
                    store
                } else {
                    val ahead = (queue.ordersAhead + delta).coerceAtLeast(0)
                    // About three minutes per order, as in the seed data; no queue means no wait.
                    val wait = if (ahead == 0) 0 else (queue.estimatedWaitMinutes + delta * 3).coerceAtLeast(3)
                    store.copy(queue = QueueStatus(ahead, wait))
                }
            }
        }
        _stores.update { state -> if (state is LoadState.Success) LoadState.Success(updated) else state }
    }

    override fun failNextLoad() {
        failNext.value = true
    }

    override fun clearFavorites() {
        _favoriteStoreIds.value = emptySet()
        _favoriteItems.value = emptyList()
    }

    override fun reset() {
        _favoriteStoreIds.value = seedFavoriteStoreIds
        _favoriteItems.value = seedFavoriteItems
        feed.value = seedStores
        failNext.value = false
        refresh()
    }

    private fun Store.withAvailability(availability: StoreAvailability): Store {
        val seed = seedStores.firstOrNull { it.id == id }
        return copy(
            availability = availability,
            // A paused store has no queue to show. When it opens again, start from its seed queue.
            queue = if (availability == StoreAvailability.PAUSED) null else queue ?: seed?.queue ?: QueueStatus(0, 0),
            openLabel = openLabel ?: seed?.openLabel ?: "Open for Lunch",
        )
    }
}

/** Debug-panel hooks for menus: what a store's own app does when an item runs out. */
interface MenuSimulator {
    /** "Stop Selling" (true) or resume selling (false) one item. Open menus update at once. */
    fun setItemSoldOut(storeId: String, itemId: String, soldOut: Boolean)
}

class FakeMenuRepository(
    private val scope: CoroutineScope,
    private val config: MockConfig,
) : MenuRepository, MenuSimulator, Resettable {

    private val menus = mutableMapOf<String, MutableStateFlow<LoadState<Menu>>>()

    /** Sold-out changes made from the debug panel, by store and item id. */
    private val soldOutOverrides = MutableStateFlow<Map<String, Map<String, Boolean>>>(emptyMap())

    override fun menu(storeId: String): StateFlow<LoadState<Menu>> =
        menus.getOrPut(storeId) {
            MutableStateFlow<LoadState<Menu>>(LoadState.Loading).also { load(storeId, it) }
        }.asStateFlow()

    override fun setItemSoldOut(storeId: String, itemId: String, soldOut: Boolean) {
        soldOutOverrides.update { all -> all + (storeId to (all[storeId].orEmpty() + (itemId to soldOut))) }
        val menu = currentMenu(storeId) ?: return
        menus[storeId]?.update { state -> if (state is LoadState.Success) LoadState.Success(menu) else state }
    }

    override fun reset() {
        soldOutOverrides.value = emptyMap()
        menus.forEach { (storeId, flow) -> load(storeId, flow) }
    }

    private fun currentMenu(storeId: String): Menu? {
        val menu = seedMenus[storeId] ?: return null
        val overrides = soldOutOverrides.value[storeId].orEmpty()
        return menu.copy(items = menu.items.map { item -> overrides[item.id]?.let { item.copy(isSoldOut = it) } ?: item })
    }

    private fun load(storeId: String, target: MutableStateFlow<LoadState<Menu>>) {
        if (seedMenus[storeId] == null) {
            target.value = LoadState.Error("No menu for store $storeId")
        } else {
            scope.simulateLoad(target, config) { checkNotNull(currentMenu(storeId)) }
        }
    }
}

class FakeCartRepository : CartRepository, Resettable {

    private val _cart = MutableStateFlow<Cart?>(null)
    override val cart: StateFlow<Cart?> = _cart.asStateFlow()

    private val _preferredMode = MutableStateFlow<FulfillmentMode?>(null)
    override val preferredMode: StateFlow<FulfillmentMode?> = _preferredMode.asStateFlow()

    override fun setPreferredMode(mode: FulfillmentMode?) {
        _preferredMode.value = mode
    }

    private val lineCounter = MutableStateFlow(0)

    override fun addLine(mode: FulfillmentMode, line: CartLine): Boolean {
        var added = false
        _cart.update { current ->
            added = false
            when {
                current == null -> Cart(line.item.storeId, mode).addLine(line).also { added = true }
                current.storeId != line.item.storeId -> current
                // The cart keeps the mode it started with; Cart Review is where it changes.
                else -> current.addLine(line).also { added = true }
            }
        }
        return added
    }

    override fun newLineId(): String = "cart-line-${lineCounter.updateAndGet { it + 1 }}"

    override fun replaceLine(lineId: String, line: CartLine) {
        _cart.update { current ->
            if (current?.line(lineId) == null) current else current.replaceLine(lineId, line)
        }
    }

    override fun setMode(mode: FulfillmentMode) {
        _cart.update { it?.copy(mode = mode) }
    }

    override fun updateQuantity(lineId: String, quantity: Int) {
        _cart.update { current ->
            current?.updateQuantity(lineId, quantity)?.takeUnless { it.isEmpty }
        }
    }

    override fun clear() {
        _cart.value = null
    }

    override fun reset() {
        clear()
        _preferredMode.value = null
    }
}

class FakeOrderRepository(
    private val scope: CoroutineScope,
    private val config: MockConfig,
    private val clock: Clock,
) : OrderRepository, Resettable {

    private val _orders = MutableStateFlow<LoadState<List<Order>>>(LoadState.Loading)
    override val orders: StateFlow<LoadState<List<Order>>> = _orders.asStateFlow()

    // Declared before init: reset() uses it, and Kotlin runs initializers in declaration order.
    /** Orders made in this session. A reload keeps them, as a server would. */
    private val created = MutableStateFlow<List<Order>>(emptyList())

    private var loadJob: Job? = null

    init {
        reset()
    }

    override fun transition(orderId: String, to: OrderStatus, reason: CompletionReason?): TransitionResult? {
        var result: TransitionResult? = null
        _orders.update { state ->
            result = null
            val orders = (state as? LoadState.Success)?.data ?: return@update state
            val order = orders.firstOrNull { it.id == orderId } ?: return@update state
            val attempt = OrderStatusMachine.transition(order, to, clock.now(), reason)
            result = attempt
            when (attempt) {
                is TransitionResult.Accepted -> LoadState.Success(orders.map { if (it.id == orderId) attempt.order else it })
                is TransitionResult.Rejected -> state
            }
        }
        return result
    }

    override fun createDraft(cart: Cart, storeName: String): Order {
        val known = (_orders.value as? LoadState.Success)?.data ?: (created.value + seedOrders(clock.now()))
        val number = (known.maxOfOrNull { it.number } ?: 0) + 1
        val order = Order.draft(
            id = "order-$number",
            number = number,
            storeId = cart.storeId,
            storeName = storeName,
            mode = cart.mode,
            lines = cart.lines,
            // Eat-In is paid at check-in, so there is no payment method yet.
            paymentMethodId = null,
            at = clock.now(),
        )
        created.update { listOf(order) + it }
        _orders.update { state -> if (state is LoadState.Success) LoadState.Success(listOf(order) + state.data) else state }
        return order
    }

    override fun reset() {
        loadJob?.cancel()
        created.value = emptyList()
        loadJob = scope.simulateLoad(_orders, config) { created.value + seedOrders(clock.now()) }
    }

    /** Debug only: removes every order, as for a customer who has never ordered. */
    fun clearAll() {
        loadJob?.cancel()
        created.value = emptyList()
        _orders.value = LoadState.Success(emptyList())
    }
}

class FakePaymentRepository(
    private val scope: CoroutineScope,
    private val config: MockConfig,
) : PaymentRepository, Resettable {

    private val _methods = MutableStateFlow<LoadState<List<PaymentMethod>>>(LoadState.Loading)
    override val methods: StateFlow<LoadState<List<PaymentMethod>>> = _methods.asStateFlow()

    private var loadJob: Job? = null

    init {
        reset()
    }

    override fun reset() {
        loadJob?.cancel()
        loadJob = scope.simulateLoad(_methods, config) { seedPaymentMethods }
    }
}

class FakeInboxRepository(
    private val scope: CoroutineScope,
    private val config: MockConfig,
    private val clock: Clock,
) : InboxRepository, Resettable {

    private val _messages = MutableStateFlow<LoadState<List<InboxMessage>>>(LoadState.Loading)
    override val messages: StateFlow<LoadState<List<InboxMessage>>> = _messages.asStateFlow()

    private var loadJob: Job? = null

    init {
        reset()
    }

    override fun markRead(messageId: String) {
        _messages.update { state ->
            val messages = (state as? LoadState.Success)?.data ?: return@update state
            LoadState.Success(messages.map { if (it.id == messageId) it.copy(isRead = true) else it })
        }
    }

    override fun reset() {
        loadJob?.cancel()
        loadJob = scope.simulateLoad(_messages, config) { seedInbox(clock.now()) }
    }
}
