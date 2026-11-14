package io.github.staninna.warosso

import io.github.staninna.warosso.data.ExportCodec
import io.github.staninna.warosso.data.ImportException
import io.github.staninna.warosso.domain.Entrance
import io.github.staninna.warosso.domain.EntranceType
import io.github.staninna.warosso.domain.FloorDoors
import io.github.staninna.warosso.domain.distanceTo
import io.github.staninna.warosso.domain.offset
import io.github.staninna.warosso.domain.rounded
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

class ExportCodecTest {
    private val exportedAt = OffsetDateTime.parse("2026-11-14T16:00:00+01:00")

    private val data = complexData(
        addresses = listOf(address(151, buildingOsmId = 1)),
        buildings = listOf(building(1, name = "Esdoorn").copy(customName = "Home"), building(2, northM = 60.0), building(3, northM = -60.0)),
        entrances = listOf(
            Entrance(
                id = 7,
                buildingOsmId = 1,
                latLng = CENTER.offset(12.0, 45.0),
                type = EntranceType.STAIRWELL,
                name = "Stairwell 2",
                floors = listOf(FloorDoors(0, listOf(169, 167, 165)), FloorDoors(1, listOf(199, 197, 195))),
            ),
            Entrance(
                id = 9,
                buildingOsmId = 2,
                latLng = CENTER.offset(60.0, 0.0),
                type = EntranceType.INTERCOM,
                innerStairs = true,
                note = "Blue door [2,3], ring twice",
                floors = listOf(FloorDoors(3, listOf(51))),
            ),
        ),
    )

    @Test
    fun `export then import gives the same setup back`() {
        val imported = ExportCodec.decode(ExportCodec.encode(data.setup, exportedAt))

        // Positions are written to the centimetre.
        assertEquals(data.complex.copy(polygon = data.complex.polygon.map { it.rounded() }), imported.complex)
        assertEquals(data.entrances.map { it.copy(id = 0, latLng = it.latLng.rounded()) }, imported.entrances)
        // Building 3 has no setup, so it is not in the file. OSM names are not setup either.
        assertEquals(
            data.buildings.take(2).map { it.copy(osmName = null, outline = it.outline.map { corner -> corner.rounded() }) },
            imported.buildings,
        )
        assertEquals(data.entrances[1].note, imported.entrances[1].note)
        assertEquals(0.0, data.entrances.first().latLng.distanceTo(imported.entrances.first().latLng), 0.02)
    }

    @Test
    fun `text the owner typed is written as typed`() {
        assertTrue(""""note": "Blue door [2,3], ring twice"""" in ExportCodec.encode(data.setup, exportedAt))
    }

    @Test
    fun `a second round trip changes nothing`() {
        val once = ExportCodec.encode(data.setup, exportedAt)
        val twice = ExportCodec.encode(ExportCodec.decode(once), exportedAt)
        // Ids are relabelled on import, everything else must be byte for byte the same.
        assertEquals(once.replace(Regex(""""id": "e\d+""""), ""), twice.replace(Regex(""""id": "e\d+""""), ""))
    }

    @Test
    fun `the file reads like the spec example`() {
        val text = ExportCodec.encode(data.setup, exportedAt)
        assertTrue(text, """"schemaVersion": 1""" in text)
        assertTrue(text, """"exportedAt": "2026-11-14T16:00:00+01:00"""" in text)
        assertTrue(text, """"doors": [169, 167, 165]""" in text)
        assertTrue(text, """"id": "e7"""" in text)
        assertTrue(text, """"note": null""" in text)
        assertFalse("BAG addresses are not setup data", "151" in text)
    }

    @Test
    fun `a file from a future schema is refused with both versions named`() {
        val future = ExportCodec.encode(data.setup, exportedAt).replace(""""schemaVersion": 1""", """"schemaVersion": 2""")
        val error = assertThrows(ImportException.NewerSchema::class.java) { ExportCodec.decode(future) }
        assertEquals(2, error.found)
        assertEquals(ExportCodec.SCHEMA_VERSION, error.supported)
    }

    @Test
    fun `an older schema is migrated one version at a time`() {
        // A made-up version 0 that called the street "streetName".
        val current = ExportCodec.encode(data.setup, exportedAt)
        val old = current
            .replace(""""schemaVersion": 1""", """"schemaVersion": 0""")
            .replace(""""street":""", """"streetName":""")
        val fromZero: (JsonObject) -> JsonObject = { document ->
            val complex = document.getValue("complex") as JsonObject
            val renamed = buildJsonObject {
                complex.forEach { (key, value) -> put(if (key == "streetName") "street" else key, value) }
            }
            JsonObject(document + ("complex" to renamed))
        }

        assertEquals(ExportCodec.decode(current), ExportCodec.decode(old, migrations = mapOf(0 to fromZero)))
    }

    @Test
    fun `an older schema without a migration is refused`() {
        val old = ExportCodec.encode(data.setup, exportedAt).replace(""""schemaVersion": 1""", """"schemaVersion": 0""")
        assertThrows(ImportException.NotAnExport::class.java) { ExportCodec.decode(old) }
    }

    @Test
    fun `other files are refused`() {
        assertThrows(ImportException.NotAnExport::class.java) { ExportCodec.decode("not json at all") }
        assertThrows(ImportException.NotAnExport::class.java) { ExportCodec.decode("[1, 2, 3]") }
        assertThrows(ImportException.NotAnExport::class.java) { ExportCodec.decode("""{"hello": "world"}""") }
        assertThrows(ImportException.NotAnExport::class.java) {
            ExportCodec.decode(buildJsonObject { put("schemaVersion", JsonPrimitive(1)) }.toString())
        }
    }

    @Test
    fun `an entrance on a building the file lacks is refused`() {
        val broken = ExportCodec.encode(data.setup, exportedAt).replace(""""buildingOsmId": 2""", """"buildingOsmId": 404""")
        val error = assertThrows(ImportException.BrokenReference::class.java) { ExportCodec.decode(broken) }
        assertEquals(404L, error.buildingOsmId)
    }
}
