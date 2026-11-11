package tech.vauldex.dekita.domain

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GeoPointTest {

    private val tokyo = GeoPoint(35.6655, 139.7580)

    @Test
    fun offsetAndDistanceAgreeAtWalkingDistances() {
        for (bearing in listOf(0.0, 45.0, 120.0, 225.0, 330.0)) {
            for (meters in listOf(12.0, 50.0, 320.0, 1_440.0)) {
                val moved = tokyo.offset(meters, bearing)
                assertTrue(abs(tokyo.distanceTo(moved) - meters) < 0.5, "$meters m at $bearing°: ${tokyo.distanceTo(moved)}")
            }
        }
    }

    @Test
    fun northMovesLatitudeOnly() {
        val north = tokyo.offset(1_000.0, 0.0)
        assertTrue(north.latitude > tokyo.latitude)
        assertEquals(tokyo.longitude, north.longitude, 1e-9)
    }

    @Test
    fun rejectsImpossibleCoordinates() {
        assertFailsWith<IllegalArgumentException> { GeoPoint(91.0, 0.0) }
        assertFailsWith<IllegalArgumentException> { GeoPoint(0.0, 181.0) }
    }

    @Test
    fun distanceIsSymmetric() {
        val other = tokyo.offset(320.0, 45.0)
        assertEquals(tokyo.distanceTo(other).roundToInt(), other.distanceTo(tokyo).roundToInt())
    }
}
