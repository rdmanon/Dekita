package tech.vauldex.dekita.presentation.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import tech.vauldex.dekita.data.AuthRepository
import tech.vauldex.dekita.data.CartRepository
import tech.vauldex.dekita.data.LoadState
import tech.vauldex.dekita.data.StoreRepository
import tech.vauldex.dekita.data.dataOrNull
import tech.vauldex.dekita.domain.Cart
import tech.vauldex.dekita.domain.Session
import tech.vauldex.dekita.domain.Store
import tech.vauldex.dekita.presentation.STOP_TIMEOUT_MILLIS

data class CartReviewUiState(
    /** Null when the cart is empty. */
    val cart: Cart? = null,
    val store: Store? = null,
    val isSignedIn: Boolean = false,
)

sealed interface CartReviewEvent {
    /** A guest tapped "Pay now"; sign-in comes back here afterwards. */
    data object RequireSignIn : CartReviewEvent

    /** Payment is a later ticket, so a signed-in tap only says so. */
    data object PaymentComingLater : CartReviewEvent
}

/** Read-only cart for the checkout sign-in. Editing lines and paying are later tickets. */
class CartReviewViewModel(
    cartRepository: CartRepository,
    storeRepository: StoreRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    val state: StateFlow<CartReviewUiState> =
        combine(cartRepository.cart, storeRepository.stores, authRepository.session, ::toUiState)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = toUiState(cartRepository.cart.value, storeRepository.stores.value, authRepository.session.value),
            )

    private val _events = Channel<CartReviewEvent>(Channel.BUFFERED)
    val events: Flow<CartReviewEvent> = _events.receiveAsFlow()

    fun payNow() {
        if (state.value.cart == null) return
        val event = if (authRepository.session.value == null) CartReviewEvent.RequireSignIn else CartReviewEvent.PaymentComingLater
        _events.trySend(event)
    }

    private fun toUiState(cart: Cart?, stores: LoadState<List<Store>>, session: Session?) = CartReviewUiState(
        cart = cart?.takeUnless { it.isEmpty },
        store = cart?.let { current -> stores.dataOrNull()?.firstOrNull { it.id == current.storeId } },
        isSignedIn = session != null,
    )
}
