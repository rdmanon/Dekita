package tech.vauldex.dekita.data.mock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import tech.vauldex.dekita.data.LoadState
import tech.vauldex.dekita.domain.GeoPoint
import tech.vauldex.dekita.domain.Store

@OptIn(ExperimentalCoroutinesApi::class)
class FakePlaceRepositoryTest {

    private fun TestScope.finishLoading() {
        runCurrent()
        advanceTimeBy(MockConfig.DEFAULT_LOADING_DELAY)
        runCurrent()
    }

    private fun FakeStoreRepository.loaded(): List<Store> = (stores.value as LoadState.Success).data

    @Test
    fun startsOnShibuyaStation() {
        val places = FakePlaceRepository()
        assertEquals("Shibuya Station", places.selected.value.name)
        assertEquals(seedPlaces, places.places.value)
    }

    @Test
    fun selectingAPlaceChangesTheSelectionAndAnUnknownIdDoesNothing() {
        val places = FakePlaceRepository()
        places.select(SeedPlaceIds.HOME)
        assertEquals("Home", places.selected.value.name)

        places.select("place-nowhere")
        assertEquals("Home", places.selected.value.name)
    }

    @Test
    fun currentLocationIsWhereTheMockCustomerIsWhenItIsPicked() {
        val moved = GeoPoint(35.70, 139.70)
        val places = FakePlaceRepository { moved }
        places.select(SeedPlaceIds.CURRENT)
        assertEquals(moved, places.selected.value.location)

        // With no position known, it falls back to the place's own seed point.
        val unknown = FakePlaceRepository { null }
        unknown.select(SeedPlaceIds.CURRENT)
        assertEquals(seedCustomerLocation, unknown.selected.value.location)
    }

    @Test
    fun resetGoesBackToTheDefaultPlace() {
        val places = FakePlaceRepository()
        places.select(SeedPlaceIds.OFFICE)
        places.reset()
        assertEquals(SeedPlaceIds.DEFAULT, places.selected.value.id)
    }

    @Test
    fun theDefaultPlaceLeavesTheSeedWalkingTimesAsTheyAre() = runTest {
        val places = FakePlaceRepository()
        val stores = FakeStoreRepository(backgroundScope, MockConfig(), places)
        finishLoading()
        assertEquals(seedStores.map { it.walkMinutes }, stores.loaded().map { it.walkMinutes })
    }

    @Test
    fun pickingAPlaceMeasuresEveryStoresWalkFromIt() = runTest {
        val places = FakePlaceRepository()
        val stores = FakeStoreRepository(backgroundScope, MockConfig(), places)
        finishLoading()

        places.select(SeedPlaceIds.HOME)
        runCurrent()

        val home = places.selected.value.location
        val loaded = stores.loaded()
        for (store in loaded) {
            assertEquals(home.walkMinutesTo(store.location), store.walkMinutes, store.name)
        }
        assertNotEquals(seedStores.map { it.walkMinutes }, loaded.map { it.walkMinutes }, "Home is not where the mock customer starts")
        assertTrue(loaded.all { it.walkMinutes >= 1 })

        places.select(SeedPlaceIds.SHIBUYA_STATION)
        runCurrent()
        assertEquals(seedStores.map { it.walkMinutes }, stores.loaded().map { it.walkMinutes })
    }
}
