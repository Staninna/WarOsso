package io.github.staninna.warosso

import io.github.staninna.warosso.domain.Address
import io.github.staninna.warosso.domain.Building
import io.github.staninna.warosso.domain.Complex
import io.github.staninna.warosso.domain.ComplexData
import io.github.staninna.warosso.domain.Entrance
import io.github.staninna.warosso.domain.FloorDoors
import io.github.staninna.warosso.domain.LatLng
import io.github.staninna.warosso.domain.offset

const val STREET = "Bulkpad"
val CENTER = Complex.DEFAULT_CENTER

/** A square building, [sizeM] metres wide, centred [northM] metres north and [eastM] metres east of the complex centre. */
fun building(osmId: Long, northM: Double = 0.0, eastM: Double = 0.0, sizeM: Double = 20.0, name: String? = null): Building {
    val middle = CENTER.offset(northM, 0.0).offset(eastM, 90.0)
    val half = sizeM / 2
    fun corner(north: Double, east: Double) = middle.offset(north, 0.0).offset(east, 90.0)
    return Building(osmId, name, customName = null, outline = listOf(corner(half, -half), corner(half, half), corner(-half, half), corner(-half, -half)))
}

fun address(number: Int, at: LatLng = CENTER, buildingOsmId: Long? = null, street: String = STREET) =
    Address(street, number, at, buildingOsmId)

fun entrance(id: Long, at: LatLng = CENTER, buildingOsmId: Long = 1, vararg floors: List<Int>) =
    Entrance(id = id, buildingOsmId = buildingOsmId, latLng = at, floors = floors.mapIndexed { floor, doors -> FloorDoors(floor, doors) })

fun complexData(
    addresses: List<Address> = emptyList(),
    buildings: List<Building> = listOf(building(1)),
    entrances: List<Entrance> = emptyList(),
) = ComplexData(Complex(STREET, Complex.defaultPolygon()), addresses, buildings, entrances)
