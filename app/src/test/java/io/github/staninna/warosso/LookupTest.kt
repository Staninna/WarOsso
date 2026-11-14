package io.github.staninna.warosso

import io.github.staninna.warosso.domain.DoorHint
import io.github.staninna.warosso.domain.Fix
import io.github.staninna.warosso.domain.FloorDoors
import io.github.staninna.warosso.domain.Guidance
import io.github.staninna.warosso.domain.Lookup
import io.github.staninna.warosso.domain.Route
import io.github.staninna.warosso.domain.closestNumbers
import io.github.staninna.warosso.domain.copyBeside
import io.github.staninna.warosso.domain.distanceTo
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

    // Door 171 hangs on a walkway that the stairwells of three buildings reach.
    private val data = complexData(
        addresses = listOf(151, 153, 155, 171, 173).map { address(it) },
        entrances = listOf(
            entrance(1, north, 1, listOf(151, 153), listOf(171, 173)),
            entrance(2, east, 2, listOf(), listOf(173, 171)),
            entrance(3, south, 3, listOf(), listOf(171)),
        ),
    )

    @Test
    fun `a number served by entrances of several buildings lists them all`() {
        val found = data.lookup(171) as Lookup.Found
        assertEquals(listOf(1L, 2L, 3L), found.routes.map { it.targetId })
    }

    @Test
    fun `the nearest entrance wins among several candidates`() {
        val routes = (data.lookup(171) as Lookup.Found).routes
        assertEquals(1L, routes.nearestTo(CENTER.offset(50.0, 10.0)).targetId)
        assertEquals(2L, routes.nearestTo(CENTER.offset(50.0, 80.0)).targetId)
        assertEquals(3L, routes.nearestTo(CENTER.offset(20.0, 180.0)).targetId)
    }

    @Test
    fun `entrances that do not serve the number are never picked, however close`() {
        // Standing on entrance 2, which has no door 151.
        val routes = (data.lookup(151) as Lookup.Found).routes
        assertEquals(1L, routes.nearestTo(east).targetId)
    }

    @Test
    fun `the target switches while walking`() {
        val found = data.lookup(171) as Lookup.Found
        val start = found.guide(Fix(CENTER.offset(55.0, 5.0), 4f), wasArrived = false)
        val later = found.guide(Fix(CENTER.offset(55.0, 175.0), 4f), wasArrived = false)
        assertEquals(1L, start.route.targetId)
        assertEquals(3L, later.route.targetId)
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
        assertEquals(1L, guidance.route.targetId)
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

    // One gallery of nine doors, six metres apart from west to east. Two stairwells stand in
    // front of it, and the second is a copy of the first: both list every door.
    private val galleryDoors = listOf(1, 3, 5, 7, 9, 11, 13, 15, 17)
    private fun inFrontOfGallery(eastM: Double) = CENTER.offset(eastM, 90.0).offset(5.0, 180.0)
    private val gallery = complexData(
        addresses = galleryDoors.mapIndexed { i, number -> address(number, CENTER.offset(i * 6.0, 90.0), buildingOsmId = 1) },
        entrances = listOf(
            entrance(1, inFrontOfGallery(10.0), 1, galleryDoors),
            entrance(2, inFrontOfGallery(39.0), 1, galleryDoors),
        ),
    )

    @Test
    fun `in one building a shared number goes through the entrance closest to its address`() {
        assertEquals(listOf(1L), (gallery.lookup(9) as Lookup.Found).routes.map { it.targetId })
        assertEquals(listOf(2L), (gallery.lookup(11) as Lookup.Found).routes.map { it.targetId })
        assertEquals(listOf(1, 3, 5, 7, 9), gallery.numbersVia(gallery.entrance(1)!!))
        assertEquals(listOf(11, 13, 15, 17), gallery.numbersVia(gallery.entrance(2)!!))
    }

    @Test
    fun `moving an entrance moves the numbers that go through it`() {
        val moved = gallery.copy(entrances = gallery.entrances.map { if (it.id == 2L) it.copy(latLng = inFrontOfGallery(20.0)) else it })
        assertEquals(listOf(1, 3, 5), moved.numbersVia(moved.entrance(1)!!))
        assertEquals(listOf(7, 9, 11, 13, 15, 17), moved.numbersVia(moved.entrance(2)!!))
    }

    @Test
    fun `a number without an address stays with every entrance that lists it`() {
        val unknown = gallery.copy(entrances = gallery.entrances.map { it.copy(floors = listOf(FloorDoors(0, galleryDoors + 99))) })
        assertEquals(listOf(1L, 2L), (unknown.lookup(99) as Lookup.Found).routes.map { it.targetId })
    }

    @Test
    fun `an entrance partway along the gallery tells which way to turn`() {
        fun hintFor(number: Int) = ((gallery.lookup(number) as Lookup.Found).routes.single() as Route.ViaEntrance).door.hint
        // The first stairwell stands between the second and the third door.
        assertEquals(DoorHint.TurnLeft(1), hintFor(3))
        assertEquals(DoorHint.TurnLeft(2), hintFor(1))
        assertEquals(DoorHint.TurnRight(2), hintFor(7))
        // The second one between the seventh and the eighth.
        assertEquals(DoorHint.TurnLeft(1), hintFor(13))
        assertEquals(DoorHint.TurnRight(1), hintFor(15))
    }

    @Test
    fun `an entrance at the end of the gallery counts from that end as before`() {
        val atTheEnd = gallery.copy(entrances = listOf(entrance(1, inFrontOfGallery(-4.0), 1, galleryDoors)))
        val route = (atTheEnd.lookup(3) as Lookup.Found).routes.single() as Route.ViaEntrance
        assertEquals(DoorHint.FromLeft(2), route.door.hint)
    }

    @Test
    fun `a copy is the same entrance a few metres on, without a name or an id`() {
        val original = gallery.entrance(1)!!.copy(name = "Trap A", note = "Blauwe deur")
        val copy = original.copyBeside()
        assertEquals(0L, copy.id)
        assertNull(copy.name)
        assertEquals(original.floors, copy.floors)
        assertEquals(original.note, copy.note)
        assertEquals(original.buildingOsmId, copy.buildingOsmId)
        assertEquals(6.0, original.latLng.distanceTo(copy.latLng), 0.01)
    }

    // Building 1 has its ground-floor doors on the street; a stairwell serves the gallery above.
    private val door143 = CENTER.offset(8.0, 90.0)
    private val streetLevel = complexData(
        addresses = listOf(address(141, buildingOsmId = 1), address(143, door143, buildingOsmId = 1), address(171, buildingOsmId = 1), address(201, buildingOsmId = 2)),
        buildings = listOf(building(1).copy(streetDoors = true), building(2, northM = 60.0)),
        entrances = listOf(entrance(1, north, 1, listOf(), listOf(171))),
    )

    @Test
    fun `a ground-floor number with its door on the street needs no entrance`() {
        val found = streetLevel.lookup(143) as Lookup.Found
        assertEquals(listOf(Route.StreetDoor(streetLevel.building(1)!!, door143)), found.routes)

        val guidance = found.guide(Fix(CENTER, 4f), wasArrived = false)
        assertEquals(8.0, guidance.distanceM!!, 0.1)
        assertEquals(90.0, guidance.bearingDeg!!, 0.1)
        assertTrue(guidance.arrived)
    }

    @Test
    fun `an entrance that lists the number wins over the door on the street`() {
        val found = streetLevel.lookup(171) as Lookup.Found
        assertEquals(listOf(1L), found.routes.map { it.targetId })
    }

    @Test
    fun `street doors only count for the building that has them`() {
        assertEquals(Lookup.NotSetUp(201), streetLevel.lookup(201))
        assertEquals(listOf(201), streetLevel.unservedNumbers)
        assertEquals(listOf(141, 143), streetLevel.streetDoorNumbers(1))
    }

    @Test
    fun `street doors alone are enough to start guiding`() {
        assertTrue(streetLevel.copy(entrances = emptyList()).canGuide)
        assertFalse(complexData(addresses = listOf(address(141, buildingOsmId = 1))).canGuide)
    }
}
