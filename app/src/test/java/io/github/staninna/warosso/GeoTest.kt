package io.github.staninna.warosso

import io.github.staninna.warosso.domain.LatLng
import io.github.staninna.warosso.domain.angleDelta
import io.github.staninna.warosso.domain.bearingTo
import io.github.staninna.warosso.domain.circlePolygon
import io.github.staninna.warosso.domain.distanceTo
import io.github.staninna.warosso.domain.distanceToEdge
import io.github.staninna.warosso.domain.isInside
import io.github.staninna.warosso.domain.offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {
    private val grotemarkt = LatLng(51.588770, 4.775560)

    @Test
    fun `distance between two known points in Breda`() {
        // Complex centre to Grote Markt, checked against an online haversine calculator: 2.01 km.
        assertEquals(2010.0, CENTER.distanceTo(grotemarkt), 15.0)
    }

    @Test
    fun `distance is zero to itself and symmetric`() {
        assertEquals(0.0, CENTER.distanceTo(CENTER), 1e-9)
        assertEquals(CENTER.distanceTo(grotemarkt), grotemarkt.distanceTo(CENTER), 1e-6)
    }

    @Test
    fun `one degree of latitude is about 111 km`() {
        assertEquals(111_195.0, LatLng(51.0, 4.8).distanceTo(LatLng(52.0, 4.8)), 10.0)
    }

    @Test
    fun `bearing to the four compass points`() {
        assertEquals(0.0, CENTER.bearingTo(CENTER.copy(lat = CENTER.lat + 0.001)), 0.01)
        assertEquals(90.0, CENTER.bearingTo(CENTER.copy(lon = CENTER.lon + 0.001)), 0.01)
        assertEquals(180.0, CENTER.bearingTo(CENTER.copy(lat = CENTER.lat - 0.001)), 0.01)
        assertEquals(270.0, CENTER.bearingTo(CENTER.copy(lon = CENTER.lon - 0.001)), 0.01)
    }

    @Test
    fun `bearing to Grote Markt is south-west`() {
        assertEquals(240.0, CENTER.bearingTo(grotemarkt), 2.0)
    }

    @Test
    fun `offset lands at the asked distance and bearing`() {
        val moved = CENTER.offset(50.0, 135.0)
        assertEquals(50.0, CENTER.distanceTo(moved), 0.01)
        assertEquals(135.0, CENTER.bearingTo(moved), 0.01)
    }

    @Test
    fun `angle delta takes the short way round`() {
        assertEquals(20.0, angleDelta(350.0, 10.0), 1e-9)
        assertEquals(-20.0, angleDelta(10.0, 350.0), 1e-9)
        assertEquals(-180.0, angleDelta(0.0, 180.0), 1e-9)
        assertEquals(0.0, angleDelta(123.0, 123.0), 1e-9)
    }

    @Test
    fun `the default circle holds points inside 150 m and not beyond`() {
        val circle = circlePolygon(CENTER, 150.0)
        assertTrue(CENTER.isInside(circle))
        assertTrue(CENTER.offset(140.0, 77.0).isInside(circle))
        assertFalse(CENTER.offset(160.0, 77.0).isInside(circle))
    }

    @Test
    fun `distance to the edge of a square`() {
        val square = building(1, sizeM = 20.0).outline
        assertEquals(10.0, CENTER.distanceToEdge(square), 0.05)
        assertEquals(5.0, CENTER.offset(15.0, 0.0).distanceToEdge(square), 0.05)
    }
}
