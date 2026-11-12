package tech.vauldex.dekita.presentation.discover

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import tech.vauldex.dekita.data.mock.seedStores
import tech.vauldex.dekita.domain.BusinessType
import tech.vauldex.dekita.domain.FulfillmentMode
import tech.vauldex.dekita.domain.StoreAvailability
import tech.vauldex.dekita.domain.StoreBadge

class StoreFiltersTest {

    private fun names(filters: StoreFilters) = seedStores.filterStores(filters).map { it.store.name }

    @Test
    fun pickupListsPickupOnlyAndBothStores() {
        val listed = seedStores.filterStores(StoreFilters(mode = FulfillmentMode.PICKUP)).map { it.store }
        assertTrue(listed.isNotEmpty())
        assertTrue(listed.all { it.businessType != BusinessType.IN_STORE_ONLY })
        assertTrue("Sample Massage Studio" !in listed.map { it.name }, "Scenario 1: the In-store only studio is hidden")
        assertEquals(seedStores.count { it.businessType != BusinessType.IN_STORE_ONLY }, listed.size)
    }

    @Test
    fun inStoreListsInStoreOnlyAndBothStores() {
        val listed = seedStores.filterStores(StoreFilters(mode = FulfillmentMode.IN_STORE)).map { it.store }
        assertTrue(listed.all { it.businessType != BusinessType.PICKUP_ONLY })
        assertEquals(seedStores.count { it.businessType != BusinessType.PICKUP_ONLY }, listed.size)
    }

    @Test
    fun noModeListsEveryStoreNearestFirst() {
        val listed = seedStores.filterStores(StoreFilters()).map { it.store }
        assertEquals(seedStores.size, listed.size)
        assertEquals(listed.sortedBy { it.walkMinutes }, listed)
    }

    @Test
    fun categoryListsOnlyThatCategoryNearestFirst() {
        assertEquals(listOf("Sample Curry Stand"), names(StoreFilters(categories = setOf("Curry"))))
        assertEquals(
            listOf("Sample Massage Studio", "Test Hair & Nail Salon"),
            names(StoreFilters(categories = setOf("Salon"))),
        )
    }

    @Test
    fun categoriesAreMultiSelect() {
        val listed = names(StoreFilters(categories = setOf("Curry", "Ramen")))
        assertEquals(listOf("Test Ramen Bar", "Sample Curry Stand"), listed)
    }

    @Test
    fun walkingDistanceKeepsStoresWithinTheLimit() {
        val within = seedStores.filterStores(StoreFilters(walkLimit = WalkLimit.FIVE)).map { it.store }
        assertTrue(within.isNotEmpty())
        assertTrue(within.all { it.walkMinutes <= 5 })
        // The farthest seed store is beyond every limit.
        assertTrue("Test Hair & Nail Salon" !in names(StoreFilters(walkLimit = WalkLimit.FIFTEEN)))
    }

    @Test
    fun statusFilterKeepsOnlyThoseStates() {
        assertEquals(listOf("Sample Soba House"), names(StoreFilters(statuses = setOf(StoreAvailability.PAUSED))))
    }

    @Test
    fun pausedStoresStayInTheListWithoutAStatusFilter() {
        assertTrue("Sample Soba House" in names(StoreFilters()))
    }

    @Test
    fun filtersCombineWithAnd() {
        val filters = StoreFilters(mode = FulfillmentMode.PICKUP, walkLimit = WalkLimit.FIVE)
        // Within 5 minutes: the mart (pickup only), the ramen bar and the bento kitchen (both modes).
        assertEquals(listOf("Test Mart", "Test Ramen Bar", "Test Bento Kitchen"), names(filters))
        // The bento kitchen is full, so an Open status filter drops it.
        assertEquals(listOf("Test Mart", "Test Ramen Bar"), names(filters.copy(statuses = setOf(StoreAvailability.OPEN))))
        assertEquals(listOf("Test Mart"), names(filters.copy(categories = setOf("Convenience store"))))
    }

    @Test
    fun searchMatchesStoreNamesIgnoringCase() {
        assertEquals(listOf("Test Ramen Bar"), names(StoreFilters(query = "ramen bar")))
    }

    @Test
    fun searchMatchesItemNamesAndSaysWhichItem() {
        val matches = seedStores.filterStores(StoreFilters(query = "shoyu"))
        assertEquals(listOf("Test Ramen Bar"), matches.map { it.store.name })
        assertEquals("Shoyu Ramen", matches.single().matchedItem)
    }

    @Test
    fun searchMatchesJapaneseText() {
        assertEquals(listOf("Sample 炭火焼き定食と季節のおばんざい さんぷる亭 駅前本店"), names(StoreFilters(query = "定食")))
    }

    @Test
    fun searchCombinesWithTheMode() {
        assertTrue(names(StoreFilters(query = "massage", mode = FulfillmentMode.PICKUP)).isEmpty())
    }

    @Test
    fun noMatchesIsAnEmptyList() {
        assertTrue(names(StoreFilters(query = "no such store")).isEmpty())
    }

    @Test
    fun refinedMeansAFilterOtherThanTheMode() {
        assertTrue(StoreFilters().isCleared)
        assertTrue(!StoreFilters(mode = FulfillmentMode.PICKUP).isRefined)
        assertTrue(StoreFilters(query = "x").isRefined)
        assertTrue(StoreFilters(walkLimit = WalkLimit.TEN).isRefined)
        // UI-01: the category chip isn't a sheet filter, so it neither counts nor switches to results.
        assertEquals(1, StoreFilters(walkLimit = WalkLimit.TEN, categories = setOf("Curry")).sheetFilterCount)
        assertFalse(StoreFilters(mode = FulfillmentMode.PICKUP, categories = setOf("Curry")).showsResults)
        assertTrue(StoreFilters(query = "x").showsResults)
        assertTrue(StoreFilters(walkLimit = WalkLimit.TEN).showsResults)
    }

    @Test
    fun badgesFollowAvailabilityThenBusinessType() {
        val byName = seedStores.associateBy { it.name }
        assertEquals(StoreBadge.OPEN_FOR_LUNCH, byName.getValue("Test Ramen Bar").badge)
        assertEquals(StoreBadge.PICKUP_ONLY, byName.getValue("Test Mart").badge)
        assertEquals(StoreBadge.IN_STORE_ONLY, byName.getValue("Sample Massage Studio").badge)
        assertEquals(StoreBadge.PAUSED, byName.getValue("Sample Soba House").badge)
        assertEquals(StoreBadge.FULL, byName.getValue("Test Bento Kitchen").badge)
        val pausedPickup = byName.getValue("Test Mart").copy(availability = StoreAvailability.PAUSED)
        assertEquals(StoreBadge.PAUSED, pausedPickup.badge, "Paused wins over the business type")
    }
}
