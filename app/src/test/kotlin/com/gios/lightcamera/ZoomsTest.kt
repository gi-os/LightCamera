package com.gios.lightcamera

import com.gios.lightcamera.camera.Zooms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomsTest {

    @Test
    fun `the ends of the strip are the ends of the range`() {
        assertEquals(1f, Zooms.at(0f, 8f), 0.001f)
        assertEquals(8f, Zooms.at(1f, 8f), 0.001f)
    }

    /**
     * The one property the drawing depends on: the ticks are placed with `positionOf` and the drag
     * reads with `at`, so if they ever stop being inverses the marker lands off the tick you
     * dragged it to.
     */
    @Test
    fun `position and value are inverses`() {
        for (max in listOf(2f, 4f, 8f, 10f)) {
            var z = 1f
            while (z <= max) {
                val round = Zooms.at(Zooms.positionOf(z, max), max)
                assertEquals("max=$max z=$z", z, round, 0.01f)
                z += 0.37f
            }
        }
    }

    /** Each doubling gets the same travel — the whole reason for the log scale. */
    @Test
    fun `doublings are evenly spaced`() {
        val one = Zooms.positionOf(1f, 8f)
        val two = Zooms.positionOf(2f, 8f)
        val four = Zooms.positionOf(4f, 8f)
        val eight = Zooms.positionOf(8f, 8f)
        assertEquals(two - one, four - two, 0.001f)
        assertEquals(four - two, eight - four, 0.001f)
    }

    @Test
    fun `out of range input is clamped rather than extrapolated`() {
        assertEquals(1f, Zooms.at(-3f, 8f), 0.001f)
        assertEquals(8f, Zooms.at(9f, 8f), 0.001f)
        assertEquals(0f, Zooms.positionOf(0.2f, 8f), 0.001f)
        assertEquals(1f, Zooms.positionOf(99f, 8f), 0.001f)
    }

    /** A lens with no zoom must not divide by a log of one. */
    @Test
    fun `a fixed lens is the whole strip at 1x`() {
        assertEquals(1f, Zooms.at(0.5f, 1f), 0.001f)
        assertEquals(0f, Zooms.positionOf(1f, 1f), 0.001f)
        assertTrue(Zooms.at(0.5f, 1f).isFinite())
    }
}
