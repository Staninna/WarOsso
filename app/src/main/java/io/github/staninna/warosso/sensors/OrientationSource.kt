package io.github.staninna.warosso.sensors

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import io.github.staninna.warosso.domain.CompassFusion
import io.github.staninna.warosso.domain.LatLng
import io.github.staninna.warosso.domain.North
import io.github.staninna.warosso.domain.Orientation
import io.github.staninna.warosso.domain.Rotation
import io.github.staninna.warosso.domain.isMagneticDisturbance
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emptyFlow
import kotlin.math.sqrt

/** How the phone is held. Headings are magnetic. */
interface OrientationSource {
    val readings: Flow<Orientation>

    /**
     * Turns the heading by [byDeg], clockwise seen from above, and holds it there without the
     * compass, see [North.BY_HAND]. For when the user sees that the compass is wrong.
     */
    fun turn(byDeg: Double)

    /** Undoes every [turn]: north comes from the compass again. */
    fun followCompass()
}

/**
 * The pose from the phone's own sensors.
 *
 * The rotation-vector sensor has the compass in it. Where the phone also has the gyroscope-only
 * game rotation vector, a [CompassFusion] joins the two, and the magnetometer tells it when the
 * field is disturbed. Without that sensor the compass pose stands in for it, so a [turn] is then
 * a fixed correction on the compass.
 *
 * @param near any point in the region the app is used in. The strength of the earth's magnetic
 *   field there is what the magnetometer should read.
 */
class SensorOrientationSource(context: Context, near: LatLng) : OrientationSource {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val compass: Sensor? = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val gyroscope: Sensor? = manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val magnetometer: Sensor? = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    /** The earth's field in microtesla. It changes by a fraction of a percent over a year or a hundred kilometres. */
    private val expectedFieldUt =
        GeomagneticField(near.lat.toFloat(), near.lon.toFloat(), 0f, System.currentTimeMillis()).fieldStrength / 1000f

    /** Sensor events and the user's turns both arrive on the main thread. */
    private val fusion = CompassFusion()

    override fun turn(byDeg: Double) = fusion.turn(byDeg)

    override fun followCompass() = fusion.followCompass()

    /** Empty on a device without the sensor: the view then stays on the map and the arrow hides. */
    override val readings: Flow<Orientation> = if (compass == null) emptyFlow() else callbackFlow {
        // A sensor that was off comes back with a new idea of north, so a turn by hand is void.
        fusion.followCompass()
        val quaternion = FloatArray(4)
        var compassPose: Rotation? = null
        var disturbed = false
        var accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH

        /** The turn from the phone's axes to east, north and up that a rotation sensor reports. */
        fun rotationOf(event: SensorEvent): Rotation {
            SensorManager.getQuaternionFromVector(quaternion, event.values)
            val (w, x, y, z) = quaternion
            return Rotation(w.toDouble(), x.toDouble(), y.toDouble(), z.toDouble())
        }

        fun send(gyro: Rotation, compass: Rotation, timeNanos: Long) {
            val rotation = fusion.update(gyro, compass, disturbed, timeNanos)
            val north = when {
                fusion.byHand -> North.BY_HAND
                accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> North.COMPASS
                else -> North.COMPASS_UNSURE
            }
            trySend(Orientation(rotation, north))
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        val (x, y, z) = event.values
                        disturbed = isMagneticDisturbance(sqrt(x * x + y * y + z * z), expectedFieldUt)
                    }
                    Sensor.TYPE_ROTATION_VECTOR -> rotationOf(event).let {
                        compassPose = it
                        if (gyroscope == null) send(gyro = it, compass = it, timeNanos = event.timestamp)
                    }
                    Sensor.TYPE_GAME_ROTATION_VECTOR -> compassPose?.let {
                        send(gyro = rotationOf(event), compass = it, timeNanos = event.timestamp)
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, newAccuracy: Int) {
                if (sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
                // A compass that has just been calibrated points somewhere new: learn north again.
                if (newAccuracy > accuracy) fusion.relearn()
                accuracy = newAccuracy
            }
        }
        manager.registerListener(listener, compass, SensorManager.SENSOR_DELAY_GAME)
        gyroscope?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        magnetometer?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL) }
        awaitClose { manager.unregisterListener(listener) }
    }.conflate()
}
