package io.github.staninna.warosso.domain

/** Fresh addresses and buildings from OpenStreetMap, before the owner has accepted them. */
data class OsmData(val addresses: List<Address>, val buildings: List<Building>)

/** An entrance the owner should look at after an OSM refresh. */
data class AffectedEntrance(
    val entrance: Entrance,
    /** Doors the entrance lists that are no longer in the address list. */
    val removedDoors: List<Int>,
    /** New numbers in the entrance's building that it does not list yet. */
    val newNumbers: List<Int>,
)

/** What an OSM refresh would change in the complex's address list. Review only, nothing is applied. */
data class RefreshDiff(
    val added: List<Int>,
    val removed: List<Int>,
    val affected: List<AffectedEntrance>,
) {
    val isEmpty: Boolean get() = added.isEmpty() && removed.isEmpty()
}

/** This snapshot with its OSM-derived parts swapped for [fresh]. The owner's setup stays as it is. */
fun ComplexData.withOsm(fresh: OsmData): ComplexData {
    val known = buildings.associateBy { it.osmId }
    val freshIds = fresh.buildings.mapTo(HashSet()) { it.osmId }
    // A building that left OSM stays while it carries setup: an entrance or a setting of the owner's.
    val withEntrance = entrances.mapTo(HashSet()) { it.buildingOsmId }
    val orphaned = buildings.filter { it.osmId !in freshIds && (it.osmId in withEntrance || it.hasSettings) }
    return copy(
        addresses = fresh.addresses,
        buildings = fresh.buildings.map { it.withSettingsOf(known[it.osmId]) } + orphaned,
    )
}

fun ComplexData.diffAgainst(fresh: OsmData): RefreshDiff {
    val after = withOsm(fresh)
    val before = bagNumbers.toSet()
    val added = after.bagNumbers.filterNot(before::contains)
    val removed = bagNumbers.filterNot(after.bagNumbers.toSet()::contains)

    val addedByBuilding = after.complexAddresses
        .filter { it.number in added }
        .groupBy({ it.buildingOsmId }, { it.number })

    val affected = entrances.mapNotNull { entrance ->
        val doors = entrance.doorNumbers.toSet()
        val removedDoors = removed.filter(doors::contains)
        val newNumbers = addedByBuilding[entrance.buildingOsmId].orEmpty().filterNot(doors::contains)
        AffectedEntrance(entrance, removedDoors, newNumbers)
            .takeIf { removedDoors.isNotEmpty() || newNumbers.isNotEmpty() }
    }
    return RefreshDiff(added, removed, affected)
}
