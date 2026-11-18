package tech.vauldex.dekita.presentation.place

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import tech.vauldex.dekita.data.mock.FakePlaceRepository

@OptIn(ExperimentalCoroutinesApi::class)
class PlacePickerViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()
    private val places = FakePlaceRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.subscribedViewModel(): PlacePickerViewModel {
        val viewModel = PlacePickerViewModel(places)
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        return viewModel
    }

    @Test
    fun listsTheSavedPlacesWithTheDefaultSelected() = runTest(mainDispatcher) {
        val state = subscribedViewModel().state.value
        assertEquals(listOf("Current location", "Shibuya Station", "Home", "Office"), state.places.map { it.name })
        assertEquals("Shibuya Station", state.selected.name)
        assertFalse(state.hasNoMatches)
    }

    @Test
    fun theSearchNarrowsPlacesByNameOrSecondLine() = runTest(mainDispatcher) {
        val viewModel = subscribedViewModel()

        viewModel.setQuery("hom")
        runCurrent()
        assertEquals(listOf("Home"), viewModel.state.value.places.map { it.name })

        viewModel.setQuery("  roppongi ")
        runCurrent()
        assertEquals(listOf("Office"), viewModel.state.value.places.map { it.name }, "Matches the second line, ignoring case and spaces")

        viewModel.setQuery("nowhere")
        runCurrent()
        assertTrue(viewModel.state.value.hasNoMatches)
    }

    @Test
    fun pickingAPlaceSelectsItAndEmptiesTheSearch() = runTest(mainDispatcher) {
        val viewModel = subscribedViewModel()
        viewModel.setQuery("off")
        runCurrent()

        viewModel.select(viewModel.state.value.places.single().id)
        runCurrent()

        assertEquals("Office", viewModel.state.value.selected.name)
        assertEquals("Office", places.selected.value.name)
        assertEquals("", viewModel.state.value.query)
        assertEquals(4, viewModel.state.value.places.size)
    }

    @Test
    fun dismissingTheSheetKeepsThePlaceAndEmptiesTheSearch() = runTest(mainDispatcher) {
        val viewModel = subscribedViewModel()
        viewModel.setQuery("home")
        runCurrent()

        viewModel.clearQuery()
        runCurrent()

        assertEquals("Shibuya Station", viewModel.state.value.selected.name)
        assertEquals(4, viewModel.state.value.places.size)
    }
}
