package tech.vauldex.dekita.domain

/** What the Cart Review button does. */
enum class CheckoutAction {
    /** Pay in the app now: pickup orders, takeout, and stores that only serve in-store. */
    PAY_NOW,

    /** Eat-In at a store with both modes: save a DRAFT order now, pay at check-in with [Start Cooking]. */
    HEAD_TO_STORE,
}

/** Only stores that offer both modes let the customer choose; the others have one fixed mode. */
val BusinessType.letsCustomerChooseMode: Boolean get() = this == BusinessType.BOTH

/**
 * The cart's mode when it starts. A single-mode store always uses its mode. A store with both
 * uses the customer's Discover choice, or Eat-In (in-store) when there was none, as in the design.
 */
fun BusinessType.defaultMode(preferred: FulfillmentMode?): FulfillmentMode = when (this) {
    BusinessType.IN_STORE_ONLY -> FulfillmentMode.IN_STORE
    BusinessType.PICKUP_ONLY -> FulfillmentMode.PICKUP
    BusinessType.BOTH -> preferred ?: FulfillmentMode.IN_STORE
}

/** A mode the store doesn't offer falls back to the store's default. */
fun BusinessType.resolveMode(mode: FulfillmentMode): FulfillmentMode =
    if (mode in supportedModes) mode else defaultMode(null)

fun checkoutAction(businessType: BusinessType, mode: FulfillmentMode): CheckoutAction =
    if (businessType == BusinessType.BOTH && mode == FulfillmentMode.IN_STORE) {
        CheckoutAction.HEAD_TO_STORE
    } else {
        CheckoutAction.PAY_NOW
    }
