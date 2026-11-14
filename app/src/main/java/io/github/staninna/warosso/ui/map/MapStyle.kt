package io.github.staninna.warosso.ui.map

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import org.maplibre.android.MapLibre
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textAnchor
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textOffset
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.maps.Style

/** OpenFreeMap vector styles: no API key, no account. */
internal const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/liberty"
internal const val STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"

internal const val SOURCE_STATIC = "warosso-static"
internal const val SOURCE_LIVE = "warosso-live"

/** Layers whose features react to touch. */
internal val MARKER_LAYERS = MarkerKind.entries.map { "warosso-marker-${it.name}" }.toTypedArray()

/** Both OpenFreeMap styles carry this font, so labels also render from the offline cache. */
private val LABEL_FONT = arrayOf("Noto Sans Regular")

/**
 * Starts MapLibre. It would normally ask Android whether there is a network, which takes the
 * ACCESS_NETWORK_STATE permission. WarOsso does not hold it, so MapLibre is
 * told to always try: offline, a request fails fast and the cached tiles are used.
 */
internal fun initMapLibre(context: Context) {
    MapLibre.getInstance(context.applicationContext)
    MapLibre.setConnected(true)
}

/** The map colours, taken from the app's colour scheme so the overlay follows the wallpaper. */
internal data class MapPalette(
    val accent: Int,
    val onAccent: Int,
    val target: Int,
    val onTarget: Int,
    val building: Int,
    val text: Int,
    val halo: Int,
    val user: Int,
) {
    constructor(colors: ColorScheme) : this(
        accent = colors.primary.toArgb(),
        onAccent = colors.onPrimary.toArgb(),
        target = colors.tertiary.toArgb(),
        onTarget = colors.onTertiary.toArgb(),
        building = colors.primary.toArgb(),
        text = colors.onSurface.toArgb(),
        halo = colors.surface.toArgb(),
        user = colors.primary.toArgb(),
    )
}

private fun kindIs(kind: String): Expression = Expression.eq(Expression.get(PROP_KIND), Expression.literal(kind))

private fun <T : Layer> T.of(kind: String): T = apply {
    when (this) {
        is FillLayer -> setFilter(kindIs(kind))
        is LineLayer -> setFilter(kindIs(kind))
        is CircleLayer -> setFilter(kindIs(kind))
        is SymbolLayer -> setFilter(kindIs(kind))
    }
}

private fun markerLayer(kind: MarkerKind, radius: Float, fill: Int, stroke: Int, strokeWidth: Float = 3f) =
    CircleLayer("warosso-marker-${kind.name}", if (kind == MarkerKind.USER) SOURCE_LIVE else SOURCE_STATIC)
        .of(kind.name)
        .withProperties(circleRadius(radius), circleColor(fill), circleStrokeColor(stroke), circleStrokeWidth(strokeWidth))

/** [below] puts the text under a marker and lets it overlap its neighbours; building names give way instead. */
private fun labelLayer(id: String, kind: String, palette: MapPalette, size: Float, below: Boolean) =
    SymbolLayer(id, SOURCE_STATIC).of(kind).withProperties(
        textField(Expression.get(PROP_LABEL)),
        textFont(LABEL_FONT),
        textSize(size),
        textColor(palette.text),
        textHaloColor(palette.halo),
        textHaloWidth(2f),
        textAnchor(if (below) Property.TEXT_ANCHOR_TOP else Property.TEXT_ANCHOR_CENTER),
        textOffset(if (below) arrayOf(0f, 1.1f) else arrayOf(0f, 0f)),
        textAllowOverlap(below),
        textIgnorePlacement(below),
    )

/** Adds WarOsso's sources and layers to a freshly loaded [style], bottom layer first. */
internal fun Style.addWarOssoLayers(palette: MapPalette) {
    addSource(GeoJsonSource(SOURCE_STATIC))
    addSource(GeoJsonSource(SOURCE_LIVE))

    val layers = listOf(
        FillLayer("warosso-building-fill", SOURCE_STATIC).of(KIND_BUILDING)
            .withProperties(fillColor(palette.building), fillOpacity(0.28f)),
        LineLayer("warosso-building-line", SOURCE_STATIC).of(KIND_BUILDING)
            .withProperties(lineColor(palette.building), lineWidth(2f)),
        LineLayer("warosso-area", SOURCE_STATIC).of(KIND_AREA)
            .withProperties(lineColor(palette.accent), lineWidth(3f), lineDasharray(arrayOf(2f, 2f)), lineCap(Property.LINE_CAP_ROUND)),
        FillLayer("warosso-accuracy", SOURCE_LIVE).of(KIND_ACCURACY)
            .withProperties(fillColor(palette.user), fillOpacity(0.16f)),
        LineLayer("warosso-accuracy-line", SOURCE_LIVE).of(KIND_ACCURACY)
            .withProperties(lineColor(palette.user), lineWidth(1.5f)),
        LineLayer("warosso-route-casing", SOURCE_LIVE).of(KIND_ROUTE)
            .withProperties(lineColor(palette.onTarget), lineWidth(9f), lineCap(Property.LINE_CAP_ROUND)),
        LineLayer("warosso-route", SOURCE_LIVE).of(KIND_ROUTE)
            .withProperties(lineColor(palette.target), lineWidth(5f), lineCap(Property.LINE_CAP_ROUND)),
        FillLayer("warosso-heading", SOURCE_LIVE).of(KIND_HEADING)
            .withProperties(fillColor(palette.user), fillOpacity(0.45f)),
        labelLayer("warosso-building-label", KIND_BUILDING_LABEL, palette, size = 13f, below = false),
        markerLayer(MarkerKind.MIDPOINT, radius = 7f, fill = palette.onAccent, stroke = palette.accent, strokeWidth = 2f),
        markerLayer(MarkerKind.VERTEX, radius = 11f, fill = palette.accent, stroke = palette.onAccent),
        markerLayer(MarkerKind.ENTRANCE, radius = 10f, fill = palette.accent, stroke = palette.onAccent),
        markerLayer(MarkerKind.PENDING, radius = 13f, fill = palette.onTarget, stroke = palette.target, strokeWidth = 5f),
        markerLayer(MarkerKind.TARGET, radius = 14f, fill = palette.target, stroke = palette.onTarget, strokeWidth = 4f),
        markerLayer(MarkerKind.USER, radius = 9f, fill = palette.user, stroke = palette.onAccent),
        labelLayer("warosso-entrance-label", MarkerKind.ENTRANCE.name, palette, size = 13f, below = true),
        labelLayer("warosso-target-label", MarkerKind.TARGET.name, palette, size = 16f, below = true),
    )
    layers.forEach(::addLayer)
}
