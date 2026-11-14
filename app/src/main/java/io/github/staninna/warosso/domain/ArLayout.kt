package io.github.staninna.warosso.domain

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.tan

/** Something to label in the camera view. */
data class ArSubject(
    val id: Long,
    val bearingDeg: Double,
    val distanceM: Double,
    val isTarget: Boolean,
)

/** Where a label goes. Positions are fractions of the view width, so the layout knows no pixels. */
data class ArLabel(
    val id: Long,
    val isTarget: Boolean,
    /** Horizontal centre, 0 at the left edge and 1 at the right. */
    val x: Float,
    /** Stack level above the horizon line. 0 sits on it. */
    val row: Int,
    val scale: Float,
    val alpha: Float,
    /** Set when the target is out of view and its label is parked at a screen edge. */
    val offScreen: Side?,
)

enum class Side { LEFT, RIGHT }

private const val NEAR_M = 20.0
private const val FAR_M = 120.0
private const val FAR_SCALE = 0.7f
private const val FAR_ALPHA = 0.55f
private const val MAX_ROW = 3

/** Room between a parked target label and the screen edge, as a fraction of the view width. */
private const val EDGE_MARGIN = 0.02f

/**
 * Horizontal screen position of [bearingDeg] for a camera facing [headingDeg]: 0 at the left
 * edge, 1 at the right. A bearing behind the user is infinitely far off to its side.
 */
fun screenX(bearingDeg: Double, headingDeg: Double, horizontalFovDeg: Double): Float {
    val delta = angleDelta(headingDeg, bearingDeg)
    if (abs(delta) >= 90) return if (delta < 0) Float.NEGATIVE_INFINITY else Float.POSITIVE_INFINITY
    val halfWidth = tan(Math.toRadians(horizontalFovDeg / 2))
    return (0.5 + tan(Math.toRadians(delta)) / halfWidth / 2).toFloat()
}

/**
 * Places a label for every subject in view.
 *
 * - Labels that would overlap stack upwards, the nearest one lowest and drawn last, so it is
 *   never covered. The returned list is in drawing order: farthest first.
 * - Far labels shrink and fade.
 * - The target is always placed. Out of view it is parked at the edge it is closest to.
 *
 * @param labelWidth width of a full-size label as a fraction of the view width.
 */
fun layoutArLabels(
    subjects: List<ArSubject>,
    headingDeg: Double,
    horizontalFovDeg: Double,
    labelWidth: Float,
): List<ArLabel> {
    val placed = mutableListOf<ArLabel>()
    for (subject in subjects.sortedBy { it.distanceM }) {
        val farness = ((subject.distanceM - NEAR_M) / (FAR_M - NEAR_M)).coerceIn(0.0, 1.0).toFloat()
        val scale = if (subject.isTarget) 1f else 1f - farness * (1f - FAR_SCALE)
        val alpha = if (subject.isTarget) 1f else 1f - farness * (1f - FAR_ALPHA)
        val halfWidth = labelWidth * scale / 2

        val trueX = screenX(subject.bearingDeg, headingDeg, horizontalFovDeg)
        if (!subject.isTarget && trueX !in -halfWidth..1 + halfWidth) continue
        val x = if (subject.isTarget) trueX.coerceIn(halfWidth + EDGE_MARGIN, 1 - halfWidth - EDGE_MARGIN) else trueX
        val offScreen = when {
            trueX < 0 -> Side.LEFT
            trueX > 1 -> Side.RIGHT
            else -> null
        }

        fun overlaps(other: ArLabel) = abs(other.x - x) < labelWidth * other.scale / 2 + halfWidth
        val row = (0..MAX_ROW).firstOrNull { row -> placed.none { it.row == row && overlaps(it) } } ?: MAX_ROW
        placed += ArLabel(subject.id, subject.isTarget, x, row, scale, alpha, offScreen.takeIf { subject.isTarget })
    }
    return placed.asReversed()
}

/** The angles the camera preview covers on screen. */
data class FieldOfView(val horizontalDeg: Double, val verticalDeg: Double) {
    /**
     * Vertical screen position of something [elevationDeg] above the horizon, for a camera
     * looking [cameraElevationDeg] above it: 0 at the top edge, 1 at the bottom.
     */
    fun screenY(elevationDeg: Double, cameraElevationDeg: Double): Float {
        val delta = (elevationDeg - cameraElevationDeg).coerceIn(-89.0, 89.0)
        return (0.5 - tan(Math.toRadians(delta)) / tan(Math.toRadians(verticalDeg / 2)) / 2).toFloat()
    }
}

/** The camera's sensor and lens, in millimetres. */
data class CameraLens(val sensorLongMm: Float, val sensorShortMm: Float, val focalLengthMm: Float) {
    /**
     * The field of view of a portrait preview that is scaled to fill a view of [viewAspect]
     * (width / height). Whatever sticks out is cropped, which narrows that side.
     */
    fun fieldOfView(viewAspect: Float): FieldOfView {
        val fullHalfWidth = sensorShortMm / (2.0 * focalLengthMm)
        val fullHalfHeight = sensorLongMm / (2.0 * focalLengthMm)
        val imageAspect = fullHalfWidth / fullHalfHeight
        val (halfWidth, halfHeight) = if (viewAspect < imageAspect) {
            fullHalfHeight * viewAspect to fullHalfHeight
        } else {
            fullHalfWidth to fullHalfWidth / viewAspect
        }
        return FieldOfView(Math.toDegrees(2 * atan(halfWidth)), Math.toDegrees(2 * atan(halfHeight)))
    }

    companion object {
        /** A common phone main camera, for when the real lens data is missing. */
        val TYPICAL = CameraLens(sensorLongMm = 6.4f, sensorShortMm = 4.8f, focalLengthMm = 4.4f)
    }
}
