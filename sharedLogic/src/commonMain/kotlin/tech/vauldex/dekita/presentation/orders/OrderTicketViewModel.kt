package tech.vauldex.dekita.presentation.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import tech.vauldex.dekita.data.AuthRepository
import tech.vauldex.dekita.data.LoadState
import tech.vauldex.dekita.data.OrderRepository
import tech.vauldex.dekita.data.dataOrNull
import tech.vauldex.dekita.data.mock.OrderSimulator
import tech.vauldex.dekita.domain.CompletionReason
import tech.vauldex.dekita.domain.Order
import tech.vauldex.dekita.domain.OrderStatus
import tech.vauldex.dekita.domain.OrderStep
import tech.vauldex.dekita.domain.Session
import tech.vauldex.dekita.domain.TransitionResult
import tech.vauldex.dekita.domain.canConfirm
import tech.vauldex.dekita.domain.currentStep
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
) {
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
class OrderTicketViewModel(
    private val orderId: String,
    private val orderRepository: OrderRepository,
    private val authRepository: AuthRepository,
    private val orderSimulator: OrderSimulator,
) : ViewModel() {

    val state: StateFlow<OrderTicketUiState> =
        combine(orderRepository.orders, authRepository.session, ::toUiState)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                toUiState(orderRepository.orders.value, authRepository.session.value),
            )

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

    private fun toUiState(orders: LoadState<List<Order>>, session: Session?): OrderTicketUiState {
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
