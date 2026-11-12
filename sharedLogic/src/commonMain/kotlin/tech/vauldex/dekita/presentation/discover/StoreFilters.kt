package tech.vauldex.dekita.presentation.discover

import tech.vauldex.dekita.domain.FulfillmentMode
import tech.vauldex.dekita.domain.Store
import tech.vauldex.dekita.domain.StoreAvailability

/** The walking-distance filter's choices. */
enum class WalkLimit(val maxMinutes: Int) { FIVE(5), TEN(10), FIFTEEN(15) }

/**
 * Every Discover filter. They combine with AND: a store is listed only when it passes all of them.
 * An empty set or null means that filter is off.
 */
data class StoreFilters(
    /** The In-store / Pickup selector. Null shows every store. */
    val mode: FulfillmentMode? = null,
    val query: String = "",
    val walkLimit: WalkLimit? = null,
    /** Picked with the Discover chips; several combine with OR among themselves. */
    val categories: Set<String> = emptySet(),
    val statuses: Set<StoreAvailability> = emptySet(),
) {
    /** True when a filter other than the mode is on. Discover then shows a result list instead of its home sections. */
    val isRefined: Boolean
        get() = query.isNotBlank() || walkLimit != null || categories.isNotEmpty() || statuses.isNotEmpty()

    val isCleared: Boolean get() = mode == null && !isRefined

    /** The number shown on the Filters button: walking distance and statuses. Categories are the chips, not the sheet. */
    val sheetFilterCount: Int
        get() = (if (walkLimit != null) 1 else 0) + statuses.size

    /**
     * UI-01: a search or a Filters-sheet filter switches Discover to the result list. The mode and the category
     * chip filter the home sections in place instead.
     */
    val showsResults: Boolean get() = query.isNotBlank() || walkLimit != null || statuses.isNotEmpty()

    /**
     * Returns the match, or null when [store] is filtered out. Search looks at the store name, its category
     * and its item names, ignoring case; when only an item matches, the match names that item.
     */
    fun match(store: Store): StoreMatch? {
        if (mode != null && !store.supports(mode)) return null
        if (walkLimit != null && store.walkMinutes > walkLimit.maxMinutes) return null
        if (categories.isNotEmpty() && store.category !in categories) return null
        if (statuses.isNotEmpty() && store.availability !in statuses) return null

        val text = query.trim()
        if (text.isEmpty()) return StoreMatch(store)
        if (store.name.contains(text, ignoreCase = true) || store.category.contains(text, ignoreCase = true)) {
            return StoreMatch(store)
        }
        val item = store.itemNames.firstOrNull { it.contains(text, ignoreCase = true) } ?: return null
        return StoreMatch(store, matchedItem = item)
    }
}

/** A store that passed the filters. [matchedItem] is set when the search matched one of its items only. */
data class StoreMatch(val store: Store, val matchedItem: String? = null)

/** Applies [filters] and sorts nearest first. Stores at the same walking time keep their feed order. */
fun List<Store>.filterStores(filters: StoreFilters): List<StoreMatch> =
    mapNotNull(filters::match).sortedBy { it.store.walkMinutes }
