package tech.vauldex.dekita.data.mock

import tech.vauldex.dekita.domain.PlaceKind
import tech.vauldex.dekita.domain.SavedPlace

internal object SeedPlaceIds {
    const val CURRENT = "place-current"
    const val SHIBUYA_STATION = "place-shibuya-station"
    const val HOME = "place-home"
    const val OFFICE = "place-office"

    /** The place the mockup starts on, as in the design. */
    const val DEFAULT = SHIBUYA_STATION
}

/**
 * The places the picker offers, with the design's names and second lines. Where they are is made up: Shibuya
 * Station and the phone's location are the mock customer's start, and Home and Office sit a walk away from it, in
 * different directions, so picking one visibly changes the walking times. None of them is a real address.
 */
internal val seedPlaces: List<SavedPlace> = listOf(
    SavedPlace(
        id = SeedPlaceIds.CURRENT,
        name = "Current location",
        subtitle = "Using your phone's location",
        kind = PlaceKind.CURRENT,
        location = seedCustomerLocation,
    ),
    SavedPlace(
        id = SeedPlaceIds.SHIBUYA_STATION,
        name = "Shibuya Station",
        subtitle = "Recent",
        kind = PlaceKind.RECENT,
        location = seedCustomerLocation,
    ),
    SavedPlace(
        id = SeedPlaceIds.HOME,
        name = "Home",
        subtitle = "Ebisu, Shibuya-ku",
        kind = PlaceKind.HOME,
        location = seedCustomerLocation.offset(900.0, 200.0),
    ),
    SavedPlace(
        id = SeedPlaceIds.OFFICE,
        name = "Office",
        subtitle = "Roppongi, Minato-ku",
        kind = PlaceKind.OFFICE,
        location = seedCustomerLocation.offset(700.0, 100.0),
    ),
)
