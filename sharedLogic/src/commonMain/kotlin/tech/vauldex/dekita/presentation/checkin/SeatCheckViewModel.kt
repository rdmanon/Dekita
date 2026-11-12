package tech.vauldex.dekita.presentation.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import tech.vauldex.dekita.data.CartRepository
import tech.vauldex.dekita.data.LoadState
import tech.vauldex.dekita.data.OrderRepository
import tech.vauldex.dekita.data.PaymentRepository
import tech.vauldex.dekita.data.StoreRepository
import tech.vauldex.dekita.data.dataOrNull
import tech.vauldex.dekita.domain.Cart
import tech.vauldex.dekita.domain.FulfillmentMode
import tech.vauldex.dekita.domain.Order
import tech.vauldex.dekita.domain.PaymentMethod
import tech.vauldex.dekita.domain.PaymentType
import tech.vauldex.dekita.domain.Store
import tech.vauldex.dekita.domain.StoreAvailability
import tech.vauldex.dekita.domain.TransitionResult
import tech.vauldex.dekita.presentation.STOP_TIMEOUT_MILLIS

/** Where the seat check is. */
enum class SeatCheckStep {
    /** "Have you found a seat?" Yes / Not yet. */
    QUESTION,

    /** The store is full, or the customer has no seat: Switch to Takeout, Wait in line, Cancel / Exit. */
    NO_SEAT,

    /** 1-tap payment: the total, the saved methods and "Pay ¥…". */
    PAYMENT,
}

data class SeatCheckUiState(
    val isLoading: Boolean = true,
    /** The checked-in draft. Null while loading or when there is no such order. */
    val order: Order? = null,
    val store: Store? = null,
    val step: SeatCheckStep = SeatCheckStep.QUESTION,
    /** Saved methods, this platform's wallet first. The other platform's wallet is left out. */
    val paymentMethods: List<PaymentMethod> = emptyList(),
    val selectedMethodId: String? = null,
    /** Set after paying here: the order number now in the kitchen. */
    val sentOrderNumber: Int? = null,
    /** Switch to Takeout waits for the customer to agree to replace the cart from this store. */
    val pendingReplaceCartStoreName: String? = null,
) {
    val isSent: Boolean get() = sentOrderNumber != null

    /**
     * Nothing to seat-check: the order is gone, not a checked-in Eat-In draft, or no longer a draft (and was
     * not just paid here).
     */
    val isUnavailable: Boolean
        get() = !isLoading && !isSent && (order == null || !order.awaitsCheckIn || order.checkIn == null)

    /** No seat for an Eat-In order: every seat taken, or the store at capacity. The question is skipped. */
    val isStoreFull: Boolean get() = store != null && (store.seatsFull || store.availability == StoreAvailability.FULL)

    val isPaused: Boolean get() = store?.availability == StoreAvailability.PAUSED

    /** Why Switch to Takeout is off, or null when it can be used. */
    val takeoutUnavailableReason: String?
        get() = when {
            store == null -> null
            !store.supports(FulfillmentMode.PICKUP) -> "${store.name} doesn't offer takeout."
            store.availability != StoreAvailability.OPEN -> "${store.name} isn't taking takeout orders right now."
            else -> null
        }

    val canSwitchToTakeout: Boolean get() = store != null && takeoutUnavailableReason == null

    val selectedMethod: PaymentMethod? get() = paymentMethods.firstOrNull { it.id == selectedMethodId }

    val canPay: Boolean
        get() = step == SeatCheckStep.PAYMENT && !isUnavailable && !isSent && !isPaused && selectedMethod != null
}

sealed interface SeatCheckEvent {
    /** The draft is the cart again, in Takeout. Open Cart Review on that store. */
    data class SwitchedToTakeout(val storeId: String) : SeatCheckEvent

    /** Wait in line: back to check-in, where Start Cooking brings the seat check back. */
    data object WaitInLine : SeatCheckEvent

    /** Cancel / Exit: back to Discover. The draft is kept. */
    data object Exited : SeatCheckEvent
}

/**
 * Seat verification (SEAT-01), opened by Start Cooking once the customer has checked in (CHECKIN-01).
 * It asks "Have you found a seat?" so food is never cooked for someone still waiting for a table. Yes opens
 * 1-tap payment, which sends the order to the kitchen. A full store skips the question and goes straight to
 * the fallbacks: Switch to Takeout, Wait in line or Cancel / Exit. None of them loses the order: it is either
 * still the draft or back in the cart.
 *
 * [wallet] is this platform's wallet (Apple Pay on iOS, Google Pay on Android).
 */
class SeatCheckViewModel(
    private val orderId: String,
    private val orderRepository: OrderRepository,
    private val storeRepository: StoreRepository,
    private val cartRepository: CartRepository,
    private val paymentRepository: PaymentRepository,
    private val wallet: PaymentType,
) : ViewModel() {

    private data class Local(
        /** The customer's own step. Null until they answer, which shows the question or, when full, the fallbacks. */
        val step: SeatCheckStep? = null,
        val selectedMethodId: String? = null,
        val sentOrderNumber: Int? = null,
        val pendingReplaceCartStoreName: String? = null,
    )

    private val local = MutableStateFlow(Local())

    val state: StateFlow<SeatCheckUiState> =
        combine(orderRepository.orders, storeRepository.stores, paymentRepository.methods, local, ::toUiState)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = currentState(),
            )

    private val _events = Channel<SeatCheckEvent>(Channel.BUFFERED)
    val events: Flow<SeatCheckEvent> = _events.receiveAsFlow()

    /** The answer to "Have you found a seat?". */
    fun answerSeat(hasSeat: Boolean) {
        if (currentState().step != SeatCheckStep.QUESTION) return
        local.update { it.copy(step = if (hasSeat) SeatCheckStep.PAYMENT else SeatCheckStep.NO_SEAT) }
    }

    /** Closing the payment sheet goes back to the question. */
    fun closePayment() {
        if (currentState().step == SeatCheckStep.PAYMENT) local.update { it.copy(step = null) }
    }

    /** Wait in line: back to check-in, which keeps the check-in, so Start Cooking asks again later. */
    fun waitInLine() {
        local.update { it.copy(step = null) }
        _events.trySend(SeatCheckEvent.WaitInLine)
    }

    /**
     * Switch to Takeout: the draft's items go back into the cart in Takeout and Cart Review opens.
     * Asks first when the cart already has items, since those would be replaced.
     */
    fun switchToTakeout() {
        val current = currentState()
        val order = current.order ?: return
        if (!current.canSwitchToTakeout || current.isUnavailable) return
        val cart = cartRepository.cart.value
        if (cart != null && !cart.isEmpty) {
            val storeName = storeRepository.stores.value.dataOrNull()?.firstOrNull { it.id == cart.storeId }?.name
            local.update { it.copy(pendingReplaceCartStoreName = storeName ?: "another store") }
            return
        }
        moveToTakeout(order)
    }

    fun confirmReplaceCart() {
        if (local.value.pendingReplaceCartStoreName == null) return
        local.update { it.copy(pendingReplaceCartStoreName = null) }
        currentState().order?.let(::moveToTakeout)
    }

    fun dismissReplaceCart() {
        local.update { it.copy(pendingReplaceCartStoreName = null) }
    }

    /** Cancel / Exit: back to Discover. The draft stays in Orders and on the Discover banner. */
    fun exit() {
        _events.trySend(SeatCheckEvent.Exited)
    }

    fun selectPaymentMethod(methodId: String) {
        if (currentState().paymentMethods.any { it.id == methodId }) local.update { it.copy(selectedMethodId = methodId) }
    }

    /** Pays with the selected method and sends the order to the kitchen. Mock only: nothing is charged. */
    fun pay() {
        val current = currentState()
        if (!current.canPay) return
        val method = current.selectedMethod ?: return
        val result = orderRepository.startCooking(orderId, method.id)
        if (result is TransitionResult.Accepted) {
            local.update { it.copy(step = null, sentOrderNumber = result.order.number) }
        }
    }

    private fun moveToTakeout(order: Order) {
        val draft = orderRepository.discardDraft(order.id) ?: return
        cartRepository.restore(Cart(draft.storeId, FulfillmentMode.PICKUP, draft.lines))
        local.update { it.copy(step = null, pendingReplaceCartStoreName = null) }
        _events.trySend(SeatCheckEvent.SwitchedToTakeout(draft.storeId))
    }

    /** The state right now, read from the sources, so actions never act on a value [state] has not caught up to. */
    private fun currentState(): SeatCheckUiState = toUiState(
        orderRepository.orders.value,
        storeRepository.stores.value,
        paymentRepository.methods.value,
        local.value,
    )

    private fun toUiState(
        orders: LoadState<List<Order>>,
        stores: LoadState<List<Store>>,
        methods: LoadState<List<PaymentMethod>>,
        local: Local,
    ): SeatCheckUiState {
        val order = orders.dataOrNull()?.firstOrNull { it.id == orderId }
        val store = order?.let { o -> stores.dataOrNull()?.firstOrNull { it.id == o.storeId } }
        val available = methods.dataOrNull().orEmpty().forPlatform(wallet)
        val selected = local.selectedMethodId?.takeIf { id -> available.any { it.id == id } }
            ?: available.firstOrNull { it.type == wallet }?.id
            ?: available.firstOrNull { it.isDefault }?.id
            ?: available.firstOrNull()?.id
        val full = store != null && (store.seatsFull || store.availability == StoreAvailability.FULL)
        return SeatCheckUiState(
            isLoading = orders is LoadState.Loading,
            order = order,
            store = store,
            step = local.step ?: if (full) SeatCheckStep.NO_SEAT else SeatCheckStep.QUESTION,
            paymentMethods = available,
            selectedMethodId = selected,
            sentOrderNumber = local.sentOrderNumber,
            pendingReplaceCartStoreName = local.pendingReplaceCartStoreName,
        )
    }
}

private val WALLETS = setOf(PaymentType.APPLE_PAY, PaymentType.GOOGLE_PAY)

/** This platform's wallet first, then the rest in their saved order; the other platform's wallet is left out. */
private fun List<PaymentMethod>.forPlatform(wallet: PaymentType): List<PaymentMethod> =
    filter { it.type == wallet } + filter { it.type !in WALLETS }
