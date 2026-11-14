package io.github.staninna.warosso.domain

// Small, pure edits the Setup screens make to door lists and to the complex polygon.

/** This entrance with [floor] added, or put in place of the floor with the same number. */
fun Entrance.withFloor(floor: FloorDoors): Entrance =
    copy(floors = (floors.filterNot { it.floor == floor.floor } + floor).sortedBy { it.floor })

fun Entrance.withoutFloor(floor: Int): Entrance = copy(floors = floors.filterNot { it.floor == floor })

/** This entrance with the door list of [floor] passed through [change]. */
fun Entrance.withDoors(floor: Int, change: (List<Int>) -> List<Int>): Entrance =
    copy(floors = floors.map { if (it.floor == floor) it.copy(doors = change(it.doors)) else it })

/** The floor number to offer for a new floor: one above the highest, starting at the ground floor. */
val Entrance.nextFloor: Int get() = (floors.maxOfOrNull { it.floor } ?: -1) + 1

/** The list with the item at [index] swapped one place to the left (`by = -1`) or right (`by = 1`). */
fun <T> List<T>.moved(index: Int, by: Int): List<T> {
    val destination = index + by
    if (index !in indices || destination !in indices) return this
    return toMutableList().apply { add(destination, removeAt(index)) }
}

fun <T> List<T>.without(index: Int): List<T> = filterIndexed { i, _ -> i != index }

/** The list with [item] placed right after position [after], or at the end when [after] is null. */
fun <T> List<T>.inserted(item: T, after: Int?): List<T> =
    toMutableList().apply { add(if (after == null) size else (after + 1).coerceIn(0, size), item) }

/** The middle of every edge of this polygon; entry `i` lies between vertex `i` and the next one. */
fun List<LatLng>.edgeMidpoints(): List<LatLng> =
    indices.map { i ->
        val next = this[(i + 1) % size]
        LatLng((this[i].lat + next.lat) / 2, (this[i].lon + next.lon) / 2)
    }

/** A polygon needs three corners, so removing one from a triangle changes nothing. */
fun List<LatLng>.withoutVertex(index: Int): List<LatLng> = if (size > 3) without(index) else this
