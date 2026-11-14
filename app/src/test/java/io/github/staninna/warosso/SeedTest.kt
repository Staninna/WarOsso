package io.github.staninna.warosso

import io.github.staninna.warosso.data.OverpassException
import io.github.staninna.warosso.data.overpassQuery
import io.github.staninna.warosso.data.parseOverpass
import io.github.staninna.warosso.domain.BuildingLabel
import io.github.staninna.warosso.domain.Complex
import io.github.staninna.warosso.domain.ComplexData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Reads the committed seed, so a re-run of `tools/fetch-seed.sh` that breaks the app fails here first. */
class SeedTest {
    private val seed = parseOverpass(File("src/main/assets/seed.json").readText())
    private val data = ComplexData(Complex("Bulkpad", Complex.defaultPolygon()), seed.addresses, seed.buildings, emptyList())

    @Test
    fun `the complex is the odd side of Bulkpad`() {
        assertEquals("Bulkpad", seed.addresses.groupingBy { it.street }.eachCount().maxBy { it.value }.key)
        assertTrue(data.bagNumbers.size > 100)
        assertTrue(data.bagNumbers.all { it % 2 == 1 })
        assertEquals(data.bagNumbers, data.unservedNumbers)
    }

    @Test
    fun `every complex address sits in a building`() {
        assertTrue(data.complexAddresses.all { address -> address.buildingOsmId?.let(data::building) != null })
    }

    @Test
    fun `buildings are named after OSM, or after their numbers`() {
        val labels = data.complexBuildings.map(data::labelOf)
        assertTrue(BuildingLabel.Named("Eik") in labels)
        assertTrue(labels.any { it is BuildingLabel.Block })
    }

    @Test
    fun `numbers with a suffix are skipped`() {
        val json = """{"elements":[
            {"type":"node","id":1,"lat":51.5,"lon":4.8,"tags":{"addr:housenumber":"6A","addr:street":"Bulkpad"}},
            {"type":"node","id":2,"lat":51.5,"lon":4.8,"tags":{"addr:housenumber":"6","addr:street":"Bulkpad"}},
            {"type":"node","id":3,"lat":51.5,"lon":4.8,"tags":{"addr:housenumber":"0","addr:street":"Bulkpad"}}
        ]}"""
        assertEquals(listOf(6), parseOverpass(json).addresses.map { it.number })
    }

    @Test
    fun `an address on a way uses its centre and the building it is in`() {
        val json = """{"elements":[
            {"type":"way","id":10,"center":{"lat":51.50005,"lon":4.80005},"tags":{"addr:housenumber":"8","addr:street":"Bulkpad"}},
            {"type":"way","id":20,"tags":{"building":"yes","name":"Den"},"geometry":[
                {"lat":51.5,"lon":4.8},{"lat":51.5001,"lon":4.8},{"lat":51.5001,"lon":4.8001},{"lat":51.5,"lon":4.8001},{"lat":51.5,"lon":4.8}]}
        ]}"""
        val parsed = parseOverpass(json)
        assertEquals(20L, parsed.addresses.single().buildingOsmId)
        assertEquals(4, parsed.buildings.single().outline.size)
        assertEquals("Den", parsed.buildings.single().osmName)
    }

    @Test
    fun `an Overpass remark is an error, not an empty complex`() {
        val error = assertThrows(OverpassException::class.java) {
            parseOverpass("""{"elements":[],"remark":"runtime error: Query timed out"}""")
        }
        assertTrue(error.message!!.contains("timed out"))
        assertThrows(OverpassException::class.java) { parseOverpass("<html>busy</html>") }
    }

    @Test
    fun `the query covers the polygon`() {
        val query = overpassQuery(File("src/main/assets/overpass.ql").readText(), Complex.defaultPolygon().take(3))
        assertFalse(query, "{{" in query)
        assertTrue(query, "(poly:\"" in query)
        assertTrue(query, """nwr["addr:housenumber"]""" in query)
        assertTrue(query, ".buildings out geom;" in query)
    }
}
