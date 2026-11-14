package io.github.staninna.warosso.domain

import kotlin.math.abs
import kotlin.math.atan2

/**
 * Joins the phone's two rotation sensors into one pose.
 *
 * The gyroscope pose is smooth and follows every turn at once, but its north is arbitrary and
 * drifts by a few degrees a minute. The compass pose knows north, but swings when a car, a
 * railing or a steel door bends the magnetic field. So the gyroscope pose is used as it comes,
 * and only its north is corrected, towards what the compass says on average.
 *
 * The compass always wins in the end: a reading that looks wrong is sat out for a few seconds,
 * never for good. The user can overrule it with [turn], and then the gyroscope alone holds north.
 */
class CompassFusion {
    /** How far the gyroscope's north must turn around the up axis to be north, counter-clockwise seen from above. */
    private var northOffsetDeg = 0.0
    private var samples = 0
    private var lastNanos = 0L

    /** For how long the compass has pointed far from the estimate. */
    private var farSeconds = 0.0

    /** North was set with [turn] and the compass is left out until [followCompass]. */
    var byHand = false
        private set

    /**
     * @param gyro the pose without a compass in it.
     * @param compass the latest pose with a compass in it.
     * @param disturbed the magnetic field is not the earth's alone right now, see [isMagneticDisturbance].
     * @return [gyro] with its heading on magnetic north, or where the user turned it.
     */
    fun update(gyro: Rotation, compass: Rotation, disturbed: Boolean, timeNanos: Long): Rotation {
        val seconds = (timeNanos - lastNanos) / 1e9
        lastNanos = timeNanos
        if (!byHand) follow(compass, gyro, disturbed, seconds)
        return Rotation.about(Vec3.UP, northOffsetDeg) * gyro
    }

    private fun follow(compass: Rotation, gyro: Rotation, disturbed: Boolean, seconds: Double) {
        // Both poses agree on what is up, so they differ by a turn around the up axis.
        val between = compass * gyro.inverse
        val gap = angleDelta(northOffsetDeg, Math.toDegrees(2 * atan2(between.z, between.w)))

        // The gyroscope cannot drift this far in the time between two corrections. So either the
        // compass is being pulled aside for a moment, and waiting is right, or the estimate
        // itself is wrong, and then north is learned again.
        if (samples > 0 && abs(gap) > FAR_DEG) {
            farSeconds += seconds
            if (farSeconds < (if (disturbed) DISTURBED_PATIENCE_SECONDS else PATIENCE_SECONDS)) return
            samples = 0
        }
        farSeconds = 0.0

        // A plain average at first, to settle fast; after that a slow pull.
        samples++
        val settleSeconds = if (disturbed) DISTURBED_SETTLE_SECONDS else SETTLE_SECONDS
        northOffsetDeg += gap * maxOf(1.0 / samples, (seconds / settleSeconds).coerceIn(0.0, 1.0))
    }

    /** Turns the heading by [byDeg], clockwise seen from above, and holds it there without the compass. */
    fun turn(byDeg: Double) {
        northOffsetDeg -= byDeg
        byHand = true
    }

    /** Hands north back to the compass, starting from its next reading. */
    fun followCompass() {
        byHand = false
        relearn()
    }

    /** Starts the average over. For when the compass has just been calibrated and its old readings are worth nothing. */
    fun relearn() {
        samples = 0
        farSeconds = 0.0
    }

    private companion object {
        /**
         * How long the compass takes to pull the heading most of the way to a new value. Long
         * enough to smooth its jitter, short enough that gyroscope drift stays under a degree.
         */
        const val SETTLE_SECONDS = 10.0

        /** The same in a disturbed field, where the compass is worth less. */
        const val DISTURBED_SETTLE_SECONDS = 40.0

        /** A compass further than this from the estimate is not drift. */
        const val FAR_DEG = 25.0

        /** How long a compass that points far off is sat out: walking past a parked car takes about this long. */
        const val PATIENCE_SECONDS = 3.0

        /**
         * The same when the field's strength already says something is pulling at the compass.
         * Not for ever: a phone case with a magnet in it reads as disturbed all day, and the
         * phone's own calibration deals with that.
         */
        const val DISTURBED_PATIENCE_SECONDS = 10.0
    }
}

/**
 * Whether a magnetometer reading of [measuredUt] microtesla shows more than the earth's own field
 * of [expectedUt]: metal or a magnet nearby adds to it or takes from it.
 */
fun isMagneticDisturbance(measuredUt: Float, expectedUt: Float): Boolean =
    abs(measuredUt - expectedUt) > expectedUt * MAX_FIELD_DEVIATION

private const val MAX_FIELD_DEVIATION = 0.15f
