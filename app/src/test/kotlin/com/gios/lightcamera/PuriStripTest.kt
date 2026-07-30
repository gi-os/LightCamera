package com.gios.lightcamera

import com.gios.lightcamera.camera.PuriStrip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the four photographs go on the sheet.
 *
 * This is the part of a strip that is arithmetic, and arithmetic is the part that is easy to get wrong
 * in a way you only notice after taking four photographs of yourself. No `Bitmap` is involved, so it
 * runs on the JVM.
 */
class PuriStripTest {

    private val cellW = 1080
    private val cellH = 1440

    @Test
    fun `off is first, so the setting has somewhere to rest`() {
        assertEquals("off", PuriStrip.layouts.first().id)
    }

    @Test
    fun `ids are unique and labels fit the row`() {
        val ids = PuriStrip.layouts.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        PuriStrip.layouts.forEach {
            assertTrue("${it.id} is too long", it.label.length <= 8)
        }
    }

    @Test
    fun `a bare strip is exactly four frames tall and no wider`() {
        val bare = PuriStrip.layoutById("bare")
        val (w, h) = bare.measure(cellW, cellH)
        assertEquals(cellW, w)
        assertEquals(cellH * 4, h)
    }

    @Test
    fun `every layout is taller than it is wide, except the grid`() {
        PuriStrip.layouts.filter { it.id != "off" }.forEach { layout ->
            val (w, h) = layout.measure(cellW, cellH)
            if (layout.columns == 1) {
                assertTrue("${layout.id} is not a strip", h > w * 2)
            } else {
                assertTrue("${layout.id} should be roughly square", h < w * 2)
            }
        }
    }

    @Test
    fun `the four cells never overlap and all fit on the sheet`() {
        PuriStrip.layouts.filter { it.id != "off" }.forEach { layout ->
            val (sheetW, sheetH) = layout.measure(cellW, cellH)
            val cells = (0 until PuriStrip.SHOTS).map { layout.cellAt(it, cellW, cellH) }
            cells.forEach { cell ->
                assertTrue("${layout.id} puts a frame off the left", cell.left >= 0)
                assertTrue("${layout.id} puts a frame off the top", cell.top >= 0)
                assertTrue("${layout.id} puts a frame off the right", cell.right <= sheetW)
                assertTrue("${layout.id} puts a frame off the bottom", cell.bottom <= sheetH)
                assertEquals("${layout.id} squashed a frame", cellW, cell.width())
                assertEquals("${layout.id} squashed a frame", cellH, cell.height())
            }
            cells.forEachIndexed { i, a ->
                cells.drop(i + 1).forEach { b ->
                    assertTrue("${layout.id} overlaps two frames", !a.intersect(b))
                }
            }
        }
    }

    @Test
    fun `the strip reads in order, top to bottom`() {
        val classic = PuriStrip.layoutById("classic")
        val tops = (0 until PuriStrip.SHOTS).map { classic.cellAt(it, cellW, cellH).top }
        assertEquals(tops.sorted(), tops)
    }

    @Test
    fun `the grid reads across then down`() {
        val grid = PuriStrip.layoutById("grid")
        val a = grid.cellAt(0, cellW, cellH)
        val b = grid.cellAt(1, cellW, cellH)
        val c = grid.cellAt(2, cellW, cellH)
        assertTrue("the second frame should be to the right of the first", b.left > a.left)
        assertEquals("and level with it", a.top, b.top)
        assertTrue("the third frame should be below the first", c.top > a.top)
        assertEquals("and back at the left", a.left, c.left)
    }

    @Test
    fun `a footer leaves blank paper below the last frame`() {
        val classic = PuriStrip.layoutById("classic")
        val (_, sheetH) = classic.measure(cellW, cellH)
        val last = classic.cellAt(PuriStrip.SHOTS - 1, cellW, cellH)
        assertTrue("nowhere to print the date", sheetH - last.bottom > cellW * 0.1f)
    }

    @Test
    fun `a mount leaves paper on every side`() {
        val mount = PuriStrip.layoutById("mount")
        val (sheetW, _) = mount.measure(cellW, cellH)
        val first = mount.cellAt(0, cellW, cellH)
        assertTrue(first.left > 0)
        assertTrue(first.top > 0)
        assertTrue(sheetW - first.right > 0)
    }

    @Test
    fun `the sheet scales with the frame`() {
        // Same layout, frames twice the size, sheet twice the size — within rounding.
        val classic = PuriStrip.layoutById("classic")
        val (smallW, smallH) = classic.measure(540, 720)
        val (bigW, bigH) = classic.measure(1080, 1440)
        assertEquals(smallW * 2, bigW, 2)
        assertEquals(smallH * 2, bigH, 2)
    }

    @Test
    fun `an unknown layout is off rather than a crash`() {
        assertEquals("off", PuriStrip.layoutById("nope").id)
        assertEquals("off", PuriStrip.layoutById(null).id)
    }

    private fun assertEquals(expected: Int, actual: Int, tolerance: Int) {
        assertTrue("expected $expected +/- $tolerance but was $actual", kotlin.math.abs(expected - actual) <= tolerance)
    }
}
