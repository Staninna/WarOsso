package io.github.staninna.warosso.sensors

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.GeomagneticField
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.os.SystemClock
import io.github.staninna.warosso.domain.Fix
import io.github.staninna.warosso.domain.LatLng
import io.github.staninna.warosso.domain.PositionFilter
import io.github.staninna.warosso.domain.PositionReading
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

sealed interface LocationState {
    /** Location is switched off for the whole phone. */
    data object Off : LocationState

    /** Listening, no fix yet. */
    data object Searching : LocationState

    data class Located(val fix: Fix) : LocationState
}

/** The current fix, or null while there is none. */
val LocationState.fix: Fix? get() = (this as? LocationState.Located)?.fix

/** Where the phone is. Collect only while the app holds the fine-location permission. */
interface LocationSource {
    val state: Flow<LocationState>
}

/** A fix at [latLng] with the magnetic declination of that spot, so headings can be made true. */
fun fixAt(latLng: LatLng, accuracyM: Float, altitudeM: Float = 0f): Fix {
    val field = GeomagneticField(latLng.lat.toFloat(), latLng.lon.toFloat(), altitudeM, System.currentTimeMillis())
    return Fix(latLng, accuracyM, field.declination)
}

/**
 * GPS through the platform [LocationManager], with the platform's fused provider as a fallback
 * for when GPS has gone quiet. No Play Services. Every fix passes a [PositionFilter].
 */
class PlatformLocationSource(context: Context) : LocationSource {
    private val manager = context.getSystemService(LocationManager::class.java)
    private val executor = context.mainExecutor

    @SuppressLint("MissingPermission") // The Find screen collects this only after the permission is granted.
    override val state: Flow<LocationState> = callbackFlow {
        // Starts as "silent for long enough", so the fused provider can deliver the very first fix.
        var lastGpsFixAt = SystemClock.elapsedRealtime() - GPS_SILENCE_MS
        val filter = PositionFilter()

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val now = SystemClock.elapsedRealtime()
                val fromGps = location.provider == LocationManager.GPS_PROVIDER
                if (fromGps) lastGpsFixAt = now
                // The fused provider only fills in when GPS has been silent for a while.
                if (!fromGps && now - lastGpsFixAt < GPS_SILENCE_MS) return
                val reading = PositionReading(
                    latLng = LatLng(location.latitude, location.longitude),
                    accuracyM = location.accuracy,
                    speedMps = location.speed.takeIf { location.hasSpeed() },
                    courseDeg = location.bearing.takeIf { location.hasBearing() },
                    timeMs = location.elapsedRealtimeNanos / 1_000_000,
                )
                trySend(LocationState.Located(fixAt(filter.update(reading), location.accuracy, location.altitude.toFloat())))
            }

            override fun onProviderDisabled(provider: String) {
                if (!manager.isLocationEnabled) trySend(LocationState.Off)
            }

            override fun onProviderEnabled(provider: String) {
                trySend(LocationState.Searching)
            }
        }

        trySend(if (manager.isLocationEnabled) LocationState.Searching else LocationState.Off)
        val request = LocationRequest.Builder(UPDATE_INTERVAL_MS)
            .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
            .build()
        listOf(LocationManager.GPS_PROVIDER, LocationManager.FUSED_PROVIDER)
            .filter(manager::hasProvider)
            .forEach { manager.requestLocationUpdates(it, request, executor, listener) }

        awaitClose { manager.removeUpdates(listener) }
    }.conflate()

    private companion object {
        const val UPDATE_INTERVAL_MS = 1_000L
        const val GPS_SILENCE_MS = 5_000L
    }
}
