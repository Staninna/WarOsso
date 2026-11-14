package io.github.staninna.warosso.domain

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.tan

/** Something to label in the camera view. */
data class ArSubject(
    val id: Long,
    val bearingDeg: Double,
    val distanceM: Double,
    val isTarget: Boolean,
)

/** Where a label goes. Positions are fractions of the view's size, so the layout knows no pixels. */
data class ArLabel(
    val id: Long,
    val isTarget: Boolean,
    /** The label's horizontal centre, 0 at the left edge and 1 at the right. */
    val x: Float,
    /** The spot the label points at, y from 0 at the top to 1 at the bottom. [markX] is [x], except where the target's label is kept clear of a screen edge. */
    val markX: Float,
    val y: Float,
    /** Stack level above that spot. 0 sits on it. */
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

/** A spot in the camera view, as fractions of its size: x from the left edge, y from the top. */
data class ScreenPoint(val x: Float, val y: Float)

/** The angles the camera preview covers on screen. */
data class FieldOfView(val horizontalDeg: Double, val verticalDeg: Double)

/**
 * The phone's camera as a pinhole: which way it is held and how much it sees.
 *
 * @param orientation how the phone is held, with its heading on true north.
 */
class ArCamera(orientation: Orientation, fieldOfView: FieldOfView) {
    private val right = orientation.rotation.rotate(Vec3.EAST)
    private val up = orientation.rotation.rotate(Vec3.NORTH)
    private val forward = -orientation.rotation.rotate(Vec3.UP)
    private val halfWidth = tan(Math.toRadians(fieldOfView.horizontalDeg / 2))
    private val halfHeight = tan(Math.toRadians(fieldOfView.verticalDeg / 2))

    /**
     * Where to mark the direction [bearingDeg], clockwise from true north.
     *
     * The mark belongs on the horizon: that is where a door at walking distance shows. When the
     * phone is tipped so far that the horizon leaves [band], the part of the view's height that
     * is free for labels, the mark slides along the vertical line through that horizon point
     * until it is back inside. On that line lies everything in this direction, from the ground
     * at the user's feet to the sky, so the mark still sits on what the camera shows there.
     *
     * A direction behind the camera has nothing to mark. Its x is infinitely far off to the side
     * it is on.
     */
    fun mark(bearingDeg: Double, band: ClosedFloatingPointRange<Float>): ScreenPoint {
        val bearing = Math.toRadians(bearingDeg)
        val level = Vec3(sin(bearing), cos(bearing), 0.0)
        // The point `level + rise * UP` in camera axes: each coordinate is `a + rise * b`.
        val ax = level dot right
        val ay = level dot up
        val az = level dot forward
        val bx = right.z
        val by = up.z
        val bz = forward.z

        fun at(rise: Double): ScreenPoint? {
            val depth = az + rise * bz
            if (depth < EPSILON) return null
            return ScreenPoint(
                x = (0.5 + (ax + rise * bx) / depth / halfWidth / 2).toFloat(),
                y = (0.5 - (ay + rise * by) / depth / halfHeight / 2).toFloat(),
            )
        }

        /** The rise at which the line crosses the height [y] of the view, or null when it runs level with it. */
        fun riseAt(y: Float): Double? {
            val slope = (1 - 2 * y) * halfHeight
            val steepness = by - slope * bz
            return if (abs(steepness) < EPSILON) null else (slope * az - ay) / steepness
        }

        val onHorizon = at(0.0)
        if (onHorizon != null && onHorizon.y in band) return onHorizon

        // The edge of the band that is reached with the smallest rise is the one nearest the horizon.
        return listOf(band.start, band.endInclusive)
            .mapNotNull { edge -> riseAt(edge)?.let { rise -> at(rise)?.let { abs(rise) to it.copy(y = edge) } } }
            .minByOrNull { (rise, _) -> rise }
            ?.second
            ?: ScreenPoint(if (ax < 0) Float.NEGATIVE_INFINITY else Float.POSITIVE_INFINITY, horizonAhead(band))
    }

    /** The height of the horizon straight ahead, kept inside [band]. A label whose own direction is out of view rests there. */
    private fun horizonAhead(band: ClosedFloatingPointRange<Float>): Float {
        val length = hypot(forward.x, forward.y)
        // Looking straight down the horizon is far above the view, straight up it is far below.
        if (length < EPSILON) return if (forward.z < 0) band.start else band.endInclusive
        // The level direction ahead is `forward` without its z, `length` long.
        val upward = forward.x * up.x + forward.y * up.y
        val depth = length * length
        return (0.5 - upward / depth / halfHeight / 2).toFloat().coerceIn(band)
    }

    private companion object {
        /** A depth or a slope below this counts as none: the point has no place on screen. */
        const val EPSILON = 1e-6
    }
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
 * @param band the part of the view's height the labels may point at, see [ArCamera.mark].
 */
fun layoutArLabels(
    subjects: List<ArSubject>,
    camera: ArCamera,
    labelWidth: Float,
    band: ClosedFloatingPointRange<Float>,
): List<ArLabel> {
    val placed = mutableListOf<ArLabel>()
    for (subject in subjects.sortedBy { it.distanceM }) {
        val farness = ((subject.distanceM - NEAR_M) / (FAR_M - NEAR_M)).coerceIn(0.0, 1.0).toFloat()
        val scale = if (subject.isTarget) 1f else 1f - farness * (1f - FAR_SCALE)
        val alpha = if (subject.isTarget) 1f else 1f - farness * (1f - FAR_ALPHA)
        val halfWidth = labelWidth * scale / 2

        val mark = camera.mark(subject.bearingDeg, band)
        if (!subject.isTarget && mark.x !in -halfWidth..1 + halfWidth) continue
        val x = if (subject.isTarget) mark.x.coerceIn(halfWidth + EDGE_MARGIN, 1 - halfWidth - EDGE_MARGIN) else mark.x
        val offScreen = when {
            mark.x < 0 -> Side.LEFT
            mark.x > 1 -> Side.RIGHT
            else -> null
        }

        fun overlaps(other: ArLabel) = abs(other.x - x) < labelWidth * other.scale / 2 + halfWidth
        val row = (0..MAX_ROW).firstOrNull { row -> placed.none { it.row == row && overlaps(it) } } ?: MAX_ROW
        placed += ArLabel(subject.id, subject.isTarget, x, mark.x, mark.y, row, scale, alpha, offScreen.takeIf { subject.isTarget })
    }
    return placed.asReversed()
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
