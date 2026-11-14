package io.github.staninna.warosso.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import io.github.staninna.warosso.domain.Orientation
import io.github.staninna.warosso.domain.orientationFromRotationMatrix
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emptyFlow

/** How the phone is held, from the rotation-vector sensor. Headings are magnetic. */
interface OrientationSource {
    val readings: Flow<Orientation>
}

class SensorOrientationSource(context: Context) : OrientationSource {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    /** Empty on a device without the sensor: the view then stays on the map and the arrow hides. */
    override val readings: Flow<Orientation> = if (sensor == null) emptyFlow() else callbackFlow {
        val matrix = FloatArray(9)
        var accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                trySend(orientationFromRotationMatrix(matrix, reliable = accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM))
            }

            override fun onAccuracyChanged(sensor: Sensor, newAccuracy: Int) {
                accuracy = newAccuracy
            }
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { manager.unregisterListener(listener) }
    }.conflate()
}
