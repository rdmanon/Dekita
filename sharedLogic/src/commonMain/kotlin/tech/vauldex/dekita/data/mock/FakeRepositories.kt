package tech.vauldex.dekita.data.mock

import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import tech.vauldex.dekita.data.AuthFailure
import tech.vauldex.dekita.data.AuthRepository
import tech.vauldex.dekita.data.AuthResult
import tech.vauldex.dekita.data.CartRepository
import tech.vauldex.dekita.data.InboxRepository
import tech.vauldex.dekita.data.LoadState
import tech.vauldex.dekita.data.MenuRepository
import tech.vauldex.dekita.data.OrderRepository
import tech.vauldex.dekita.data.PaymentRepository
import tech.vauldex.dekita.data.StoreRepository
import tech.vauldex.dekita.data.dataOrNull
import tech.vauldex.dekita.domain.BusinessType
import tech.vauldex.dekita.domain.Cart
import tech.vauldex.dekita.domain.CartLine
import tech.vauldex.dekita.domain.CheckIn
import tech.vauldex.dekita.domain.CompletionReason
import tech.vauldex.dekita.domain.FavoriteItem
import tech.vauldex.dekita.domain.FulfillmentMode
import tech.vauldex.dekita.domain.InboxKind
import tech.vauldex.dekita.domain.InboxMessage
import tech.vauldex.dekita.domain.OrderRating
import tech.vauldex.dekita.domain.RATING_TAGS
import tech.vauldex.dekita.domain.Menu
import tech.vauldex.dekita.domain.MenuItem
import tech.vauldex.dekita.domain.Order
import tech.vauldex.dekita.domain.OrderStatus
import tech.vauldex.dekita.domain.OrderStatusMachine
import tech.vauldex.dekita.domain.PaymentMethod
import tech.vauldex.dekita.domain.PhoneNumber
import tech.vauldex.dekita.domain.QueueStatus
import tech.vauldex.dekita.domain.Session
import tech.vauldex.dekita.domain.SignInMethod
import tech.vauldex.dekita.domain.SsoProvider
import tech.vauldex.dekita.domain.Store
import tech.vauldex.dekita.domain.StoreAvailability
import tech.vauldex.dekita.domain.TransitionResult
import tech.vauldex.dekita.domain.UserProfile
import tech.vauldex.dekita.domain.canBeRated
import tech.vauldex.dekita.domain.canConfirm
import tech.vauldex.dekita.domain.isActive

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

    /** Switches a store's service mode (In-store only, Pickup only or both), as the store's own app would. */
    fun setBusinessType(storeId: String, businessType: BusinessType)
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
    private var queueTicks = 0

    init {
        refresh()
        scope.launch {
            while (true) {
                delay(MockConfig.QUEUE_TICK_INTERVAL)
                if (config.liveQueue.value) tickQueues()
            }
        }
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

    override fun toggleFavoriteItem(item: FavoriteItem) {
        _favoriteItems.update { items ->
            val existing = items.any { it.storeId == item.storeId && it.itemId == item.itemId }
            if (existing) items.filterNot { it.storeId == item.storeId && it.itemId == item.itemId } else listOf(item) + items
        }
    }

    override fun setAvailability(storeId: String, availability: StoreAvailability) {
        val updated = feed.updateAndGet { stores ->
            stores.map { if (it.id == storeId) it.withAvailability(availability) else it }
        }
        // Push to anyone showing the feed. A load in flight reads the updated feed when it finishes.
        _stores.update { state -> if (state is LoadState.Success) LoadState.Success(updated) else state }
    }

    override fun setBusinessType(storeId: String, businessType: BusinessType) {
        val updated = feed.updateAndGet { stores ->
            stores.map { if (it.id == storeId) it.copy(businessType = businessType) else it }
        }
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
        queueTicks = 0
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

    private fun tickQueues() {
        queueTicks++
        val offset = QUEUE_OFFSETS[queueTicks % QUEUE_OFFSETS.size]
        // Ticks around the feed's own value, so a debug-panel queue change (adjustQueue) is kept.
        val base = feed.value
        _stores.update { state ->
            if (state !is LoadState.Success) return@update state
            LoadState.Success(base.map { it.withQueueOffset(offset) })
        }
    }
}

// A fixed pattern instead of random steps, so tests and demos are repeatable.
private val QUEUE_OFFSETS = listOf(0, 1, 2, 1, 0, -1)

/**
 * Moves an open store's queue around its feed value (the seed, unless the debug panel changed it).
 * Paused, full and empty queues stay as they are.
 */
private fun Store.withQueueOffset(offset: Int): Store {
    val base = queue ?: return this
    if (availability != StoreAvailability.OPEN || base.ordersAhead == 0) return this
    val ahead = (base.ordersAhead + offset).coerceAtLeast(0)
    // Keeps the base's minutes per order, rounded half up.
    val wait = (base.estimatedWaitMinutes * ahead + base.ordersAhead / 2) / base.ordersAhead
    return copy(queue = QueueStatus(ahead, wait))
}

/** Debug-panel-only changes to a menu, as the store's own app would push them. */
interface MenuSimulator {
    /** Marks an item sold out or back on sale. A menu that is open changes at once; one opened later loads it. */
    fun setItemSoldOut(storeId: String, itemId: String, soldOut: Boolean)

    /** Marks an option sold out or back on sale on every item of the store that offers it. */
    fun setOptionSoldOut(storeId: String, optionId: String, soldOut: Boolean)
}

class FakeMenuRepository(
    private val scope: CoroutineScope,
    private val config: MockConfig,
) : MenuRepository, MenuSimulator, Resettable {

    private val menus = mutableMapOf<String, MutableStateFlow<LoadState<Menu>>>()

    /** What the "server" holds now. Loads read it, and live changes are applied to it first. */
    private val feed = MutableStateFlow(seedMenus)

    override fun menu(storeId: String): StateFlow<LoadState<Menu>> =
        menus.getOrPut(storeId) {
            MutableStateFlow<LoadState<Menu>>(LoadState.Loading).also { load(storeId, it) }
        }.asStateFlow()

    override fun setItemSoldOut(storeId: String, itemId: String, soldOut: Boolean) =
        change(storeId) { menu ->
            menu.copy(items = menu.items.map { if (it.id == itemId) it.copy(isSoldOut = soldOut) else it })
        }

    override fun setOptionSoldOut(storeId: String, optionId: String, soldOut: Boolean) =
        change(storeId) { menu ->
            menu.copy(items = menu.items.map { item -> item.withOptionSoldOut(optionId, soldOut) })
        }

    override fun reset() {
        feed.value = seedMenus
        menus.forEach { (storeId, flow) -> load(storeId, flow) }
    }

    private fun change(storeId: String, transform: (Menu) -> Menu) {
        val updated = feed.updateAndGet { all -> all[storeId]?.let { all + (storeId to transform(it)) } ?: all }[storeId] ?: return
        // Push to anyone showing the menu. A load in flight reads the updated feed when it finishes.
        menus[storeId]?.update { state -> if (state is LoadState.Success) LoadState.Success(updated) else state }
    }

    private fun load(storeId: String, target: MutableStateFlow<LoadState<Menu>>) {
        if (feed.value[storeId] == null) {
            target.value = LoadState.Error("No menu for store $storeId")
        } else {
            scope.simulateLoad(target, config) { feed.value.getValue(storeId) }
        }
    }
}

private fun MenuItem.withOptionSoldOut(optionId: String, soldOut: Boolean): MenuItem {
    if (modifierGroups.none { group -> group.options.any { it.id == optionId } }) return this
    return copy(
        modifierGroups = modifierGroups.map { group ->
            group.copy(options = group.options.map { if (it.id == optionId) it.copy(isSoldOut = soldOut) else it })
        },
    )
}

class FakeCartRepository : CartRepository, Resettable {

    private val _cart = MutableStateFlow<Cart?>(null)
    override val cart: StateFlow<Cart?> = _cart.asStateFlow()

    private val _preferredMode = MutableStateFlow<FulfillmentMode?>(null)
    override val preferredMode: StateFlow<FulfillmentMode?> = _preferredMode.asStateFlow()

    override fun setPreferredMode(mode: FulfillmentMode?) {
        _preferredMode.value = mode
    }

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

    override fun replaceWith(mode: FulfillmentMode, line: CartLine) {
        _cart.value = Cart(line.item.storeId, mode).addLine(line)
    }

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

/** Debug-only moves of an order, as the store's own app would make them. Only the fake implements this. */
interface OrderSimulator {
    /** Moves a paid order one status on, stopping at Ready or Served. Returns false when it can't move. */
    fun advance(orderId: String): Boolean

    /** Completes a Ready or Served order as if the customer never tapped [Confirm]. Returns false otherwise. */
    fun autoComplete(orderId: String): Boolean
}

/**
 * Orders over the seed data. Orders paid in this session move on by themselves, like a busy kitchen:
 * one status every [MockConfig.ORDER_STEP_INTERVAL], then auto-complete [MockConfig.AUTO_COMPLETE_AFTER]
 * after Ready or Served. Seed orders stay where they are, so QA can check them; the debug buttons move them.
 */
class FakeOrderRepository(
    private val scope: CoroutineScope,
    private val config: MockConfig,
    private val clock: Clock,
) : OrderRepository, OrderSimulator, Resettable {

    private val _orders = MutableStateFlow<LoadState<List<Order>>>(LoadState.Loading)
    override val orders: StateFlow<LoadState<List<Order>>> = _orders.asStateFlow()

    // Sized so tryEmit never drops a change while a slow collector catches up.
    private val _statusChanges = MutableSharedFlow<Order>(extraBufferCapacity = 64)
    override val statusChanges: Flow<Order> = _statusChanges.asSharedFlow()

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
        // Emitted after the update, which can run its block more than once.
        (result as? TransitionResult.Accepted)?.let { _statusChanges.tryEmit(it.order) }
        return result
    }

    override fun createDraft(cart: Cart, storeName: String): Order =
        // Eat-In is paid at check-in, so there is no payment method yet.
        newDraft(cart, storeName).also(::add)

    override fun placeOrder(cart: Cart, storeName: String): Order {
        val draft = newDraft(cart, storeName)
        val order = paid(draft) ?: error("A new draft can always be paid")
        add(order)
        _statusChanges.tryEmit(order)
        drive(order.id)
        return order
    }

    override fun payAtCheckIn(orderId: String): Order? {
        var result: Order? = null
        _orders.update { state ->
            result = null
            val orders = state.dataOrNull() ?: return@update state
            val draft = orders.firstOrNull { it.id == orderId }?.takeIf { it.awaitsCheckIn && it.checkIn != null }
                ?: return@update state
            val order = paid(draft) ?: return@update state
            result = order
            LoadState.Success(orders.map { if (it.id == orderId) order else it })
        }
        result?.let {
            _statusChanges.tryEmit(it)
            drive(it.id)
        }
        return result
    }

    override fun rate(orderId: String, stars: Int, tags: Set<String>): Boolean {
        // Checked here as a server would, not only in the screen.
        if (stars !in 1..5 || !tags.all { it in RATING_TAGS }) return false
        var rated = false
        _orders.update { state ->
            rated = false
            val orders = state.dataOrNull() ?: return@update state
            val order = orders.firstOrNull { it.id == orderId }?.takeIf { it.canBeRated } ?: return@update state
            rated = true
            val updated = order.copy(rating = OrderRating(stars, tags, clock.now()))
            LoadState.Success(orders.map { if (it.id == orderId) updated else it })
        }
        return rated
    }

    override fun advance(orderId: String): Boolean {
        val order = order(orderId)?.takeIf { it.isActive && !it.canConfirm } ?: return false
        val next = OrderStatusMachine.nextStatus(order.status, order.mode) ?: return false
        return transition(orderId, next) is TransitionResult.Accepted
    }

    override fun autoComplete(orderId: String): Boolean {
        if (order(orderId)?.canConfirm != true) return false
        // The state machine rejects this if the customer confirmed first, so an order completes once.
        return transition(orderId, OrderStatus.COMPLETED, CompletionReason.AUTO_COMPLETED) is TransitionResult.Accepted
    }

    private fun order(orderId: String): Order? = _orders.value.dataOrNull()?.firstOrNull { it.id == orderId }

    private fun newDraft(cart: Cart, storeName: String): Order {
        val known = _orders.value.dataOrNull() ?: (created.value + seedOrders(clock.now()))
        val number = (known.maxOfOrNull { it.number } ?: 0) + 1
        return Order.draft(
            id = "order-$number",
            number = number,
            storeId = cart.storeId,
            storeName = storeName,
            mode = cart.mode,
            lines = cart.lines,
            paymentMethodId = null,
            at = clock.now(),
        )
    }

    private fun add(order: Order) {
        created.update { listOf(order) + it }
        _orders.update { state -> if (state is LoadState.Success) LoadState.Success(listOf(order) + state.data) else state }
    }

    /** The draft after a mock payment with the default method: PAID for pickup, QUEUED for in-store. Null if it isn't a draft. */
    private fun paid(draft: Order): Order? {
        if (draft.status != OrderStatus.DRAFT) return null
        val next = OrderStatusMachine.nextStatus(draft.status, draft.mode) ?: return null
        val accepted = OrderStatusMachine.transition(draft, next, clock.now()) as? TransitionResult.Accepted ?: return null
        return accepted.order.copy(paymentMethodId = DEFAULT_PAYMENT_METHOD_ID)
    }

    /**
     * The mock kitchen for one paid order. Ends when the order completes or is gone (reset). A change made
     * during a wait, by a debug button or [Confirm], starts the wait again from the new status.
     */
    private fun drive(orderId: String) {
        scope.launch {
            while (true) {
                val before = order(orderId)?.takeIf { it.isActive } ?: return@launch
                delay(if (before.canConfirm) MockConfig.AUTO_COMPLETE_AFTER else MockConfig.ORDER_STEP_INTERVAL)
                config.liveOrders.first { it }
                val now = order(orderId) ?: return@launch
                if (now.history.size != before.history.size) continue
                if (now.canConfirm) autoComplete(orderId) else advance(orderId)
            }
        }
    }

    override fun checkIn(orderId: String, checkIn: CheckIn): Boolean {
        var recorded = false
        _orders.update { state ->
            recorded = false
            val orders = (state as? LoadState.Success)?.data ?: return@update state
            val updated = orders.firstOrNull { it.id == orderId }?.checkedIn(checkIn) ?: return@update state
            recorded = true
            LoadState.Success(orders.map { if (it.id == orderId) updated else it })
        }
        return recorded
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

    private companion object {
        val DEFAULT_PAYMENT_METHOD_ID = seedPaymentMethods.first { it.isDefault }.id
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

    // Declared before init: reset() uses it, and Kotlin runs initializers in declaration order.
    /** Messages added in this session. A reload keeps them, as a server would. */
    private val added = MutableStateFlow<List<InboxMessage>>(emptyList())

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

    override fun add(id: String, title: String, body: String, kind: InboxKind, orderId: String?) {
        if (added.value.any { it.id == id } || _messages.value.dataOrNull()?.any { it.id == id } == true) return
        val message = InboxMessage(id, title, body, receivedAt = clock.now(), isRead = false, orderId = orderId, kind = kind)
        added.update { listOf(message) + it }
        _messages.update { state -> if (state is LoadState.Success) LoadState.Success(listOf(message) + state.data) else state }
    }

    override fun reset() {
        loadJob?.cancel()
        added.value = emptyList()
        loadJob = scope.simulateLoad(_messages, config) { added.value + seedInbox(clock.now()) }
    }

    /** Debug only: removes every message, as for a customer who has never ordered. */
    fun clearAll() {
        loadJob?.cancel()
        added.value = emptyList()
        _messages.value = LoadState.Success(emptyList())
    }
}

/**
 * Simulated sign-in. Each call waits the loading delay in the caller's coroutine, so a test dispatcher
 * runs it on virtual time. Accepts only [SeedAuth.TEST_OTP]; Google and Apple fail when
 * [MockConfig.forceSsoFailure] is on.
 */
class FakeAuthRepository(private val config: MockConfig) : AuthRepository, Resettable {

    private val _session = MutableStateFlow<Session?>(null)
    override val session: StateFlow<Session?> = _session.asStateFlow()

    // Mock only: lowercase email to the password it was created with, so log-in works after sign-up.
    // A real backend keeps a salted hash on the server and never holds or returns the password.
    private val accounts = MutableStateFlow(seedAccounts)

    override suspend fun requestOtp(phone: PhoneNumber) {
        delay(config.loadingDelay.value)
    }

    override suspend fun verifyOtp(phone: PhoneNumber, code: String): AuthResult {
        delay(config.loadingDelay.value)
        if (code != SeedAuth.TEST_OTP) return AuthResult.Failure(AuthFailure.INVALID_CODE)
        return signIn(UserProfile(seedUser.displayName, phone = phone.formatted()), SignInMethod.PHONE)
    }

    override suspend fun signUpWithEmail(email: String, password: String): AuthResult {
        delay(config.loadingDelay.value)
        var created = false
        accounts.update { current ->
            created = email.lowercase() !in current
            if (created) current + (email.lowercase() to password) else current
        }
        if (!created) return AuthResult.Failure(AuthFailure.EMAIL_TAKEN)
        return signIn(UserProfile(seedUser.displayName, email = email), SignInMethod.EMAIL)
    }

    override suspend fun signInWithEmail(email: String, password: String): AuthResult {
        delay(config.loadingDelay.value)
        // One answer for an unknown email and a wrong password, so the error can't reveal which accounts exist.
        if (accounts.value[email.lowercase()] != password) return AuthResult.Failure(AuthFailure.WRONG_CREDENTIALS)
        return signIn(UserProfile(seedUser.displayName, email = email), SignInMethod.EMAIL)
    }

    override suspend fun requestPasswordReset(email: String) {
        delay(config.loadingDelay.value)
    }

    override suspend fun signInWith(provider: SsoProvider): AuthResult {
        delay(config.loadingDelay.value)
        if (config.forceSsoFailure.value) return AuthResult.Failure(AuthFailure.SSO_FAILED)
        val method = when (provider) {
            SsoProvider.GOOGLE -> SignInMethod.GOOGLE
            SsoProvider.APPLE -> SignInMethod.APPLE
        }
        return signIn(seedUser, method)
    }

    override fun signOut() {
        _session.value = null
    }

    override fun reset() {
        signOut()
        accounts.value = seedAccounts
    }

    private fun signIn(profile: UserProfile, method: SignInMethod): AuthResult {
        _session.value = Session(profile, method)
        return AuthResult.Success
    }
}
