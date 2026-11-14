package io.github.staninna.warosso

import io.github.staninna.warosso.domain.CompassFusion
import io.github.staninna.warosso.domain.LatLng
import io.github.staninna.warosso.domain.Orientation
import io.github.staninna.warosso.domain.PositionFilter
import io.github.staninna.warosso.domain.PositionReading
import io.github.staninna.warosso.domain.Rotation
import io.github.staninna.warosso.domain.Vec3
import io.github.staninna.warosso.domain.angleDelta
import io.github.staninna.warosso.domain.distanceTo
import io.github.staninna.warosso.domain.isMagneticDisturbance
import io.github.staninna.warosso.domain.offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SensingTest {
    private val home = LatLng(51.5978, 4.8008)

    private fun reading(at: LatLng, second: Int, speedMps: Float? = 0f, courseDeg: Float? = null, accuracyM: Float = 4f) =
        PositionReading(at, accuracyM, speedMps, courseDeg, timeMs = second * 1000L)

    @Test
    fun `the first fix is taken as it is`() {
        assertEquals(home, PositionFilter().update(reading(home, second = 0)))
    }

    @Test
    fun `standing still, the position dances far less than the fixes do`() {
        val filter = PositionFilter()
        // Fixes four metres east and west of the truth, in turn.
        val errors = (0 until 40).map { second ->
            val fix = home.offset(4.0, if (second % 2 == 0) 90.0 else 270.0)
            filter.update(reading(fix, second)).distanceTo(home)
        }
        assertTrue("settled errors: ${errors.takeLast(20)}", errors.takeLast(20).all { it < 1.0 })
    }

    @Test
    fun `walking, the position keeps up with exact fixes`() {
        val filter = PositionFilter()
        for (second in 0 until 30) {
            val truth = home.offset(1.4 * second, 0.0)
            val estimate = filter.update(reading(truth, second, speedMps = 1.4f, courseDeg = 0f))
            assertEquals("second $second", 0.0, estimate.distanceTo(truth), 0.01)
        }
    }

    @Test
    fun `walking, sideways noise is damped without falling behind`() {
        val filter = PositionFilter()
        val errors = (0 until 40).map { second ->
            val truth = home.offset(1.4 * second, 0.0)
            val fix = truth.offset(4.0, if (second % 2 == 0) 90.0 else 270.0)
            filter.update(reading(fix, second, speedMps = 1.4f, courseDeg = 0f)).distanceTo(truth)
        }
        assertTrue("settled errors: ${errors.takeLast(20)}", errors.takeLast(20).all { it < 1.5 })
    }

    @Test
    fun `one wild fix is ignored, a lasting move is followed`() {
        val filter = PositionFilter()
        for (second in 0 until 10) filter.update(reading(home, second))
        val away = home.offset(60.0, 45.0)

        assertEquals(0.0, filter.update(reading(away, second = 10)).distanceTo(home), 0.5)
        assertEquals(0.0, filter.update(reading(home, second = 11)).distanceTo(home), 0.5)

        filter.update(reading(away, second = 12))
        filter.update(reading(away, second = 13))
        assertEquals(away, filter.update(reading(away, second = 14)))
    }

    @Test
    fun `after a long silence the next fix starts over`() {
        val filter = PositionFilter()
        for (second in 0 until 10) filter.update(reading(home, second))
        val elsewhere = home.offset(8.0, 180.0)
        assertEquals(elsewhere, filter.update(reading(elsewhere, second = 60)))
    }

    /** A phone facing [headingDeg], as the compass sensor and as the gyroscope sensor with its own idea of north report it. */
    private fun compassPose(headingDeg: Double) = Orientation.of(headingDeg.toFloat(), tiltDeg = 75f).rotation
    private fun gyroPose(headingDeg: Double, gyroNorthDeg: Double = 37.0) = Rotation.about(Vec3.UP, gyroNorthDeg) * compassPose(headingDeg)

    private fun headingOf(rotation: Rotation) = Orientation(rotation, reliable = true).headingDeg.toDouble()

    /** Feeds [seconds] of readings at 50 a second and returns the last heading. */
    private fun CompassFusion.run(
        fromSecond: Double,
        seconds: Double,
        disturbed: Boolean = false,
        gyroHeading: (Double) -> Double,
        gyroNorth: (Double) -> Double = { 37.0 },
        compassHeading: (Double) -> Double,
    ): Double {
        var heading = Double.NaN
        var t = fromSecond
        while (t < fromSecond + seconds) {
            heading = headingOf(update(gyroPose(gyroHeading(t), gyroNorth(t)), compassPose(compassHeading(t)), disturbed, (t * 1e9).toLong()))
            t += 0.02
        }
        return heading
    }

    @Test
    fun `the first reading takes its north from the compass`() {
        val fused = CompassFusion().update(gyroPose(120.0), compassPose(120.0), disturbed = false, timeNanos = 0)
        assertEquals(120.0, headingOf(fused), 0.01)
        assertEquals(75f, Orientation(fused, reliable = true).tiltDeg, 0.01f)
    }

    @Test
    fun `a turn of the phone shows at once`() {
        val fusion = CompassFusion()
        fusion.run(0.0, 5.0, gyroHeading = { 10.0 }, compassHeading = { 10.0 })
        // The gyroscope has turned, the compass has not caught up yet.
        val fused = fusion.update(gyroPose(100.0), compassPose(10.0), disturbed = false, timeNanos = 5_020_000_000)
        assertEquals(100.0, headingOf(fused), 1.0)
    }

    @Test
    fun `a compass that swings for a moment hardly moves the heading`() {
        val fusion = CompassFusion()
        fusion.run(0.0, 20.0, gyroHeading = { 200.0 }, compassHeading = { 200.0 })
        val swung = fusion.run(20.0, 2.0, gyroHeading = { 200.0 }, compassHeading = { 230.0 })
        assertTrue("moved to $swung", abs(angleDelta(200.0, swung)) < 6.0)
    }

    @Test
    fun `a disturbed field moves nothing at all`() {
        val fusion = CompassFusion()
        fusion.run(0.0, 20.0, gyroHeading = { 200.0 }, compassHeading = { 200.0 })
        val swung = fusion.run(20.0, 30.0, disturbed = true, gyroHeading = { 200.0 }, compassHeading = { 260.0 })
        assertEquals(200.0, swung, 0.01)
    }

    @Test
    fun `gyroscope drift is corrected as it comes`() {
        val fusion = CompassFusion()
        // The gyroscope's north creeps by a tenth of a degree a second for two minutes.
        val heading = fusion.run(0.0, 120.0, gyroHeading = { 80.0 }, gyroNorth = { 37.0 + 0.1 * it }, compassHeading = { 80.0 })
        assertEquals(80.0, heading, 1.5)
    }

    @Test
    fun `after a calibration the new north is learned fast`() {
        val fusion = CompassFusion()
        fusion.run(0.0, 20.0, gyroHeading = { 300.0 }, compassHeading = { 300.0 })
        fusion.reset()
        val heading = fusion.run(20.0, 1.0, gyroHeading = { 300.0 }, compassHeading = { 320.0 })
        assertEquals(320.0, heading, 1.0)
    }

    @Test
    fun `a field far from the earth's own counts as disturbed`() {
        assertFalse(isMagneticDisturbance(measuredUt = 51f, expectedUt = 49f))
        assertTrue(isMagneticDisturbance(measuredUt = 60f, expectedUt = 49f))
        assertTrue(isMagneticDisturbance(measuredUt = 38f, expectedUt = 49f))
    }
}
