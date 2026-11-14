package io.github.staninna.warosso.domain

import kotlin.math.sqrt

/** One report from a location provider. */
data class PositionReading(
    val latLng: LatLng,
    /** The radius the provider is 68% sure of, in metres. */
    val accuracyM: Float,
    /** Speed over the ground in metres per second, or null when the provider does not know. */
    val speedMps: Float?,
    /** Direction of travel, clockwise from true north, or null when the provider does not know. */
    val courseDeg: Float?,
    val timeMs: Long,
) {
    /** [courseDeg] when the user walks fast enough for it to mean something. */
    val walkingCourseDeg: Float? get() = courseDeg.takeIf { (speedMps ?: 0f) >= WALKING_ABOVE_MPS }

    private companion object {
        /** Below this the reported direction of travel is mostly noise. A slow walk is about 1 m/s. */
        const val WALKING_ABOVE_MPS = 0.7f
    }
}

/**
 * Steadies the position from one fix to the next.
 *
 * A fix alone is off by a few metres, differently every second. The speed the receiver reports
 * is much better than that. So between fixes the position is carried along with the reported
 * speed, and each new fix only pulls it part of the way: far when the fix is precise, hardly
 * when it is vague. Standing still, the position stops dancing; walking, it does not fall behind.
 */
class PositionFilter {
    private var estimate: LatLng? = null

    /** How unsure the estimate is, as a variance in square metres. */
    private var variance = 0.0
    private var lastTimeMs = 0L
    private var rejectedInARow = 0

    fun update(reading: PositionReading): LatLng {
        val previous = estimate
        val elapsedMs = reading.timeMs - lastTimeMs
        lastTimeMs = reading.timeMs
        val fixVariance = reading.accuracyM.coerceAtLeast(MIN_ACCURACY_M).let { it * it }.toDouble()
        if (previous == null || elapsedMs !in 0..RESTART_AFTER_MS) return restart(reading, fixVariance)

        val seconds = elapsedMs / 1000.0
        val speed = reading.speedMps?.toDouble() ?: 0.0
        val course = reading.courseDeg?.toDouble().takeIf { speed > MOVING_ABOVE_MPS }
        val carried = course?.let { previous.offset(speed * seconds, it) } ?: previous
        val drift = (if (course != null) WALKING_DRIFT_MPS else RESTING_DRIFT_MPS) * seconds
        val carriedVariance = variance + drift * drift

        // A fix far outside what the estimate and the fix allow together is a reflection off a
        // wall. A few in a row mean the estimate itself was wrong.
        val jump = carried.distanceTo(reading.latLng)
        if (jump > OUTLIER_SIGMAS * sqrt(carriedVariance + fixVariance)) {
            if (++rejectedInARow > MAX_REJECTED_IN_A_ROW) return restart(reading, fixVariance)
            variance = carriedVariance
            return carried.also { estimate = it }
        }
        rejectedInARow = 0

        val pull = carriedVariance / (carriedVariance + fixVariance)
        variance = carriedVariance * (1 - pull)
        return carried.offset(jump * pull, carried.bearingTo(reading.latLng)).also { estimate = it }
    }

    private fun restart(reading: PositionReading, fixVariance: Double): LatLng {
        variance = fixVariance
        rejectedInARow = 0
        return reading.latLng.also { estimate = it }
    }

    private companion object {
        /** No receiver is better than this, whatever it claims. */
        const val MIN_ACCURACY_M = 1f

        /** Below this the reported speed is noise and the phone counts as standing still. */
        const val MOVING_ABOVE_MPS = 0.3

        /** How fast the estimate may wander off per second: the error in the reported speed, or a first step. */
        const val WALKING_DRIFT_MPS = 1.0
        const val RESTING_DRIFT_MPS = 0.5

        /** After a silence this long the old estimate says nothing about where the phone is now. */
        const val RESTART_AFTER_MS = 10_000L
        const val OUTLIER_SIGMAS = 3.0
        const val MAX_REJECTED_IN_A_ROW = 2
    }
}
