package com.gios.lightcamera

import com.gios.lightcamera.camera.DateStamp
import com.gios.lightcamera.StampStyle
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/**
 * The stamp's text, which is the one part of it that can be checked without a screen — and the part
 * that was wrong first time round. The order is month, day, apostrophe-year, and the padding is
 * spaces rather than zeroes; both were read off photographs of the real thing.
 */
class DateStampTest {

    private fun at(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply {
            set(year, month - 1, day, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun `month day apostrophe year`() {
        assertEquals("11  5 '21", DateStamp.format(at(2021, 11, 5)))
    }

    @Test
    fun `single digits are space padded, not zero padded`() {
        // A leading zero is the tell of a stamp somebody typeset rather than remembered.
        assertEquals(" 3  7 '99", DateStamp.format(at(1999, 3, 7)))
    }

    @Test
    fun `two digit days and months keep the same width`() {
        assertEquals("12 25 '26", DateStamp.format(at(2026, 12, 25)))
        // Same string length whatever the date, so the stamp never shifts about the corner.
        assertEquals(9, DateStamp.format(at(2026, 1, 1)).length)
        assertEquals(9, DateStamp.format(at(2026, 12, 31)).length)
    }

    @Test
    fun `the year is two digits and wraps at the century`() {
        assertEquals(" 1  1 '00", DateStamp.format(at(2000, 1, 1)))
        assertEquals(" 1  1 '08", DateStamp.format(at(2008, 1, 1)))
    }

    @Test
    fun `quartz puts the year first and pads with zeroes`() {
        // The film SLR backs did it the other way round from the compacts, and zero-padded.
        assertEquals("'99 12 29", DateStamp.format(at(1999, 12, 29), StampStyle.Quartz))
        assertEquals("'21 11 05", DateStamp.format(at(2021, 11, 5), StampStyle.Quartz))
    }

    @Test
    fun `the camcorder stamp uses slashes and four digits`() {
        assertEquals("08/31/2015", DateStamp.format(at(2015, 8, 31), StampStyle.Outline))
        assertEquals("01/01/2026", DateStamp.format(at(2026, 1, 1), StampStyle.Outline))
    }
}
