package tech.vauldex.dekita.presentation.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import tech.vauldex.dekita.data.LoadState
import tech.vauldex.dekita.data.StoreRepository
import tech.vauldex.dekita.domain.FavoriteItem
import tech.vauldex.dekita.domain.Store
import tech.vauldex.dekita.presentation.STOP_TIMEOUT_MILLIS

/** A favorited item with its store, so the row can show the store's live status. */
data class FavoriteItemEntry(val item: FavoriteItem, val store: Store)

data class FavoritesUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val items: List<FavoriteItemEntry> = emptyList(),
    val stores: List<Store> = emptyList(),
) {
    val isEmpty: Boolean get() = !isLoading && error == null && items.isEmpty() && stores.isEmpty()
}

/** The decided customer's shortcut: favorited items and stores. Each opens its store. */
class FavoritesViewModel(private val storeRepository: StoreRepository) : ViewModel() {

    val state: StateFlow<FavoritesUiState> =
        combine(storeRepository.stores, storeRepository.favoriteItems, storeRepository.favoriteStoreIds) { stores, items, storeIds ->
            when (stores) {
                LoadState.Loading -> FavoritesUiState(isLoading = true)
                is LoadState.Error -> FavoritesUiState(isLoading = false, error = stores.message)
                is LoadState.Success -> {
                    val byId = stores.data.associateBy { it.id }
                    FavoritesUiState(
                        isLoading = false,
                        // A favorite whose store left the feed has nothing to open, so it is not listed.
                        items = items.mapNotNull { item -> byId[item.storeId]?.let { FavoriteItemEntry(item, it) } },
                        stores = stores.data.filter { it.id in storeIds }.sortedBy { it.walkMinutes },
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), FavoritesUiState())

    fun retry() = storeRepository.refresh()
}
