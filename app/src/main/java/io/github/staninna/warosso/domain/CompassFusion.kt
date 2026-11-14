package io.github.staninna.warosso.domain

import kotlin.math.abs
import kotlin.math.atan2

/**
 * Joins the phone's two rotation sensors into one pose.
 *
 * The gyroscope pose is smooth and follows every turn at once, but its north is arbitrary and
 * drifts by a few degrees a minute. The compass pose knows north, but swings when a car, a
 * railing or a steel door bends the magnetic field. So the gyroscope pose is used as it comes,
 * and only its north is corrected: slowly, towards what the compass says on average, and not at
 * all while the field is disturbed.
 */
class CompassFusion {
    /** How far the gyroscope's north must turn around the up axis to be the compass's north. */
    private var northOffsetDeg = 0.0
    private var samples = 0
    private var lastNanos = 0L

    /**
     * @param gyro the pose without a compass in it.
     * @param compass the latest pose with a compass in it.
     * @param disturbed the magnetic field is not the earth's alone right now, see [isMagneticDisturbance].
     * @return [gyro] with its heading on magnetic north.
     */
    fun update(gyro: Rotation, compass: Rotation, disturbed: Boolean, timeNanos: Long): Rotation {
        val seconds = (timeNanos - lastNanos) / 1e9
        lastNanos = timeNanos
        // Without any estimate even a disturbed compass beats an arbitrary north.
        if (!disturbed || samples == 0) {
            // Both poses agree on what is up, so they differ by a turn around the up axis.
            val between = compass * gyro.inverse
            val measured = Math.toDegrees(2 * atan2(between.z, between.w))
            // A plain average at first, to settle fast; after that a slow pull.
            samples++
            val weight = maxOf(1.0 / samples, (seconds / SETTLE_SECONDS).coerceIn(0.0, 1.0))
            northOffsetDeg += angleDelta(northOffsetDeg, measured) * weight
        }
        return Rotation.about(Vec3.UP, northOffsetDeg) * gyro
    }

    /** Starts the average over. For when the compass has just been calibrated and its old readings are worth nothing. */
    fun reset() {
        samples = 0
    }

    private companion object {
        /**
         * How long the compass takes to pull the heading most of the way to a new value. Long
         * enough to sit out a passing disturbance, short enough that gyroscope drift stays
         * under a degree.
         */
        const val SETTLE_SECONDS = 10.0
    }
}

/**
 * Whether a magnetometer reading of [measuredUt] microtesla shows more than the earth's own field
 * of [expectedUt]: metal or a magnet nearby adds to it or takes from it.
 */
fun isMagneticDisturbance(measuredUt: Float, expectedUt: Float): Boolean =
    abs(measuredUt - expectedUt) > expectedUt * MAX_FIELD_DEVIATION

private const val MAX_FIELD_DEVIATION = 0.15f
