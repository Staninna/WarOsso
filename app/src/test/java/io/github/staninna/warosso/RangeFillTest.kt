package io.github.staninna.warosso

import io.github.staninna.warosso.domain.FloorDoors
import io.github.staninna.warosso.domain.fillRange
import io.github.staninna.warosso.domain.suggestFloors
import io.github.staninna.warosso.domain.suggestFloorsFor
import io.github.staninna.warosso.domain.suggestRuns
import org.junit.Assert.assertEquals
import org.junit.Test

class RangeFillTest {
    private val odd = (1..247 step 2).toList()

    @Test
    fun `counting down keeps the typed direction`() {
        assertEquals(listOf(169, 167, 165, 163, 161, 159, 157, 155, 153, 151), fillRange(169, 151, odd))
    }

    @Test
    fun `counting up`() {
        assertEquals(listOf(9, 11, 13, 15, 17), fillRange(9, 17, odd))
    }

    @Test
    fun `only numbers that exist in BAG are filled in`() {
        val bag = listOf(1, 2, 3, 5, 8, 13)
        assertEquals(listOf(2, 3, 5, 8), fillRange(2, 10, bag))
        assertEquals(listOf(8, 5, 3, 2), fillRange(10, 2, bag))
    }

    @Test
    fun `ends that are not in BAG are left out`() {
        assertEquals(listOf(151, 153), fillRange(150, 154, odd))
    }

    @Test
    fun `a range with nothing in it is empty`() {
        assertEquals(emptyList<Int>(), fillRange(300, 400, odd))
    }

    @Test
    fun `from equal to to is one door`() {
        assertEquals(listOf(57), fillRange(57, 57, odd))
    }

    @Test
    fun `runs split where the numbering jumps a floor`() {
        // Building "Eik" in the seed.
        val eik = listOf(9, 11, 13, 15, 17, 27, 29, 31, 33, 35, 45, 47, 49, 51, 53)
        assertEquals(
            listOf(listOf(9, 11, 13, 15, 17), listOf(27, 29, 31, 33, 35), listOf(45, 47, 49, 51, 53)),
            suggestRuns(eik),
        )
    }

    @Test
    fun `an even row is one run`() {
        assertEquals(listOf((141..247 step 2).toList()), suggestRuns((141..247 step 2).toList()))
    }

    @Test
    fun `runs need no fixed step`() {
        assertEquals(listOf(listOf(1, 2, 3, 4), listOf(20, 21, 22)), suggestRuns(listOf(22, 21, 20, 4, 3, 2, 1)))
        assertEquals(listOf(listOf(107)), suggestRuns(listOf(107)))
        assertEquals(emptyList<List<Int>>(), suggestRuns(emptyList()))
    }

    @Test
    fun `floors are proposed ground floor first`() {
        val esdoornWing = listOf(109, 111, 113, 115, 125, 127, 129, 131)
        assertEquals(
            listOf(FloorDoors(0, listOf(109, 111, 113, 115)), FloorDoors(1, listOf(125, 127, 129, 131))),
            suggestFloors(listOf(esdoornWing), existing = emptyList()),
        )
    }

    @Test
    fun `the halves of a block share their floors`() {
        // Building "Eik" in the seed: OSM maps it as two outlines side by side.
        val unnamedHalf = listOf(1, 3, 5, 7, 19, 21, 23, 25, 37, 39, 41, 43)
        val namedHalf = listOf(9, 11, 13, 15, 17, 27, 29, 31, 33, 35, 45, 47, 49, 51, 53)
        assertEquals(
            listOf(
                FloorDoors(0, (1..17 step 2).toList()),
                FloorDoors(1, (19..35 step 2).toList()),
                FloorDoors(2, (37..53 step 2).toList()),
            ),
            suggestFloors(listOf(namedHalf, unnamedHalf), existing = emptyList()),
        )
    }

    @Test
    fun `floors the entrance already has are not proposed again`() {
        val linde = listOf(109, 111, 113, 115, 125, 127, 129, 131)
        val existing = listOf(FloorDoors(0, listOf(115, 113, 111, 109)))
        assertEquals(listOf(FloorDoors(1, listOf(125, 127, 129, 131))), suggestFloors(listOf(linde), existing))
    }

    @Test
    fun `a ground floor with its doors on the street is not proposed`() {
        val linde = listOf(109, 111, 113, 115, 125, 127, 129, 131)
        val stairwell = entrance(1, buildingOsmId = 1)
        val data = complexData(addresses = linde.map { address(it, buildingOsmId = 1) })
        assertEquals(listOf(0, 1), data.suggestFloorsFor(stairwell).map { it.floor })

        val streetLevel = data.copy(buildings = data.buildings.map { it.copy(streetDoors = true) })
        assertEquals(listOf(FloorDoors(1, listOf(125, 127, 129, 131))), streetLevel.suggestFloorsFor(stairwell))
    }
}
