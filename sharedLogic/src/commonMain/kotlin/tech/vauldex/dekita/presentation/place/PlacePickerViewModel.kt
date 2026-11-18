package tech.vauldex.dekita.presentation.place

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import tech.vauldex.dekita.data.PlaceRepository
import tech.vauldex.dekita.domain.SavedPlace
import tech.vauldex.dekita.presentation.STOP_TIMEOUT_MILLIS

data class PlacePickerUiState(
    /** The place Discover measures from. The chip shows its name. */
    val selected: SavedPlace,
    /** The saved places that match [query], in the repository's order. All of them while the search is empty. */
    val places: List<SavedPlace>,
    /** What the address search field holds, as typed. */
    val query: String = "",
) {
    val hasNoMatches: Boolean get() = places.isEmpty()
}

/**
 * The location chip and its picker sheet. The search field only narrows the saved places by name or second line:
 * the mockup has no address lookup.
 */
class PlacePickerViewModel(private val placeRepository: PlaceRepository) : ViewModel() {

    private val query = MutableStateFlow("")

    val state: StateFlow<PlacePickerUiState> =
        combine(placeRepository.places, placeRepository.selected, query, ::toUiState).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = toUiState(placeRepository.places.value, placeRepository.selected.value, query.value),
        )

    fun setQuery(text: String) {
        query.value = text
    }

    /** Measures from [placeId] from now on. The search starts empty the next time the sheet opens. */
    fun select(placeId: String) {
        placeRepository.select(placeId)
        query.value = ""
    }

    /** Empties the search, for when the sheet is dismissed without a pick. */
    fun clearQuery() {
        query.value = ""
    }

    private fun toUiState(places: List<SavedPlace>, selected: SavedPlace, query: String): PlacePickerUiState {
        val text = query.trim()
        val matches = if (text.isEmpty()) {
            places
        } else {
            places.filter { it.name.contains(text, ignoreCase = true) || it.subtitle.contains(text, ignoreCase = true) }
        }
        return PlacePickerUiState(selected = selected, places = matches, query = query)
    }
}
