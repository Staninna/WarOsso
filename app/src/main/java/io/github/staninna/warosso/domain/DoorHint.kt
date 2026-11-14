package io.github.staninna.warosso.domain

import kotlin.math.cos

/** Where a door is on its floor. */
sealed interface DoorHint {
    /** Counted from the left end of the floor: the entrance is at one of its ends, or its place is unknown. */
    data class FromLeft(val position: Int) : DoorHint
    data class FromRight(val position: Int) : DoorHint

    /** The entrance comes out partway along the floor. From there the door is the [position]-th to the left. */
    data class TurnLeft(val position: Int) : DoorHint
    data class TurnRight(val position: Int) : DoorHint

    /** The floor has a single door, so there is nothing to count. */
    data object Only : DoorHint
}

data class DoorLocation(val floor: Int, val hint: DoorHint)

/**
 * Tells where [number] is in [doors], which run left to right. Returns null when the door is not
 * in the list. Nothing here looks at the numbers themselves, so irregular numbering is fine.
 *
 * With [doorsLeftOfEntrance] doors on either side of the entrance, the count starts at the
 * entrance and says which way to turn. Otherwise it starts from the shorter end of the floor; a
 * door in the exact middle counts from the left.
 */
fun doorHint(doors: List<Int>, number: Int, doorsLeftOfEntrance: Int? = null): DoorHint? {
    val index = doors.indexOf(number)
    if (index < 0) return null
    val fromLeft = index + 1
    val fromRight = doors.size - index
    return when {
        doors.size == 1 -> DoorHint.Only
        doorsLeftOfEntrance != null && doorsLeftOfEntrance in 1 until doors.size ->
            if (index < doorsLeftOfEntrance) DoorHint.TurnLeft(doorsLeftOfEntrance - index) else DoorHint.TurnRight(index - doorsLeftOfEntrance + 1)
        fromLeft <= fromRight -> DoorHint.FromLeft(fromLeft)
        else -> DoorHint.FromRight(fromRight)
    }
}

/**
 * How many of a floor's doors lie to the left of [entrance], judged by where their addresses
 * are. [doors] holds the address of each door from left to right, null where a number has none.
 * The line from the first to the last address is the floor; the entrance and the doors are
 * compared along it.
 *
 * Null when the addresses cannot tell: fewer than two are known, or they lie on one spot.
 */
fun doorsLeftOf(entrance: LatLng, doors: List<LatLng?>): Int? {
    val known = doors.filterNotNull()
    val first = known.firstOrNull() ?: return null
    if (first.distanceTo(known.last()) < MIN_FLOOR_LENGTH_M) return null
    val along = first.bearingTo(known.last())
    fun LatLng.onFloor() = first.distanceTo(this) * cos(Math.toRadians(first.bearingTo(this) - along))

    val entranceOnFloor = entrance.onFloor()
    val knownOnTheLeft = known.count { it.onFloor() < entranceOnFloor }
    if (knownOnTheLeft == 0) return 0
    // In the list, everything up to the last of those known doors is on the left.
    var seen = 0
    return doors.indexOfFirst { it != null && ++seen > knownOnTheLeft }.let { if (it < 0) doors.size else it }
}

/** Addresses closer together than this say nothing about which way a floor runs. */
private const val MIN_FLOOR_LENGTH_M = 3.0

/**
 * The floor and door position of [number] behind this entrance, or null if it is not served
 * here. [doorsLeftOfEntrance] tells for a floor how many of its doors are left of the entrance,
 * see [doorHint].
 */
fun Entrance.locate(number: Int, doorsLeftOfEntrance: (FloorDoors) -> Int? = { null }): DoorLocation? =
    floors.firstOrNull { number in it.doors }?.let { floor ->
        doorHint(floor.doors, number, doorsLeftOfEntrance(floor))?.let { DoorLocation(floor.floor, it) }
    }
