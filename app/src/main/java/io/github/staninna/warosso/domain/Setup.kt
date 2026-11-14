package io.github.staninna.warosso.domain

/** What the owner adds to the public data: the area, building names and entrances. The content of `warosso.json`. */
data class Setup(
    val complex: Complex,
    /** Only the buildings that carry setup: a setting of the owner's or an entrance. */
    val buildings: List<Building>,
    val entrances: List<Entrance>,
)

/**
 * This snapshot with the owner's setup replaced by [setup]. The address list stays. A building
 * this phone already knows keeps its OSM name and outline and takes the imported settings, or
 * loses its own when the import has none. Buildings only the import knows are added.
 */
fun ComplexData.withSetup(setup: Setup): ComplexData {
    val imported = setup.buildings.associateBy { it.osmId }
    val known = buildings.mapTo(HashSet()) { it.osmId }
    return copy(
        complex = setup.complex,
        buildings = buildings.map { it.withSettingsOf(imported[it.osmId]) } +
            setup.buildings.filterNot { it.osmId in known },
        // Import hands out fresh ids.
        entrances = setup.entrances.map { it.copy(id = 0) },
    )
}
