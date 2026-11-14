package io.github.staninna.warosso

import io.github.staninna.warosso.domain.DoorHint
import io.github.staninna.warosso.domain.DoorLocation
import io.github.staninna.warosso.domain.doorHint
import io.github.staninna.warosso.domain.locate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DoorHintTest {
    // Odd numbers counting down, as on the ground floor in SPEC.md.
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
