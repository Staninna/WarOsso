package io.github.staninna.warosso

import io.github.staninna.warosso.data.parseOverpass
import io.github.staninna.warosso.domain.Complex
import io.github.staninna.warosso.domain.ComplexData
import io.github.staninna.warosso.domain.BuildingLabel
import io.github.staninna.warosso.domain.centroid
import io.github.staninna.warosso.domain.isInside
import io.github.staninna.warosso.domain.mergeBuildingParts
import io.github.staninna.warosso.domain.suggestFloors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BuildingPartsTest {
    // Two 8 m squares with a 2 m gap: the blocks in the complex are mapped as halves like these.
    private val named = building(10, northM = 5.0, sizeM = 8.0, name = "Eik")
    private val unnamed = building(4, northM = -5.0, sizeM = 8.0)

    @Test
    fun `halves two metres apart are one building, named after the named half`() {
        val byOutline = mergeBuildingParts(listOf(unnamed, named))
        val merged = byOutline.getValue(10)
        assertEquals(merged, byOutline.getValue(4))
        assertEquals(10L, merged.osmId)
        assertEquals("Eik", merged.osmName)
        // The joined outline wraps both halves, gap included.
        assertTrue(CENTER.isInside(merged.outline))
        assertTrue(named.outline.centroid().isInside(merged.outline))
        assertTrue(unnamed.outline.centroid().isInside(merged.outline))
    }

    @Test
    fun `without a name the lowest id leads`() {
        val byOutline = mergeBuildingParts(listOf(named.copy(osmName = null), unnamed))
        assertEquals(setOf(4L), byOutline.values.map { it.osmId }.toSet())
    }

    @Test
    fun `buildings six metres apart stay apart`() {
        val far = building(7, northM = 19.0, sizeM = 8.0)
        assertEquals(mapOf(10L to named, 7L to far), mergeBuildingParts(listOf(named, far)))
    }

    @Test
    fun `two differently named neighbours are not joined`() {
        val berk = unnamed.copy(osmName = "Berk")
        assertEquals(2, mergeBuildingParts(listOf(named, berk)).values.distinct().size)
    }

    @Test
    fun `overlapping outlines are one building`() {
        val overlapping = building(5, northM = 8.0, sizeM = 8.0)
        assertEquals(1, mergeBuildingParts(listOf(named, overlapping)).values.distinct().size)
    }

    @Test
    fun `in the seed the tree names cover both halves of their block`() {
        val seed = parseOverpass(File("src/main/assets/seed.json").readText())
        val data = ComplexData(Complex("Bulkpad", Complex.defaultPolygon()), seed.addresses, seed.buildings, emptyList())
        val numbers = data.complexBuildings.associate { data.labelOf(it) to data.numbersOf(it.osmId) }

        assertEquals((1..53 step 2).toList(), numbers[BuildingLabel.Named("Eik")])
        assertEquals((55..105 step 2).toList(), numbers[BuildingLabel.Named("Berk")])
        assertEquals((109..139 step 2).toList(), numbers[BuildingLabel.Named("Linde")])
        assertEquals((141..247 step 2).toList(), numbers[BuildingLabel.Named("Esdoorn")])
        assertEquals(listOf(107), numbers[BuildingLabel.Block(107, 107)])
        assertEquals(5, numbers.size)
        // The halves keep their own numbers, which is what shows where the floors are.
        val eik = data.complexBuildings.first { it.osmName == "Eik" }
        assertEquals(listOf(0, 1, 2), suggestFloors(data.numbersByPartOf(eik.osmId), emptyList()).map { it.floor })
    }
}
