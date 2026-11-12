package tech.vauldex.dekita.presentation.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tech.vauldex.dekita.data.AuthRepository
import tech.vauldex.dekita.data.InboxRepository
import tech.vauldex.dekita.data.LoadState
import tech.vauldex.dekita.data.OrderRepository
import tech.vauldex.dekita.data.PaymentRepository
import tech.vauldex.dekita.data.StoreRepository
import tech.vauldex.dekita.data.dataOrNull
import tech.vauldex.dekita.data.mock.OrderSimulator
import tech.vauldex.dekita.domain.CompletionReason
import tech.vauldex.dekita.domain.FulfillmentMode
import tech.vauldex.dekita.domain.Order
import tech.vauldex.dekita.domain.OrderStatus
import tech.vauldex.dekita.domain.OrderStep
import tech.vauldex.dekita.domain.PaymentMethod
import tech.vauldex.dekita.domain.Session
import tech.vauldex.dekita.domain.Store
import tech.vauldex.dekita.domain.TransitionResult
import tech.vauldex.dekita.domain.canBeRated
import tech.vauldex.dekita.domain.canConfirm
import tech.vauldex.dekita.domain.currentStep
import tech.vauldex.dekita.domain.paidAt
import tech.vauldex.dekita.domain.steps
import tech.vauldex.dekita.presentation.STOP_TIMEOUT_MILLIS
import tech.vauldex.dekita.presentation.label

enum class StepState { DONE, CURRENT, TODO }

data class TicketStep(
    val step: OrderStep,
    val label: String,
    val state: StepState,
    /** UI-01: when the order reached this step, in seconds since 1970. Null until it does. */
    val atEpochSeconds: Long? = null,
) {
    /** For TalkBack and VoiceOver, for example "Cooking, step 2 of 3, current". */
    fun spokenDescription(position: Int, count: Int): String {
        val status = when (state) {
            StepState.DONE -> "done"
            StepState.CURRENT -> "current"
            StepState.TODO -> "not started"
        }
        return "$label, step $position of $count, $status"
    }
}

data class OrderTicketUiState(
    val isLoading: Boolean = true,
    val isSignedIn: Boolean = false,
    /** Null when there is no paid order with this id, or while signed out. */
    val order: Order? = null,
    val steps: List<TicketStep> = emptyList(),
    /** UI-01: the banner at the top of the ticket, for example "Your order will be ready shortly." */
    val bannerTitle: String = "",
    /** The banner's second line, for example "We'll let you know when it's served." */
    val bannerDetail: String = "",
    /** True once the order is ready or served: the message shows in green and [Confirm] turns on. */
    val canConfirm: Boolean = false,
    /** RCPT-01 receipt: the payment method's masked label, such as "Test Visa •••• 4242". */
    val paymentLabel: String? = null,
    /** When the order was paid, in seconds since 1970. Each platform formats it in the device time zone. */
    val paidAtEpochSeconds: Long? = null,
    /** UI-01: the store's usual cooking time, "Usually 6–8 min", while the order is cooking. Null otherwise. */
    val cookingNote: String? = null,
) {
    /** "Rated 4 of 5 · Tasty", or null while unrated. */
    val ratingLabel: String? get() = order?.rating?.label

    /** A completed order that isn't rated yet offers "Rate this order". */
    val canRate: Boolean get() = order?.canBeRated == true

    val isCompleted: Boolean get() = order?.status == OrderStatus.COMPLETED

    val numberLabel: String get() = order?.let { "#${it.number}" }.orEmpty()

    val storeLine: String get() = order?.let { "${it.storeName} · ${it.mode.label}" }.orEmpty()

    val tableLabel: String? get() = order?.checkIn?.tableNumber?.let { "Table $it" }

    /** UI-01: on the number card, for example "Paid ¥1,550 with Test Visa •••• 4242". */
    val paidLine: String
        get() = order?.let { order ->
            val total = order.total.formatted()
            paymentLabel?.let { "Paid $total with $it" } ?: "Paid $total"
        }.orEmpty()

    /** Above the confirm button. Null once the order is completed. */
    val confirmHint: String?
        get() = when {
            order == null || isCompleted -> null
            canConfirm -> "Got your order? Confirm to close it."
            order.mode == FulfillmentMode.PICKUP -> "Confirm after you pick up your order."
            else -> "Confirm after your order is served."
        }
}

/**
 * The order ticket: the number to show at the counter, live status, and [Confirm]. Status changes reach
 * it without a refresh. Confirm, or auto-complete when the customer doesn't tap it, closes the order.
 */
sealed interface OrderTicketEvent {
    /** Open Rate & Add to Favorites: the order auto-completed and was never rated or skipped (RCPT-01). */
    data object OpenRating : OrderTicketEvent
}

class OrderTicketViewModel(
    private val orderId: String,
    private val orderRepository: OrderRepository,
    private val authRepository: AuthRepository,
    private val paymentRepository: PaymentRepository,
    private val inboxRepository: InboxRepository,
    private val orderSimulator: OrderSimulator,
    private val storeRepository: StoreRepository,
) : ViewModel() {

    val state: StateFlow<OrderTicketUiState> =
        combine(orderRepository.orders, authRepository.session, paymentRepository.methods, storeRepository.stores, ::toUiState)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                toUiState(
                    orderRepository.orders.value,
                    authRepository.session.value,
                    paymentRepository.methods.value,
                    storeRepository.stores.value,
                ),
            )

    private val _events = Channel<OrderTicketEvent>(Channel.BUFFERED)
    val events: Flow<OrderTicketEvent> = _events.receiveAsFlow()

    init {
        // "Shown the next time the customer opens that order": checked once, when the ticket opens.
        // An order that auto-completes while the ticket is open offers "Rate this order" instead.
        viewModelScope.launch {
            val opened = state.first { !it.isLoading }.order ?: return@launch
            val skipped = inboxRepository.messages.value.dataOrNull().orEmpty().any { it.id == ratePromptId(opened.id) }
            if (opened.canBeRated && opened.completionReason == CompletionReason.AUTO_COMPLETED && !skipped) {
                _events.send(OrderTicketEvent.OpenRating)
            }
        }
    }

    /**
     * [Confirm]: closes the order as received. Returns true when it did, so the screen opens
     * Rate & Add to Favorites. Returns false before the order is ready, after it closed, or for a second tap.
     */
    fun confirm(): Boolean {
        if (authRepository.session.value == null) return false
        val result = orderRepository.transition(orderId, OrderStatus.COMPLETED, CompletionReason.CUSTOMER_CONFIRMED)
        return result is TransitionResult.Accepted
    }

    /** Debug only: the store moves the order one status on. */
    fun debugAdvance() {
        orderSimulator.advance(orderId)
    }

    /** Debug only: the auto-complete time passes now. */
    fun debugAutoComplete() {
        orderSimulator.autoComplete(orderId)
    }

    private fun toUiState(
        orders: LoadState<List<Order>>,
        session: Session?,
        methods: LoadState<List<PaymentMethod>>,
        stores: LoadState<List<Store>>,
    ): OrderTicketUiState {
        val isSignedIn = session != null
        val order = orders.dataOrNull()
            ?.firstOrNull { it.id == orderId && it.status != OrderStatus.DRAFT }
            ?.takeIf { isSignedIn }
            ?: return OrderTicketUiState(isLoading = orders is LoadState.Loading, isSignedIn = isSignedIn)
        val steps = order.ticketSteps()
        val store = stores.dataOrNull()?.firstOrNull { it.id == order.storeId }
        return OrderTicketUiState(
            isLoading = false,
            isSignedIn = true,
            order = order,
            steps = steps,
            bannerTitle = order.bannerTitle(),
            bannerDetail = order.bannerDetail(),
            canConfirm = order.canConfirm,
            paymentLabel = order.paymentMethodId?.let { id -> methods.dataOrNull()?.firstOrNull { it.id == id }?.label },
            paidAtEpochSeconds = order.paidAt?.epochSeconds,
            cookingNote = store?.prepMinutes
                ?.takeIf { steps.any { it.step == OrderStep.COOKING && it.state == StepState.CURRENT } }
                ?.let(::usuallyLabel),
        )
    }
}

/** "Usually 6–8 min", or "Usually 6 min" when the range is one number. */
internal fun usuallyLabel(minutes: IntRange): String =
    if (minutes.first == minutes.last) "Usually ${minutes.first} min" else "Usually ${minutes.first}–${minutes.last} min"

/** The ticket step a status belongs to. Null for statuses that come before or after the steps. */
private fun stepFor(status: OrderStatus): OrderStep? = when (status) {
    OrderStatus.PAID, OrderStatus.QUEUED -> OrderStep.RECEIVED
    OrderStatus.PREPARING -> OrderStep.COOKING
    OrderStatus.READY_FOR_PICKUP -> OrderStep.READY
    OrderStatus.SERVED -> OrderStep.SERVED
    OrderStatus.DRAFT, OrderStatus.COMPLETED -> null
}

private fun Order.ticketSteps(): List<TicketStep> {
    val currentIndex = steps.indexOf(currentStep)
    return steps.mapIndexed { index, step ->
        val state = when {
            status == OrderStatus.COMPLETED || index < currentIndex -> StepState.DONE
            index == currentIndex -> StepState.CURRENT
            else -> StepState.TODO
        }
        val reachedAt = history.firstOrNull { stepFor(it.status) == step }?.at?.epochSeconds
        TicketStep(step, step.label, state, atEpochSeconds = reachedAt)
    }
}

private fun Order.bannerTitle(): String = when (status) {
    OrderStatus.COMPLETED -> "Completed"
    OrderStatus.READY_FOR_PICKUP -> "Your order is ready for pickup."
    OrderStatus.SERVED -> "Your order has been served."
    else -> "Your order will be ready shortly."
}

private fun Order.bannerDetail(): String = when (status) {
    OrderStatus.COMPLETED -> completionReason?.label.orEmpty()
    OrderStatus.READY_FOR_PICKUP -> "Pick it up at the $storeName counter."
    OrderStatus.SERVED -> checkIn?.tableNumber?.let { "Served at table $it. Enjoy!" } ?: "Enjoy! Confirm below once you have it."
    else -> if (mode == FulfillmentMode.PICKUP) "We'll let you know when it's ready for pickup." else "We'll let you know when it's served."
}
