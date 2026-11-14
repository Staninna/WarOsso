package io.github.staninna.warosso.data

import io.github.staninna.warosso.domain.Building
import io.github.staninna.warosso.domain.Complex
import io.github.staninna.warosso.domain.Entrance
import io.github.staninna.warosso.domain.EntranceType
import io.github.staninna.warosso.domain.FloorDoors
import io.github.staninna.warosso.domain.LatLng
import io.github.staninna.warosso.domain.Setup
import io.github.staninna.warosso.domain.rounded
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** The shape of `warosso.json` (SPEC.md section 7). */
@Serializable
private data class ExportFile(
    val schemaVersion: Int,
    val exportedAt: String,
    val complex: ExportComplex,
    val buildings: List<ExportBuilding>,
    val entrances: List<ExportEntrance>,
)

@Serializable
private data class ExportComplex(val street: String, val polygon: List<LatLng>)

@Serializable
private data class ExportBuilding(val osmId: Long, val customName: String?, val outline: List<LatLng>)

@Serializable
private data class ExportEntrance(
    /** A label for people reading the file. Import hands out fresh ids. */
    val id: String,
    val buildingOsmId: Long,
    val lat: Double,
    val lon: Double,
    val type: EntranceType,
    val innerStairs: Boolean,
    val name: String?,
    val note: String?,
    val floors: List<FloorDoors>,
)

/** Why a file could not be imported. The UI turns each case into a sentence. */
sealed class ImportException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NotAnExport(cause: Throwable? = null) : ImportException("Not a WarOsso export", cause)
    class NewerSchema(val found: Int, val supported: Int) :
        ImportException("Export has schema $found, this app reads up to $supported")
    class BrokenReference(val buildingOsmId: Long) :
        ImportException("An entrance points at building $buildingOsmId, which the file does not contain")
}

/** One step up the schema ladder: turns a document of version `n` into version `n + 1`. */
typealias SchemaMigration = (JsonObject) -> JsonObject

object ExportCodec {
    const val SCHEMA_VERSION = 1

    /** Keyed by the version a step upgrades from. Empty while version 1 is the only one. */
    private val MIGRATIONS: Map<Int, SchemaMigration> = emptyMap()

    private val json = Json { encodeDefaults = true }

    /** Writes the owner's setup as the text of `warosso.json`. */
    fun encode(setup: Setup, exportedAt: OffsetDateTime): String {
        val file = ExportFile(
            schemaVersion = SCHEMA_VERSION,
            exportedAt = exportedAt.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            complex = ExportComplex(setup.complex.street, setup.complex.polygon.map(LatLng::rounded)),
            buildings = setup.buildings.map { ExportBuilding(it.osmId, it.customName, it.outline.map(LatLng::rounded)) },
            entrances = setup.entrances.map {
                val position = it.latLng.rounded()
                ExportEntrance(
                    id = "e${it.id}",
                    buildingOsmId = it.buildingOsmId,
                    lat = position.lat,
                    lon = position.lon,
                    type = it.type,
                    innerStairs = it.innerStairs,
                    name = it.name,
                    note = it.note,
                    floors = it.floors,
                )
            },
        )
        return render(json.encodeToJsonElement(file)) + "\n"
    }

    /**
     * Pretty prints with one twist: an array of plain numbers stays on one line, so a door list
     * reads `[169, 167, 165]` and a coordinate `[51.5979, 4.8005]`.
     */
    private fun render(element: JsonElement, indent: String = ""): String {
        val inner = "$indent    "
        return when (element) {
            is JsonObject -> element.entries.joinToString(",\n", "{\n", "\n$indent}") { (key, value) ->
                "$inner${JsonPrimitive(key)}: ${render(value, inner)}"
            }
            is JsonArray -> if (element.all { it is JsonPrimitive && !it.isString }) {
                element.joinToString(", ", "[", "]")
            } else {
                element.joinToString(",\n", "[\n", "\n$indent]") { inner + render(it, inner) }
            }
            else -> element.toString()
        }
    }

    /**
     * Reads an export into the setup it describes.
     *
     * @throws ImportException when the text is not an export this app can read.
     */
    fun decode(text: String, migrations: Map<Int, SchemaMigration> = MIGRATIONS): Setup {
        val file = try {
            val document = json.parseToJsonElement(text).jsonObject
            json.decodeFromJsonElement<ExportFile>(migrate(document, migrations))
        } catch (e: SerializationException) {
            throw ImportException.NotAnExport(e)
        } catch (e: IllegalArgumentException) {
            throw ImportException.NotAnExport(e)
        }

        val buildingIds = file.buildings.mapTo(HashSet()) { it.osmId }
        file.entrances.firstOrNull { it.buildingOsmId !in buildingIds }?.let {
            throw ImportException.BrokenReference(it.buildingOsmId)
        }
        return Setup(
            complex = Complex(file.complex.street, file.complex.polygon),
            buildings = file.buildings.map { Building(it.osmId, osmName = null, it.customName, it.outline) },
            entrances = file.entrances.map {
                Entrance(
                    buildingOsmId = it.buildingOsmId,
                    latLng = LatLng(it.lat, it.lon),
                    type = it.type,
                    innerStairs = it.innerStairs,
                    name = it.name,
                    note = it.note,
                    floors = it.floors,
                )
            },
        )
    }

    /** Walks [document] up to [SCHEMA_VERSION], one migration per version. */
    private fun migrate(document: JsonObject, migrations: Map<Int, SchemaMigration>): JsonObject {
        var current = document
        var version = versionOf(current)
        if (version > SCHEMA_VERSION) throw ImportException.NewerSchema(version, SCHEMA_VERSION)
        while (version < SCHEMA_VERSION) {
            val step = migrations[version] ?: throw ImportException.NotAnExport()
            current = JsonObject(step(current) + ("schemaVersion" to JsonPrimitive(version + 1)))
            version++
        }
        return current
    }

    private fun versionOf(document: JsonObject): Int =
        (document["schemaVersion"] as? JsonPrimitive)?.intOrNull ?: throw ImportException.NotAnExport()
}
