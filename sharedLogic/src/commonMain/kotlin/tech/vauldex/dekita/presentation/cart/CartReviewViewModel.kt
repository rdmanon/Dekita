package tech.vauldex.dekita.presentation.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import tech.vauldex.dekita.data.AuthRepository
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
import tech.vauldex.dekita.domain.ItemDraft
import tech.vauldex.dekita.domain.MAX_QUANTITY
import tech.vauldex.dekita.domain.Menu
import tech.vauldex.dekita.domain.MenuItem
import tech.vauldex.dekita.domain.ModifierOption
import tech.vauldex.dekita.domain.Session
import tech.vauldex.dekita.domain.Store
import tech.vauldex.dekita.domain.StoreAvailability
import tech.vauldex.dekita.domain.checkoutAction
import tech.vauldex.dekita.domain.letsCustomerChooseMode
import tech.vauldex.dekita.domain.resolveMode
import tech.vauldex.dekita.presentation.ITEM_SOLD_OUT_MESSAGE
import tech.vauldex.dekita.presentation.STOP_TIMEOUT_MILLIS
import tech.vauldex.dekita.presentation.STORE_NOT_TAKING_ORDERS_MESSAGE
import tech.vauldex.dekita.presentation.choiceLabel
import tech.vauldex.dekita.presentation.label
import tech.vauldex.dekita.presentation.store.ItemSheetState

/**
 * A cart line and, when it can't be ordered right now, why: "Sold out", or "Eat-In only" when the item
 * is limited to the other mode. Checked against the live menu.
 */
data class CartLineUi(val line: CartLine, val unavailableReason: String?) {
    val isAvailable: Boolean get() = unavailableReason == null
}

/** Why checkout is off. The cart is kept in every case. */
enum class CheckoutBlock {
    /** The store has stopped taking orders. */
    STORE_PAUSED,

    /** The store is at capacity. */
    STORE_FULL,

    /** A line is sold out or not offered in the chosen mode; remove or edit it. */
    UNAVAILABLE_LINES,

    /** The store's live state is not loaded yet. */
    CHECKING,
}

data class CartReviewUiState(
    /** Null when the cart is empty. */
    val cart: Cart? = null,
    val store: Store? = null,
    val isSignedIn: Boolean = false,
    val lines: List<CartLineUi> = emptyList(),
    /** The mode the order will use: fixed for single-mode stores, the customer's pick for stores with both. */
    val mode: FulfillmentMode? = null,
    /** True only for stores with both modes, which show the Eat-In / Takeout toggle. */
    val canChooseMode: Boolean = false,
    val action: CheckoutAction = CheckoutAction.PAY_NOW,
    val block: CheckoutBlock? = null,
    /** The item sheet while a line is being edited; its button saves over that line. */
    val editSheet: ItemSheetState? = null,
) {
    val isEmpty: Boolean get() = cart == null

    val canCheckout: Boolean get() = cart != null && block == null
}

sealed interface CartReviewEvent {
    /** A guest tapped the checkout button; sign-in comes back here afterwards. */
    data object RequireSignIn : CartReviewEvent

    /** "Pay now" when signed in. Payment is a later ticket (PAY-01), so this only says so; the cart is kept. */
    data object PaymentComingLater : CartReviewEvent

    /** "Head to store" saved the cart as a DRAFT order. Show check-in for [orderId] (SEAT-01). */
    data class DraftSaved(val orderId: String) : CartReviewEvent
}

/**
 * Cart Review, the confirm-or-change loop before checkout. Lines can be edited in place, re-counted or
 * removed. The button depends on the store's business type: single-mode stores pay now; stores with both
 * offer Eat-In (save a DRAFT, pay at check-in) or Takeout (pay now). Guests sign in first.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CartReviewViewModel(
    private val cartRepository: CartRepository,
    private val storeRepository: StoreRepository,
    private val menuRepository: MenuRepository,
    private val orderRepository: OrderRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private class Editing(val lineId: String, val draft: ItemDraft)

    private val editing = MutableStateFlow<Editing?>(null)

    /** The live menu of the cart's store, for sold-out checks. Null when there is no cart. */
    private val cartMenu: Flow<LoadState<Menu>?> = cartRepository.cart
        .map { it?.storeId }
        .distinctUntilChanged()
        .flatMapLatest { storeId -> if (storeId == null) flowOf(null) else menuRepository.menu(storeId) }

    val state: StateFlow<CartReviewUiState> =
        combine(cartRepository.cart, storeRepository.stores, cartMenu, authRepository.session, editing) { cart, stores, menu, session, editing ->
            toUiState(cart, stores.dataOrNull(), menu?.dataOrNull(), session, editing)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = currentState(),
        )

    private val _events = Channel<CartReviewEvent>(Channel.BUFFERED)
    val events: Flow<CartReviewEvent> = _events.receiveAsFlow()

    /** Eat-In or Takeout. Ignored for single-mode stores, whose mode is fixed. */
    fun selectMode(mode: FulfillmentMode) {
        if (currentState().canChooseMode) cartRepository.setMode(mode)
    }

    fun increase(lineId: String) {
        val line = cartRepository.cart.value?.line(lineId) ?: return
        if (line.quantity < MAX_QUANTITY) cartRepository.updateQuantity(lineId, line.quantity + 1)
    }

    /** Going below one removes the line. Removing the last line empties the cart. */
    fun decrease(lineId: String) {
        val line = cartRepository.cart.value?.line(lineId) ?: return
        cartRepository.updateQuantity(lineId, line.quantity - 1)
    }

    fun remove(lineId: String) {
        if (cartRepository.cart.value?.line(lineId) != null) cartRepository.updateQuantity(lineId, 0)
    }

    /** Opens the item sheet on a line, prefilled with its choices and quantity. */
    fun editLine(lineId: String) {
        val cart = cartRepository.cart.value ?: return
        val line = cart.line(lineId) ?: return
        editing.value = Editing(lineId, ItemDraft.fromLine(line, liveItem(cart.storeId, line.item) ?: line.item))
    }

    fun toggleEditOption(groupId: String, optionId: String) {
        val open = editing.value ?: return
        val sheet = currentState().editSheet ?: return
        editing.value = Editing(open.lineId, sheet.draft.toggle(groupId, optionId))
    }

    fun setEditQuantity(quantity: Int) {
        val open = editing.value ?: return
        val sheet = currentState().editSheet ?: return
        editing.value = Editing(open.lineId, sheet.draft.withQuantity(quantity))
    }

    /** Saves the edited choices over the same line, so editing never adds a second line. */
    fun saveEdit(): Boolean {
        val open = editing.value ?: return false
        val sheet = currentState().editSheet?.takeIf { it.canAdd } ?: return false
        cartRepository.replaceLine(open.lineId, sheet.draft.toCartLine())
        editing.value = null
        return true
    }

    fun closeEdit() {
        editing.value = null
    }

    /**
     * The one checkout button. Guests are sent to sign-in first. "Pay now" says payment comes later and
     * keeps the cart; "Head to store" saves a DRAFT order, empties the cart and opens check-in.
     */
    fun checkout() {
        val current = currentState()
        val cart = current.cart ?: return
        if (!current.canCheckout) return
        if (authRepository.session.value == null) {
            _events.trySend(CartReviewEvent.RequireSignIn)
            return
        }
        when (current.action) {
            CheckoutAction.PAY_NOW -> _events.trySend(CartReviewEvent.PaymentComingLater)
            CheckoutAction.HEAD_TO_STORE -> {
                val store = current.store ?: return
                val draft = orderRepository.createDraft(cart.copy(mode = current.mode ?: cart.mode), store.name)
                cartRepository.clear()
                _events.trySend(CartReviewEvent.DraftSaved(draft.id))
            }
        }
    }

    /**
     * The state right now, read straight from the sources. [state] reaches the same value a moment
     * later, so actions use this to never act on a mode or line the customer just changed.
     */
    private fun currentState(): CartReviewUiState {
        val cart = cartRepository.cart.value
        val menu = cart?.let { menuRepository.menu(it.storeId).value.dataOrNull() }
        return toUiState(cart, storeRepository.stores.value.dataOrNull(), menu, authRepository.session.value, editing.value)
    }

    private fun liveItem(storeId: String, item: MenuItem): MenuItem? =
        menuRepository.menu(storeId).value.dataOrNull()?.items?.firstOrNull { it.id == item.id }

    private fun toUiState(
        cart: Cart?,
        stores: List<Store>?,
        menu: Menu?,
        session: Session?,
        editing: Editing?,
    ): CartReviewUiState {
        val nonEmpty = cart?.takeUnless { it.isEmpty } ?: return CartReviewUiState(isSignedIn = session != null)
        val store = stores?.firstOrNull { it.id == nonEmpty.storeId }
        val mode = store?.businessType?.resolveMode(nonEmpty.mode) ?: nonEmpty.mode
        val canChoose = store?.businessType?.letsCustomerChooseMode == true
        val lines = nonEmpty.lines.map { line -> CartLineUi(line, line.unavailableReason(menu, mode, canChoose)) }
        val block = when {
            store == null -> CheckoutBlock.CHECKING
            store.availability == StoreAvailability.PAUSED -> CheckoutBlock.STORE_PAUSED
            store.availability == StoreAvailability.FULL -> CheckoutBlock.STORE_FULL
            lines.any { !it.isAvailable } -> CheckoutBlock.UNAVAILABLE_LINES
            else -> null
        }
        return CartReviewUiState(
            cart = nonEmpty,
            store = store,
            isSignedIn = session != null,
            lines = lines,
            mode = mode,
            canChooseMode = canChoose,
            action = store?.let { checkoutAction(it.businessType, mode) } ?: CheckoutAction.PAY_NOW,
            block = block,
            editSheet = editing?.let { editSheet(it, nonEmpty, store, menu, mode) },
        )
    }

    /** The edit sheet against the live item, so a sell-out while it is open blocks saving at once. */
    private fun editSheet(editing: Editing, cart: Cart, store: Store?, menu: Menu?, mode: FulfillmentMode): ItemSheetState? {
        if (cart.line(editing.lineId) == null) return null
        val live = menu?.items?.firstOrNull { it.id == editing.draft.item.id }
        val draft = editing.draft.withItem(live ?: editing.draft.item)
        val reason = when {
            store == null || !store.isAcceptingOrders -> STORE_NOT_TAKING_ORDERS_MESSAGE
            // Gone from a loaded menu, sold out, or a picked option sold out.
            (menu != null && live == null) || draft.item.isSoldOut || draft.selected.values.flatten().any { it.soldOutIn(draft.item) } ->
                ITEM_SOLD_OUT_MESSAGE
            mode !in draft.item.availableModes ->
                "Only for ${draft.item.availableModes.joinToString(" or ") { it.label }} orders"
            else -> null
        }
        return ItemSheetState(draft, reason)
    }
}

/** True when the live item has this option sold out, or no longer has it. */
private fun ModifierOption.soldOutIn(liveItem: MenuItem): Boolean =
    liveItem.modifierGroups.flatMap { it.options }.firstOrNull { it.id == id }?.isSoldOut ?: true

/**
 * Why a line can't be ordered: the live item or one of its options is sold out or gone, or the item is
 * only offered in the other mode. Null when it can.
 */
private fun CartLine.unavailableReason(menu: Menu?, mode: FulfillmentMode, canChooseMode: Boolean): String? {
    val live = if (menu == null) item else menu.items.firstOrNull { it.id == item.id } ?: return "Sold out"
    if (live.isSoldOut || selectedOptions.any { it.soldOutIn(live) }) return "Sold out"
    if (mode !in live.availableModes) {
        val only = live.availableModes.joinToString(" or ") { if (canChooseMode) it.choiceLabel else it.label }
        return "$only only"
    }
    return null
}
