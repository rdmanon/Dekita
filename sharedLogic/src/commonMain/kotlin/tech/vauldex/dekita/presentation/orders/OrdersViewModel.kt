package tech.vauldex.dekita.presentation.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import tech.vauldex.dekita.data.LoadState
import tech.vauldex.dekita.data.OrderRepository
import tech.vauldex.dekita.domain.Order
import tech.vauldex.dekita.domain.OrderStatus
import tech.vauldex.dekita.presentation.STOP_TIMEOUT_MILLIS

data class OrdersUiState(
    val isLoading: Boolean = true,
    val active: List<Order> = emptyList(),
    val history: List<Order> = emptyList(),
)

class OrdersViewModel(orderRepository: OrderRepository) : ViewModel() {

    val state: StateFlow<OrdersUiState> = orderRepository.orders
        .map { load ->
            when (load) {
                is LoadState.Success -> {
                    val (done, active) = load.data
                        .sortedByDescending { it.lastUpdatedAt }
                        .partition { it.status == OrderStatus.COMPLETED }
                    OrdersUiState(isLoading = false, active = active, history = done)
                }
                is LoadState.Error -> OrdersUiState(isLoading = false)
                LoadState.Loading -> OrdersUiState(isLoading = true)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), OrdersUiState())
}
