package tech.vauldex.dekita.presentation.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import tech.vauldex.dekita.data.LoadState
import tech.vauldex.dekita.data.OrderRepository
import tech.vauldex.dekita.data.StoreRepository
import tech.vauldex.dekita.domain.Order
import tech.vauldex.dekita.domain.OrderStatus
import tech.vauldex.dekita.domain.Store
import tech.vauldex.dekita.presentation.STOP_TIMEOUT_MILLIS

/** A past or active order with its store. [store] is null when the store is no longer in the feed. */
data class HistoryOrderEntry(val order: Order, val store: Store?)

data class HistoryUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    /** Stores ordered from, most recent first, each once. */
    val recentStores: List<Store> = emptyList(),
    /** Orders, newest first. */
    val recentOrders: List<HistoryOrderEntry> = emptyList(),
) {
    val isEmpty: Boolean get() = !isLoading && error == null && recentOrders.isEmpty()
}

/** The decided customer's other shortcut: where they ordered before. Each row opens its store. */
class HistoryViewModel(
    private val storeRepository: StoreRepository,
    orderRepository: OrderRepository,
) : ViewModel() {

    val state: StateFlow<HistoryUiState> =
        combine(storeRepository.stores, orderRepository.orders) { stores, orders ->
            when {
                stores is LoadState.Error -> HistoryUiState(isLoading = false, error = stores.message)
                orders is LoadState.Error -> HistoryUiState(isLoading = false, error = orders.message)
                stores is LoadState.Success && orders is LoadState.Success -> {
                    val byId = stores.data.associateBy { it.id }
                    // Drafts aren't placed yet; they resume from Orders and the Discover banner instead.
                    val recent = orders.data.filter { it.status != OrderStatus.DRAFT }.sortedByDescending { it.createdAt }
                    HistoryUiState(
                        isLoading = false,
                        recentStores = recent.map { it.storeId }.distinct().mapNotNull { byId[it] },
                        recentOrders = recent.map { HistoryOrderEntry(it, byId[it.storeId]) },
                    )
                }
                else -> HistoryUiState(isLoading = true)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HistoryUiState())

    fun retry() = storeRepository.refresh()
}
