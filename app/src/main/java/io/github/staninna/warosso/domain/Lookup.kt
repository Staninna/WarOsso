package io.github.staninna.warosso.domain

/** A way to reach a house number: through [entrance], to the door at [door]. */
data class Route(val entrance: Entrance, val door: DoorLocation)

/** What the complex knows about a house number someone typed. */
sealed interface Lookup {
    val number: Int

    /** At least one entrance serves the number. */
    data class Found(override val number: Int, val routes: List<Route>) : Lookup

    /** A search that leads nowhere yet. */
    sealed interface Miss : Lookup

    /** The number is in the address list, but no entrance lists it yet. */
    data class NotSetUp(override val number: Int) : Miss

    /** No such number. [suggestions] holds the closest numbers that do exist, lowest first. */
    data class Unknown(override val number: Int, val suggestions: List<Int>) : Miss
}

fun ComplexData.lookup(number: Int): Lookup {
    val routes = entrances.mapNotNull { entrance -> entrance.locate(number)?.let { Route(entrance, it) } }
    return when {
        routes.isNotEmpty() -> Lookup.Found(number, routes)
        number in bagNumbers -> Lookup.NotSetUp(number)
        else -> Lookup.Unknown(number, closestNumbers(number, bagNumbers))
    }
}

/** The nearest existing number below [number] and the nearest above it. */
fun closestNumbers(number: Int, existing: Collection<Int>): List<Int> =
    listOfNotNull(existing.filter { it < number }.maxOrNull(), existing.filter { it > number }.minOrNull())

/** Of all routes, the one whose entrance is closest to [from] in a straight line. */
fun List<Route>.nearestTo(from: LatLng): Route = minBy { from.distanceTo(it.entrance.latLng) }
