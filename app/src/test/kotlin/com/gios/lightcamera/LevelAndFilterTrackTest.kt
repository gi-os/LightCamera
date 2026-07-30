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

    /* ---------------- the dial ---------------- */

    @Test
    fun `stepping wraps and reaches everything, one notch per filter`() {
        // Every filter is one notch now. None gets its emphasis from a dwell in the view model
        // rather than from extra positions on the track, which meant three clicks to leave it.
        val seen = HashSet<String>()
        var here = Filters.all.first()
        repeat(Filters.all.size) {
            seen += here.id
            here = Filters.step(here, 1)
        }
        assertEquals(Filters.all.map { it.id }.toSet(), seen)
        assertEquals(Filters.all.first().id, here.id)
    }

    @Test
    fun `the dwell is long enough to catch a fast spin`() {
        // The sensor fires a notch every ~35ms, so anything much under a second would be
        // spun straight through, which was the whole problem.
        assertTrue(Filters.NONE_DWELL_MS >= 1_000L)
    }

    @Test
    fun `the Game Boy filters are there and are quantised on the sensor's own grid`() {
        listOf("gameboy", "gbcolor").forEach { id ->
            val filter = Filters.byId(id)
            assertEquals(id, filter.id)
            val source = filter.agsl!!
            // 128 cells across the short edge — the GB Camera's sensor width. Without the
            // pixel grid these are just palette filters, which is not the look.
            assertTrue("$id should quantise to 128 cells", source.contains("128.0"))
            assertTrue("$id should dither", source.contains("bayer"))
        }
    }
}
