package tech.vauldex.dekita.ui.checkin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.ceil
import kotlin.math.max
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
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
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.SymbolLayer
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
private const val STORE_SOURCE = "check-in-store"
private const val STORE_PIN_IMAGE = "check-in-store-pin"
private const val CAMERA_MILLIS = 600

/** Far past any screen edge, to move the live map out of view while its still stands in. */
private const val OFF_SCREEN_PX = 100_000f

/**
 * The check-in map: MapLibre with the geofence, the customer's dot and the design's store pin as map layers. The pin is
 * a map symbol, not a Compose overlay: an overlay follows the camera a frame or more behind the map, so it jumped
 * around the geofence while the map was dragged. The mockup never reads the real location: the dot is the mock
 * position the debug panel moves, so no location permission is needed.
 *
 * The map draws on MapLibre's default SurfaceView, off the UI thread, so dragging stays smooth (texture mode made
 * every map frame a UI frame, and drags stuttered). A SurfaceView can't fade with the screen, though, so while the
 * screen is leaving or covered, a still of the map taken when it last settled stands in for it and fades instead,
 * and the live map moves off screen until the screen is back; see [MapViewLifecycle].
 */
@Composable
internal fun ArrivalMap(storeName: String, store: GeoPoint, customer: GeoPoint?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val styleUrl = LocalMapStyleUrl.current
    val colors = MaterialTheme.colorScheme
    val fenceColor = colors.primary.toArgb()
    val pinColors = StorePinColors(colors.primary.toArgb(), colors.onPrimary.toArgb(), colors.surface.toArgb(), colors.onSurface.toArgb())
    val storePin = remember(storeName, pinColors) { storePinBitmap(context, storeName, pinColors) }
    val mapView = remember {
        MapLibre.getInstance(context)
        val options = MapLibreMapOptions.createFromAttributes(context)
            .logoEnabled(false)
            .attributionEnabled(false)
        MapView(context, options).apply {
            onCreate(null)
            // Decorative: the sheet says the same in words.
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
    }
    // The latest still of the settled map, and the one shown in its place while the screen isn't resumed.
    val stills = remember { MapStills() }
    var still by remember { mutableStateOf<ImageBitmap?>(null) }
    MapViewLifecycle(
        mapView,
        onPause = {
            stills.latest?.let {
                still = it.asImageBitmap()
                // Off screen rather than hidden: hiding a SurfaceView destroys its surface, and tearing down the
                // map's renderer then froze the first frame of the transition for up to 650 ms on the emulator.
                mapView.translationX = OFF_SCREEN_PX
            }
        },
        onResume = {
            mapView.translationX = 0f
            still = null
        },
    )

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }

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
                    // No MapLibre logo and no attribution button, as asked for the mockup. OpenStreetMap's licence
                    // needs the map data credited somewhere in the app before release; it isn't shown anywhere yet.
                    isLogoEnabled = false
                    isAttributionEnabled = false
                }
                // Idle means no gestures or animations and every tile loaded, so the still is complete.
                mapView.addOnDidBecomeIdleListener { loaded.snapshot { stills.latest = it } }
                loaded.setStyle(Style.Builder().fromUri(styleUrl)) { ready ->
                    ready.addCheckInLayers(fenceColor)
                    style = ready
                }
                map = loaded
            }
        }

        LaunchedEffect(map, style, store, customer, storePin) {
            val loaded = map ?: return@LaunchedEffect
            val ready = style ?: return@LaunchedEffect
            ready.addImage(STORE_PIN_IMAGE, storePin)
            ready.getSourceAs<GeoJsonSource>(STORE_SOURCE)?.setGeoJson(Feature.fromGeometry(store.point))
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
        still?.let { Image(it, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize()) }
    }
}

private class CameraPadding(val side: Int, val top: Int, val bottom: Int)

/** Holds the latest still without recomposing: it is only read when the screen pauses. */
private class MapStills {
    var latest: Bitmap? = null
}

/**
 * Forwards the screen's lifecycle to the MapView, which needs it to start, pause and free its renderer.
 * Navigation pauses a screen as soon as it starts leaving (Back, or another screen opening over it) and resumes it once
 * it is settled on screen again, so [onPause] and [onResume] bracket every transition.
 */
@Composable
private fun MapViewLifecycle(mapView: MapView, onPause: () -> Unit, onResume: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnPause by rememberUpdatedState(onPause)
    val currentOnResume by rememberUpdatedState(onResume)
    DisposableEffect(lifecycle, mapView) {
        var started = false
        var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> { mapView.onStart(); started = true }
                Lifecycle.Event.ON_RESUME -> { mapView.onResume(); resumed = true; currentOnResume() }
                Lifecycle.Event.ON_PAUSE -> { currentOnPause(); mapView.onPause(); resumed = false }
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
    addSource(GeoJsonSource(STORE_SOURCE))
    addLayer(
        SymbolLayer("$STORE_SOURCE-pin", STORE_SOURCE).withProperties(
            iconImage(STORE_PIN_IMAGE),
            // Always drawn, whatever map labels are nearby.
            iconAllowOverlap(true),
            iconIgnorePlacement(true),
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

/** The pin's colors, from the theme, so the pin follows light and dark mode. */
private data class StorePinColors(val pin: Int, val onPin: Int, val label: Int, val onLabel: Int)

/**
 * The design's store marker, the store's name over a restaurant pin, drawn into a bitmap for the map's symbol layer.
 * The pin's center is the bitmap's center, so the symbol's default center anchor puts the pin on the store.
 */
private fun storePinBitmap(context: Context, storeName: String, colors: StorePinColors): Bitmap {
    val metrics = context.resources.displayMetrics
    fun dp(value: Float) = value * metrics.density
    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colors.onLabel
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, metrics)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    val labelPadX = dp(10f)
    val labelPadY = dp(6f)
    // Long names are cut at one line, as before.
    val label = TextUtils.ellipsize(storeName, textPaint, dp(160f) - 2 * labelPadX, TextUtils.TruncateAt.END).toString()
    val font = textPaint.fontMetrics
    val labelWidth = textPaint.measureText(label) + 2 * labelPadX
    val labelHeight = font.descent - font.ascent + 2 * labelPadY
    val pinSize = dp(48f)
    val pinTop = labelHeight + dp(6f)
    val pinCenterY = pinTop + pinSize / 2

    val width = ceil(max(labelWidth, pinSize)).toInt()
    val bitmap = Bitmap.createBitmap(width, ceil(2 * pinCenterY).toInt(), Bitmap.Config.ARGB_8888)
    bitmap.density = metrics.densityDpi
    val canvas = Canvas(bitmap)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    val labelLeft = (width - labelWidth) / 2
    fill.color = colors.label
    canvas.drawRoundRect(RectF(labelLeft, 0f, labelLeft + labelWidth, labelHeight), dp(8f), dp(8f), fill)
    canvas.drawText(label, labelLeft + labelPadX, labelPadY - font.ascent, textPaint)

    val pinLeft = (width - pinSize) / 2
    val pinRect = RectF(pinLeft, pinTop, pinLeft + pinSize, pinTop + pinSize)
    fill.color = colors.pin
    canvas.drawRoundRect(pinRect, dp(16f), dp(16f), fill)
    val border = dp(3f)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = border
        color = AndroidColor.WHITE
    }
    pinRect.inset(border / 2, border / 2)
    canvas.drawRoundRect(pinRect, dp(16f) - border / 2, dp(16f) - border / 2, stroke)

    val iconSize = dp(22f).toInt()
    val icon = requireNotNull(context.getDrawable(R.drawable.ic_restaurant)).mutate()
    icon.setTint(colors.onPin)
    val iconLeft = ((width - iconSize) / 2f).toInt()
    val iconTop = (pinCenterY - iconSize / 2f).toInt()
    icon.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
    icon.draw(canvas)
    return bitmap
}
