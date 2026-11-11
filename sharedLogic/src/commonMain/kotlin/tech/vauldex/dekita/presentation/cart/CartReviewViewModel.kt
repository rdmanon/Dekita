package tech.vauldex.dekita.presentation.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import tech.vauldex.dekita.data.CartRepository
import tech.vauldex.dekita.data.LoadState
import tech.vauldex.dekita.data.MenuRepository
import tech.vauldex.dekita.data.OrderRepository
import tech.vauldex.dekita.data.StoreRepository
import tech.vauldex.dekita.data.dataOrNull
import tech.vauldex.dekita.domain.Cart
import tech.vauldex.dekita.domain.CartLine
import tech.vauldex.dekita.domain.CheckoutAction
import tech.vauldex.dekita.domain.FulfillmentMode
import tech.vauldex.dekita.domain.ItemSelection
import tech.vauldex.dekita.domain.Menu
import tech.vauldex.dekita.domain.Money
import tech.vauldex.dekita.domain.Order
import tech.vauldex.dekita.domain.Store
import tech.vauldex.dekita.domain.StoreAvailability
import tech.vauldex.dekita.domain.checkoutAction
import tech.vauldex.dekita.domain.letsCustomerChooseMode
import tech.vauldex.dekita.domain.resolveMode
import tech.vauldex.dekita.presentation.STOP_TIMEOUT_MILLIS

/** A cart line and whether it can still be ordered, checked against the live menu. */
data class CartLineUi(val line: CartLine, val isSoldOut: Boolean)

/** Why checkout is off. The cart is kept in every case. */
enum class CheckoutBlock {
    /** The store has stopped taking orders. */
    STORE_PAUSED,

    /** The store is at capacity. */
    STORE_FULL,

    /** A line is sold out; remove or edit it. */
    SOLD_OUT_LINES,

    /** The store's live state is not loaded yet. */
    CHECKING,
}

data class CartReviewUiState(
    val hasCart: Boolean = false,
    val store: Store? = null,
    val lines: List<CartLineUi> = emptyList(),
    val itemCount: Int = 0,
    val subtotal: Money = Money.ZERO,
    val total: Money = Money.ZERO,
    /** The mode the order will use: fixed for single-mode stores, the customer's pick for stores with both. */
    val mode: FulfillmentMode? = null,
    /** True only for stores with both modes, which show the Eat-In / Takeout toggle. */
    val canChooseMode: Boolean = false,
    val action: CheckoutAction = CheckoutAction.PAY_NOW,
    val block: CheckoutBlock? = null,
) {
    val isEmpty: Boolean get() = lines.isEmpty()

    val canCheckout: Boolean get() = !isEmpty && block == null
}

/**
 * Cart Review: the confirm-or-change loop before paying. Routes by the store's business type:
 * single-mode stores pay now; stores with both offer Eat-In (save a DRAFT, pay at check-in) or Takeout (pay now).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CartReviewViewModel(
    private val cartRepository: CartRepository,
    storeRepository: StoreRepository,
    menuRepository: MenuRepository,
    private val orderRepository: OrderRepository,
) : ViewModel() {

    /** The live menu of the cart's store, for sold-out checks. Null when there is no cart. */
    private val cartMenu: Flow<LoadState<Menu>?> = cartRepository.cart
        .map { it?.storeId }
        .distinctUntilChanged()
        .flatMapLatest { storeId -> if (storeId == null) flowOf(null) else menuRepository.menu(storeId) }

    val state: StateFlow<CartReviewUiState> =
        combine(cartRepository.cart, storeRepository.stores, cartMenu) { cart, stores, menu ->
            toUiState(cart, stores.dataOrNull(), menu?.dataOrNull())
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            toUiState(cartRepository.cart.value, storeRepository.stores.value.dataOrNull(), null),
        )

    /** Eat-In or Takeout. Ignored for single-mode stores, whose mode is fixed. */
    fun selectMode(mode: FulfillmentMode) {
        if (state.value.canChooseMode) cartRepository.setMode(mode)
    }

    fun increase(lineId: String) {
        val line = cartRepository.cart.value?.line(lineId) ?: return
        if (line.quantity < ItemSelection.MAX_QUANTITY) cartRepository.updateQuantity(lineId, line.quantity + 1)
    }

    /** Going below one removes the line. Removing the last line empties the cart. */
    fun decrease(lineId: String) {
        val line = cartRepository.cart.value?.line(lineId) ?: return
        cartRepository.updateQuantity(lineId, line.quantity - 1)
    }

    fun remove(lineId: String) {
        if (cartRepository.cart.value?.line(lineId) != null) cartRepository.updateQuantity(lineId, 0)
    }

    /**
     * Eat-In: saves the cart as a DRAFT order and empties the cart. Returns the draft, or null when
     * checkout is blocked or the button isn't "Head to store".
     */
    fun headToStore(): Order? {
        val current = state.value
        val cart = cartRepository.cart.value ?: return null
        val store = current.store ?: return null
        if (!current.canCheckout || current.action != CheckoutAction.HEAD_TO_STORE) return null
        val order = orderRepository.createDraft(cart.copy(mode = current.mode ?: cart.mode), store.name)
        cartRepository.clear()
        return order
    }

    /**
     * "Pay now". Payment is a later ticket (PAY-01), so this only says whether payment may start.
     * The cart is kept, because a failed or cancelled payment returns here.
     */
    fun canStartPayment(): Boolean = state.value.let { it.canCheckout && it.action == CheckoutAction.PAY_NOW }

    private fun toUiState(cart: Cart?, stores: List<Store>?, menu: Menu?): CartReviewUiState {
        if (cart == null) return CartReviewUiState()
        val store = stores?.firstOrNull { it.id == cart.storeId }
        val lines = cart.lines.map { line -> CartLineUi(line, isSoldOut = line.isSoldOutIn(menu)) }
        val mode = store?.businessType?.resolveMode(cart.mode) ?: cart.mode
        val block = when {
            store == null -> CheckoutBlock.CHECKING
            store.availability == StoreAvailability.PAUSED -> CheckoutBlock.STORE_PAUSED
            store.availability == StoreAvailability.FULL -> CheckoutBlock.STORE_FULL
            lines.any { it.isSoldOut } -> CheckoutBlock.SOLD_OUT_LINES
            else -> null
        }
        return CartReviewUiState(
            hasCart = true,
            store = store,
            lines = lines,
            itemCount = cart.itemCount,
            subtotal = cart.subtotal,
            total = cart.total,
            mode = mode,
            canChooseMode = store?.businessType?.letsCustomerChooseMode == true,
            action = store?.let { checkoutAction(it.businessType, mode) } ?: CheckoutAction.PAY_NOW,
            block = block,
        )
    }
}

/** Sold out when the live item, or one of the line's options, is sold out or gone from the menu. */
private fun CartLine.isSoldOutIn(menu: Menu?): Boolean {
    if (menu == null) return item.isSoldOut || selectedOptions.any { it.isSoldOut }
    val live = menu.items.firstOrNull { it.id == item.id } ?: return true
    if (live.isSoldOut) return true
    val liveOptions = live.modifierGroups.flatMap { it.options }.associateBy { it.id }
    return selectedOptions.any { option -> liveOptions[option.id]?.isSoldOut ?: true }
}
