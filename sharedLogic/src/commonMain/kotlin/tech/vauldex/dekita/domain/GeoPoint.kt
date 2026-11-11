package tech.vauldex.dekita.domain

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A place on the map, in degrees (WGS 84, as GPS and both map SDKs use). */
data class GeoPoint(val latitude: Double, val longitude: Double) {
    init {
        require(latitude in -90.0..90.0) { "Latitude out of range: $latitude" }
        require(longitude in -180.0..180.0) { "Longitude out of range: $longitude" }
    }

    /**
     * The point [meters] away along [bearingDegrees] (0 is north, 90 east). A flat-earth approximation,
     * close enough for the few hundred meters of a walk to a store.
     */
    fun offset(meters: Double, bearingDegrees: Double): GeoPoint {
        val bearing = bearingDegrees * PI / 180
        val north = meters * cos(bearing)
        val east = meters * sin(bearing)
        return GeoPoint(
            latitude = latitude + north / METERS_PER_DEGREE,
            longitude = longitude + east / (METERS_PER_DEGREE * cos(latitude * PI / 180)),
        )
    }

    /** Meters to [other], with the same flat-earth approximation as [offset]. */
    fun distanceTo(other: GeoPoint): Double {
        val meanLatitude = (latitude + other.latitude) / 2 * PI / 180
        val north = (other.latitude - latitude) * METERS_PER_DEGREE
        val east = (other.longitude - longitude) * METERS_PER_DEGREE * cos(meanLatitude)
        return sqrt(north * north + east * east)
    }

    private companion object {
        const val METERS_PER_DEGREE = 111_320.0
    }
}
