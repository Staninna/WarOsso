package io.github.staninna.warosso

import android.content.Context
import androidx.room.Room
import io.github.staninna.warosso.data.ComplexRepository
import io.github.staninna.warosso.data.Documents
import io.github.staninna.warosso.data.OfflineTiles
import io.github.staninna.warosso.data.OverpassClient
import io.github.staninna.warosso.data.SettingsRepository
import io.github.staninna.warosso.data.WarOssoDatabase
import io.github.staninna.warosso.data.settingsStore
import io.github.staninna.warosso.domain.Complex
import io.github.staninna.warosso.sensors.ArrivalBuzzer
import io.github.staninna.warosso.sensors.LocationSource
import io.github.staninna.warosso.sensors.OrientationSource
import io.github.staninna.warosso.sensors.PlatformLocationSource
import io.github.staninna.warosso.sensors.PositionSimulator
import io.github.staninna.warosso.sensors.SensorOrientationSource
import io.github.staninna.warosso.sensors.SimulatedLocationSource
import io.github.staninna.warosso.sensors.SimulatedOrientationSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

/** Everything the screens share, wired by hand. One instance lives in [WarOssoApp]. */
class AppContainer(context: Context) {
    /** Outlives every screen. The repositories write in it, so a save started on the way out still finishes. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val database = Room.databaseBuilder(context, WarOssoDatabase::class.java, WarOssoDatabase.FILE_NAME).build()

    val complex = ComplexRepository(database, appScope) { context.readAsset("seed.json") }
    val settings = SettingsRepository(context.settingsStore, appScope)
    val overpass = OverpassClient(queryTemplate = context.readAsset("overpass.ql"))

    /** Present in debug builds only; R8 drops the simulation classes from release builds. */
    val simulator: PositionSimulator? = if (BuildConfig.DEBUG) PositionSimulator() else null

    val location: LocationSource = PlatformLocationSource(context).let { real ->
        simulator?.let { SimulatedLocationSource(real, it) } ?: real
    }
    val orientation: OrientationSource = SensorOrientationSource(context, near = Complex.DEFAULT_CENTER).let { real ->
        simulator?.let { SimulatedOrientationSource(real, it) } ?: real
    }

    val buzzer = ArrivalBuzzer(context)
    val documents = Documents(context.contentResolver)
    private val offlineTiles = OfflineTiles(context)

    init {
        // The map tiles of the complex are kept on the phone, and fetched again when its outline moves.
        appScope.launch {
            complex.data.mapNotNull { it?.complex?.polygon }.distinctUntilChanged().collect(offlineTiles::cache)
        }
    }

    private fun Context.readAsset(name: String): String = assets.open(name).bufferedReader().use { it.readText() }
}
