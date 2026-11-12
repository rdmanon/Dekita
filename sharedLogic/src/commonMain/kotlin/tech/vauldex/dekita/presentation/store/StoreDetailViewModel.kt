package tech.vauldex.dekita.presentation.store

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import tech.vauldex.dekita.data.CartRepository
import tech.vauldex.dekita.data.LoadState
import tech.vauldex.dekita.data.MenuRepository
import tech.vauldex.dekita.data.StoreRepository
import tech.vauldex.dekita.data.dataOrNull
import tech.vauldex.dekita.data.mock.MenuSimulator
import tech.vauldex.dekita.domain.Cart
import tech.vauldex.dekita.domain.CartLine
import tech.vauldex.dekita.domain.FulfillmentMode
import tech.vauldex.dekita.domain.ItemDraft
import tech.vauldex.dekita.domain.Menu
import tech.vauldex.dekita.domain.MenuItem
import tech.vauldex.dekita.domain.Store
import tech.vauldex.dekita.presentation.ITEM_SOLD_OUT_MESSAGE
import tech.vauldex.dekita.presentation.OPTION_SOLD_OUT_MESSAGE
import tech.vauldex.dekita.presentation.STOP_TIMEOUT_MILLIS
import tech.vauldex.dekita.presentation.STORE_NOT_TAKING_ORDERS_MESSAGE
import tech.vauldex.dekita.presentation.label

/** The open item sheet. [blockedReason] explains why Add is off even when the choices are complete. */
data class ItemSheetState(
    val draft: ItemDraft,
    val blockedReason: String?,
) {
    val canAdd: Boolean get() = draft.canAdd && blockedReason == null
}

data class StoreDetailUiState(
    val store: Store? = null,
    val menu: LoadState<Menu> = LoadState.Loading,
    val isFavorite: Boolean = false,
    /** The cart, from any store, when it has items. Drives the "View cart" button. */
    val cart: Cart? = null,
    val sheet: ItemSheetState? = null,
    /** The other store's name while Add waits for the customer to agree to start a new cart. */
    val pendingReplaceStoreName: String? = null,
    /** The tab the customer picked. Null, or a tab the menu no longer has, means the first tab. */
    val selectedTabId: String? = null,
) {
    /** False while the store is paused or full, so every menu row shows Add as off. */
    val canOrder: Boolean get() = store?.isAcceptingOrders == true

    /** UI-01: Popular first when the menu has popular items, then each category in menu order. */
    val tabs: List<MenuTab> get() = menu.dataOrNull()?.let(::menuTabs).orEmpty()

    val currentTab: MenuTab? get() = tabs.firstOrNull { it.id == selectedTabId } ?: tabs.firstOrNull()

    /** The rows under [currentTab]. */
    val visibleItems: List<MenuItem>
        get() {
            val menu = menu.dataOrNull() ?: return emptyList()
            val tab = currentTab ?: return emptyList()
            return if (tab.id == POPULAR_TAB_ID) menu.items.filter { it.isPopular } else menu.itemsIn(tab.id)
        }

    /** Shown in place of the rows when [currentTab] has nothing on it. */
    val emptyTabMessage: String?
        get() = currentTab?.takeIf { visibleItems.isEmpty() }?.let { "No ${it.name.lowercase()} on the menu right now." }
}

data class MenuTab(val id: String, val name: String)

const val POPULAR_TAB_ID = "popular"

fun menuTabs(menu: Menu): List<MenuTab> {
    val popular = if (menu.items.any { it.isPopular }) listOf(MenuTab(POPULAR_TAB_ID, "Popular")) else emptyList()
    return popular + menu.categories.map { MenuTab(it.id, it.name) }
}

enum class AddResult { ADDED, NEEDS_REPLACE, NOT_ADDED }

class StoreDetailViewModel(
    private val storeId: String,
    private val storeRepository: StoreRepository,
    menuRepository: MenuRepository,
    private val cartRepository: CartRepository,
    private val menuSimulator: MenuSimulator,
) : ViewModel() {

    private class PendingAdd(val mode: FulfillmentMode, val line: CartLine)

    private val draft = MutableStateFlow<ItemDraft?>(null)
    private val pendingAdd = MutableStateFlow<PendingAdd?>(null)
    private val selectedTab = MutableStateFlow<String?>(null)

    private val menuFlow = menuRepository.menu(storeId)

    val state: StateFlow<StoreDetailUiState> =
        combine(
            combine(storeRepository.stores, storeRepository.favoriteStoreIds, menuFlow, ::Triple),
            cartRepository.cart,
            cartRepository.preferredMode,
            combine(draft, pendingAdd, selectedTab, ::Triple),
        ) { (stores, favorites, menu), cart, preferred, (draft, pending, tab) ->
            val storeList = stores.dataOrNull().orEmpty()
            val store = storeList.firstOrNull { it.id == storeId }
            StoreDetailUiState(
                store = store,
                menu = menu,
                isFavorite = storeId in favorites,
                cart = cart?.takeUnless { it.isEmpty },
                sheet = sheetState(store, menu.dataOrNull(), cart, preferred, draft),
                pendingReplaceStoreName = pending?.let { storeList.firstOrNull { s -> s.id == cart?.storeId }?.name ?: "another store" },
                selectedTabId = tab,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), StoreDetailUiState())

    fun toggleFavorite() = storeRepository.toggleFavorite(storeId)

    fun selectTab(tabId: String) {
        selectedTab.value = tabId
    }

    /** Opens the sheet for an item. Sold-out items don't open. */
    fun openItem(itemId: String) {
        val item = menuFlow.value.dataOrNull()?.items?.firstOrNull { it.id == itemId } ?: return
        if (!item.isSoldOut) draft.value = ItemDraft(item)
    }

    fun toggleOption(groupId: String, optionId: String) = draft.update { it?.toggle(groupId, optionId) }

    fun setQuantity(quantity: Int) = draft.update { it?.withQuantity(quantity) }

    fun closeItem() {
        draft.value = null
    }

    /** Adds the open item. A cart from another store needs [confirmReplace] first. */
    fun addToCart(): AddResult {
        val store = currentStore() ?: return AddResult.NOT_ADDED
        val sheet = sheetState(
            store, menuFlow.value.dataOrNull(), cartRepository.cart.value, cartRepository.preferredMode.value, draft.value,
        )
        if (sheet == null || !sheet.canAdd) return AddResult.NOT_ADDED
        val mode = orderMode(store, cartRepository.cart.value, cartRepository.preferredMode.value)
        val line = sheet.draft.toCartLine()
        if (!cartRepository.addLine(mode, line)) {
            pendingAdd.value = PendingAdd(mode, line)
            return AddResult.NEEDS_REPLACE
        }
        draft.value = null
        return AddResult.ADDED
    }

    /** Drops the other store's cart and adds the waiting item. */
    fun confirmReplace() {
        val pending = pendingAdd.value ?: return
        cartRepository.replaceWith(pending.mode, pending.line)
        pendingAdd.value = null
        draft.value = null
    }

    fun dismissReplace() {
        pendingAdd.value = null
    }

    /** Debug only: the open item sells out, as if the store pressed "Stop Selling". */
    fun stopSellingOpenItem() {
        val item = draft.value?.item ?: return
        menuSimulator.setItemSoldOut(storeId, item.id, soldOut = true)
    }

    private fun currentStore(): Store? = storeRepository.stores.value.dataOrNull()?.firstOrNull { it.id == storeId }

    private fun sheetState(
        store: Store?,
        menu: Menu?,
        cart: Cart?,
        preferred: FulfillmentMode?,
        draft: ItemDraft?,
    ): ItemSheetState? {
        draft ?: return null
        // Take the item from the live menu, so a sell-out while the sheet is open shows at once.
        val current = draft.withItem(menu?.items?.firstOrNull { it.id == draft.item.id } ?: draft.item)
        val mode = store?.let { orderMode(it, cart, preferred) }
        val reason = when {
            store == null || !store.isAcceptingOrders -> STORE_NOT_TAKING_ORDERS_MESSAGE
            current.item.isSoldOut -> ITEM_SOLD_OUT_MESSAGE
            current.soldOutSelections.isNotEmpty() -> OPTION_SOLD_OUT_MESSAGE
            mode != null && mode !in current.item.availableModes ->
                "Only for ${current.item.availableModes.joinToString(" or ") { it.label }} orders"
            else -> null
        }
        return ItemSheetState(current, reason)
    }

    /**
     * This store's cart mode, else the mode picked on Discover, else the store's first mode. Each only counts
     * while the store still offers it, so a switch to one mode takes effect at once.
     */
    private fun orderMode(store: Store, cart: Cart?, preferred: FulfillmentMode?): FulfillmentMode =
        cart?.takeIf { it.storeId == storeId }?.mode?.takeIf { store.supports(it) }
            ?: preferred?.takeIf { store.supports(it) }
            ?: store.businessType.supportedModes.first()
}
