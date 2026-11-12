package tech.vauldex.dekita.presentation.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import tech.vauldex.dekita.data.LoadState
import tech.vauldex.dekita.data.mock.SeedStoreIds
import tech.vauldex.dekita.data.mock.seedMenus

/** UI-01: the store screen's menu tabs. */
class MenuTabsTest {

    private fun state(storeId: String, tab: String? = null) =
        StoreDetailUiState(menu = LoadState.Success(seedMenus.getValue(storeId)), selectedTabId = tab)

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
}
