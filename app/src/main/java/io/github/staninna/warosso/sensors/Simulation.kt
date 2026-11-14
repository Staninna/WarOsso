package io.github.staninna.warosso.sensors

import io.github.staninna.warosso.domain.Complex
import io.github.staninna.warosso.domain.LatLng
import io.github.staninna.warosso.domain.Orientation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** What the debug menu fakes. A null override leaves the real sensor in charge. */
data class Simulation(
    val enabled: Boolean = false,
    val latLng: LatLng = Complex.DEFAULT_CENTER,
    val accuracyM: Float = 4f,
    val headingDeg: Float? = null,
    val tiltDeg: Float? = null,
    val compassReliable: Boolean = true,
)

/**
 * Debug builds only: a fake position and heading that enter the app through
 * the same [LocationSource] and [OrientationSource] the real sensors use.
 */
class PositionSimulator {
    val state = MutableStateFlow(Simulation())

    fun update(change: (Simulation) -> Simulation) {
        state.value = change(state.value)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SimulatedLocationSource(real: LocationSource, simulator: PositionSimulator) : LocationSource {
    override val state: Flow<LocationState> = simulator.state
        .map { it.enabled }
        .distinctUntilChanged()
        .flatMapLatest { enabled ->
            if (enabled) simulator.state.map { LocationState.Located(fixAt(it.latLng, it.accuracyM)) } else real.state
        }
}

class SimulatedOrientationSource(real: OrientationSource, simulator: PositionSimulator) : OrientationSource {
    /** Starts from a flat, north-facing phone, so the overrides also work where the sensor is missing. */
    override val readings: Flow<Orientation> = combine(
        real.readings.onStart { emit(Orientation.of(headingDeg = 0f, tiltDeg = 0f)) },
        simulator.state,
    ) { reading, simulation ->
        val reliable = simulation.compassReliable && reading.reliable
        when {
            !simulation.enabled -> reading
            simulation.headingDeg == null && simulation.tiltDeg == null -> reading.copy(reliable = reliable)
            // A faked heading or tilt replaces the whole pose: the phone is then held without roll.
            else -> Orientation.of(
                headingDeg = simulation.headingDeg ?: reading.headingDeg,
                tiltDeg = simulation.tiltDeg ?: reading.tiltDeg,
                reliable = reliable,
            )
        }
    }
}
