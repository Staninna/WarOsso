package io.github.staninna.warosso

import io.github.staninna.warosso.domain.OsmData
import io.github.staninna.warosso.domain.Setup
import io.github.staninna.warosso.domain.diffAgainst
import io.github.staninna.warosso.domain.offset
import io.github.staninna.warosso.domain.withOsm
import io.github.staninna.warosso.domain.withSetup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshDiffTest {
    private val blockA = building(1, northM = 40.0, name = "Linde")
    private val blockB = building(2, northM = -40.0)
    private val current = complexData(
        addresses = listOf(51, 53, 55, 57).map { address(it, buildingOsmId = 1) } + address(101, buildingOsmId = 2),
        buildings = listOf(blockA.copy(customName = "Home"), blockB),
        entrances = listOf(
            entrance(1, buildingOsmId = 1, floors = arrayOf(listOf(51, 53, 55, 57))),
            entrance(2, buildingOsmId = 2, floors = arrayOf(listOf(101))),
        ),
    )

    @Test
    fun `no change is an empty diff`() {
        val diff = current.diffAgainst(OsmData(current.addresses, current.buildings))
        assertTrue(diff.isEmpty)
        assertEquals(emptyList<Any>(), diff.affected)
    }

    @Test
    fun `three new numbers and one removed, with the entrances to look at`() {
        val fresh = OsmData(
            addresses = listOf(51, 53, 55, 59, 61).map { address(it, buildingOsmId = 1) } +
                listOf(101, 103).map { address(it, buildingOsmId = 2) },
            buildings = listOf(blockA, blockB),
        )
        val diff = current.diffAgainst(fresh)

        assertEquals(listOf(59, 61, 103), diff.added)
        assertEquals(listOf(57), diff.removed)

        val (first, second) = diff.affected
        assertEquals(1L, first.entrance.id)
        assertEquals(listOf(57), first.removedDoors)
        assertEquals(listOf(59, 61), first.newNumbers)
        assertEquals(2L, second.entrance.id)
        assertEquals(emptyList<Int>(), second.removedDoors)
        assertEquals(listOf(103), second.newNumbers)
    }

    @Test
    fun `numbers on other streets or outside the polygon do not count`() {
        val fresh = OsmData(
            addresses = current.addresses + address(7, street = "Klaverweide") + address(9, at = CENTER.offset(500.0, 0.0)),
            buildings = current.buildings,
        )
        assertTrue(current.diffAgainst(fresh).isEmpty)
    }

    @Test
    fun `a refresh never touches the setup`() {
        val fresh = OsmData(
            addresses = listOf(address(51, buildingOsmId = 1)),
            buildings = listOf(blockA.copy(osmName = "Linde II")),
        )
        val after = current.withOsm(fresh)

        assertEquals(current.entrances, after.entrances)
        assertEquals(current.complex, after.complex)
        assertEquals("Home", after.building(1)!!.customName)
        assertEquals("Linde II", after.building(1)!!.osmName)
        // Block B left OSM, but entrance 2 still stands at it.
        assertEquals(blockB, after.building(2))
    }

    @Test
    fun `a building without setup that left OSM is dropped`() {
        val lonely = current.copy(entrances = current.entrances.take(1))
        val after = lonely.withOsm(OsmData(lonely.addresses, listOf(blockA)))
        assertNull(after.building(2))
    }

    @Test
    fun `a building the owner named stays, even without an entrance`() {
        val named = current.copy(entrances = emptyList())
        val after = named.withOsm(OsmData(named.addresses, listOf(blockB)))
        assertEquals("Home", after.building(1)!!.customName)
    }

    @Test
    fun `an import replaces the setup and keeps the address list`() {
        val imported = Setup(
            complex = current.complex.copy(street = "Klaverweide"),
            buildings = listOf(blockB.copy(osmName = null, customName = "Shed"), building(9, eastM = 80.0)),
            entrances = listOf(entrance(5, buildingOsmId = 9, floors = arrayOf(listOf(1)))),
        )
        val after = current.withSetup(imported)

        assertEquals("Klaverweide", after.complex.street)
        assertEquals(current.addresses, after.addresses)
        // A known building keeps its OSM name and takes the imported custom name, or loses its own.
        assertNull(after.building(1)!!.customName)
        assertEquals("Linde", after.building(1)!!.osmName)
        assertEquals("Shed", after.building(2)!!.customName)
        assertEquals(imported.buildings[1], after.building(9))
        assertEquals(listOf(0L), after.entrances.map { it.id })
    }
}
