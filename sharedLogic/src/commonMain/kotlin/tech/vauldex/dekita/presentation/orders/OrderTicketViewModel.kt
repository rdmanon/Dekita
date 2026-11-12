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
import tech.vauldex.dekita.data.dataOrNull
import tech.vauldex.dekita.data.mock.OrderSimulator
import tech.vauldex.dekita.domain.CompletionReason
import tech.vauldex.dekita.domain.Order
import tech.vauldex.dekita.domain.OrderStatus
import tech.vauldex.dekita.domain.OrderStep
import tech.vauldex.dekita.domain.PaymentMethod
import tech.vauldex.dekita.domain.Session
import tech.vauldex.dekita.domain.TransitionResult
import tech.vauldex.dekita.domain.canBeRated
import tech.vauldex.dekita.domain.canConfirm
import tech.vauldex.dekita.domain.currentStep
import tech.vauldex.dekita.domain.paidAt
import tech.vauldex.dekita.domain.steps
import tech.vauldex.dekita.presentation.ORDER_PLACED_MESSAGE
import tech.vauldex.dekita.presentation.STOP_TIMEOUT_MILLIS
import tech.vauldex.dekita.presentation.label
import tech.vauldex.dekita.presentation.orderReadyMessage

enum class StepState { DONE, CURRENT, TODO }

data class TicketStep(val step: OrderStep, val label: String, val state: StepState) {
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
    /** Under the stepper: the notification text, the served confirmation, or how the order closed. */
    val message: String = "",
    /** True once the order is ready or served: the message shows in green and [Confirm] turns on. */
    val canConfirm: Boolean = false,
    /** RCPT-01 receipt: the payment method's masked label, such as "Test Visa •••• 4242". */
    val paymentLabel: String? = null,
    /** When the order was paid, in seconds since 1970. Each platform formats it in the device time zone. */
    val paidAtEpochSeconds: Long? = null,
) {
    /** "Rated 4 of 5 · Tasty", or null while unrated. */
    val ratingLabel: String? get() = order?.rating?.label

    /** A completed order that isn't rated yet offers "Rate this order". */
    val canRate: Boolean get() = order?.canBeRated == true

    val isCompleted: Boolean get() = order?.status == OrderStatus.COMPLETED

    val numberLabel: String get() = order?.let { "#${it.number}" }.orEmpty()

    val storeLine: String get() = order?.let { "${it.storeName} · ${it.mode.label}" }.orEmpty()

    val tableLabel: String? get() = order?.checkIn?.tableNumber?.let { "Table $it" }

    /** Under the [Confirm] button. Null once the order is completed. */
    val confirmHint: String?
        get() = when {
            order == null || isCompleted -> null
            canConfirm -> "Tap Confirm once you've received your order."
            else -> "You can confirm once your order is ready."
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
) : ViewModel() {

    val state: StateFlow<OrderTicketUiState> =
        combine(orderRepository.orders, authRepository.session, paymentRepository.methods, ::toUiState)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                toUiState(orderRepository.orders.value, authRepository.session.value, paymentRepository.methods.value),
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
    ): OrderTicketUiState {
        val isSignedIn = session != null
        val order = orders.dataOrNull()
            ?.firstOrNull { it.id == orderId && it.status != OrderStatus.DRAFT }
            ?.takeIf { isSignedIn }
            ?: return OrderTicketUiState(isLoading = orders is LoadState.Loading, isSignedIn = isSignedIn)
        return OrderTicketUiState(
            isLoading = false,
            isSignedIn = true,
            order = order,
            steps = order.ticketSteps(),
            message = order.ticketMessage(),
            canConfirm = order.canConfirm,
            paymentLabel = order.paymentMethodId?.let { id -> methods.dataOrNull()?.firstOrNull { it.id == id }?.label },
            paidAtEpochSeconds = order.paidAt?.epochSeconds,
        )
    }
}

private fun Order.ticketSteps(): List<TicketStep> {
    val currentIndex = steps.indexOf(currentStep)
    return steps.mapIndexed { index, step ->
        val state = when {
            status == OrderStatus.COMPLETED || index < currentIndex -> StepState.DONE
            index == currentIndex -> StepState.CURRENT
            else -> StepState.TODO
        }
        TicketStep(step, step.label, state)
    }
}

private fun Order.ticketMessage(): String = when (status) {
    OrderStatus.COMPLETED -> "Completed · ${completionReason?.label.orEmpty()}"
    OrderStatus.READY_FOR_PICKUP -> orderReadyMessage(number)
    OrderStatus.SERVED -> checkIn?.tableNumber?.let { "Served at table $it. Enjoy!" } ?: "Your order has been served. Enjoy!"
    // Eat-In is paid after check-in, so the customer is already at the store.
    else -> if (checkIn != null) "The kitchen has your order. You'll be served shortly." else ORDER_PLACED_MESSAGE
}
