package tech.vauldex.dekita.presentation.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import tech.vauldex.dekita.data.AuthRepository
import tech.vauldex.dekita.data.InboxRepository
import tech.vauldex.dekita.data.LoadState
import tech.vauldex.dekita.data.OrderRepository
import tech.vauldex.dekita.data.dataOrNull
import tech.vauldex.dekita.domain.InboxKind
import tech.vauldex.dekita.domain.InboxMessage
import tech.vauldex.dekita.domain.Order
import tech.vauldex.dekita.domain.OrderStatus
import tech.vauldex.dekita.presentation.STOP_TIMEOUT_MILLIS

/** An inbox message and, for a rate prompt, whether its order is rated by now. */
data class InboxRow(val message: InboxMessage, val isDone: Boolean) {
    val isRatePrompt: Boolean get() = message.kind == InboxKind.RATE_ORDER
}

data class OrdersUiState(
    val isLoading: Boolean = true,
    /** Paid orders not closed yet, and in-store drafts waiting for check-in. Newest first. */
    val active: List<Order> = emptyList(),
    /** In-app messages, newest first. RCPT-01 moved these here from Account. */
    val inbox: List<InboxRow> = emptyList(),
    /** Completed orders, newest first. Each opens as a receipt. */
    val receipts: List<Order> = emptyList(),
    /** Guests see a sign-in prompt instead of the order list. */
    val isSignedIn: Boolean = false,
) {
    val isEmpty: Boolean get() = !isLoading && active.isEmpty() && inbox.isEmpty() && receipts.isEmpty()
}

/**
 * The Orders inbox (RCPT-01): active orders on top, then inbox messages, then past receipts, each newest
 * first. A rate prompt counts as done once its order is rated, so it can't go out of step with the order.
 */
class OrdersViewModel(
    orderRepository: OrderRepository,
    private val inboxRepository: InboxRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    val state: StateFlow<OrdersUiState> =
        combine(orderRepository.orders, inboxRepository.messages, authRepository.session) { load, messages, session ->
            val isSignedIn = session != null
            when (load) {
                is LoadState.Success -> {
                    val (done, active) = load.data
                        .sortedByDescending { it.lastUpdatedAt }
                        .partition { it.status == OrderStatus.COMPLETED }
                    val rated = load.data.filter { it.rating != null }.map { it.id }.toSet()
                    val inbox = messages.dataOrNull().orEmpty()
                        .sortedByDescending { it.receivedAt }
                        .map { InboxRow(it, isDone = it.kind == InboxKind.RATE_ORDER && it.orderId in rated) }
                    OrdersUiState(isLoading = false, active = active, inbox = inbox, receipts = done, isSignedIn = isSignedIn)
                }
                is LoadState.Error -> OrdersUiState(isLoading = false, isSignedIn = isSignedIn)
                LoadState.Loading -> OrdersUiState(isLoading = true, isSignedIn = isSignedIn)
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            OrdersUiState(isSignedIn = authRepository.session.value != null),
        )

    /** Marks a message read when it is opened. */
    fun openMessage(messageId: String) = inboxRepository.markRead(messageId)
}
