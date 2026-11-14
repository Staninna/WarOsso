package io.github.staninna.warosso.domain

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** A steady walk in one direction, seen over the last few fixes. */
data class Walk(
    /** Direction of travel, clockwise from true north. */
    val courseDeg: Double,
    /**
     * The phone pointed backwards the whole time. Nobody walks like that for long, so it is the
     * heading that is turned around.
     */
    val headingAgainst: Boolean,
)

/**
 * Checks the heading against the one thing that needs no compass: the direction GPS sees the
 * user walk in. It cannot tell a small error, because a phone is not always held straight ahead,
 * but it catches a heading that points the wrong way altogether.
 */
class WalkTracker {
    private class Step(val courseDeg: Double, val headingDeg: Double?)

    private val steps = ArrayDeque<Step>()

    /**
     * Takes one fix.
     *
     * @param courseDeg the fix's direction of travel, see [Fix.courseDeg].
     * @param headingDeg where the phone pointed at that moment, clockwise from true north.
     * @return the walk the last fixes add up to, or null while the user stands, turns or has only just set off.
     */
    fun update(courseDeg: Float?, headingDeg: Float?): Walk? {
        if (courseDeg == null) {
            steps.clear()
            return null
        }
        steps.addLast(Step(courseDeg.toDouble(), headingDeg?.toDouble()))
        if (steps.size > STEPS) steps.removeFirst()
        if (steps.size < STEPS) return null

        val course = meanDirection(steps.map { it.courseDeg })
        if (steps.any { abs(angleDelta(course, it.courseDeg)) > STRAIGHT_WITHIN_DEG }) return null
        return Walk(
            courseDeg = course,
            headingAgainst = steps.all { it.headingDeg != null && abs(angleDelta(it.courseDeg, it.headingDeg)) > AGAINST_ABOVE_DEG },
        )
    }

    private companion object {
        /** Fixes come once a second, so this is four seconds and about five metres. */
        const val STEPS = 4
        const val STRAIGHT_WITHIN_DEG = 25.0

        /** Well past a right angle: looking sideways at a building while walking past it is normal. */
        const val AGAINST_ABOVE_DEG = 110.0
    }
}

/** The direction in the middle of [directionsDeg], which are clockwise from north. */
fun meanDirection(directionsDeg: List<Double>): Double {
    val radians = directionsDeg.map(Math::toRadians)
    return normalizeDegrees(Math.toDegrees(atan2(radians.sumOf(::sin), radians.sumOf(::cos))))
}
