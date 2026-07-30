package com.gios.lightcamera

import com.gios.lightcamera.filter.Filters
import com.gios.lightcamera.ui.fromNearestQuarter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The level's reference angle, and the wheel's filter track. Both are small pieces of arithmetic
 * that were wrong on the device in ways no compiler would catch: a horizon line permanently 90°
 * over, and a dial that skated past the setting you wanted most.
 */
class LevelAndFilterTrackTest {

    /* ---------------- the level ---------------- */

    @Test
    fun `every quarter turn reads level`() {
        // The four ways up a phone can be held and still take a square photograph.
        listOf(0f, 90f, 180f, -90f, -180f).forEach { roll ->
            assertEquals("roll $roll", 0f, fromNearestQuarter(roll), 0.001f)
        }
    }

    @Test
    fun `a few degrees off reads a few degrees off, in any pose`() {
        assertEquals(3f, fromNearestQuarter(3f), 0.001f)
        assertEquals(3f, fromNearestQuarter(93f), 0.001f)
        assertEquals(-3f, fromNearestQuarter(87f), 0.001f)
        assertEquals(3f, fromNearestQuarter(-87f), 0.001f)
        assertEquals(-3f, fromNearestQuarter(177f), 0.001f)
    }

    @Test
    fun `the reading never leaves plus or minus 45`() {
        var roll = -180f
        while (roll <= 180f) {
            val off = fromNearestQuarter(roll)
            assertTrue("roll $roll gave $off", off > -45.001f && off <= 45.001f)
            roll += 0.5f
        }
    }

    /* ---------------- the filter track ---------------- */

    @Test
    fun `None is three notches wide and everything else is one`() {
        assertEquals(Filters.all.size + Filters.NONE_NOTCHES - 1, Filters.wheelPositions)
        val counts = (0 until Filters.wheelPositions)
            .groupingBy { Filters.filterAt(it).id }
            .eachCount()
        assertEquals(Filters.NONE_NOTCHES, counts[Filters.none.id])
        Filters.all.filter { it.id != Filters.none.id }.forEach { filter ->
            assertEquals("${filter.id} should occupy one notch", 1, counts[filter.id])
        }
    }

    @Test
    fun `leaving None takes three notches, from either side`() {
        var position = Filters.positionOf(Filters.none)
        assertEquals(Filters.none.id, Filters.filterAt(position).id)
        // Middle of three: one notch each way is still None, the second leaves it.
        assertEquals(Filters.none.id, Filters.filterAt(Filters.stepPosition(position, 1)).id)
        assertEquals(Filters.none.id, Filters.filterAt(Filters.stepPosition(position, -1)).id)
        assertTrue(Filters.filterAt(Filters.stepPosition(position, 2)).id != Filters.none.id)
        assertTrue(Filters.filterAt(Filters.stepPosition(position, -2)).id != Filters.none.id)

        // And walking in from a neighbour spends three notches inside it.
        position = Filters.positionOf(Filters.all[1])
        var inside = 0
        for (step in 1..4) {
            if (Filters.filterAt(Filters.stepPosition(position, -step)).id == Filters.none.id) {
                inside++
            }
        }
        assertEquals(Filters.NONE_NOTCHES, inside)
    }

    @Test
    fun `the track wraps rather than dead-ending`() {
        val last = Filters.wheelPositions - 1
        assertEquals(0, Filters.stepPosition(last, 1))
        assertEquals(last, Filters.stepPosition(0, -1))
    }

    @Test
    fun `every filter is reachable by turning one way`() {
        val seen = HashSet<String>()
        var position = 0
        repeat(Filters.wheelPositions) {
            seen += Filters.filterAt(position).id
            position = Filters.stepPosition(position, 1)
        }
        assertEquals(Filters.all.map { it.id }.toSet(), seen)
    }
}
