package tech.vauldex.dekita.presentation.store

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import tech.vauldex.dekita.data.CartRepository
import tech.vauldex.dekita.data.LoadState
import tech.vauldex.dekita.data.MenuRepository
import tech.vauldex.dekita.data.StoreRepository
import tech.vauldex.dekita.data.dataOrNull
import tech.vauldex.dekita.domain.Cart
import tech.vauldex.dekita.domain.Menu
import tech.vauldex.dekita.domain.Store
import tech.vauldex.dekita.presentation.STOP_TIMEOUT_MILLIS

data class StoreDetailUiState(
    val store: Store? = null,
    val menu: LoadState<Menu> = LoadState.Loading,
    val isFavorite: Boolean = false,
    /** The cart, from any store, when it has items. Drives the "View cart" button. */
    val cart: Cart? = null,
)

class StoreDetailViewModel(
    private val storeId: String,
    private val storeRepository: StoreRepository,
    menuRepository: MenuRepository,
    cartRepository: CartRepository,
) : ViewModel() {

    val state: StateFlow<StoreDetailUiState> =
        combine(
            storeRepository.stores,
            storeRepository.favoriteStoreIds,
            menuRepository.menu(storeId),
            cartRepository.cart,
        ) { stores, favorites, menu, cart ->
            StoreDetailUiState(
                store = stores.dataOrNull()?.firstOrNull { it.id == storeId },
                menu = menu,
                isFavorite = storeId in favorites,
                cart = cart?.takeUnless { it.isEmpty },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), StoreDetailUiState())

    fun toggleFavorite() = storeRepository.toggleFavorite(storeId)
}
