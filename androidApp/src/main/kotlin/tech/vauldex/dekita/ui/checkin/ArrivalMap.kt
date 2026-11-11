package tech.vauldex.dekita.ui.checkin

import android.graphics.Color as AndroidColor
import android.view.Gravity
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.roundToInt
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import tech.vauldex.dekita.R
import tech.vauldex.dekita.domain.GeoPoint
import tech.vauldex.dekita.domain.Geofence

/** MapLibre's own demo style: country outlines only, no streets. No API key and no extra supplier. */
const val DEMO_MAP_STYLE_URL = "https://demotiles.maplibre.org/style.json"

/**
 * The MapLibre style the check-in map loads. MainActivity provides the build's `dekita.mapStyleUrl`
 * (see androidApp/build.gradle.kts); the demo style is only the fallback when nothing provides one.
 */
val LocalMapStyleUrl = staticCompositionLocalOf { DEMO_MAP_STYLE_URL }

private const val FENCE_SOURCE = "check-in-fence"
private const val CUSTOMER_SOURCE = "check-in-customer"
private const val CAMERA_MILLIS = 600

/**
 * The check-in map: MapLibre with the geofence and the customer's dot as map layers. The store marker is the design's
 * Compose pin, kept over the store's spot as the camera moves. The mockup never reads the real
 * location: the dot is the mock position the debug panel moves, so no location permission is needed.
 */
@Composable
internal fun ArrivalMap(storeName: String, store: GeoPoint, customer: GeoPoint?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val styleUrl = LocalMapStyleUrl.current
    val fenceColor = MaterialTheme.colorScheme.primary.toArgb()
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            onCreate(null)
            // Decorative: the sheet says the same in words.
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
    }
    MapViewLifecycle(mapView)

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var storeOnScreen by remember { mutableStateOf<Offset?>(null) }
    val currentStore by rememberUpdatedState(store)

    BoxWithConstraints(modifier.clearAndSetSemantics {}) {
        val density = LocalDensity.current
        val statusBarTop = WindowInsets.statusBars.getTop(density)
        // The check-in sheet covers about the lower 55%, so frame the map in the space above it.
        val padding = with(density) {
            CameraPadding(
                side = 48.dp.roundToPx(),
                top = statusBarTop + 72.dp.roundToPx(),
                bottom = (maxHeight * 0.58f).roundToPx(),
            )
        }

        LaunchedEffect(mapView, styleUrl) {
            mapView.getMapAsync { loaded ->
                loaded.uiSettings.apply {
                    isRotateGesturesEnabled = false
                    isTiltGesturesEnabled = false
                    isCompassEnabled = false
                    // Keep the map data's attribution visible, under the back button rather than behind the sheet.
                    logoGravity = Gravity.TOP or Gravity.START
                    attributionGravity = Gravity.TOP or Gravity.START
                    setLogoMargins(padding.side / 3, padding.top, 0, 0)
                    setAttributionMargins(padding.side / 3 + logoWidth(density), padding.top, 0, 0)
                }
                val follow = { storeOnScreen = loaded.projection.toScreenLocation(currentStore.latLng).let { Offset(it.x, it.y) } }
                loaded.addOnCameraMoveListener(follow)
                loaded.addOnCameraIdleListener(follow)
                loaded.setStyle(Style.Builder().fromUri(styleUrl)) { ready ->
                    ready.addCheckInLayers(fenceColor)
                    style = ready
                }
                map = loaded
            }
        }

        LaunchedEffect(map, style, store, customer) {
            val loaded = map ?: return@LaunchedEffect
            val ready = style ?: return@LaunchedEffect
            ready.getSourceAs<GeoJsonSource>(FENCE_SOURCE)?.setGeoJson(fencePolygon(store))
            val dot = customer?.let { FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(it.point))) }
            ready.getSourceAs<GeoJsonSource>(CUSTOMER_SOURCE)?.setGeoJson(dot ?: FeatureCollection.fromFeatures(emptyList()))

            val update = CameraUpdateFactory.newLatLngBounds(
                framing(store, customer),
                padding.side,
                padding.top,
                padding.side,
                padding.bottom,
            )
            if (loaded.cameraPosition.zoom < 1) loaded.moveCamera(update) else loaded.animateCamera(update, CAMERA_MILLIS)
        }

        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        storeOnScreen?.let { point ->
            StorePin(
                storeName,
                Modifier
                    .offset {
                        IntOffset(
                            point.x.roundToInt() - StorePinWidth.roundToPx() / 2,
                            point.y.roundToInt() - StorePinCenterY.roundToPx(),
                        )
                    }
                    .width(StorePinWidth),
            )
        }
    }
}

private class CameraPadding(val side: Int, val top: Int, val bottom: Int)

/** Room for MapLibre's logo, so the attribution button sits beside it rather than on it. */
private fun logoWidth(density: Density): Int = with(density) { 88.dp.roundToPx() }

/** Forwards the screen's lifecycle to the MapView, which needs it to start, pause and free its renderer. */
@Composable
private fun MapViewLifecycle(mapView: MapView) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        var started = false
        var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> { mapView.onStart(); started = true }
                Lifecycle.Event.ON_RESUME -> { mapView.onResume(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> { mapView.onPause(); resumed = false }
                Lifecycle.Event.ON_STOP -> { mapView.onStop(); started = false }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }
}

private fun Style.addCheckInLayers(fenceColor: Int) {
    addSource(GeoJsonSource(FENCE_SOURCE))
    addSource(GeoJsonSource(CUSTOMER_SOURCE))
    addLayer(FillLayer("$FENCE_SOURCE-fill", FENCE_SOURCE).withProperties(fillColor(fenceColor), fillOpacity(0.12f)))
    addLayer(
        LineLayer("$FENCE_SOURCE-line", FENCE_SOURCE).withProperties(
            lineColor(fenceColor),
            lineOpacity(0.6f),
            lineWidth(2f),
            lineDasharray(arrayOf(3f, 3f)),
        ),
    )
    val blue = LocationBlue.toArgb()
    addLayer(CircleLayer("$CUSTOMER_SOURCE-halo", CUSTOMER_SOURCE).withProperties(circleColor(blue), circleOpacity(0.16f), circleRadius(22f)))
    addLayer(
        CircleLayer("$CUSTOMER_SOURCE-dot", CUSTOMER_SOURCE).withProperties(
            circleColor(blue),
            circleRadius(8f),
            circleStrokeColor(AndroidColor.WHITE),
            circleStrokeWidth(3f),
        ),
    )
}

/** The geofence as a 64-sided polygon, since GeoJSON has no circles. */
private fun fencePolygon(store: GeoPoint): Polygon {
    val ring = (0..64).map { step -> store.offset(Geofence.RADIUS_METERS.toDouble(), step * 360.0 / 64).point }
    return Polygon.fromLngLats(listOf(ring))
}

/** The store's whole geofence plus the customer, so both are always on screen. */
private fun framing(store: GeoPoint, customer: GeoPoint?): LatLngBounds {
    val reach = Geofence.RADIUS_METERS * 2.0
    val points = listOf(0.0, 90.0, 180.0, 270.0).map { store.offset(reach, it) } + listOfNotNull(customer)
    return LatLngBounds.Builder().includes(points.map { it.latLng }).build()
}

private val GeoPoint.latLng: LatLng get() = LatLng(latitude, longitude)

private val GeoPoint.point: Point get() = Point.fromLngLat(longitude, latitude)

/** The location dot. Not a theme role: it reads as "you" on any map, in light and dark. */
internal val LocationBlue = Color(0xFF1A73E8)

/** The design's store marker: the store's name over a restaurant pin. Its pin's center is [StorePinCenterY] from the top. */
@Composable
internal fun StorePin(storeName: String, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
    ) {
        Text(
            storeName,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
                .border(3.dp, Color.White, MaterialTheme.shapes.medium),
        ) {
            Icon(
                painterResource(R.drawable.ic_restaurant),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** Label (about 28 dp), gap (6 dp), then half the 48 dp pin. */
internal val StorePinCenterY = 58.dp

/** Wide enough for most store names; longer ones are cut at one line. */
internal val StorePinWidth = 160.dp
