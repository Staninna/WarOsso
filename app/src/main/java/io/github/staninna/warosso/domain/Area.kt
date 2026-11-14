package io.github.staninna.warosso.domain

/** How far the starting outline stays clear of the outer walls. */
const val AREA_MARGIN_M = 15.0

/** Corners of the starting outline closer together than this are merged into one. */
private const val MIN_CORNER_GAP_M = 12.0

/**
 * The outline a complex starts with: a polygon around every building that holds an address on
 * [street], [AREA_MARGIN_M] wider all round. The owner reshapes it in Setup. Without such
 * buildings there is nothing to wrap, and the outline is the default circle.
 */
fun areaAround(street: String, addresses: List<Address>, buildings: List<Building>): List<LatLng> {
    val onStreet = addresses.filter { it.street == street }.mapNotNullTo(HashSet()) { it.buildingOsmId }
    val corners = buildings.filter { it.osmId in onStreet }.flatMap { it.outline }
    if (corners.size < 3) return Complex.defaultPolygon()

    val hull = convexHull(corners)
    val middle = hull.centroid()
    val widened = hull.map { middle.offset(middle.distanceTo(it) + AREA_MARGIN_M, middle.bearingTo(it)) }

    // Neighbouring wall corners give corners a few metres apart. One of each bunch is enough,
    // and leaves handles that a finger can tell apart.
    val spaced = widened.fold(emptyList<LatLng>()) { kept, corner ->
        if (kept.isNotEmpty() && kept.last().distanceTo(corner) < MIN_CORNER_GAP_M) kept else kept + corner
    }
    return if (spaced.size > 3 && spaced.first().distanceTo(spaced.last()) < MIN_CORNER_GAP_M) spaced.dropLast(1) else spaced
}
