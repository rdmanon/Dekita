package tech.vauldex.dekita.data.mock

import tech.vauldex.dekita.domain.BusinessType
import tech.vauldex.dekita.domain.Cart
import tech.vauldex.dekita.domain.Store
import tech.vauldex.dekita.presentation.headline
import tech.vauldex.dekita.presentation.label

// AVAIL-01: wording and defaults for the debug panel's "Live availability" section, so the Compose and
// SwiftUI panels act on the same store and describe it the same way. Debug panel only.

/** The store the controls act on until another is picked: the cart's store, else Test Ramen Bar. */
fun defaultAvailabilityStoreId(cart: Cart?): String = cart?.storeId ?: SeedStoreIds.RAMEN_BAR

/** The service-mode choice's words. */
val BusinessType.debugLabel: String
    get() = when (this) {
        BusinessType.BOTH -> "Both"
        BusinessType.IN_STORE_ONLY -> "In-store only"
        BusinessType.PICKUP_ONLY -> "Pickup only"
    }

/** One line on the store's live state, for example "Open for Lunch · Both · 3 orders ahead". */
fun Store.availabilityStatus(): String =
    listOfNotNull(badge.label, businessType.debugLabel, queue?.headline()).joinToString(" · ")
