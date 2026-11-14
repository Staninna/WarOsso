package io.github.staninna.warosso.domain

/** A position reading. [declinationDeg] is how far magnetic north is east of true north there. */
data class Fix(val latLng: LatLng, val accuracyM: Float, val declinationDeg: Float = 0f) {
    /** Too vague to trust the arrow (SPEC.md 5.1). */
    val isWeak: Boolean get() = accuracyM > WEAK_ACCURACY_M

    companion object {
        const val WEAK_ACCURACY_M = 15f
    }
}

/** Live guidance to one house number. Distance and bearing are null until the first fix. */
data class Guidance(
    val number: Int,
    val route: Route,
    val distanceM: Double?,
    /** Direction of the route's target, clockwise from true north. */
    val bearingDeg: Double?,
    val arrived: Boolean,
) {
    companion object {
        /** Arrival starts inside this radius and ends outside [LEAVE_RADIUS_M], so GPS jitter cannot flap it. */
        const val ARRIVE_RADIUS_M = 10.0
        const val LEAVE_RADIUS_M = 15.0
    }
}

/**
 * Picks the route whose target is closest to [fix] and measures the way there. Called again on
 * every fix, so the target switches when another entrance gets closer. Without a fix the first
 * route stands in.
 */
fun Lookup.Found.guide(fix: Fix?, wasArrived: Boolean): Guidance {
    if (fix == null) return Guidance(number, routes.first(), distanceM = null, bearingDeg = null, arrived = false)

    val route = routes.nearestTo(fix.latLng)
    val distance = fix.latLng.distanceTo(route.target)
    val arrivalRadius = if (wasArrived) Guidance.LEAVE_RADIUS_M else Guidance.ARRIVE_RADIUS_M
    return Guidance(
        number = number,
        route = route,
        distanceM = distance,
        bearingDeg = fix.latLng.bearingTo(route.target),
        arrived = distance <= arrivalRadius,
    )
}
