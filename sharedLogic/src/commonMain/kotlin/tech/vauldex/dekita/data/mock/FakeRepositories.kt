package tech.vauldex.dekita.data.mock

import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
import tech.vauldex.dekita.domain.FulfillmentMode
import tech.vauldex.dekita.domain.InboxMessage
import tech.vauldex.dekita.domain.Menu
import tech.vauldex.dekita.domain.Order
import tech.vauldex.dekita.domain.OrderStatus
import tech.vauldex.dekita.domain.OrderStatusMachine
import tech.vauldex.dekita.domain.PaymentMethod
import tech.vauldex.dekita.domain.Store
import tech.vauldex.dekita.domain.TransitionResult

// In-memory fakes over the seed data. They never touch the network or disk.
// Pass a test scope in tests so the simulated delay runs on virtual time.

class FakeStoreRepository(
    private val scope: CoroutineScope,
    private val config: MockConfig,
) : StoreRepository, Resettable {

    private val _stores = MutableStateFlow<LoadState<List<Store>>>(LoadState.Loading)
    override val stores: StateFlow<LoadState<List<Store>>> = _stores.asStateFlow()

    private val _favoriteStoreIds = MutableStateFlow(seedFavoriteStoreIds)
    override val favoriteStoreIds: StateFlow<Set<String>> = _favoriteStoreIds.asStateFlow()

    private var loadJob: Job? = null

    init {
        refresh()
    }

    override fun refresh() {
        loadJob?.cancel()
        loadJob = scope.simulateLoad(_stores, config) { seedStores }
    }

    override fun toggleFavorite(storeId: String) {
        _favoriteStoreIds.update { ids -> if (storeId in ids) ids - storeId else ids + storeId }
    }

    override fun reset() {
        _favoriteStoreIds.value = seedFavoriteStoreIds
        refresh()
    }
}

class FakeMenuRepository(
    private val scope: CoroutineScope,
    private val config: MockConfig,
) : MenuRepository, Resettable {

    private val menus = mutableMapOf<String, MutableStateFlow<LoadState<Menu>>>()

    override fun menu(storeId: String): StateFlow<LoadState<Menu>> =
        menus.getOrPut(storeId) {
            MutableStateFlow<LoadState<Menu>>(LoadState.Loading).also { load(storeId, it) }
        }.asStateFlow()

    override fun reset() {
        menus.forEach { (storeId, flow) -> load(storeId, flow) }
    }

    private fun load(storeId: String, target: MutableStateFlow<LoadState<Menu>>) {
        val menu = seedMenus[storeId]
        if (menu == null) {
            target.value = LoadState.Error("No menu for store $storeId")
        } else {
            scope.simulateLoad(target, config) { menu }
        }
    }
}

class FakeCartRepository : CartRepository, Resettable {

    private val _cart = MutableStateFlow<Cart?>(null)
    override val cart: StateFlow<Cart?> = _cart.asStateFlow()

    override fun addLine(mode: FulfillmentMode, line: CartLine): Boolean {
        var added = false
        _cart.update { current ->
            added = false
            when {
                current == null -> Cart(line.item.storeId, mode).addLine(line).also { added = true }
                current.storeId != line.item.storeId -> current
                else -> current.copy(mode = mode).addLine(line).also { added = true }
            }
        }
        return added
    }

    override fun updateQuantity(lineId: String, quantity: Int) {
        _cart.update { current ->
            current?.updateQuantity(lineId, quantity)?.takeUnless { it.isEmpty }
        }
    }

    override fun clear() {
        _cart.value = null
    }

    override fun reset() = clear()
}

class FakeOrderRepository(
    private val scope: CoroutineScope,
    private val config: MockConfig,
    private val clock: Clock,
) : OrderRepository, Resettable {

    private val _orders = MutableStateFlow<LoadState<List<Order>>>(LoadState.Loading)
    override val orders: StateFlow<LoadState<List<Order>>> = _orders.asStateFlow()

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

    override fun reset() {
        loadJob?.cancel()
        loadJob = scope.simulateLoad(_orders, config) { seedOrders(clock.now()) }
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
