package tech.vauldex.dekita.data.mock

import tech.vauldex.dekita.domain.BusinessType
import tech.vauldex.dekita.domain.FavoriteItem
import tech.vauldex.dekita.domain.QueueStatus
import tech.vauldex.dekita.domain.Store
import tech.vauldex.dekita.domain.StoreAvailability

/** Seed store ids. Every store name says Test or Sample so it can't pass for a real business. */
internal object SeedStoreIds {
    const val RAMEN_BAR = "store-test-ramen-bar"
    const val MART = "store-test-mart"
    const val MASSAGE_STUDIO = "store-sample-massage-studio"
    const val CURRY_STAND = "store-sample-curry-stand"
    const val SOBA_HOUSE = "store-sample-soba-house"
    const val BENTO_KITCHEN = "store-test-bento-kitchen"
    const val UDON_HALL = "store-test-udon-hall"
    const val TEISHOKU = "store-sample-teishoku"
    const val HAIR_SALON = "store-test-hair-salon"
}

// The list order sets the order of the category chips on Discover (first appearance wins),
// so restaurants come first, as in the design. Discover sorts the stores themselves by walking time.
private val baseStores: List<Store> = listOf(
    Store(
        id = SeedStoreIds.RAMEN_BAR,
        name = "Test Ramen Bar",
        category = "Ramen",
        businessType = BusinessType.BOTH,
        availability = StoreAvailability.OPEN,
        walkMinutes = 4,
        openLabel = "Open for Lunch",
        queue = QueueStatus(ordersAhead = 3, estimatedWaitMinutes = 8),
        artKey = "ramen",
    ),
    Store(
        id = SeedStoreIds.CURRY_STAND,
        name = "Sample Curry Stand",
        category = "Curry",
        businessType = BusinessType.PICKUP_ONLY,
        availability = StoreAvailability.OPEN,
        walkMinutes = 6,
        openLabel = "Open for Lunch",
        queue = QueueStatus(ordersAhead = 0, estimatedWaitMinutes = 0),
        artKey = "curry",
    ),
    Store(
        id = SeedStoreIds.SOBA_HOUSE,
        name = "Sample Soba House",
        category = "Soba",
        businessType = BusinessType.BOTH,
        availability = StoreAvailability.PAUSED,
        walkMinutes = 7,
        openLabel = null,
        queue = null,
        artKey = "soba",
    ),
    Store(
        id = SeedStoreIds.BENTO_KITCHEN,
        name = "Test Bento Kitchen",
        category = "Bento",
        businessType = BusinessType.BOTH,
        availability = StoreAvailability.FULL,
        walkMinutes = 5,
        openLabel = "Open for Lunch",
        queue = QueueStatus(ordersAhead = 18, estimatedWaitMinutes = 35),
        artKey = "bento",
    ),
    Store(
        id = SeedStoreIds.MASSAGE_STUDIO,
        name = "Sample Massage Studio",
        category = "Salon",
        businessType = BusinessType.IN_STORE_ONLY,
        availability = StoreAvailability.OPEN,
        walkMinutes = 8,
        openLabel = "Open until 21:00",
        queue = QueueStatus(ordersAhead = 1, estimatedWaitMinutes = 25),
        artKey = "massage",
    ),
    Store(
        id = SeedStoreIds.MART,
        name = "Test Mart",
        category = "Convenience store",
        businessType = BusinessType.PICKUP_ONLY,
        availability = StoreAvailability.OPEN,
        walkMinutes = 3,
        openLabel = "Open 24 hours",
        queue = QueueStatus(ordersAhead = 0, estimatedWaitMinutes = 0),
        artKey = "convenience",
    ),
    Store(
        id = SeedStoreIds.UDON_HALL,
        name = "Test Udon Hall",
        category = "Udon",
        businessType = BusinessType.BOTH,
        availability = StoreAvailability.OPEN,
        walkMinutes = 9,
        openLabel = "Open for Lunch",
        queue = QueueStatus(ordersAhead = 15, estimatedWaitMinutes = 25),
        artKey = "udon",
    ),
    // A long Japanese name, so QA can check that store cards truncate it with an ellipsis.
    Store(
        id = SeedStoreIds.TEISHOKU,
        name = "Sample 炭火焼き定食と季節のおばんざい さんぷる亭 駅前本店",
        category = "Teishoku",
        businessType = BusinessType.BOTH,
        availability = StoreAvailability.OPEN,
        walkMinutes = 12,
        openLabel = "Open for Lunch",
        queue = QueueStatus(ordersAhead = 5, estimatedWaitMinutes = 12),
        artKey = "teishoku",
    ),
    // Farther than every walking-distance filter, so the 15 min filter still hides something.
    Store(
        id = SeedStoreIds.HAIR_SALON,
        name = "Test Hair & Nail Salon",
        category = "Salon",
        businessType = BusinessType.IN_STORE_ONLY,
        availability = StoreAvailability.OPEN,
        walkMinutes = 18,
        openLabel = "Open until 20:00",
        queue = QueueStatus(ordersAhead = 0, estimatedWaitMinutes = 0),
        artKey = "hair",
    ),
)

/** The feed carries each store's item names, taken from the seed menus, so search can match dishes. */
internal val seedStores: List<Store> = baseStores.map { store ->
    store.copy(itemNames = seedMenus[store.id]?.items?.map { it.name }.orEmpty())
}

internal val seedFavoriteStoreIds: Set<String> = setOf(
    SeedStoreIds.RAMEN_BAR,
    SeedStoreIds.MART,
    SeedStoreIds.MASSAGE_STUDIO,
    SeedStoreIds.CURRY_STAND,
)

private fun favorite(storeId: String, itemId: String): FavoriteItem {
    val item = seedItem(storeId, itemId)
    return FavoriteItem(storeId = storeId, itemId = item.id, itemName = item.name, price = item.basePrice)
}

internal val seedFavoriteItems: List<FavoriteItem> by lazy {
    listOf(
        favorite(SeedStoreIds.RAMEN_BAR, "item-shoyu-ramen"),
        favorite(SeedStoreIds.CURRY_STAND, "item-beef-curry"),
        favorite(SeedStoreIds.MART, "item-onigiri-salmon"),
        favorite(SeedStoreIds.MASSAGE_STUDIO, "item-shoulder-30"),
    )
}

/** Matches the design's Recommended row: ramen first, then the mart and the curry stand. */
internal val seedRecommendedStoreIds: List<String> = listOf(
    SeedStoreIds.RAMEN_BAR,
    SeedStoreIds.MART,
    SeedStoreIds.CURRY_STAND,
    SeedStoreIds.TEISHOKU,
)
