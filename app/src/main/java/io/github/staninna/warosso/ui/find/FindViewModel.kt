package io.github.staninna.warosso.ui.find

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.staninna.warosso.data.ComplexRepository
import io.github.staninna.warosso.data.SettingsRepository
import io.github.staninna.warosso.domain.ComplexData
import io.github.staninna.warosso.domain.Fix
import io.github.staninna.warosso.domain.Guidance
import io.github.staninna.warosso.domain.LatLng
import io.github.staninna.warosso.domain.Lookup
import io.github.staninna.warosso.domain.Orientation
import io.github.staninna.warosso.domain.ViewMode
import io.github.staninna.warosso.domain.Walk
import io.github.staninna.warosso.domain.WalkTracker
import io.github.staninna.warosso.domain.afterTilt
import io.github.staninna.warosso.domain.angleDelta
import io.github.staninna.warosso.domain.guide
import io.github.staninna.warosso.domain.lookup
import io.github.staninna.warosso.sensors.ArrivalBuzzer
import io.github.staninna.warosso.sensors.LocationSource
import io.github.staninna.warosso.sensors.LocationState
import io.github.staninna.warosso.sensors.OrientationSource
import io.github.staninna.warosso.sensors.PositionSimulator
import io.github.staninna.warosso.sensors.fix
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.runningReduce
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** What the Find screen shows. Each stage carries exactly what it draws. */
sealed interface FindUiState {
    /** The database is still being read. */
    data object Loading : FindUiState

    /** Nothing is set up yet, so there is no number to walk to. */
    data class Welcome(val data: ComplexData) : FindUiState

    data class Search(val data: ComplexData, val query: String, val miss: Lookup.Miss?) : FindUiState

    data class Guiding(
        val data: ComplexData,
        val guidance: Guidance,
        val location: LocationState,
        /** Tilt switching is off in Settings, so the screen offers a button to switch views. */
        val manualSwitch: Boolean,
    ) : FindUiState
}

/** Guidance from one fix to the next, noting the moment of arrival. */
private data class GuidanceStep(val guidance: Guidance? = null, val justArrived: Boolean = false) {
    fun next(found: Lookup.Found?, fix: Fix?): GuidanceStep {
        val wasArrived = guidance?.arrived == true && guidance.number == found?.number
        val next = found?.guide(fix, wasArrived)
        return GuidanceStep(next, justArrived = next?.arrived == true && !wasArrived)
    }
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class FindViewModel(
    private val complex: ComplexRepository,
    private val settings: SettingsRepository,
    locationSource: LocationSource,
    private val orientationSource: OrientationSource,
    private val buzzer: ArrivalBuzzer,
    private val simulator: PositionSimulator?,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val miss = MutableStateFlow<Lookup.Miss?>(null)
    private val target = MutableStateFlow<Int?>(null)
    private val locationPermitted = MutableStateFlow(false)
    private val manualMode = MutableStateFlow(ViewMode.MAP)

    private val whileOnScreen = SharingStarted.WhileSubscribed(5_000)

    /** GPS only runs while the Find screen is up and the permission is held. */
    private val location: StateFlow<LocationState> = locationPermitted
        .flatMapLatest { permitted -> if (permitted) locationSource.state else flowOf(LocationState.Searching) }
        .stateIn(viewModelScope, whileOnScreen, LocationState.Searching)

    private val guidance: Flow<Guidance?> = combine(complex.data, target, location) { data, number, location ->
        (number?.let { data?.lookup(it) } as? Lookup.Found) to location.fix
    }
        .runningFold(GuidanceStep()) { step, (found, fix) -> step.next(found, fix) }
        .onEach { if (it.justArrived && settings.current.vibrateOnArrival) buzzer.buzz() }
        .map { it.guidance }

    val state: StateFlow<FindUiState> = combine(
        combine(complex.data, query, miss, ::Triple),
        guidance,
        location,
        settings.settings,
    ) { (data, query, miss), guidance, location, settings ->
        when {
            data == null -> FindUiState.Loading
            !data.canGuide -> FindUiState.Welcome(data)
            guidance != null -> FindUiState.Guiding(data, guidance, location, manualSwitch = settings?.autoSwitchView == false)
            else -> FindUiState.Search(data, query, miss)
        }
    }.stateIn(viewModelScope, whileOnScreen, FindUiState.Loading)

    private val readings = orientationSource.readings.shareIn(viewModelScope, whileOnScreen, replay = 1)

    /** How the phone is held, with the heading smoothed and turned to true north. Null without a compass. */
    val pose: StateFlow<Orientation?> = combine(readings, location) { reading, location ->
        reading.withDeclination(location.fix?.declinationDeg ?: 0f)
    }
        .runningReduce { previous, next -> next.smoothedFrom(previous) }
        .stateIn(viewModelScope, whileOnScreen, null)

    /**
     * The walk the last fixes add up to, with whether the heading agrees with it. Null while the
     * user stands or turns.
     */
    val walk: StateFlow<Walk?> = flow {
        val tracker = WalkTracker()
        location.collect { emit(tracker.update(it.fix?.courseDeg, pose.value?.headingDeg)) }
    }.stateIn(viewModelScope, whileOnScreen, null)

    /** The heading for the map's cone. A few updates a second is plenty there. */
    val mapHeading: StateFlow<Float?> = pose
        .sample(MAP_HEADING_INTERVAL_MS)
        .map { it?.headingDeg }
        .stateIn(viewModelScope, whileOnScreen, null)

    val viewMode: StateFlow<ViewMode> = settings.settings
        .map { it?.autoSwitchView != false }
        .distinctUntilChanged()
        .flatMapLatest { auto ->
            if (auto) readings.runningFold(manualMode.value) { mode, reading -> mode.afterTilt(reading.tiltDeg) } else manualMode
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, whileOnScreen, ViewMode.MAP)

    /** Debug position simulation is on: the user's dot on the map can be moved by hand. */
    val canMoveUser: StateFlow<Boolean> = (simulator?.state?.map { it.enabled } ?: flowOf(false))
        .stateIn(viewModelScope, whileOnScreen, false)

    fun onQueryChange(text: String) {
        query.value = text.filter(Char::isDigit).take(MAX_DIGITS)
        miss.value = null
    }

    fun submit() {
        query.value.toIntOrNull()?.let(::find)
    }

    fun find(number: Int) {
        query.value = number.toString()
        val result = complex.data.value?.lookup(number) ?: return
        miss.value = result as? Lookup.Miss
        target.value = number.takeIf { result is Lookup.Found }
    }

    fun stop() {
        target.value = null
        query.value = ""
    }

    fun setLocationPermitted(permitted: Boolean) {
        locationPermitted.value = permitted
    }

    fun toggleViewMode() {
        manualMode.update { if (viewMode.value == ViewMode.AR) ViewMode.MAP else ViewMode.AR }
    }

    /** Turns the heading by hand, clockwise. The compass is left out from then on. */
    fun turnHeading(byDeg: Float) = orientationSource.turn(byDeg.toDouble())

    /** Sets the heading to the direction the user walks in. They are asked to point the phone that way first. */
    fun alignHeadingToWalk() {
        val course = walk.value?.courseDeg ?: return
        val heading = pose.value?.headingDeg ?: return
        orientationSource.turn(angleDelta(heading.toDouble(), course))
    }

    fun followCompass() = orientationSource.followCompass()

    /** Puts the simulated user at [point]. Does nothing unless simulation is on. */
    fun moveSimulatedUser(point: LatLng) {
        simulator?.update { if (it.enabled) it.copy(latLng = point) else it }
    }

    private companion object {
        const val MAX_DIGITS = 4
        const val MAP_HEADING_INTERVAL_MS = 150L
    }
}
