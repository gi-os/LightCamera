package com.gios.lightcamera

import com.gios.lightcamera.camera.Luma
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LumaTest {

    private fun grey(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v

    private fun flat(v: Int, w: Int = 24, h: Int = 32) = IntArray(w * h) { grey(v) }

    @Test
    fun `a black frame piles up in the first bin`() {
        val r = Luma.read(flat(0), 24, 32)
        assertEquals(24 * 32, r.counts.first())
        assertEquals(0, r.counts.drop(1).sum())
    }

    @Test
    fun `a white frame piles up in the last bin`() {
        val r = Luma.read(flat(255), 24, 32)
        assertEquals(24 * 32, r.counts.last())
    }

    @Test
    fun `every pixel lands in exactly one bin`() {
        val pixels = IntArray(64 * 64) { grey(it % 256) }
        val r = Luma.read(pixels, 64, 64)
        assertEquals(64 * 64, r.counts.sum())
        assertEquals(r.counts.max(), r.peak)
    }

    @Test
    fun `pure white clips and mid grey does not`() {
        assertTrue(Luma.read(flat(255), 24, 32).blown.all { it })
        assertFalse(Luma.read(flat(128), 24, 32).blown.any { it })
    }

    /**
     * The threshold is 250 rather than 255 because this ISP rarely reports a clean 255 even on a
     * specular highlight. A test at 255 only would have passed against a broken threshold.
     */
    @Test
    fun `just below the threshold is not clipped`() {
        assertTrue(Luma.read(flat(Luma.BLOWN), 24, 32).blown.all { it })
        assertFalse(Luma.read(flat(Luma.BLOWN - 1), 24, 32).blown.any { it })
    }

    /** One hot pixel in a cell is noise. A third of the cell is a blown area. */
    @Test
    fun `a speckle does not flag a cell`() {
        val w = Luma.CELLS_X * 4
        val h = Luma.CELLS_Y * 4
        val pixels = IntArray(w * h) { grey(0) }
        // One pixel of 16 in the top-left cell.
        pixels[0] = grey(255)
        assertFalse(Luma.read(pixels, w, h).blown[0])
        // Six of 16 is past a third.
        for (i in 0 until 3) {
            pixels[i] = grey(255)
            pixels[w + i] = grey(255)
        }
        assertTrue(Luma.read(pixels, w, h).blown[0])
    }

    @Test
    fun `clipping is located, not just detected`() {
        val w = Luma.CELLS_X
        val h = Luma.CELLS_Y
        // The bottom half blown, the top half black.
        val pixels = IntArray(w * h) { if (it >= w * h / 2) grey(255) else grey(0) }
        val r = Luma.read(pixels, w, h)
        assertFalse(r.blown[0])
        assertTrue(r.blown[r.blown.size - 1])
    }

    @Test
    fun `an empty frame is answered rather than thrown`() {
        val r = Luma.read(IntArray(0), 0, 0)
        assertEquals(0, r.peak)
        assertEquals(Luma.BINS, r.counts.size)
    }
}
