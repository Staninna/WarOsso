package io.github.staninna.warosso.data

import io.github.staninna.warosso.domain.Address
import io.github.staninna.warosso.domain.Building
import io.github.staninna.warosso.domain.LatLng
import io.github.staninna.warosso.domain.OsmData
import io.github.staninna.warosso.domain.holdingOrNearest
import io.github.staninna.warosso.domain.mergeBuildingParts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

@Serializable
private data class OverpassResponse(val elements: List<OverpassElement> = emptyList(), val remark: String? = null)

@Serializable
private data class OverpassElement(
    val type: String,
    val id: Long,
    val lat: Double? = null,
    val lon: Double? = null,
    /** Ways and relations printed with `out center`. */
    val center: OverpassPoint? = null,
    /** Ways printed with `out geom`. */
    val geometry: List<OverpassPoint>? = null,
    val tags: Map<String, String> = emptyMap(),
)

@Serializable
private data class OverpassPoint(val lat: Double, val lon: Double) {
    fun toLatLng() = LatLng(lat, lon)
}

class OverpassException(message: String, cause: Throwable? = null) : IOException(message, cause)

private val overpassJson = Json { ignoreUnknownKeys = true }

/**
 * Reads an Overpass JSON answer, as written by `tools/fetch-seed.sh` and [OverpassClient].
 *
 * What the data for this complex looks like, and so what the parser keeps:
 * - Addresses are BAG nodes with `addr:street` and `addr:housenumber`. Numbers with a suffix
 *   ("6A") or a utility code ("2600-TRC") only occur on neighbouring streets and are skipped.
 * - Buildings are closed ways. The apartment blocks are mapped as two outlines each, which
 *   [mergeBuildingParts] joins. An address belongs to the building it sits in.
 * - OSM has no `entrance=*` nodes here, so the query's third part comes back empty and is not read.
 */
fun parseOverpass(json: String): OsmData {
    val response = try {
        overpassJson.decodeFromString<OverpassResponse>(json)
    } catch (e: SerializationException) {
        throw OverpassException("Overpass sent something that is not its JSON format", e)
    }
    response.remark?.let { throw OverpassException(it) }

    val outlines = response.elements
        .filter { it.type == "way" && "building" in it.tags }
        .mapNotNull { way ->
            val ring = way.geometry.orEmpty().map { it.toLatLng() }.dropClosingPoint()
            if (ring.size < 3) null else Building(way.id, way.tags["name"], customName = null, outline = ring)
        }
        .distinctBy { it.osmId }
    val buildingOf = mergeBuildingParts(outlines)

    val addresses = response.elements
        .mapNotNull { element ->
            val number = element.tags["addr:housenumber"]?.toIntOrNull()?.takeIf { it > 0 }
            val street = element.tags["addr:street"]
            val point = element.center?.toLatLng()
                ?: element.lat?.let { lat -> element.lon?.let { lon -> LatLng(lat, lon) } }
            if (number == null || street == null || point == null) {
                null
            } else {
                val outline = outlines.holdingOrNearest(point)
                Address(street, number, point, outline?.let { buildingOf.getValue(it.osmId).osmId }, outline?.osmId)
            }
        }
        .distinctBy { it.street to it.number }

    return OsmData(addresses, buildingOf.values.distinct())
}

private fun List<LatLng>.dropClosingPoint(): List<LatLng> =
    if (size > 1 && first() == last()) dropLast(1) else this

/** The placeholder in `assets/overpass.ql` for the area to search. */
private const val AREA_PLACEHOLDER = "{{area}}"

/** The Overpass QL for everything inside [polygon], from the template `tools/fetch-seed.sh` also uses. */
fun overpassQuery(template: String, polygon: List<LatLng>): String {
    val area = polygon.joinToString(" ", prefix = "(poly:\"", postfix = "\")") { "${it.lat} ${it.lon}" }
    return template.replace(AREA_PLACEHOLDER, area)
}

/** The one place the app talks to a server that is not a tile server, and only when asked to. */
class OverpassClient(
    private val queryTemplate: String,
    private val endpoint: String = "https://overpass-api.de/api/interpreter",
) {

    suspend fun fetch(polygon: List<LatLng>): OsmData = withContext(Dispatchers.IO) {
        val connection = URI(endpoint).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 75_000
            connection.setRequestProperty("User-Agent", "WarOsso")
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.outputStream.use {
                it.write("data=${URLEncoder.encode(overpassQuery(queryTemplate, polygon), Charsets.UTF_8)}".toByteArray())
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw OverpassException("Overpass answered HTTP ${connection.responseCode}")
            }
            parseOverpass(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }
}
