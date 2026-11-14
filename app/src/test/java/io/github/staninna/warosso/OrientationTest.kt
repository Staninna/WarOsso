package io.github.staninna.warosso

import io.github.staninna.warosso.domain.ArCamera
import io.github.staninna.warosso.domain.ArSubject
import io.github.staninna.warosso.domain.FieldOfView
import io.github.staninna.warosso.domain.Orientation
import io.github.staninna.warosso.domain.Rotation
import io.github.staninna.warosso.domain.Side
import io.github.staninna.warosso.domain.Vec3
import io.github.staninna.warosso.domain.ViewMode
import io.github.staninna.warosso.domain.afterTilt
import io.github.staninna.warosso.domain.layoutArLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.math.tan

class OrientationTest {
    private val half = sqrt(0.5)

    // Quaternions as the rotation-vector sensor reports them: phone axes to east, north and up.
    private val flatFacingNorth = Rotation(1.0, 0.0, 0.0, 0.0)
    private val flatFacingEast = Rotation(half, 0.0, 0.0, -half)

    // Upright: top edge to the sky, screen towards the user, camera looking north.
    private val uprightFacingNorth = Rotation(half, half, 0.0, 0.0)
    private val uprightFacingEast = Rotation(0.5, 0.5, -0.5, -0.5)

    private val fieldOfView = FieldOfView(horizontalDeg = 60.0, verticalDeg = 90.0)
    private val wholeView = 0f..1f

    private fun camera(headingDeg: Float = 0f, tiltDeg: Float = 90f) = ArCamera(Orientation.of(headingDeg, tiltDeg), fieldOfView)

    private fun assertDirection(expected: Vec3, actual: Vec3) {
        assertEquals(expected.x, actual.x, 1e-9)
        assertEquals(expected.y, actual.y, 1e-9)
        assertEquals(expected.z, actual.z, 1e-9)
    }

    @Test
    fun `a rotation turns the phone's axes to where they point in the world`() {
        // Upright and facing east: the right edge points south, the top edge up, the screen west.
        assertDirection(-Vec3.NORTH, uprightFacingEast.rotate(Vec3.EAST))
        assertDirection(Vec3.UP, uprightFacingEast.rotate(Vec3.NORTH))
        assertDirection(-Vec3.EAST, uprightFacingEast.rotate(Vec3.UP))
    }

    @Test
    fun `a flat phone points where its top edge points`() {
        val north = Orientation(flatFacingNorth, reliable = true)
        assertEquals(0f, north.headingDeg, 0.01f)
        assertEquals(0f, north.tiltDeg, 0.01f)
        assertEquals(90f, Orientation(flatFacingEast, reliable = true).headingDeg, 0.01f)
    }

    @Test
    fun `an upright phone points where its camera looks`() {
        val north = Orientation(uprightFacingNorth, reliable = true)
        assertEquals(0f, north.headingDeg, 0.01f)
        assertEquals(90f, north.tiltDeg, 0.01f)
        assertEquals(0f, north.elevationDeg, 0.01f)
        assertEquals(90f, Orientation(uprightFacingEast, reliable = false).headingDeg, 0.01f)
    }

    @Test
    fun `a pose built from heading and tilt reads back the same`() {
        val pose = Orientation.of(headingDeg = 250f, tiltDeg = 60f)
        assertEquals(250f, pose.headingDeg, 0.01f)
        assertEquals(60f, pose.tiltDeg, 0.01f)
        assertEquals(-30f, pose.elevationDeg, 0.01f)
        assertEquals(20f, Orientation.of(headingDeg = 20f, tiltDeg = 10f).headingDeg, 0.01f)
    }

    @Test
    fun `declination turns the heading and leaves the tilt alone`() {
        val trueNorth = Orientation.of(headingDeg = 358f, tiltDeg = 70f).withDeclination(5f)
        assertEquals(3f, trueNorth.headingDeg, 0.01f)
        assertEquals(70f, trueNorth.tiltDeg, 0.01f)
    }

    @Test
    fun `smoothing eases the whole pose along the short way round`() {
        val eased = Orientation.of(headingDeg = 10f, tiltDeg = 80f).smoothedFrom(Orientation.of(headingDeg = 350f, tiltDeg = 80f), factor = 0.5)
        assertEquals(0f, angleFromNorth(eased.headingDeg), 0.05f)

        val tipped = Orientation.of(headingDeg = 90f, tiltDeg = 90f).smoothedFrom(Orientation.of(headingDeg = 90f, tiltDeg = 70f), factor = 0.5)
        assertEquals(80f, tipped.tiltDeg, 0.01f)
        assertEquals(90f, tipped.headingDeg, 0.01f)
    }

    private fun angleFromNorth(headingDeg: Float) = if (headingDeg > 180f) headingDeg - 360f else headingDeg

    @Test
    fun `tilt switches the view with hysteresis`() {
        assertEquals(ViewMode.AR, ViewMode.MAP.afterTilt(70f))
        assertEquals(ViewMode.MAP, ViewMode.AR.afterTilt(20f))
        // In the band between the thresholds nothing flips, from either side.
        assertEquals(ViewMode.MAP, ViewMode.MAP.afterTilt(45f))
        assertEquals(ViewMode.AR, ViewMode.AR.afterTilt(45f))
    }

    @Test
    fun `straight ahead is the middle of the screen`() {
        val ahead = camera(headingDeg = 30f).mark(bearingDeg = 30.0, band = wholeView)
        assertEquals(0.5f, ahead.x, 1e-6f)
        assertEquals(0.5f, ahead.y, 1e-6f)
        assertEquals(1f, camera(headingDeg = 30f).mark(60.0, wholeView).x, 1e-4f)
        assertEquals(0f, camera(headingDeg = 20f).mark(350.0, wholeView).x, 1e-4f)
        assertEquals(Float.POSITIVE_INFINITY, camera(headingDeg = 30f).mark(180.0, wholeView).x)
        assertEquals(Float.NEGATIVE_INFINITY, camera(headingDeg = 30f).mark(-100.0, wholeView).x)
    }

    @Test
    fun `tipping the phone down moves the horizon up and spreads the marks`() {
        // 30 degrees down from upright.
        val tipped = camera(tiltDeg = 60f)
        val level = camera().mark(15.0, wholeView)
        val mark = tipped.mark(15.0, wholeView)

        val horizon = 0.5 - tan(Math.toRadians(30.0)) / tan(Math.toRadians(45.0)) / 2
        assertEquals(horizon.toFloat(), mark.y, 1e-5f)
        assertEquals(horizon.toFloat(), tipped.mark(0.0, wholeView).y, 1e-5f)
        // The same direction lies further from the middle than with the phone upright.
        val spread = (mark.x - 0.5) / (level.x - 0.5)
        assertEquals(1 / cos(Math.toRadians(30.0)), spread, 1e-4)
    }

    @Test
    fun `a horizon outside the band is followed down to the band's edge`() {
        // 60 degrees down: the horizon is far above the top of the view.
        val steep = camera(tiltDeg = 30f)
        val band = 0.25f..0.75f

        val ahead = steep.mark(0.0, band)
        assertEquals(0.5f, ahead.x, 1e-6f)
        assertEquals(0.25f, ahead.y, 1e-6f)

        // Off to the right the mark stays on the ground line of its direction, which runs towards
        // the middle as it nears the user's feet.
        val right = steep.mark(20.0, band)
        assertEquals(0.25f, right.y, 1e-6f)
        assertTrue(right.x > 0.5f)
        assertTrue(right.x < camera().mark(20.0, wholeView).x)
    }

    @Test
    fun `a direction behind the phone rests on the horizon ahead`() {
        val behind = camera(tiltDeg = 70f).mark(170.0, 0.4f..0.9f)
        assertEquals(Float.POSITIVE_INFINITY, behind.x)
        // 20 degrees down puts the horizon above the band, so the band's top stands in.
        assertEquals(0.4f, behind.y, 1e-6f)
        assertEquals(0.5f, camera().mark(170.0, wholeView).y, 1e-6f)
    }

    @Test
    fun `rolling the phone tilts the horizon across the screen`() {
        // Upright, facing north, rolled 10 degrees clockwise as the user sees it.
        val rolled = Rotation.about(Vec3.NORTH, 10.0) * uprightFacingNorth
        val camera = ArCamera(Orientation(rolled, reliable = true), fieldOfView)
        assertTrue(camera.mark(-20.0, wholeView).y > 0.5f)
        assertEquals(0.5f, camera.mark(0.0, wholeView).y, 1e-6f)
        assertTrue(camera.mark(20.0, wholeView).y < 0.5f)
    }

    @Test
    fun `overlapping labels stack with the nearest lowest and drawn last`() {
        val labels = layoutArLabels(
            subjects = listOf(
                ArSubject(1, bearingDeg = 0.0, distanceM = 80.0, isTarget = false),
                ArSubject(2, bearingDeg = 2.0, distanceM = 30.0, isTarget = false),
                ArSubject(3, bearingDeg = 25.0, distanceM = 50.0, isTarget = false),
            ),
            camera = camera(),
            labelWidth = 0.3f,
            band = wholeView,
        )
        assertEquals(listOf(1L, 3L, 2L), labels.map { it.id })
        val byId = labels.associateBy { it.id }
        assertEquals(0, byId.getValue(2).row)
        assertEquals(1, byId.getValue(1).row)
        assertEquals(0, byId.getValue(3).row)
        assertTrue(labels.all { it.y == 0.5f && it.markX == it.x })
    }

    @Test
    fun `far labels are smaller and fainter`() {
        val (far, near) = layoutArLabels(
            subjects = listOf(
                ArSubject(1, bearingDeg = -20.0, distanceM = 10.0, isTarget = false),
                ArSubject(2, bearingDeg = 20.0, distanceM = 200.0, isTarget = false),
            ),
            camera = camera(),
            labelWidth = 0.2f,
            band = wholeView,
        )
        assertEquals(1f, near.scale, 1e-6f)
        assertEquals(1f, near.alpha, 1e-6f)
        assertTrue(far.scale < near.scale && far.alpha < near.alpha)
    }

    @Test
    fun `the target stays on screen, others out of view are dropped`() {
        val labels = layoutArLabels(
            subjects = listOf(
                ArSubject(1, bearingDeg = 170.0, distanceM = 300.0, isTarget = true),
                ArSubject(2, bearingDeg = 200.0, distanceM = 20.0, isTarget = false),
                ArSubject(3, bearingDeg = 0.0, distanceM = 20.0, isTarget = false),
            ),
            camera = camera(),
            labelWidth = 0.3f,
            band = wholeView,
        )
        val target = labels.single { it.isTarget }
        assertEquals(Side.RIGHT, target.offScreen)
        assertEquals(0.83f, target.x, 1e-6f)
        assertEquals(Float.POSITIVE_INFINITY, target.markX)
        assertEquals(0.5f, target.y, 1e-6f)
        assertEquals(1f, target.alpha, 1e-6f)
        assertEquals(listOf(1L, 3L), labels.map { it.id })
        assertNull(labels.single { it.id == 3L }.offScreen)
    }
}
