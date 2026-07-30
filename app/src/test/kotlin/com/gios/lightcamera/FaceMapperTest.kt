package com.gios.lightcamera

import com.gios.lightcamera.camera.FaceBox
import com.gios.lightcamera.camera.FaceMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sensor-to-screen mapping, which is the one piece of geometry in the app that cannot be
 * checked by looking at it: a face bracket that is subtly wrong looks like the camera being
 * bad at detecting faces.
 *
 * The numbers below are worked by hand from the transformation, not recorded from a run.
 */
class FaceMapperTest {

    /** A 4:3 sensor, its buffer in landscape, the phone in portrait: the ordinary case. */
    private fun map(
        sensor: IntArray,
        crop: IntArray = intArrayOf(0, 0, 4000, 3000),
        rotation: Int = 90,
        mirrored: Boolean = false,
    ): FaceBox? = FaceMapper.toView(
        id = 1,
        score = 90,
        sensorRect = sensor,
        cropRect = crop,
        rotationDegrees = rotation,
        bufferWidth = 640,
        bufferHeight = 480,
        mirrored = mirrored,
        viewWidth = 480,
        viewHeight = 640,
    )

    @Test
    fun `quarter frame maps through a 90 degree turn`() {
        // Sensor-space (0.25, 0.25)..(0.5, 0.5). Turning clockwise sends (x, y) to (1-y, x),
        // so it lands at (0.5, 0.25)..(0.75, 0.5) of an upright 480x640 frame.
        val box = map(intArrayOf(1000, 750, 2000, 1500))!!
        assertEquals(240f, box.left, 0.01f)
        assertEquals(360f, box.right, 0.01f)
        assertEquals(160f, box.top, 0.01f)
        assertEquals(320f, box.bottom, 0.01f)
    }

    @Test
    fun `front camera mirrors horizontally and leaves the vertical alone`() {
        val box = map(intArrayOf(1000, 750, 2000, 1500), mirrored = true)!!
        assertEquals(120f, box.left, 0.01f)
        assertEquals(240f, box.right, 0.01f)
        assertEquals(160f, box.top, 0.01f)
        assertEquals(320f, box.bottom, 0.01f)
    }

    @Test
    fun `a face filling the crop region fills the view, whatever the zoom`() {
        // Zoomed 2x: the read-out region is the middle half of the array. A face exactly
        // filling it must fill the frame — normalising against the full array instead would
        // put it in the middle quarter, which is the bug this guards.
        val crop = intArrayOf(1000, 750, 3000, 2250)
        val box = map(crop.copyOf(), crop = crop)!!
        assertEquals(0f, box.left, 0.01f)
        assertEquals(480f, box.right, 0.01f)
        assertEquals(0f, box.top, 0.01f)
        assertEquals(640f, box.bottom, 0.01f)
    }

    @Test
    fun `an unrotated buffer needs no turn`() {
        val box = FaceMapper.toView(
            id = 0,
            score = 0,
            sensorRect = intArrayOf(0, 0, 2000, 1500),
            cropRect = intArrayOf(0, 0, 4000, 3000),
            rotationDegrees = 0,
            bufferWidth = 640,
            bufferHeight = 480,
            mirrored = false,
            viewWidth = 640,
            viewHeight = 480,
        )!!
        assertEquals(0f, box.left, 0.01f)
        assertEquals(320f, box.right, 0.01f)
        assertEquals(240f, box.bottom, 0.01f)
    }

    @Test
    fun `a view with no size maps to nothing rather than to infinity`() {
        assertNull(
            FaceMapper.toView(
                id = 0,
                score = 0,
                sensorRect = intArrayOf(0, 0, 100, 100),
                cropRect = intArrayOf(0, 0, 4000, 3000),
                rotationDegrees = 90,
                bufferWidth = 640,
                bufferHeight = 480,
                mirrored = false,
                viewWidth = 0,
                viewHeight = 0,
            ),
        )
    }

    @Test
    fun `a degenerate crop region is refused`() {
        assertNull(map(intArrayOf(0, 0, 100, 100), crop = intArrayOf(10, 10, 10, 10)))
    }

    /* ---------------- subject choice ---------------- */

    private fun box(id: Int, cx: Float, cy: Float, side: Float) = FaceBox(
        id = id,
        left = cx - side / 2,
        top = cy - side / 2,
        right = cx + side / 2,
        bottom = cy + side / 2,
        score = 90,
    )

    @Test
    fun `the nearest face wins`() {
        val small = box(1, 100f, 100f, 60f)
        val large = box(2, 380f, 500f, 160f)
        assertEquals(2, FaceMapper.priority(listOf(small, large), 480, 640)!!.id)
    }

    @Test
    fun `faces the same size are settled by the centre of the frame`() {
        val edge = box(1, 40f, 40f, 120f)
        val middle = box(2, 240f, 320f, 120f)
        assertEquals(2, FaceMapper.priority(listOf(edge, middle), 480, 640)!!.id)
    }

    @Test
    fun `no faces means no subject`() {
        assertNull(FaceMapper.priority(emptyList(), 480, 640))
    }

    /* ---------------- continuous focus ---------------- */

    @Test
    fun `a new subject always refocuses`() {
        assertTrue(FaceMapper.movedEnoughToRefocus(null, box(1, 240f, 320f, 120f), 480, 640))
    }

    @Test
    fun `a subject that has not moved does not refocus`() {
        val face = box(1, 240f, 320f, 120f)
        assertFalse(FaceMapper.movedEnoughToRefocus(face, face, 480, 640))
        // A couple of pixels of jitter is not movement.
        assertFalse(FaceMapper.movedEnoughToRefocus(face, box(1, 243f, 322f, 121f), 480, 640))
    }

    @Test
    fun `a subject walking across the frame refocuses`() {
        val face = box(1, 240f, 320f, 120f)
        assertTrue(FaceMapper.movedEnoughToRefocus(face, box(1, 240f, 420f, 120f), 480, 640))
    }

    @Test
    fun `a subject walking towards you refocuses`() {
        val face = box(1, 240f, 320f, 120f)
        assertTrue(FaceMapper.movedEnoughToRefocus(face, box(1, 240f, 320f, 180f), 480, 640))
    }

    @Test
    fun `losing the subject leaves the lens where it is`() {
        assertFalse(FaceMapper.movedEnoughToRefocus(box(1, 240f, 320f, 120f), null, 480, 640))
    }
}
