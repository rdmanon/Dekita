package tech.vauldex.dekita.data.mock

import tech.vauldex.dekita.domain.BusinessType
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
}

internal val seedStores: List<Store> = listOf(
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
        id = SeedStoreIds.MART,
        name = "Test Mart",
        category = "Convenience",
        businessType = BusinessType.PICKUP_ONLY,
        availability = StoreAvailability.OPEN,
        walkMinutes = 3,
        openLabel = "Open 24 hours",
        queue = QueueStatus(ordersAhead = 0, estimatedWaitMinutes = 0),
        artKey = "convenience",
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
        id = SeedStoreIds.CURRY_STAND,
        name = "Sample Curry Stand",
        category = "Curry",
        businessType = BusinessType.PICKUP_ONLY,
        availability = StoreAvailability.OPEN,
        walkMinutes = 6,
        openLabel = "Open for Lunch",
        queue = QueueStatus(ordersAhead = 2, estimatedWaitMinutes = 5),
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
)

internal val seedFavoriteStoreIds: Set<String> = setOf(
    SeedStoreIds.RAMEN_BAR,
    SeedStoreIds.MART,
    SeedStoreIds.MASSAGE_STUDIO,
    SeedStoreIds.CURRY_STAND,
)
