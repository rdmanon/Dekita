package tech.vauldex.dekita.domain

/** What a place is, so each screen can pick its icon. */
enum class PlaceKind { CURRENT, RECENT, HOME, OFFICE }

/**
 * A spot the customer can look for stores from. Nearby stores, walking times and queues are measured from it.
 * [location] is where the spot is.
 */
data class SavedPlace(
    val id: String,
    val name: String,
    /** The second line: an address, "Recent", or what the place is. */
    val subtitle: String,
    val kind: PlaceKind,
    val location: GeoPoint,
)
