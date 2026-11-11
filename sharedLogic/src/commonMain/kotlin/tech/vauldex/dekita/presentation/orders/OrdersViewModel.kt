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
import tech.vauldex.dekita.domain.Order
import tech.vauldex.dekita.domain.OrderStatus
import tech.vauldex.dekita.presentation.STOP_TIMEOUT_MILLIS

data class OrdersUiState(
    val isLoading: Boolean = true,
    val active: List<Order> = emptyList(),
    val history: List<Order> = emptyList(),
    /** Guests see a sign-in prompt instead of the order list. */
    val isSignedIn: Boolean = false,
)

class OrdersViewModel(orderRepository: OrderRepository, authRepository: AuthRepository) : ViewModel() {

    val state: StateFlow<OrdersUiState> =
        combine(orderRepository.orders, authRepository.session) { load, session ->
            val isSignedIn = session != null
            when (load) {
                is LoadState.Success -> {
                    val (done, active) = load.data
                        .sortedByDescending { it.lastUpdatedAt }
                        .partition { it.status == OrderStatus.COMPLETED }
                    OrdersUiState(isLoading = false, active = active, history = done, isSignedIn = isSignedIn)
                }
                is LoadState.Error -> OrdersUiState(isLoading = false, isSignedIn = isSignedIn)
                LoadState.Loading -> OrdersUiState(isLoading = true, isSignedIn = isSignedIn)
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            OrdersUiState(isSignedIn = authRepository.session.value != null),
        )
}
