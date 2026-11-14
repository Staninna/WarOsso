package io.github.staninna.warosso.ui.map

import android.annotation.SuppressLint
import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.staninna.warosso.domain.LatLng
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.sources.GeoJsonSource
import kotlin.math.hypot
import org.maplibre.android.geometry.LatLng as MapLatLng

private const val MAX_ZOOM = 20.0
private const val SINGLE_POINT_ZOOM = 18.0
private const val CAMERA_ANIMATION_MS = 700

/**
 * What the camera should keep in view. The camera moves when this changes; bump [generation] to
 * move it again to the same points, after the user has panned away.
 */
data class Framing(val points: List<LatLng>, val generation: Int = 0)

/** Something the user did on the map. What can be touched is in the scene: [MapMarker.interactive], [MapUser.draggable]. */
sealed interface MapEvent {
    data class Click(val at: LatLng) : MapEvent
    data class LongClick(val at: LatLng) : MapEvent
    data class MarkerClick(val handle: MapHandle) : MapEvent

    /** Sent for every move of a dragged marker, and once more with [done] on release. */
    data class MarkerDrag(val handle: MapHandle, val to: LatLng, val done: Boolean) : MapEvent

    /** The user panned or zoomed by hand, so the camera should stop following. */
    data object CameraMovedByUser : MapEvent
}

/**
 * The MapLibre map with WarOsso's overlay.
 *
 * @param contentPadding the part of the map that other UI covers.
 */
@Composable
fun MapCanvas(
    scene: MapScene,
    framing: Framing,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onEvent: (MapEvent) -> Unit = {},
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    val palette = remember(colors) { MapPalette(colors) }
    val currentOnEvent by rememberUpdatedState(onEvent)

    val mapView = remember {
        initMapLibre(context)
        MapView(context)
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }

    MapViewLifecycle(mapView)

    LaunchedEffect(mapView) {
        mapView.getMapAsync { loaded ->
            loaded.uiSettings.run {
                isCompassEnabled = false
                isTiltGesturesEnabled = false
                isLogoEnabled = false
            }
            loaded.setMaxZoomPreference(MAX_ZOOM)
            loaded.addOnMapClickListener { point ->
                currentOnEvent(MapEvent.Click(point.toDomain()))
                false
            }
            loaded.addOnMapLongClickListener { point ->
                currentOnEvent(MapEvent.LongClick(point.toDomain()))
                false
            }
            loaded.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) currentOnEvent(MapEvent.CameraMovedByUser)
            }
            mapView.installMarkerTouch(loaded) { currentOnEvent(it) }
            map = loaded
        }
    }

    // A new base style (light or dark) or new colours rebuild the overlay layers from scratch.
    LaunchedEffect(map, dark, palette) {
        val loaded = map ?: return@LaunchedEffect
        style = null
        loaded.setStyle(Style.Builder().fromUri(if (dark) STYLE_DARK else STYLE_LIGHT)) { fresh ->
            fresh.addWarOssoLayers(palette)
            style = fresh
        }
    }

    val staticPart = remember(scene.area, scene.buildings, scene.markers) { scene.staticFeatures() }
    val livePart = remember(scene.user, scene.route) { scene.liveFeatures() }
    LaunchedEffect(style, staticPart) { style?.getSourceAs<GeoJsonSource>(SOURCE_STATIC)?.setGeoJson(staticPart) }
    LaunchedEffect(style, livePart) { style?.getSourceAs<GeoJsonSource>(SOURCE_LIVE)?.setGeoJson(livePart) }

    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val edgePx = with(density) { 56.dp.roundToPx() }
    val paddingPx by rememberUpdatedState(
        with(density) {
            intArrayOf(
                contentPadding.calculateStartPadding(layoutDirection).roundToPx(),
                contentPadding.calculateTopPadding().roundToPx(),
                contentPadding.calculateEndPadding(layoutDirection).roundToPx(),
                contentPadding.calculateBottomPadding().roundToPx(),
            )
        },
    )
    LaunchedEffect(map, paddingPx[0], paddingPx[3]) {
        map?.uiSettings?.setAttributionMargins(paddingPx[0] + edgePx / 4, 0, 0, paddingPx[3] + edgePx / 4)
    }
    // Only a new framing moves the camera. Padding that changes later (a panel growing) leaves it alone.
    LaunchedEffect(map, framing) {
        val loaded = map ?: return@LaunchedEffect
        val (left, top, right, bottom) = paddingPx
        val points = framing.points.map { it.toMap() }
        val update = when (points.size) {
            0 -> return@LaunchedEffect
            1 -> {
                loaded.setPadding(left, top, right, bottom)
                CameraUpdateFactory.newLatLngZoom(points.single(), SINGLE_POINT_ZOOM)
            }
            else -> {
                loaded.setPadding(0, 0, 0, 0)
                val bounds = LatLngBounds.Builder().includes(points).build()
                CameraUpdateFactory.newLatLngBounds(bounds, left + edgePx, top + edgePx, right + edgePx, bottom + edgePx)
            }
        }
        loaded.animateCamera(update, CAMERA_ANIMATION_MS)
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

/** Forwards the host lifecycle to the [MapView] and tears it down when the composable leaves. */
@Composable
private fun MapViewLifecycle(mapView: MapView) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        mapView.onCreate(null)
        // addObserver replays the events up to the current state, so a map that joins a resumed
        // screen still gets onStart and onResume.
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }
}

/**
 * Lets a finger pick up interactive markers. A touch that starts on one never reaches the map:
 * released in place it is a marker click, moved past the touch slop it drags the marker along.
 */
@SuppressLint("ClickableViewAccessibility") // Markers are also reachable through the lists beside the map.
private fun MapView.installMarkerTouch(map: MapLibreMap, onEvent: (MapEvent) -> Unit) {
    val slop = ViewConfiguration.get(context).scaledTouchSlop
    val reach = 22 * resources.displayMetrics.density
    val interactive = Expression.eq(Expression.get(PROP_INTERACTIVE), Expression.literal(true))

    var held: MapHandle? = null
    var down = PointF()
    var dragging = false

    setOnTouchListener { _, event ->
        val position = PointF(event.x, event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val area = RectF(event.x - reach, event.y - reach, event.x + reach, event.y + reach)
                // The topmost layer comes first, so the user dot wins over an entrance under it.
                held = map.queryRenderedFeatures(area, interactive, *MARKER_LAYERS).firstNotNullOfOrNull { it.toHandle() }
                down = position
                dragging = false
                held != null
            }
            MotionEvent.ACTION_MOVE -> held?.let { handle ->
                if (!dragging && hypot(position.x - down.x, position.y - down.y) > slop) dragging = true
                if (dragging) onEvent(MapEvent.MarkerDrag(handle, map.projection.fromScreenLocation(position).toDomain(), done = false))
                true
            } ?: false
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> held?.let { handle ->
                when {
                    dragging -> onEvent(MapEvent.MarkerDrag(handle, map.projection.fromScreenLocation(position).toDomain(), done = true))
                    event.actionMasked == MotionEvent.ACTION_UP -> onEvent(MapEvent.MarkerClick(handle))
                }
                held = null
                true
            } ?: false
            else -> held != null
        }
    }
}

private fun MapLatLng.toDomain() = LatLng(latitude, longitude)

private fun LatLng.toMap() = MapLatLng(lat, lon)
