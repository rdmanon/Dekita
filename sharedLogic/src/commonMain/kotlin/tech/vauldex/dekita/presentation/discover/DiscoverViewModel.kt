package tech.vauldex.dekita.presentation.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import tech.vauldex.dekita.data.LoadState
import tech.vauldex.dekita.data.StoreRepository
import tech.vauldex.dekita.domain.Store
import tech.vauldex.dekita.presentation.STOP_TIMEOUT_MILLIS

data class DiscoverUiState(
    val isLoading: Boolean = true,
    val stores: List<Store> = emptyList(),
    val favoriteStoreIds: Set<String> = emptySet(),
    val error: String? = null,
) {
    val favoritesCount: Int get() = favoriteStoreIds.size
}

class DiscoverViewModel(private val storeRepository: StoreRepository) : ViewModel() {

    val state: StateFlow<DiscoverUiState> =
        combine(storeRepository.stores, storeRepository.favoriteStoreIds, ::toUiState)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = toUiState(storeRepository.stores.value, storeRepository.favoriteStoreIds.value),
            )

    fun refresh() = storeRepository.refresh()

    private fun toUiState(stores: LoadState<List<Store>>, favorites: Set<String>) = when (stores) {
        LoadState.Loading -> DiscoverUiState(isLoading = true, favoriteStoreIds = favorites)
        is LoadState.Error -> DiscoverUiState(isLoading = false, favoriteStoreIds = favorites, error = stores.message)
        is LoadState.Success -> DiscoverUiState(isLoading = false, stores = stores.data, favoriteStoreIds = favorites)
    }
}
