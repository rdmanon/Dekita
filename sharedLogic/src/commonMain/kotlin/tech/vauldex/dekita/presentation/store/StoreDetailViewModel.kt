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
import tech.vauldex.dekita.domain.Menu
import tech.vauldex.dekita.domain.Money
import tech.vauldex.dekita.domain.Store
import tech.vauldex.dekita.presentation.STOP_TIMEOUT_MILLIS

data class StoreDetailUiState(
    val store: Store? = null,
    val menu: LoadState<Menu> = LoadState.Loading,
    val isFavorite: Boolean = false,
    /** Items in the cart when it belongs to this store; 0 otherwise. Drives the View cart button. */
    val cartItemCount: Int = 0,
    val cartTotal: Money = Money.ZERO,
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
            val ownCart = cart?.takeIf { it.storeId == storeId }
            StoreDetailUiState(
                store = stores.dataOrNull()?.firstOrNull { it.id == storeId },
                menu = menu,
                isFavorite = storeId in favorites,
                cartItemCount = ownCart?.itemCount ?: 0,
                cartTotal = ownCart?.total ?: Money.ZERO,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), StoreDetailUiState())

    fun toggleFavorite() = storeRepository.toggleFavorite(storeId)
}
