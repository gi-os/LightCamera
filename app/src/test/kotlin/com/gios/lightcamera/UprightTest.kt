package com.gios.lightcamera

import com.gios.lightcamera.camera.Frames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which way round a captured frame has to be put.
 *
 * The selfie cases are why this exists. A photograph off the back lens is only ever turned, and turning
 * is hard to get wrong; a photograph off the front is turned *and* mirrored, the two do not commute, and
 * the only report you get from the phone is a picture that looks slightly wrong in a way nobody can
 * describe. So the decision is arithmetic and it is checked here.
 */
class UprightTest {

    /** The TIFF orientation values, the same ones `ExifInterface` names. */
    private companion object {
        const val UNDEFINED = 0
        const val NORMAL = 1
        const val FLIP_HORIZONTAL = 2
        const val ROTATE_180 = 3
        const val FLIP_VERTICAL = 4
        const val TRANSPOSE = 5
        const val ROTATE_90 = 6
        const val TRANSVERSE = 7
        const val ROTATE_270 = 8
    }

    @Test
    fun `with no exif tag the frame takes CameraX's rotation and no mirror`() {
        val upright = Frames.uprightFor(90, UNDEFINED, mirrored = false)
        assertEquals(90, upright.turn)
        assertFalse(upright.flip)
    }

    @Test
    fun `a normal tag says nothing, so CameraX still decides`() {
        assertEquals(270, Frames.uprightFor(270, NORMAL, mirrored = false).turn)
    }

    @Test
    fun `exif beats CameraX, because it describes the bytes in hand`() {
        assertEquals(90, Frames.uprightFor(270, ROTATE_90, mirrored = false).turn)
        assertEquals(180, Frames.uprightFor(0, ROTATE_180, mirrored = false).turn)
        assertEquals(270, Frames.uprightFor(90, ROTATE_270, mirrored = false).turn)
    }

    @Test
    fun `a rotation outside 0 to 360 still lands inside it`() {
        assertEquals(270, Frames.uprightFor(-90, UNDEFINED, mirrored = false).turn)
        assertEquals(90, Frames.uprightFor(450, UNDEFINED, mirrored = false).turn)
    }

    @Test
    fun `the front lens mirrors, and the turn is unaffected by it`() {
        val upright = Frames.uprightFor(270, UNDEFINED, mirrored = true)
        assertEquals(270, upright.turn)
        assertTrue(upright.flip)
    }

    @Test
    fun `a flipped exif tag carries a rotation of its own`() {
        assertEquals(0, Frames.uprightFor(90, FLIP_HORIZONTAL, mirrored = false).turn)
        assertEquals(180, Frames.uprightFor(0, FLIP_VERTICAL, mirrored = false).turn)
        assertEquals(90, Frames.uprightFor(0, TRANSPOSE, mirrored = false).turn)
        assertEquals(270, Frames.uprightFor(0, TRANSVERSE, mirrored = false).turn)
    }

    @Test
    fun `a HAL that already declared the mirror is not mirrored twice`() {
        // The selfie case that matters: the tag asks for a flip and so does the lens, and doing both
        // would hand back exactly the frame we started with.
        assertFalse(Frames.uprightFor(0, TRANSVERSE, mirrored = true).flip)
        assertFalse(Frames.uprightFor(0, FLIP_HORIZONTAL, mirrored = true).flip)
        // And the other way round: a back-lens photograph whose tag asks for a flip still gets one.
        assertTrue(Frames.uprightFor(0, TRANSVERSE, mirrored = false).flip)
    }

    @Test
    fun `a back-lens frame that is already upright is left completely alone`() {
        val upright = Frames.uprightFor(0, NORMAL, mirrored = false)
        assertEquals(0, upright.turn)
        assertFalse(upright.flip)
    }
}
