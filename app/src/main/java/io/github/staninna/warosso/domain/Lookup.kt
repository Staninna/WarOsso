package io.github.staninna.warosso.domain

/** A way to reach a house number. */
sealed interface Route {
    /** Where the arrow points and where arrival is measured. */
    val target: LatLng

    /** Tells targets apart on the map and in the camera view: the entrance's id, or [STREET_DOOR_ID]. */
    val targetId: Long

    /** Through [entrance], to the door at [door]. */
    data class ViaEntrance(val entrance: Entrance, val door: DoorLocation) : Route {
        override val target: LatLng get() = entrance.latLng
        override val targetId: Long get() = entrance.id
    }

    /** Straight to the home's own front door on the ground floor of [building]. The [target] is the address point. */
    data class StreetDoor(val building: Building, override val target: LatLng) : Route {
        override val targetId: Long get() = STREET_DOOR_ID
    }

    companion object {
        /** Entrance ids start at 1, so this never collides with one. */
        const val STREET_DOOR_ID = -1L
    }
}

/** What the complex knows about a house number someone typed. */
sealed interface Lookup {
    val number: Int

    /** There is a way to the number: every entrance that serves it, see [ComplexData.entrancesFor], or else its own front door on the street. */
    data class Found(override val number: Int, val routes: List<Route>) : Lookup

    /** A search that leads nowhere yet. */
    sealed interface Miss : Lookup

    /** The number is in the address list, but no entrance lists it and its building has no front doors on the street. */
    data class NotSetUp(override val number: Int) : Miss

    /** No such number. [suggestions] holds the closest numbers that do exist, lowest first. */
    data class Unknown(override val number: Int, val suggestions: List<Int>) : Miss
}

fun ComplexData.lookup(number: Int): Lookup {
    val routes = entrancesFor(number).mapNotNull { entrance -> locate(entrance, number)?.let { Route.ViaEntrance(entrance, it) } }
    val streetDoor = streetDoor(number)?.let { address ->
        address.buildingOsmId?.let(::building)?.let { Route.StreetDoor(it, address.latLng) }
    }
    return when {
        routes.isNotEmpty() -> Lookup.Found(number, routes)
        streetDoor != null -> Lookup.Found(number, listOf(streetDoor))
        number in bagNumbers -> Lookup.NotSetUp(number)
        else -> Lookup.Unknown(number, closestNumbers(number, bagNumbers))
    }
}

/** The nearest existing number below [number] and the nearest above it. */
fun closestNumbers(number: Int, existing: Collection<Int>): List<Int> =
    listOfNotNull(existing.filter { it < number }.maxOrNull(), existing.filter { it > number }.minOrNull())

/** Of all routes, the one whose target is closest to [from] in a straight line. */
fun List<Route>.nearestTo(from: LatLng): Route = minBy { from.distanceTo(it.target) }
