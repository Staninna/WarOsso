package io.github.staninna.warosso

import io.github.staninna.warosso.domain.DoorHint
import io.github.staninna.warosso.domain.DoorLocation
import io.github.staninna.warosso.domain.doorHint
import io.github.staninna.warosso.domain.doorsLeftOf
import io.github.staninna.warosso.domain.locate
import io.github.staninna.warosso.domain.offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DoorHintTest {
    // Odd numbers counting down, as on a ground-floor gallery.
    private val gallery = listOf(169, 167, 165, 163, 161, 159, 157, 155, 153, 151)

    @Test
    fun `doors in the left half count from the left`() {
        assertEquals(DoorHint.FromLeft(1), doorHint(gallery, 169))
        assertEquals(DoorHint.FromLeft(3), doorHint(gallery, 165))
        assertEquals(DoorHint.FromLeft(5), doorHint(gallery, 161))
    }

    @Test
    fun `doors in the right half count from the right`() {
        assertEquals(DoorHint.FromRight(5), doorHint(gallery, 159))
        assertEquals(DoorHint.FromRight(2), doorHint(gallery, 153))
        assertEquals(DoorHint.FromRight(1), doorHint(gallery, 151))
    }

    @Test
    fun `the middle door of an odd row counts from the left`() {
        assertEquals(DoorHint.FromLeft(2), doorHint(listOf(9, 11, 13), 11))
    }

    @Test
    fun `the count follows the list, not the numbers`() {
        val irregular = listOf(199, 12, 3, 47, 151)
        assertEquals(DoorHint.FromLeft(2), doorHint(irregular, 12))
        assertEquals(DoorHint.FromRight(2), doorHint(irregular, 47))
    }

    @Test
    fun `a single door needs no counting`() {
        assertEquals(DoorHint.Only, doorHint(listOf(107), 107))
    }

    @Test
    fun `with the entrance partway along the floor the count starts there`() {
        val doors = listOf(1, 3, 5, 7, 9)
        assertEquals(DoorHint.TurnLeft(2), doorHint(doors, 1, doorsLeftOfEntrance = 2))
        assertEquals(DoorHint.TurnLeft(1), doorHint(doors, 3, doorsLeftOfEntrance = 2))
        assertEquals(DoorHint.TurnRight(1), doorHint(doors, 5, doorsLeftOfEntrance = 2))
        assertEquals(DoorHint.TurnRight(3), doorHint(doors, 9, doorsLeftOfEntrance = 2))
    }

    @Test
    fun `with the entrance at an end the count starts from the nearer end of the floor`() {
        val doors = listOf(1, 3, 5, 7, 9)
        assertEquals(DoorHint.FromLeft(2), doorHint(doors, 3, doorsLeftOfEntrance = 0))
        assertEquals(DoorHint.FromRight(1), doorHint(doors, 9, doorsLeftOfEntrance = 5))
    }

    @Test
    fun `the addresses tell how many doors are left of the entrance`() {
        // Doors six metres apart from west to east; the entrance stands in front of them.
        fun door(i: Int) = CENTER.offset(i * 6.0, 90.0)
        fun entranceAt(eastM: Double) = CENTER.offset(eastM, 90.0).offset(5.0, 180.0)
        val doors = List(5, ::door)

        assertEquals(2, doorsLeftOf(entranceAt(9.0), doors))
        assertEquals(0, doorsLeftOf(entranceAt(-3.0), doors))
        assertEquals(5, doorsLeftOf(entranceAt(40.0), doors))
        // Read from east to west, the same entrance has the other doors on its left.
        assertEquals(3, doorsLeftOf(entranceAt(9.0), doors.reversed()))
        // A door without an address goes with its neighbours in the list.
        assertEquals(3, doorsLeftOf(entranceAt(15.0), listOf(door(0), null, door(2), door(3), door(4))))
    }

    @Test
    fun `too few addresses tell nothing`() {
        assertNull(doorsLeftOf(CENTER, emptyList()))
        assertNull(doorsLeftOf(CENTER, listOf(CENTER.offset(4.0, 0.0), null)))
        assertNull(doorsLeftOf(CENTER, listOf(CENTER, CENTER, CENTER)))
    }

    @Test
    fun `a door that is not on the floor has no hint`() {
        assertNull(doorHint(gallery, 170))
        assertNull(doorHint(emptyList(), 1))
    }

    @Test
    fun `an entrance finds the floor of a door`() {
        val entrance = entrance(1, floors = arrayOf(gallery, listOf(199, 197, 195, 193)))
        assertEquals(DoorLocation(0, DoorHint.FromLeft(3)), entrance.locate(165))
        assertEquals(DoorLocation(1, DoorHint.FromRight(1)), entrance.locate(193))
        assertNull(entrance.locate(201))
    }
}
