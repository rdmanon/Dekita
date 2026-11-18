package tech.vauldex.dekita.data.mock

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tech.vauldex.dekita.data.PlaceRepository
import tech.vauldex.dekita.domain.GeoPoint
import tech.vauldex.dekita.domain.PlaceKind
import tech.vauldex.dekita.domain.SavedPlace

/**
 * In-memory places over [seedPlaces]. The mockup never reads the real location. "Current location" is where
 * [currentLocation] says the mock customer is when it is picked, which is the seed start unless the debug panel moved them.
 */
class FakePlaceRepository(
    private val currentLocation: () -> GeoPoint? = { null },
) : PlaceRepository, Resettable {

    private val _places = MutableStateFlow(seedPlaces)
    override val places: StateFlow<List<SavedPlace>> = _places.asStateFlow()

    private val _selected = MutableStateFlow(seedPlaces.first { it.id == SeedPlaceIds.DEFAULT })
    override val selected: StateFlow<SavedPlace> = _selected.asStateFlow()

    override fun select(placeId: String) {
        val place = _places.value.firstOrNull { it.id == placeId } ?: return
        _selected.value = if (place.kind == PlaceKind.CURRENT) place.copy(location = currentLocation() ?: place.location) else place
    }

    override fun reset() {
        select(SeedPlaceIds.DEFAULT)
    }
}
