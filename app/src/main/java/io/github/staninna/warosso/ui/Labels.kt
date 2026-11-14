package io.github.staninna.warosso.ui

import android.content.res.Resources
import android.icu.text.MessageFormat
import io.github.staninna.warosso.R
import io.github.staninna.warosso.domain.Building
import io.github.staninna.warosso.domain.BuildingLabel
import io.github.staninna.warosso.domain.ComplexData
import io.github.staninna.warosso.domain.DoorHint
import io.github.staninna.warosso.domain.Entrance
import io.github.staninna.warosso.domain.EntranceType
import io.github.staninna.warosso.domain.Route
import kotlin.math.roundToInt

// How domain values read on screen. Every function takes Resources, so the same wording serves
// composables, map labels and the camera overlay.

fun Resources.buildingName(data: ComplexData, building: Building): String =
    when (val label = data.labelOf(building)) {
        is BuildingLabel.Named -> label.name
        is BuildingLabel.Block -> getString(R.string.building_block, numberRange(label.min, label.max))
        BuildingLabel.Unnumbered -> getString(R.string.building_unnumbered)
    }

fun Resources.buildingName(data: ComplexData, entrance: Entrance): String =
    data.building(entrance.buildingOsmId)?.let { buildingName(data, it) } ?: getString(R.string.building_unnumbered)

fun Resources.entranceName(data: ComplexData, entrance: Entrance): String =
    entrance.name ?: getString(R.string.entrance_default_name, data.ordinalOf(entrance))

fun Resources.entranceTypeName(type: EntranceType): String = getString(
    when (type) {
        EntranceType.STAIRWELL -> R.string.entrance_type_stairwell
        EntranceType.INTERCOM -> R.string.entrance_type_intercom
    },
)

/** "151-199", or "151" for a single door, or null while the entrance has no doors. */
fun Resources.numberRange(entrance: Entrance): String? = entrance.numberRange?.let { numberRange(it.first, it.last) }

fun Resources.numberRange(first: Int, last: Int): String =
    if (first == last) first.toString() else getString(R.string.number_range, first, last)

/** What a marker or camera label says about an entrance: its numbers, or its name while it has none. */
fun Resources.entranceBadge(data: ComplexData, entrance: Entrance): String =
    numberRange(entrance) ?: entranceName(data, entrance)

/** "Ground floor", "Floor 2": a floor as a heading. */
fun Resources.floorTitle(floor: Int): String =
    if (floor == 0) getString(R.string.floor_ground) else getString(R.string.floor_n, floor)

private fun String.asSentence(): String = replaceFirstChar(Char::uppercase)

/** "3rd", "3e": the ordinal pattern is an ICU message, so each language brings its own rules. */
fun Resources.ordinal(n: Int): String = MessageFormat(getString(R.string.ordinal_pattern), configuration.locales[0]).format(arrayOf(n))

fun Resources.doorHint(hint: DoorHint): String = when (hint) {
    is DoorHint.FromLeft -> getString(R.string.door_from_left, ordinal(hint.position))
    is DoorHint.FromRight -> getString(R.string.door_from_right, ordinal(hint.position))
    DoorHint.Only -> getString(R.string.door_only)
}

fun Resources.distance(metres: Double): String = getString(R.string.distance_m, metres.roundToInt())

/** How to get from the entrance to the right floor, in lower case: it sits mid-sentence in the directions line. */
fun Resources.floorStep(route: Route): String {
    val floor = route.door.floor
    return when {
        floor == 0 -> getString(R.string.step_ground_floor)
        route.entrance.type == EntranceType.STAIRWELL -> getString(R.string.step_stairs_to_floor, floor)
        route.entrance.innerStairs -> getString(R.string.step_inner_stairs_to_floor, floor)
        else -> getString(R.string.step_to_floor, floor)
    }
}

/** The one-line directions from SPEC.md 5.1, without the building name (the card shows that as its title). */
fun Resources.directionsLine(data: ComplexData, number: Int, route: Route): String {
    val first = when (route.entrance.type) {
        EntranceType.STAIRWELL -> entranceName(data, route.entrance)
        EntranceType.INTERCOM -> getString(R.string.step_ring, number)
    }
    return getString(R.string.directions_line, first, floorStep(route), doorHint(route.door.hint))
}

/** The same steps as headings, for the expanded card. */
fun Resources.floorStepTitle(route: Route): String = floorStep(route).asSentence()

fun Resources.doorHintTitle(hint: DoorHint): String = doorHint(hint).asSentence()
