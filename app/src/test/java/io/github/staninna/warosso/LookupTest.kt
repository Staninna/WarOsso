package io.github.staninna.warosso

import io.github.staninna.warosso.domain.Fix
import io.github.staninna.warosso.domain.Guidance
import io.github.staninna.warosso.domain.Lookup
import io.github.staninna.warosso.domain.closestNumbers
import io.github.staninna.warosso.domain.guide
import io.github.staninna.warosso.domain.lookup
import io.github.staninna.warosso.domain.nearestTo
import io.github.staninna.warosso.domain.offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LookupTest {
    private val north = CENTER.offset(60.0, 0.0)
    private val east = CENTER.offset(60.0, 90.0)
    private val south = CENTER.offset(60.0, 180.0)

    // Door 171 hangs on a gallery that all three stairwells reach.
    private val data = complexData(
        addresses = listOf(151, 153, 155, 171, 173).map { address(it) },
        entrances = listOf(
            entrance(1, north, 1, listOf(151, 153), listOf(171, 173)),
            entrance(2, east, 1, listOf(), listOf(173, 171)),
            entrance(3, south, 1, listOf(), listOf(171)),
        ),
    )

    @Test
    fun `a number served by several entrances lists them all`() {
        val found = data.lookup(171) as Lookup.Found
        assertEquals(listOf(1L, 2L, 3L), found.routes.map { it.entrance.id })
    }

    @Test
    fun `the nearest entrance wins among several candidates`() {
        val routes = (data.lookup(171) as Lookup.Found).routes
        assertEquals(1L, routes.nearestTo(CENTER.offset(50.0, 10.0)).entrance.id)
        assertEquals(2L, routes.nearestTo(CENTER.offset(50.0, 80.0)).entrance.id)
        assertEquals(3L, routes.nearestTo(CENTER.offset(20.0, 180.0)).entrance.id)
    }

    @Test
    fun `entrances that do not serve the number are never picked, however close`() {
        // Standing on entrance 2, which has no door 151.
        val routes = (data.lookup(151) as Lookup.Found).routes
        assertEquals(1L, routes.nearestTo(east).entrance.id)
    }

    @Test
    fun `the target switches while walking`() {
        val found = data.lookup(171) as Lookup.Found
        val start = found.guide(Fix(CENTER.offset(55.0, 5.0), 4f), wasArrived = false)
        val later = found.guide(Fix(CENTER.offset(55.0, 175.0), 4f), wasArrived = false)
        assertEquals(1L, start.route.entrance.id)
        assertEquals(3L, later.route.entrance.id)
    }

    @Test
    fun `guidance measures distance and bearing to the entrance`() {
        val guidance = (data.lookup(151) as Lookup.Found).guide(Fix(CENTER, 4f), wasArrived = false)
        assertEquals(60.0, guidance.distanceM!!, 0.1)
        assertEquals(0.0, guidance.bearingDeg!!, 0.1)
        assertFalse(guidance.arrived)
    }

    @Test
    fun `without a fix there is a route but no distance`() {
        val guidance = (data.lookup(171) as Lookup.Found).guide(fix = null, wasArrived = false)
        assertEquals(1L, guidance.route.entrance.id)
        assertNull(guidance.distanceM)
        assertNull(guidance.bearingDeg)
    }

    @Test
    fun `arrival starts within 10 m and holds until 15 m`() {
        val found = data.lookup(151) as Lookup.Found
        fun at(metres: Double, wasArrived: Boolean) =
            found.guide(Fix(north.offset(metres, 180.0), 4f), wasArrived).arrived

        assertFalse(at(Guidance.ARRIVE_RADIUS_M + 2, wasArrived = false))
        assertTrue(at(Guidance.ARRIVE_RADIUS_M - 1, wasArrived = false))
        assertTrue(at(Guidance.ARRIVE_RADIUS_M + 2, wasArrived = true))
        assertFalse(at(Guidance.LEAVE_RADIUS_M + 1, wasArrived = true))
    }

    @Test
    fun `a number that is not in BAG suggests its neighbours`() {
        assertEquals(Lookup.Unknown(154, listOf(153, 155)), data.lookup(154))
    }

    @Test
    fun `did you mean looks at the closest number on each side`() {
        val existing = listOf(51, 53, 55, 57, 169)
        assertEquals(listOf(53, 55), closestNumbers(54, existing))
        assertEquals(listOf(57, 169), closestNumbers(100, existing))
        assertEquals(listOf(51), closestNumbers(2, existing))
        assertEquals(listOf(169), closestNumbers(500, existing))
        assertEquals(emptyList<Int>(), closestNumbers(54, emptyList()))
    }

    @Test
    fun `a BAG number without an entrance is not set up yet`() {
        assertEquals(Lookup.NotSetUp(155), data.lookup(155))
    }

    @Test
    fun `addresses on another street or outside the polygon are not in the complex`() {
        val wider = complexData(
            addresses = listOf(
                address(7),
                address(9, street = "Klaverweide"),
                address(11, at = CENTER.offset(400.0, 0.0)),
            ),
        )
        assertEquals(listOf(7), wider.bagNumbers)
        assertEquals(Lookup.Unknown(9, listOf(7)), wider.lookup(9))
    }
}
