package com.gios.lightcamera

import com.gios.lightcamera.hw.CameraKeyAdvice
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Version comparison, which is the whole of the advice's logic and the sort of thing that is
 * quietly wrong for a year: a string compare would call "1.1.10" older than "1.1.6", and the
 * advice would then nag forever about a version that had long since fixed it.
 */
class CameraKeyAdviceTest {

    @Test
    fun `versions parse into three numbers`() {
        assertArrayEquals(intArrayOf(1, 1, 6), CameraKeyAdvice.parse("1.1.6"))
        assertArrayEquals(intArrayOf(1, 2, 0), CameraKeyAdvice.parse("1.2"))
        assertArrayEquals(intArrayOf(2, 0, 0), CameraKeyAdvice.parse("2"))
    }

    @Test
    fun `nonsense parses to the oldest possible version`() {
        assertArrayEquals(intArrayOf(0, 0, 0), CameraKeyAdvice.parse(null))
        assertArrayEquals(intArrayOf(0, 0, 0), CameraKeyAdvice.parse(""))
        assertArrayEquals(intArrayOf(0, 0, 0), CameraKeyAdvice.parse("dev"))
        // A CI build tagged with a suffix still reads as its numbers.
        assertArrayEquals(intArrayOf(1, 1, 7), CameraKeyAdvice.parse("1.1.7-debug"))
    }

    @Test
    fun `the fixed version and anything after it passes`() {
        val fixed = intArrayOf(1, 1, 6)
        assertTrue(CameraKeyAdvice.atLeast(intArrayOf(1, 1, 6), fixed))
        assertTrue(CameraKeyAdvice.atLeast(intArrayOf(1, 1, 7), fixed))
        // The one a string compare gets wrong.
        assertTrue(CameraKeyAdvice.atLeast(intArrayOf(1, 1, 10), fixed))
        assertTrue(CameraKeyAdvice.atLeast(intArrayOf(1, 2, 0), fixed))
        assertTrue(CameraKeyAdvice.atLeast(intArrayOf(2, 0, 0), fixed))
    }

    @Test
    fun `anything before it fails`() {
        val fixed = intArrayOf(1, 1, 6)
        assertFalse(CameraKeyAdvice.atLeast(intArrayOf(1, 1, 5), fixed))
        assertFalse(CameraKeyAdvice.atLeast(intArrayOf(1, 0, 9), fixed))
        assertFalse(CameraKeyAdvice.atLeast(intArrayOf(0, 9, 9), fixed))
        assertFalse(CameraKeyAdvice.atLeast(intArrayOf(0, 0, 0), fixed))
    }
}
