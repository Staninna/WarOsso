package io.github.staninna.warosso.domain

import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A direction in space. In world axes x is east, y is north and z is up. */
data class Vec3(val x: Double, val y: Double, val z: Double) {
    infix fun dot(other: Vec3): Double = x * other.x + y * other.y + z * other.z

    operator fun unaryMinus(): Vec3 = Vec3(-x, -y, -z)

    companion object {
        val EAST = Vec3(1.0, 0.0, 0.0)
        val NORTH = Vec3(0.0, 1.0, 0.0)
        val UP = Vec3(0.0, 0.0, 1.0)
    }
}

/** A turn in space, as a unit quaternion. */
data class Rotation(val w: Double, val x: Double, val y: Double, val z: Double) {
    /** [v] after this turn. */
    fun rotate(v: Vec3): Vec3 {
        // v + 2w (q × v) + 2 q × (q × v), with q the vector part.
        val cx = y * v.z - z * v.y
        val cy = z * v.x - x * v.z
        val cz = x * v.y - y * v.x
        return Vec3(
            v.x + 2 * (w * cx + y * cz - z * cy),
            v.y + 2 * (w * cy + z * cx - x * cz),
            v.z + 2 * (w * cz + x * cy - y * cx),
        )
    }

    /** The turn back. */
    val inverse: Rotation get() = Rotation(w, -x, -y, -z)

    /** [other] first, then this turn. */
    operator fun times(other: Rotation): Rotation = Rotation(
        w * other.w - x * other.x - y * other.y - z * other.z,
        w * other.x + x * other.w + y * other.z - z * other.y,
        w * other.y - x * other.z + y * other.w + z * other.x,
        w * other.z + x * other.y - y * other.x + z * other.w,
    )

    /** The turn that lies [fraction] of the way from this one to [to], along the short way round. */
    fun towards(to: Rotation, fraction: Double): Rotation {
        // q and -q are the same turn. Blending needs the one on this turn's side.
        val side = if (w * to.w + x * to.x + y * to.y + z * to.z < 0) -1 else 1
        val bw = w + (side * to.w - w) * fraction
        val bx = x + (side * to.x - x) * fraction
        val by = y + (side * to.y - y) * fraction
        val bz = z + (side * to.z - z) * fraction
        val length = sqrt(bw * bw + bx * bx + by * by + bz * bz)
        return Rotation(bw / length, bx / length, by / length, bz / length)
    }

    companion object {
        /** A turn of [degrees] around the unit vector [axis], counter-clockwise seen from its tip. */
        fun about(axis: Vec3, degrees: Double): Rotation {
            val half = Math.toRadians(degrees) / 2
            val s = sin(half)
            return Rotation(cos(half), axis.x * s, axis.y * s, axis.z * s)
        }
    }
}

/** Where a pose gets its north from. */
enum class North {
    /** The compass, and it is sure of itself. */
    COMPASS,

    /** The compass, but it asks for calibration. */
    COMPASS_UNSURE,

    /** The user turned the view into place. The gyroscope holds it there and the compass is left out. */
    BY_HAND,
}

/**
 * How the phone is held.
 *
 * @param rotation the turn from the phone's own axes to the world's. The phone's x points to its
 *   right edge, y to its top edge and z out of the screen. The camera looks along -z.
 */
data class Orientation(val rotation: Rotation, val north: North = North.COMPASS) {
    /** Where the screen faces. */
    private val screenNormal = rotation.rotate(Vec3.UP)

    /** 0 with the phone flat and the screen up, 90 with the phone upright. */
    val tiltDeg: Float = Math.toDegrees(acos(screenNormal.z.coerceIn(-1.0, 1.0))).toFloat()

    /** How far the camera looks above the horizon. Negative looks down. */
    val elevationDeg: Float = Math.toDegrees(asin((-screenNormal.z).coerceIn(-1.0, 1.0))).toFloat()

    /**
     * Where the user faces, clockwise from north: the camera's direction when the phone is
     * upright, the top edge's direction when it lies flat.
     */
    val headingDeg: Float = run {
        // Upright, the top edge points at the sky and says nothing about heading; flat, the camera
        // looks at the ground. Each vector is used where its horizontal part is long.
        val heading = if (tiltDeg > 45f) {
            atan2(-screenNormal.x, -screenNormal.y)
        } else {
            val topEdge = rotation.rotate(Vec3.NORTH)
            atan2(topEdge.x, topEdge.y)
        }
        normalizeDegrees(Math.toDegrees(heading)).toFloat()
    }

    /** The same pose with its heading turned from magnetic to true north. */
    fun withDeclination(declinationDeg: Float): Orientation =
        copy(rotation = Rotation.about(Vec3.UP, -declinationDeg.toDouble()) * rotation)

    /**
     * This reading, eased from [previous]. The whole pose moves as one, so a label follows a turn
     * of the phone equally fast sideways and up and down.
     */
    fun smoothedFrom(previous: Orientation, factor: Double = 0.25): Orientation =
        copy(rotation = previous.rotation.towards(rotation, factor))

    companion object {
        /** A phone held without roll: facing [headingDeg] and tipped up by [tiltDeg] from lying flat. */
        fun of(headingDeg: Float, tiltDeg: Float, north: North = North.COMPASS): Orientation = Orientation(
            rotation = Rotation.about(Vec3.UP, -headingDeg.toDouble()) * Rotation.about(Vec3.EAST, tiltDeg.toDouble()),
            north = north,
        )
    }
}

/** Which of the two guidance views is on screen. */
enum class ViewMode { AR, MAP }

/** Upright past this tilt switches to the camera view. */
const val AR_ABOVE_TILT_DEG = 55f

/** Flatter than this tilt switches to the map. Between the two thresholds the view stays put. */
const val MAP_BELOW_TILT_DEG = 35f

fun ViewMode.afterTilt(tiltDeg: Float): ViewMode = when {
    tiltDeg > AR_ABOVE_TILT_DEG -> ViewMode.AR
    tiltDeg < MAP_BELOW_TILT_DEG -> ViewMode.MAP
    else -> this
}
