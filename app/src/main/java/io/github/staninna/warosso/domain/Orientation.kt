package io.github.staninna.warosso.domain

import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2

/**
 * How the phone is held.
 *
 * @param headingDeg where the user faces, clockwise from magnetic north: the camera's direction
 *   when the phone is upright, the top edge's direction when it lies flat.
 * @param tiltDeg 0 with the phone flat and the screen up, 90 with the phone upright.
 * @param elevationDeg how far the camera looks above the horizon. Negative looks down.
 * @param reliable false when the compass asks for calibration.
 */
data class Orientation(
    val headingDeg: Float,
    val tiltDeg: Float,
    val elevationDeg: Float,
    val reliable: Boolean,
) {
    /** The same pose with its heading turned from magnetic to true north. */
    fun withDeclination(declinationDeg: Float): Orientation =
        copy(headingDeg = normalizeDegrees((headingDeg + declinationDeg).toDouble()).toFloat())

    /** This reading, with the heading eased from [previous] along the short way round. */
    fun smoothedFrom(previous: Orientation, factor: Float = 0.25f): Orientation {
        val step = angleDelta(previous.headingDeg.toDouble(), headingDeg.toDouble()) * factor
        return copy(headingDeg = normalizeDegrees(previous.headingDeg + step).toFloat())
    }
}

/**
 * Reads heading and tilt from a rotation matrix as `SensorManager.getRotationMatrixFromVector`
 * fills it: row-major 3×3, device axes to world axes (x east, y north, z up).
 */
fun orientationFromRotationMatrix(r: FloatArray, reliable: Boolean): Orientation {
    // Third column: the screen normal (device +z) in world axes. The camera looks along -z.
    val normalEast = r[2]
    val normalNorth = r[5]
    val normalUp = r[8].coerceIn(-1f, 1f)
    // Second column: the phone's top edge (device +y) in world axes.
    val topEast = r[1]
    val topNorth = r[4]

    val tilt = Math.toDegrees(acos(normalUp).toDouble()).toFloat()
    // Upright, the top edge points at the sky and says nothing about heading; flat, the camera
    // looks at the ground. Each vector is used where its horizontal part is long.
    val heading = if (tilt > 45f) atan2(-normalEast, -normalNorth) else atan2(topEast, topNorth)
    return Orientation(
        headingDeg = normalizeDegrees(Math.toDegrees(heading.toDouble())).toFloat(),
        tiltDeg = tilt,
        elevationDeg = Math.toDegrees(asin(-normalUp).toDouble()).toFloat(),
        reliable = reliable,
    )
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
