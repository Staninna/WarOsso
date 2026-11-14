package io.github.staninna.warosso.ui.map

import android.content.res.Resources
import io.github.staninna.warosso.domain.ComplexData
import io.github.staninna.warosso.domain.LatLng
import io.github.staninna.warosso.domain.centroid
import io.github.staninna.warosso.domain.circlePolygon
import io.github.staninna.warosso.domain.distanceTo
import io.github.staninna.warosso.domain.sectorPolygon
import io.github.staninna.warosso.ui.buildingName
import io.github.staninna.warosso.ui.entranceBadge
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Geometry
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

/** Everything WarOsso draws on top of the base map. Screens describe a scene; [MapCanvas] renders it. */
data class MapScene(
    /** The complex polygon. */
    val area: List<LatLng> = emptyList(),
    val buildings: List<MapBuilding> = emptyList(),
    val markers: List<MapMarker> = emptyList(),
    val user: MapUser? = null,
    /** A straight line, from the user to the target entrance. */
    val route: Pair<LatLng, LatLng>? = null,
)

data class MapBuilding(val outline: List<LatLng>, val label: String)

enum class MarkerKind { ENTRANCE, TARGET, PENDING, VERTEX, MIDPOINT, USER }

/** What a touch on the map hit. [id] means whatever the screen that made the marker says it means. */
data class MapHandle(val kind: MarkerKind, val id: Long)

data class MapMarker(
    val handle: MapHandle,
    val latLng: LatLng,
    val label: String? = null,
    /** Touchable: a tap reports the handle, a drag moves it. */
    val interactive: Boolean = false,
)

data class MapUser(
    val latLng: LatLng,
    val accuracyM: Float,
    val headingDeg: Float?,
    /** Debug position simulation: the dot can be dragged. */
    val draggable: Boolean = false,
)

/**
 * The complex as both map screens draw it: its outline, its numbered buildings by name and a
 * marker on every entrance. Screens add their own parts with `copy`.
 */
fun ComplexData.toScene(resources: Resources, targetId: Long? = null, interactiveEntrances: Boolean = false): MapScene = MapScene(
    area = complex.polygon,
    buildings = complexBuildings.map { MapBuilding(it.outline, resources.buildingName(this, it)) },
    markers = entrances.map { entrance ->
        val kind = if (entrance.id == targetId) MarkerKind.TARGET else MarkerKind.ENTRANCE
        MapMarker(MapHandle(kind, entrance.id), entrance.latLng, resources.entranceBadge(this, entrance), interactiveEntrances)
    },
)

// Feature properties the style layers filter and label on.
internal const val PROP_KIND = "kind"
internal const val PROP_LABEL = "label"
internal const val PROP_ID = "id"
internal const val PROP_INTERACTIVE = "interactive"

internal const val KIND_AREA = "area"
internal const val KIND_BUILDING = "building"
internal const val KIND_BUILDING_LABEL = "building-label"
internal const val KIND_ACCURACY = "accuracy"
internal const val KIND_HEADING = "heading"
internal const val KIND_ROUTE = "route"

private const val HEADING_CONE_DEG = 60.0

/** The cone reaches a quarter of the way to the target, so it looks about the same at every zoom. */
private const val HEADING_CONE_REACH = 0.25
private val HEADING_CONE_M = 2.0..12.0

private fun LatLng.toPoint(): Point = Point.fromLngLat(lon, lat)

private fun List<LatLng>.toPolygon(): Polygon = Polygon.fromLngLats(listOf((this + first()).map { it.toPoint() }))

private fun feature(geometry: Geometry, kind: String, label: String? = null): Feature =
    Feature.fromGeometry(geometry).apply {
        addStringProperty(PROP_KIND, kind)
        label?.let { addStringProperty(PROP_LABEL, it) }
    }

/** The parts that change when the setup changes: area, buildings, markers. */
internal fun MapScene.staticFeatures(): FeatureCollection {
    val features = buildList {
        if (area.size >= 3) add(feature(area.toPolygon(), KIND_AREA))
        buildings.forEach { building ->
            add(feature(building.outline.toPolygon(), KIND_BUILDING))
            add(feature(building.outline.centroid().toPoint(), KIND_BUILDING_LABEL, building.label))
        }
        markers.forEach { add(it.toFeature()) }
    }
    return FeatureCollection.fromFeatures(features)
}

/** The parts that change with every fix: the user's dot, accuracy circle, heading cone and the line. */
internal fun MapScene.liveFeatures(): FeatureCollection {
    val features = buildList {
        user?.let { user ->
            add(feature(circlePolygon(user.latLng, user.accuracyM.toDouble(), vertices = 48).toPolygon(), KIND_ACCURACY))
            user.headingDeg?.let { heading ->
                val reach = route?.let { (from, to) -> from.distanceTo(to) * HEADING_CONE_REACH } ?: HEADING_CONE_M.endInclusive
                val cone = sectorPolygon(user.latLng, reach.coerceIn(HEADING_CONE_M), heading.toDouble(), HEADING_CONE_DEG)
                add(feature(cone.toPolygon(), KIND_HEADING))
            }
            add(MapMarker(MapHandle(MarkerKind.USER, 0), user.latLng, interactive = user.draggable).toFeature())
        }
        route?.let { (from, to) -> add(feature(LineString.fromLngLats(listOf(from.toPoint(), to.toPoint())), KIND_ROUTE)) }
    }
    return FeatureCollection.fromFeatures(features)
}

private fun MapMarker.toFeature(): Feature = feature(latLng.toPoint(), handle.kind.name, label).apply {
    addNumberProperty(PROP_ID, handle.id)
    addBooleanProperty(PROP_INTERACTIVE, interactive)
}

internal fun Feature.toHandle(): MapHandle? {
    val kind = MarkerKind.entries.firstOrNull { it.name == getStringProperty(PROP_KIND) } ?: return null
    return MapHandle(kind, getNumberProperty(PROP_ID).toLong())
}
