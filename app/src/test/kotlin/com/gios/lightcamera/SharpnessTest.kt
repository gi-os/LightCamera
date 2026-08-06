package com.gios.lightcamera

import com.gios.lightcamera.camera.Sharpness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharpnessTest {

    private fun grey(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v

    @Test
    fun `a flat frame has no detail at all`() {
        val pixels = IntArray(64 * 64) { grey(128) }
        assertEquals(0f, Sharpness.of(pixels, 64, 64), 0.001f)
    }

    /** The property the burst relies on: the sharper of two versions of a scene scores higher. */
    @Test
    fun `a hard edge beats a soft one`() {
        val w = 64
        val h = 64
        val hard = IntArray(w * h) { grey(if ((it % w) < w / 2) 0 else 255) }
        // The same edge, ramped over eight pixels — what camera shake does to it.
        val soft = IntArray(w * h) { i ->
            val x = i % w
            val t = ((x - (w / 2 - 4)).coerceIn(0, 8)) / 8f
            grey((t * 255).toInt())
        }
        assertTrue(Sharpness.of(hard, w, h) > Sharpness.of(soft, w, h))
    }

    @Test
    fun `checkerboard detail scores higher than a single edge`() {
        val w = 64
        val h = 64
        val edge = IntArray(w * h) { grey(if ((it % w) < w / 2) 0 else 255) }
        val checks = IntArray(w * h) { i ->
            grey(if (((i % w) + (i / w)) % 2 == 0) 0 else 255)
        }
        assertTrue(Sharpness.of(checks, w, h) > Sharpness.of(edge, w, h))
    }

    /**
     * The inset is the point of the central weighting: detail crammed against the edge of the
     * viewfinder — floor, ceiling, a passing shoulder — must not decide the frame.
     */
    @Test
    fun `detail outside the inset is ignored`() {
        val w = 64
        val h = 64
        val edgesOnly = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            val outer = x < 8 || x >= w - 8 || y < 8 || y >= h - 8
            grey(if (outer && (x + y) % 2 == 0) 255 else 0)
        }
        assertEquals(0f, Sharpness.of(edgesOnly, w, h, inset = 0.25f), 0.001f)
    }

    @Test
    fun `a frame too small to convolve is answered rather than thrown`() {
        assertEquals(0f, Sharpness.of(IntArray(4) { grey(0) }, 2, 2), 0.001f)
        assertEquals(0f, Sharpness.of(IntArray(0), 0, 0), 0.001f)
    }
}
