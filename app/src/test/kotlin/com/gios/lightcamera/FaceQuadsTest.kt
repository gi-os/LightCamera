package com.gios.lightcamera

import com.gios.lightcamera.camera.FaceBox
import com.gios.lightcamera.filter.FaceQuad
import com.gios.lightcamera.filter.FaceQuads
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arithmetic between a detected face and the shader that warps it.
 *
 * Worth testing because the failure is silent and specific: get a sign wrong and somebody's eye is
 * enlarged next to their ear, in a filter whose whole job is enlarging eyes. None of it needs a
 * device.
 */
class FaceQuadsTest {

    private fun box(left: Float, top: Float, right: Float, bottom: Float) =
        FaceBox(id = 1, left = left, top = top, right = right, bottom = bottom, score = 90)

    private fun assertQuad(expected: FaceQuad, actual: FaceQuad) {
        assertEquals(expected.cx, actual.cx, 0.0001f)
        assertEquals(expected.cy, actual.cy, 0.0001f)
        assertEquals(expected.hw, actual.hw, 0.0001f)
        assertEquals(expected.hh, actual.hh, 0.0001f)
    }

    @Test
    fun `a face in the middle of the view is a face in the middle of the image`() {
        val quads = FaceQuads.of(listOf(box(400f, 800f, 600f, 1000f)), 1000, 2000)
        assertQuad(FaceQuad(cx = 0.5f, cy = 0.45f, hw = 0.1f, hh = 0.05f), quads.single())
    }

    @Test
    fun `the biggest face comes first and only three are kept`() {
        val faces = listOf(
            box(0f, 0f, 10f, 10f),
            box(0f, 0f, 100f, 100f),
            box(0f, 0f, 50f, 50f),
            box(0f, 0f, 20f, 20f),
        )
        val quads = FaceQuads.of(faces, 1000, 1000)
        assertEquals(3, quads.size)
        assertTrue(quads[0].hw > quads[1].hw)
        assertTrue(quads[1].hw > quads[2].hw)
    }

    @Test
    fun `a quarter turn clockwise sends the top left corner to the top right`() {
        val topLeft = FaceQuad(cx = 0.2f, cy = 0.1f, hw = 0.05f, hh = 0.2f)
        val turned = FaceQuads.rotated(topLeft, 90)
        assertQuad(FaceQuad(cx = 0.9f, cy = 0.2f, hw = 0.2f, hh = 0.05f), turned)
    }

    @Test
    fun `four quarter turns is where you started`() {
        val start = FaceQuad(cx = 0.3f, cy = 0.7f, hw = 0.1f, hh = 0.15f)
        var quad = start
        repeat(4) { quad = FaceQuads.rotated(quad, 90) }
        assertQuad(start, quad)
    }

    @Test
    fun `half a turn is the same either way round`() {
        val quad = FaceQuad(cx = 0.25f, cy = 0.6f, hw = 0.1f, hh = 0.1f)
        assertQuad(FaceQuads.rotated(quad, 180), FaceQuads.rotated(FaceQuads.rotated(quad, 90), 90))
        assertQuad(quad, FaceQuads.rotated(FaceQuads.rotated(quad, 270), 90))
    }

    @Test
    fun `a centred face stays centred through a crop, and grows as a fraction of it`() {
        // 1000x2000 down to 1000x1333 — a 3:2 crop of a portrait frame. The centre does not move.
        val quad = FaceQuad(cx = 0.5f, cy = 0.5f, hw = 0.1f, hh = 0.05f)
        val cropped = FaceQuads.cropped(quad, 1000, 2000, 1000, 1333)
        assertEquals(0.5f, cropped.cx, 0.0001f)
        assertEquals(0.5f, cropped.cy, 0.001f)
        assertEquals(0.1f, cropped.hw, 0.0001f)
        // Same pixels, smaller frame: the face is a larger share of it.
        assertTrue(cropped.hh > quad.hh)
    }

    @Test
    fun `a face near the top edge is pushed off by a crop that removes the top`() {
        // The crop takes 333 rows off the top of a 2000-row frame, so a face 100 rows down is gone.
        val quad = FaceQuad(cx = 0.5f, cy = 100f / 2000f, hw = 0.1f, hh = 0.05f)
        val cropped = FaceQuads.cropped(quad, 1000, 2000, 1000, 1333)
        assertTrue("a face above the crop should read as outside it", cropped.cy < 0f)
    }

    @Test
    fun `nothing is normalised against a view that has no size`() {
        assertTrue(FaceQuads.of(listOf(box(0f, 0f, 10f, 10f)), 0, 0).isEmpty())
    }

    @Test
    fun `a crop to the same size changes nothing`() {
        val quad = FaceQuad(cx = 0.4f, cy = 0.6f, hw = 0.1f, hh = 0.2f)
        assertQuad(quad, FaceQuads.cropped(quad, 800, 600, 800, 600))
    }
}
