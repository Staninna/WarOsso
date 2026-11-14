package io.github.staninna.warosso

import io.github.staninna.warosso.domain.ArSubject
import io.github.staninna.warosso.domain.Side
import io.github.staninna.warosso.domain.ViewMode
import io.github.staninna.warosso.domain.afterTilt
import io.github.staninna.warosso.domain.layoutArLabels
import io.github.staninna.warosso.domain.orientationFromRotationMatrix
import io.github.staninna.warosso.domain.screenX
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrientationTest {
    // Rotation matrices map device axes to world axes (x east, y north, z up), row-major.
    private val flatFacingNorth = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    private val flatFacingEast = floatArrayOf(0f, 1f, 0f, -1f, 0f, 0f, 0f, 0f, 1f)
    // Upright: top edge to the sky, screen towards the user, camera looking north.
    private val uprightFacingNorth = floatArrayOf(1f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f)
    private val uprightFacingEast = floatArrayOf(0f, 0f, -1f, -1f, 0f, 0f, 0f, 1f, 0f)

    @Test
    fun `a flat phone points where its top edge points`() {
        val north = orientationFromRotationMatrix(flatFacingNorth, reliable = true)
        assertEquals(0f, north.headingDeg, 0.01f)
        assertEquals(0f, north.tiltDeg, 0.01f)
        assertEquals(90f, orientationFromRotationMatrix(flatFacingEast, reliable = true).headingDeg, 0.01f)
    }

    @Test
    fun `an upright phone points where its camera looks`() {
        val north = orientationFromRotationMatrix(uprightFacingNorth, reliable = true)
        assertEquals(0f, north.headingDeg, 0.01f)
        assertEquals(90f, north.tiltDeg, 0.01f)
        assertEquals(0f, north.elevationDeg, 0.01f)
        assertEquals(90f, orientationFromRotationMatrix(uprightFacingEast, reliable = false).headingDeg, 0.01f)
    }

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
        assertEquals(0.5f, screenX(bearingDeg = 30.0, headingDeg = 30.0, horizontalFovDeg = 60.0), 1e-6f)
        assertEquals(1f, screenX(bearingDeg = 60.0, headingDeg = 30.0, horizontalFovDeg = 60.0), 1e-4f)
        assertEquals(0f, screenX(bearingDeg = 350.0, headingDeg = 20.0, horizontalFovDeg = 60.0), 1e-4f)
        assertEquals(Float.POSITIVE_INFINITY, screenX(bearingDeg = 180.0, headingDeg = 30.0, horizontalFovDeg = 60.0))
    }

    @Test
    fun `overlapping labels stack with the nearest lowest and drawn last`() {
        val labels = layoutArLabels(
            subjects = listOf(
                ArSubject(1, bearingDeg = 0.0, distanceM = 80.0, isTarget = false),
                ArSubject(2, bearingDeg = 2.0, distanceM = 30.0, isTarget = false),
                ArSubject(3, bearingDeg = 25.0, distanceM = 50.0, isTarget = false),
            ),
            headingDeg = 0.0,
            horizontalFovDeg = 60.0,
            labelWidth = 0.3f,
        )
        assertEquals(listOf(1L, 3L, 2L), labels.map { it.id })
        val byId = labels.associateBy { it.id }
        assertEquals(0, byId.getValue(2).row)
        assertEquals(1, byId.getValue(1).row)
        assertEquals(0, byId.getValue(3).row)
    }

    @Test
    fun `far labels are smaller and fainter`() {
        val (far, near) = layoutArLabels(
            subjects = listOf(
                ArSubject(1, bearingDeg = -20.0, distanceM = 10.0, isTarget = false),
                ArSubject(2, bearingDeg = 20.0, distanceM = 200.0, isTarget = false),
            ),
            headingDeg = 0.0,
            horizontalFovDeg = 60.0,
            labelWidth = 0.2f,
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
            headingDeg = 0.0,
            horizontalFovDeg = 60.0,
            labelWidth = 0.3f,
        )
        val target = labels.single { it.isTarget }
        assertEquals(Side.RIGHT, target.offScreen)
        assertEquals(0.83f, target.x, 1e-6f)
        assertEquals(1f, target.alpha, 1e-6f)
        assertEquals(listOf(1L, 3L), labels.map { it.id })
        assertNull(labels.single { it.id == 3L }.offScreen)
    }
}
