package io.github.staninna.warosso.domain

/** Where a door is on its floor, counted from whichever end of the gallery is closer. */
sealed interface DoorHint {
    data class FromLeft(val position: Int) : DoorHint
    data class FromRight(val position: Int) : DoorHint

    /** The floor has a single door, so there is nothing to count. */
    data object Only : DoorHint
}

data class DoorLocation(val floor: Int, val hint: DoorHint)

/**
 * Counts [number]'s position in [doors], which run left to right. The count starts from the
 * shorter side; a door in the exact middle counts from the left. Returns null when the door is
 * not in the list. Nothing here looks at the numbers themselves, so irregular numbering is fine.
 */
fun doorHint(doors: List<Int>, number: Int): DoorHint? {
    val index = doors.indexOf(number)
    if (index < 0) return null
    val fromLeft = index + 1
    val fromRight = doors.size - index
    return when {
        doors.size == 1 -> DoorHint.Only
        fromLeft <= fromRight -> DoorHint.FromLeft(fromLeft)
        else -> DoorHint.FromRight(fromRight)
    }
}

/** The floor and door position of [number] behind this entrance, or null if it is not served here. */
fun Entrance.locate(number: Int): DoorLocation? =
    floors.firstNotNullOfOrNull { floor -> doorHint(floor.doors, number)?.let { DoorLocation(floor.floor, it) } }
