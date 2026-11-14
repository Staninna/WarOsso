package io.github.staninna.warosso.domain

/**
 * Outlines this close together are one building. BAG registers the blocks of this complex as two
 * halves with a narrow gap between them, about 2 m, and OSM follows BAG: "Eik" is the half that
 * carries the name plus the unnamed half beside it. The next real neighbour is 6 m away.
 */
const val SAME_BUILDING_GAP_M = 3.0

/**
 * Joins OSM outlines that are parts of one building, and returns for every outline id the
 * building it ended up in. A joined building takes the name and the id of its named part, or
 * else its lowest id, and the convex hull of all parts as its outline. Two parts that both have
 * a name, and not the same one, are never joined directly.
 */
fun mergeBuildingParts(parts: List<Building>): Map<Long, Building> {
    // Union-find over the parts: every close pair ends up in one group.
    val group = IntArray(parts.size) { it }
    fun find(i: Int): Int = if (group[i] == i) i else find(group[i]).also { group[i] = it }

    for (i in parts.indices) {
        for (j in i + 1 until parts.size) {
            val a = parts[i]
            val b = parts[j]
            val differentNames = a.osmName != null && b.osmName != null && a.osmName != b.osmName
            if (!differentNames && gapBetween(a.outline, b.outline) <= SAME_BUILDING_GAP_M) group[find(j)] = find(i)
        }
    }

    return parts.indices.groupBy(::find).values.flatMap { members ->
        val group = members.map(parts::get).sortedBy { it.osmId }
        val lead = group.firstOrNull { it.osmName != null } ?: group.first()
        val building = if (group.size == 1) lead else lead.copy(outline = convexHull(group.flatMap { it.outline }))
        group.map { it.osmId to building }
    }.toMap()
}

/** The narrowest gap between two outlines, in metres. Zero when one reaches into the other. */
internal fun gapBetween(a: List<LatLng>, b: List<LatLng>): Double {
    if (a.any { it.isInside(b) } || b.any { it.isInside(a) }) return 0.0
    return minOf(a.minOf { it.distanceToEdge(b) }, b.minOf { it.distanceToEdge(a) })
}

/** Andrew's monotone chain. Flat-earth maths on degrees, fine for a building. */
internal fun convexHull(points: List<LatLng>): List<LatLng> {
    val sorted = points.distinct().sortedWith(compareBy(LatLng::lon, LatLng::lat))
    if (sorted.size < 3) return sorted

    fun turn(o: LatLng, a: LatLng, b: LatLng) = (a.lon - o.lon) * (b.lat - o.lat) - (a.lat - o.lat) * (b.lon - o.lon)
    fun chain(ordered: List<LatLng>): List<LatLng> {
        val hull = mutableListOf<LatLng>()
        for (point in ordered) {
            while (hull.size >= 2 && turn(hull[hull.size - 2], hull.last(), point) <= 0) hull.removeAt(hull.lastIndex)
            hull += point
        }
        return hull.dropLast(1)
    }
    return chain(sorted) + chain(sorted.asReversed())
}
