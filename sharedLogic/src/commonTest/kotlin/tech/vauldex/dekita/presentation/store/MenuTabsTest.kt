package tech.vauldex.dekita.presentation.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import tech.vauldex.dekita.data.LoadState
import tech.vauldex.dekita.data.mock.SeedStoreIds
import tech.vauldex.dekita.data.mock.seedMenus
import tech.vauldex.dekita.domain.FulfillmentMode
import tech.vauldex.dekita.domain.Menu
import tech.vauldex.dekita.domain.MenuCategory
import tech.vauldex.dekita.domain.MenuItem
import tech.vauldex.dekita.domain.Money

/** UI-01: the store screen's menu tabs. */
class MenuTabsTest {

    private fun state(storeId: String, tab: String? = null, mode: FulfillmentMode? = null) =
        StoreDetailUiState(menu = LoadState.Success(seedMenus.getValue(storeId)), selectedTabId = tab, mode = mode)

    @Test
    fun popularComesFirstWhenTheMenuHasPopularItems() {
        val state = state(SeedStoreIds.RAMEN_BAR)
        assertEquals(listOf("Popular", "Ramen", "Sides", "Drinks", "Desserts"), state.tabs.map { it.name })
        assertEquals(POPULAR_TAB_ID, state.currentTab?.id, "No pick yet: the first tab")
        assertEquals(listOf("Shoyu Ramen", "Gyoza (6 pcs)"), state.visibleItems.map { it.name })
        assertNull(state.emptyTabMessage)
    }

    @Test
    fun noPopularTabWithoutPopularItems() {
        val state = state(SeedStoreIds.MASSAGE_STUDIO)
        assertEquals(listOf("Massage"), state.tabs.map { it.name })
        assertEquals("massage", state.currentTab?.id)
    }

    @Test
    fun aPickedTabShowsOnlyItsCategory() {
        val state = state(SeedStoreIds.RAMEN_BAR, tab = "sides")
        assertEquals(listOf("Gyoza (6 pcs)", "Edamame", "Take-home Ramen Kit"), state.visibleItems.map { it.name })
    }

    @Test
    fun anEmptyTabSaysSo() {
        val state = state(SeedStoreIds.RAMEN_BAR, tab = "desserts")
        assertEquals(emptyList(), state.visibleItems)
        assertEquals("No desserts on the menu right now.", state.emptyTabMessage)
    }

    @Test
    fun aTabTheMenuNoLongerHasFallsBackToTheFirst() {
        assertEquals(POPULAR_TAB_ID, state(SeedStoreIds.RAMEN_BAR, tab = "gone").currentTab?.id)
    }

    @Test
    fun noTabsWhileTheMenuLoads() {
        val state = StoreDetailUiState()
        assertEquals(emptyList(), state.tabs)
        assertNull(state.emptyTabMessage)
    }

    // Mode filter: the menu hides what the chosen mode can't order.

    private fun item(id: String, category: String, modes: Set<FulfillmentMode>, popular: Boolean = false) = MenuItem(
        id = id, storeId = "store", categoryId = category, name = id, description = "",
        basePrice = Money.yen(100), availableModes = modes, isPopular = popular,
    )

    private val bothModes = FulfillmentMode.entries.toSet()
    private val eatInOnly = setOf(FulfillmentMode.IN_STORE)
    private val takeoutOnly = setOf(FulfillmentMode.PICKUP)

    @Test
    fun eatInHidesItemsOnlyOfferedForTakeout() {
        val state = state(SeedStoreIds.RAMEN_BAR, tab = "sides", mode = FulfillmentMode.IN_STORE)
        assertEquals(listOf("Gyoza (6 pcs)", "Edamame"), state.visibleItems.map { it.name })
    }

    @Test
    fun takeoutHidesItemsOnlyOfferedForEatIn() {
        val state = state(SeedStoreIds.RAMEN_BAR, tab = "ramen", mode = FulfillmentMode.PICKUP)
        assertEquals(listOf("Shoyu Ramen", "Tonkotsu Ramen"), state.visibleItems.map { it.name })
    }

    @Test
    fun noModeShowsEveryItem() {
        val state = state(SeedStoreIds.RAMEN_BAR, tab = "ramen", mode = null)
        assertEquals(listOf("Shoyu Ramen", "Tonkotsu Ramen", "Kaedama (extra noodles)"), state.visibleItems.map { it.name })
    }

    @Test
    fun aCategoryTheModeEmptiesHasNoTab() {
        val menu = Menu(
            storeId = "store",
            categories = listOf(MenuCategory("mains", "Mains"), MenuCategory("kits", "Kits")),
            items = listOf(item("a", "mains", bothModes), item("b", "kits", takeoutOnly)),
        )
        assertEquals(listOf("Mains"), menuTabs(menu, FulfillmentMode.IN_STORE).map { it.name })
        assertEquals(listOf("Mains", "Kits"), menuTabs(menu, FulfillmentMode.PICKUP).map { it.name })
        assertEquals(listOf("Mains", "Kits"), menuTabs(menu).map { it.name })
    }

    @Test
    fun popularOnlyCountsItemsTheModeOffers() {
        val menu = Menu(
            storeId = "store",
            categories = listOf(MenuCategory("mains", "Mains")),
            items = listOf(item("a", "mains", bothModes), item("b", "mains", takeoutOnly, popular = true)),
        )
        assertEquals(listOf("Mains"), menuTabs(menu, FulfillmentMode.IN_STORE).map { it.name })
        assertEquals(listOf("Popular", "Mains"), menuTabs(menu, FulfillmentMode.PICKUP).map { it.name })
    }

    @Test
    fun aMenuTheModeEmptiesSaysSo() {
        val menu = Menu(
            storeId = "store",
            categories = listOf(MenuCategory("kits", "Kits")),
            items = listOf(item("a", "kits", takeoutOnly)),
        )
        val state = StoreDetailUiState(menu = LoadState.Success(menu), mode = FulfillmentMode.IN_STORE)
        assertEquals(emptyList(), state.tabs)
        assertEquals(emptyList(), state.visibleItems)
        assertEquals("Nothing on the menu for Eat-in right now.", state.emptyTabMessage)
        assertEquals(listOf("a"), StoreDetailUiState(menu = LoadState.Success(menu), mode = FulfillmentMode.PICKUP).visibleItems.map { it.id })
    }

    @Test
    fun anItemOfferedForEatInOnlyIsNotInTheTakeoutPopularTab() {
        val menu = Menu(
            storeId = "store",
            categories = listOf(MenuCategory("mains", "Mains")),
            items = listOf(item("a", "mains", eatInOnly, popular = true), item("b", "mains", bothModes, popular = true)),
        )
        val state = StoreDetailUiState(menu = LoadState.Success(menu), mode = FulfillmentMode.PICKUP)
        assertEquals(POPULAR_TAB_ID, state.currentTab?.id)
        assertEquals(listOf("b"), state.visibleItems.map { it.id })
    }
}
