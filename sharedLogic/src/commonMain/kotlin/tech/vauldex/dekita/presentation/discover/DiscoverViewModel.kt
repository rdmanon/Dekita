package tech.vauldex.dekita.presentation.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import tech.vauldex.dekita.data.AuthRepository
import tech.vauldex.dekita.data.CartRepository
import tech.vauldex.dekita.data.LoadState
import tech.vauldex.dekita.data.OrderRepository
import tech.vauldex.dekita.data.StoreRepository
import tech.vauldex.dekita.data.dataOrNull
import tech.vauldex.dekita.domain.FavoriteItem
import tech.vauldex.dekita.domain.FulfillmentMode
import tech.vauldex.dekita.domain.Order
import tech.vauldex.dekita.domain.OrderStatus
import tech.vauldex.dekita.domain.Store
import tech.vauldex.dekita.domain.StoreAvailability
import tech.vauldex.dekita.domain.isActive
import tech.vauldex.dekita.presentation.STOP_TIMEOUT_MILLIS

data class DiscoverUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val filters: StoreFilters = StoreFilters(),
    /** Every category in the feed, in feed order, for the category chips and the filter sheet. */
    val categories: List<String> = emptyList(),
    val recommended: List<StoreMatch> = emptyList(),
    /** The filtered stores, nearest first. Paused stores stay in the list. */
    val stores: List<StoreMatch> = emptyList(),
    val favoriteItemCount: Int = 0,
    /** The store of the most recent order, for the History shortcut. Null when there are no orders. */
    val lastOrderStoreName: String? = null,
    /** An in-store draft waiting for check-in, for the Resume banner. Null when there is none, or for guests. */
    val draftToResume: Order? = null,
    /** The paid order updated most recently and not closed yet, for the banner that opens its ticket. Null for guests. */
    val activeOrder: Order? = null,
) {
    /** When a filter other than the mode is on, Discover shows only the result list. */
    val showsResults: Boolean get() = filters.isRefined

    val hasNoMatches: Boolean get() = !isLoading && error == null && stores.isEmpty()
}

/** The orders for Discover, and whether they belong to a signed-in customer. */
private class DiscoverOrders(val orders: LoadState<List<Order>>, val isSignedIn: Boolean)

class DiscoverViewModel(
    private val storeRepository: StoreRepository,
    orderRepository: OrderRepository,
    private val cartRepository: CartRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    // Every filter except the mode. The mode lives in the cart repository, so every Discover screen
    // (the Android tab, the iOS Discover and Search tabs) shows the same one and Cart Review starts in it.
    private val filters = MutableStateFlow(StoreFilters())

    private val filtersWithMode = combine(filters, cartRepository.preferredMode) { current, mode -> current.copy(mode = mode) }

    private val orders = combine(orderRepository.orders, authRepository.session) { orders, session ->
        DiscoverOrders(orders, isSignedIn = session != null)
    }

    val state: StateFlow<DiscoverUiState> =
        combine(
            storeRepository.stores,
            storeRepository.recommendedStoreIds,
            storeRepository.favoriteItems,
            orders,
            filtersWithMode,
            ::toUiState,
        ).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = toUiState(
                storeRepository.stores.value,
                storeRepository.recommendedStoreIds.value,
                storeRepository.favoriteItems.value,
                DiscoverOrders(orderRepository.orders.value, isSignedIn = authRepository.session.value != null),
                filters.value.copy(mode = cartRepository.preferredMode.value),
            ),
        )

    /** Picks In-store or Pickup, or null for all stores. The cart starts in this mode. */
    fun selectMode(mode: FulfillmentMode?) = cartRepository.setPreferredMode(mode)

    fun setQuery(query: String) = filters.update { it.copy(query = query) }

    /** Picking the selected limit again turns the filter off. */
    fun selectWalkLimit(limit: WalkLimit?) = filters.update { it.copy(walkLimit = limit.takeUnless { l -> l == it.walkLimit }) }

    fun toggleCategory(category: String) = filters.update { it.copy(categories = it.categories.toggle(category)) }

    fun toggleStatus(status: StoreAvailability) = filters.update { it.copy(statuses = it.statuses.toggle(status)) }

    /** Clears every filter, the mode and the search, so the full list shows again. */
    fun clearFilters() {
        filters.value = StoreFilters()
        cartRepository.setPreferredMode(null)
    }

    fun retry() = storeRepository.refresh()

    private fun toUiState(
        stores: LoadState<List<Store>>,
        recommendedIds: List<String>,
        favoriteItems: List<FavoriteItem>,
        orders: DiscoverOrders,
        filters: StoreFilters,
    ): DiscoverUiState {
        val loadedOrders = orders.orders.dataOrNull().orEmpty()
        // The banners name a customer's live orders, so a signed-out phone never shows them.
        val ownOrders = if (orders.isSignedIn) loadedOrders else emptyList()
        val base = DiscoverUiState(
            filters = filters,
            favoriteItemCount = favoriteItems.size,
            // A draft hasn't been placed yet, so it isn't the last order.
            lastOrderStoreName = loadedOrders.filter { it.status != OrderStatus.DRAFT }.maxByOrNull { it.createdAt }?.storeName,
            draftToResume = ownOrders.filter { it.awaitsCheckIn }.maxByOrNull { it.createdAt },
            activeOrder = ownOrders.filter { it.isActive }.maxByOrNull { it.lastUpdatedAt },
        )
        return when (stores) {
            LoadState.Loading -> base.copy(isLoading = true)
            is LoadState.Error -> base.copy(isLoading = false, error = stores.message)
            is LoadState.Success -> {
                val byId = stores.data.associateBy { it.id }
                base.copy(
                    isLoading = false,
                    categories = stores.data.map { it.category }.distinct(),
                    // Recommendations keep their own order rather than walking distance.
                    recommended = recommendedIds.mapNotNull { id -> byId[id]?.let(filters::match) },
                    stores = stores.data.filterStores(filters),
                )
            }
        }
    }
}

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value
