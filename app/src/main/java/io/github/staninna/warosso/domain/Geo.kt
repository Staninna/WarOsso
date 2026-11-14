package io.github.staninna.warosso.domain

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.DoubleArraySerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_M = 6_371_008.8

/** A WGS84 position. In JSON it is a two-element array, `[lat, lon]`. */
@Serializable(with = LatLngSerializer::class)
data class LatLng(val lat: Double, val lon: Double)

/** Rounded to seven decimals, about a centimetre: all a file that people read and diff needs. */
fun LatLng.rounded(): LatLng = LatLng(Math.round(lat * 1e7) / 1e7, Math.round(lon * 1e7) / 1e7)

object LatLngSerializer : KSerializer<LatLng> {
    private val delegate = DoubleArraySerializer()

    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor = SerialDescriptor("LatLng", delegate.descriptor)

    override fun serialize(encoder: Encoder, value: LatLng) =
        encoder.encodeSerializableValue(delegate, doubleArrayOf(value.lat, value.lon))

    override fun deserialize(decoder: Decoder): LatLng {
        val pair = decoder.decodeSerializableValue(delegate)
        require(pair.size == 2) { "A position needs [lat, lon], got ${pair.size} values" }
        return LatLng(pair[0], pair[1])
    }
}

/** Great-circle distance in metres. */
fun LatLng.distanceTo(other: LatLng): Double {
    val lat1 = Math.toRadians(lat)
    val lat2 = Math.toRadians(other.lat)
    val dLat = lat2 - lat1
    val dLon = Math.toRadians(other.lon - lon)
    val h = sin(dLat / 2).let { it * it } + cos(lat1) * cos(lat2) * sin(dLon / 2).let { it * it }
    return 2 * EARTH_RADIUS_M * asin(sqrt(h))
}

/** Initial bearing towards [other], in degrees clockwise from true north, 0 until 360. */
fun LatLng.bearingTo(other: LatLng): Double {
    val lat1 = Math.toRadians(lat)
    val lat2 = Math.toRadians(other.lat)
    val dLon = Math.toRadians(other.lon - lon)
    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    return normalizeDegrees(Math.toDegrees(atan2(y, x)))
}

/** The point [distanceM] metres away in direction [bearingDeg]. */
fun LatLng.offset(distanceM: Double, bearingDeg: Double): LatLng {
    val angular = distanceM / EARTH_RADIUS_M
    val bearing = Math.toRadians(bearingDeg)
    val lat1 = Math.toRadians(lat)
    val lat2 = asin(sin(lat1) * cos(angular) + cos(lat1) * sin(angular) * cos(bearing))
    val lon2 = Math.toRadians(lon) +
        atan2(sin(bearing) * sin(angular) * cos(lat1), cos(angular) - sin(lat1) * sin(lat2))
    return LatLng(Math.toDegrees(lat2), Math.toDegrees(lon2))
}

fun normalizeDegrees(degrees: Double): Double = ((degrees % 360) + 360) % 360

/** The signed shortest turn from [from] to [to], between -180 and 180. Positive is clockwise. */
fun angleDelta(from: Double, to: Double): Double = normalizeDegrees(to - from + 180) - 180

/** A circle drawn as a polygon, vertices clockwise from north. */
fun circlePolygon(center: LatLng, radiusM: Double, vertices: Int = 24): List<LatLng> =
    List(vertices) { center.offset(radiusM, it * 360.0 / vertices) }

/** A pie slice from [center], [widthDeg] wide around [bearingDeg]. Used for the heading cone. */
fun sectorPolygon(center: LatLng, radiusM: Double, bearingDeg: Double, widthDeg: Double): List<LatLng> {
    val steps = 8
    val arc = List(steps + 1) { center.offset(radiusM, bearingDeg - widthDeg / 2 + it * widthDeg / steps) }
    return listOf(center) + arc
}

/** Ray casting. [polygon] is a ring without a repeated closing point; fewer than 3 points holds nothing. */
fun LatLng.isInside(polygon: List<LatLng>): Boolean {
    var inside = false
    for (i in polygon.indices) {
        val a = polygon[i]
        val b = polygon[(i + 1) % polygon.size]
        if ((a.lat > lat) != (b.lat > lat)) {
            val crossingLon = a.lon + (lat - a.lat) / (b.lat - a.lat) * (b.lon - a.lon)
            if (lon < crossingLon) inside = !inside
        }
    }
    return inside
}

/** Distance in metres to the nearest edge of [polygon]. Flat-earth maths, fine at complex scale. */
fun LatLng.distanceToEdge(polygon: List<LatLng>): Double {
    if (polygon.isEmpty()) return Double.POSITIVE_INFINITY
    val metresPerDegLat = Math.toRadians(EARTH_RADIUS_M)
    val metresPerDegLon = metresPerDegLat * cos(Math.toRadians(lat))
    fun local(p: LatLng) = (p.lon - lon) * metresPerDegLon to (p.lat - lat) * metresPerDegLat

    return polygon.indices.minOf { i ->
        val (ax, ay) = local(polygon[i])
        val (bx, by) = local(polygon[(i + 1) % polygon.size])
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        // Project the origin (this point) on the segment a-b, clamped to its ends.
        val t = if (lengthSquared == 0.0) 0.0 else (-(ax * dx + ay * dy) / lengthSquared).coerceIn(0.0, 1.0)
        hypot(ax + t * dx, ay + t * dy)
    }
}

/** Vertex average. Good enough to place a label on a building. */
fun List<LatLng>.centroid(): LatLng = LatLng(sumOf { it.lat } / size, sumOf { it.lon } / size)
