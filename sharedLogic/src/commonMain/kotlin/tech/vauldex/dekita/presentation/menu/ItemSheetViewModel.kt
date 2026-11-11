package tech.vauldex.dekita.presentation.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
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
import tech.vauldex.dekita.domain.ItemSelection
import tech.vauldex.dekita.domain.Menu
import tech.vauldex.dekita.domain.Store
import tech.vauldex.dekita.domain.defaultMode
import tech.vauldex.dekita.presentation.STOP_TIMEOUT_MILLIS

data class ItemSheetUiState(
    val isLoading: Boolean = true,
    val store: Store? = null,
    /** The live item with the customer's choices. Null while loading or when the item left the menu. */
    val selection: ItemSelection? = null,
    val isEditing: Boolean = false,
    /** Set when the cart holds another store's items, so adding asks before starting a new cart. */
    val otherCartStoreName: String? = null,
)

enum class SaveResult {
    /** Added to the cart, or the edited line was updated in place. */
    SAVED,

    /** The cart belongs to another store. Ask, then call [ItemSheetViewModel.startNewCartAndSave]. */
    CART_FROM_OTHER_STORE,

    /** A required choice is missing or something is sold out. Nothing changed. */
    NOT_SAVED,
}

/**
 * The customize-item sheet. Adds a new line, or with [editingLineId] edits that line in place,
 * starting from its current choices, so editing never creates a duplicate.
 */
class ItemSheetViewModel(
    private val storeId: String,
    private val itemId: String,
    private val editingLineId: String?,
    private val menuRepository: MenuRepository,
    private val storeRepository: StoreRepository,
    private val cartRepository: CartRepository,
) : ViewModel() {

    /** The customer's changes. Null until they change something, so the sheet starts from the defaults. */
    private val edits = MutableStateFlow<ItemSelection?>(null)

    val state: StateFlow<ItemSheetUiState> =
        combine(menuRepository.menu(storeId), storeRepository.stores, cartRepository.cart, edits) { menu, stores, cart, edits ->
            toUiState(menu, stores.dataOrNull(), cart, edits)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            toUiState(menuRepository.menu(storeId).value, storeRepository.stores.value.dataOrNull(), cartRepository.cart.value, null),
        )

    fun toggle(groupId: String, optionId: String) {
        val current = currentState().selection ?: return
        edits.value = current.toggle(groupId, optionId)
    }

    fun setQuantity(quantity: Int) {
        val current = currentState().selection ?: return
        edits.value = current.withQuantity(quantity)
    }

    fun save(): SaveResult {
        val current = currentState()
        val selection = current.selection?.takeIf { it.canSave } ?: return SaveResult.NOT_SAVED
        val store = current.store ?: return SaveResult.NOT_SAVED

        if (editingLineId != null) {
            cartRepository.replaceLine(editingLineId, selection.toLine(editingLineId))
            return SaveResult.SAVED
        }
        val mode = store.businessType.defaultMode(cartRepository.preferredMode.value)
        val added = cartRepository.addLine(mode, selection.toLine(cartRepository.newLineId()))
        return if (added) SaveResult.SAVED else SaveResult.CART_FROM_OTHER_STORE
    }

    /** The customer agreed to drop the other store's cart. */
    fun startNewCartAndSave(): SaveResult {
        cartRepository.clear()
        return save()
    }

    /**
     * The state right now, read straight from the sources. [state] reaches the same value a moment
     * later, so actions use this to never act on a choice the customer just changed.
     */
    private fun currentState(): ItemSheetUiState = toUiState(
        menuRepository.menu(storeId).value,
        storeRepository.stores.value.dataOrNull(),
        cartRepository.cart.value,
        edits.value,
    )

    private fun toUiState(menu: LoadState<Menu>, stores: List<Store>?, cart: Cart?, edits: ItemSelection?): ItemSheetUiState {
        val store = stores?.firstOrNull { it.id == storeId }
        val item = menu.dataOrNull()?.items?.firstOrNull { it.id == itemId }
        val editingLine = editingLineId?.let { cart?.line(it) }
        // Always against the live item, so a sold-out change reaches an open sheet.
        val selection = item?.let { live ->
            edits?.copy(item = live)
                ?: editingLine?.let { ItemSelection.fromLine(it, live) }
                ?: ItemSelection.start(live)
        }
        val otherStoreName = cart?.takeIf { it.storeId != storeId }?.let { other ->
            stores?.firstOrNull { it.id == other.storeId }?.name ?: "another store"
        }
        return ItemSheetUiState(
            isLoading = menu is LoadState.Loading || stores == null,
            store = store,
            selection = selection,
            isEditing = editingLineId != null,
            otherCartStoreName = if (editingLineId == null) otherStoreName else null,
        )
    }
}
