package io.github.staninna.warosso.domain

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

// The domain classes double as Room entities. The app has four small tables that map one to one
// on these types, so a separate entity layer would only be copies and mappers.

/** The residential complex: one street inside a polygon the owner draws in Setup. */
@Entity(tableName = "complex")
data class Complex(
    val street: String,
    val polygon: List<LatLng>,
    @PrimaryKey val id: Int = SINGLETON_ID,
) {
    companion object {
        const val SINGLETON_ID = 0
        val DEFAULT_CENTER = LatLng(51.597787, 4.800806)
        const val DEFAULT_RADIUS_M = 150.0

        /**
         * The area when OSM has no buildings to wrap, see [areaAround]. Twelve corners: round
         * enough, and few enough to drag into shape in Setup.
         */
        fun defaultPolygon(): List<LatLng> = circlePolygon(DEFAULT_CENTER, DEFAULT_RADIUS_M, vertices = 12)
    }
}

/** A house number from BAG, as mapped in OpenStreetMap. */
@Entity(tableName = "address", primaryKeys = ["street", "number"])
data class Address(
    val street: String,
    val number: Int,
    @Embedded val latLng: LatLng,
    /** The building this address belongs to. Null only when OSM has no buildings. */
    val buildingOsmId: Long?,
    /**
     * The OSM outline the address sits in. It differs from [buildingOsmId] where one building is
     * mapped as several outlines, see [mergeBuildingParts].
     */
    val partOsmId: Long? = buildingOsmId,
)

/**
 * A building from OpenStreetMap. Buildings only ever come from OSM, so an OSM id is the key.
 * One building can join several OSM outlines, see [mergeBuildingParts].
 */
@Entity(tableName = "building")
data class Building(
    @PrimaryKey val osmId: Long,
    val osmName: String?,
    val customName: String?,
    /** A ring without a repeated closing point. Around all parts when there are several. */
    val outline: List<LatLng>,
    /**
     * The ground-floor homes have their own front door on the street and need no entrance: a
     * number of this building that no entrance lists is walked to directly.
     */
    @ColumnInfo(defaultValue = "0") val streetDoors: Boolean = false,
) {
    /** The owner set something on this building. That keeps it through a refresh and puts it in an export. */
    val hasSettings: Boolean get() = customName != null || streetDoors

    /** This building with the owner's settings of [other], or without any when [other] is null. */
    fun withSettingsOf(other: Building?): Building =
        copy(customName = other?.customName, streetDoors = other?.streetDoors == true)
}

enum class EntranceType {
    /** Outdoor stairs up to the galleries. */
    STAIRWELL,

    /** A shared front door with an intercom panel. */
    INTERCOM,

    /** A plain door: no intercom, no outdoor stairs. A home's own front door, for one. */
    DOOR,
}

/** The doors on one floor, ordered left to right as seen from the entrance, facing the building. */
@Serializable
data class FloorDoors(val floor: Int, val doors: List<Int>)

@Entity(
    tableName = "entrance",
    foreignKeys = [
        ForeignKey(
            entity = Building::class,
            parentColumns = ["osmId"],
            childColumns = ["buildingOsmId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("buildingOsmId")],
)
data class Entrance(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val buildingOsmId: Long,
    @Embedded val latLng: LatLng,
    val type: EntranceType = EntranceType.STAIRWELL,
    /** Intercom entrances only: there are stairs behind the door. */
    val innerStairs: Boolean = false,
    val name: String? = null,
    val note: String? = null,
    val floors: List<FloorDoors> = emptyList(),
) {
    val doorNumbers: List<Int> get() = floors.flatMap { it.doors }
}

/** What a building is called when it has no name of its own. */
sealed interface BuildingLabel {
    data class Named(val name: String) : BuildingLabel
    data class Block(val min: Int, val max: Int) : BuildingLabel
    data object Unnumbered : BuildingLabel
}

/** One consistent read of everything the app knows about the complex. */
data class ComplexData(
    val complex: Complex,
    /** Every address that was fetched, including neighbouring streets. */
    val addresses: List<Address>,
    val buildings: List<Building>,
    val entrances: List<Entrance>,
) {
    /** The addresses that belong to the complex: on its street and inside its polygon. */
    val complexAddresses: List<Address> by lazy {
        addresses
            .filter { it.street == complex.street && it.latLng.isInside(complex.polygon) }
            .sortedBy { it.number }
    }

    /** The complex's house numbers, ascending. */
    val bagNumbers: List<Int> by lazy { complexAddresses.map { it.number } }

    private val buildingsById: Map<Long, Building> by lazy { buildings.associateBy { it.osmId } }

    /** The complex's addresses per building, ascending by number. */
    private val addressesByBuilding: Map<Long?, List<Address>> by lazy { complexAddresses.groupBy { it.buildingOsmId } }

    /** The owner's part of this snapshot, with only the buildings that carry setup. */
    val setup: Setup
        get() {
            val withEntrance = entrances.mapTo(HashSet()) { it.buildingOsmId }
            return Setup(complex, buildings.filter { it.hasSettings || it.osmId in withEntrance }, entrances)
        }

    private val servedNumbers: Set<Int> by lazy { entrances.flatMapTo(HashSet()) { it.doorNumbers } }

    /** The addresses reached straight from the street, by number: no entrance lists them and their building has [Building.streetDoors]. */
    private val streetDoors: Map<Int, Address> by lazy {
        complexAddresses
            .filter { it.number !in servedNumbers && it.buildingOsmId?.let(::building)?.streetDoors == true }
            .associateBy { it.number }
    }

    /** Complex numbers that lead nowhere yet: no entrance lists them and they have no front door on the street. */
    val unservedNumbers: List<Int> by lazy { bagNumbers.filterNot { it in servedNumbers || it in streetDoors } }

    /** There is at least one number to walk to. */
    val canGuide: Boolean get() = entrances.isNotEmpty() || streetDoors.isNotEmpty()

    /** Buildings that hold at least one complex address or an entrance, ordered by lowest number. */
    val complexBuildings: List<Building> by lazy {
        val withEntrance = entrances.mapTo(HashSet()) { it.buildingOsmId }
        buildings
            .filter { numbersOf(it.osmId).isNotEmpty() || it.osmId in withEntrance }
            .sortedBy { numbersOf(it.osmId).firstOrNull() ?: Int.MAX_VALUE }
    }

    /** The corners worth keeping in view on a map: the complex's buildings, or its whole area before there are any. */
    val extent: List<LatLng> by lazy { complexBuildings.flatMap { it.outline }.ifEmpty { complex.polygon } }

    val streets: List<String> by lazy { addresses.map { it.street }.distinct().sorted() }

    /** The outline that wraps the buildings on the complex's street, to start over from in Setup. */
    fun fittedArea(): List<LatLng> = areaAround(complex.street, addresses, buildings)

    fun building(osmId: Long): Building? = buildingsById[osmId]

    fun numbersOf(buildingOsmId: Long): List<Int> = addressesByBuilding[buildingOsmId].orEmpty().map { it.number }

    /** The same numbers, split by the OSM outline they sit in: the halves of a block, each ascending. */
    fun numbersByPartOf(buildingOsmId: Long): List<List<Int>> =
        addressesByBuilding[buildingOsmId].orEmpty().groupBy({ it.partOsmId }, { it.number }).values.toList()

    private val addressesByNumber: Map<Int, Address> by lazy { complexAddresses.associateBy { it.number } }

    /**
     * The entrances to reach [number] through. Where several entrances of one building list it,
     * only the one closest to the number's address counts. That is how a copy of an entrance
     * shares the building's numbers with its original: each takes the numbers on its side.
     */
    fun entrancesFor(number: Int): List<Entrance> {
        val listing = entrances.filter { number in it.doorNumbers }
        val address = addressesByNumber[number]?.latLng ?: return listing
        return listing.groupBy { it.buildingOsmId }.values.map { siblings -> siblings.minBy { it.latLng.distanceTo(address) } }
    }

    /** The numbers [entrance] is the way to, see [entrancesFor]. Fewer than it lists when it shares some with a closer entrance. */
    fun numbersVia(entrance: Entrance): List<Int> =
        entrance.doorNumbers.filter { number -> entrancesFor(number).any { it.id == entrance.id } }

    /** Where [number] is behind [entrance], with the turn to take there when the addresses tell, see [doorsLeftOf]. */
    fun locate(entrance: Entrance, number: Int): DoorLocation? =
        entrance.locate(number) { floor -> doorsLeftOf(entrance.latLng, floor.doors.map { addressesByNumber[it]?.latLng }) }

    /** The address of [number] when its front door is on the street, see [Building.streetDoors]. */
    fun streetDoor(number: Int): Address? = streetDoors[number]

    /** The numbers of a building that are reached straight from the street, ascending. */
    fun streetDoorNumbers(buildingOsmId: Long): List<Int> = numbersOf(buildingOsmId).filter { it in streetDoors }

    fun entrance(id: Long): Entrance? = entrances.firstOrNull { it.id == id }

    /** 1-based position of [entrance] among all entrances, for the default name "Entrance n". */
    fun ordinalOf(entrance: Entrance): Int = entrances.indexOfFirst { it.id == entrance.id } + 1

    fun labelOf(building: Building): BuildingLabel {
        val name = building.customName ?: building.osmName
        val numbers = numbersOf(building.osmId)
        return when {
            name != null -> BuildingLabel.Named(name)
            numbers.isNotEmpty() -> BuildingLabel.Block(numbers.first(), numbers.last())
            else -> BuildingLabel.Unnumbered
        }
    }
}

/** The building whose outline holds [point], or else the one with the nearest wall. */
fun List<Building>.holdingOrNearest(point: LatLng): Building? =
    firstOrNull { point.isInside(it.outline) } ?: minByOrNull { point.distanceToEdge(it.outline) }
