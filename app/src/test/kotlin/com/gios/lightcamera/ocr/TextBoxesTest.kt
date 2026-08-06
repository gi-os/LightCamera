package com.gios.lightcamera.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The two transformations between a recognised line and a rectangle on the panel.
 *
 * Worth testing for the same reason `FaceMapperTest` is: a box that is out by a scale factor
 * looks like "slightly off" rather than like a wrong formula, and a box that is transposed only
 * shows up on the shots people actually take — the ones with the phone on its side.
 */
class TextBoxesTest {

    /** A line across the top-left of a 100x200 upright page. */
    private val line = TextLine("x", left = 10f, top = 20f, right = 60f, bottom = 40f)

    private fun r(a: FloatArray) = a.toList()

    @Test
    fun `no rotation is the identity`() {
        assertEquals(listOf(10f, 20f, 60f, 40f), r(TextBoxes.unrotate(line, 100, 200, 0)))
    }

    @Test
    fun `a quarter turn swaps the axes`() {
        // Upright was 100x200, so the source was 200x100 — the box must come back inside those
        // bounds. A box that stays within 0..100 on x is this step not having happened.
        val (w, h) = TextBoxes.sourceSize(100, 200, 90)
        assertEquals(200, w)
        assertEquals(100, h)
        assertEquals(listOf(20f, 100f - 60f, 40f, 100f - 10f), r(TextBoxes.unrotate(line, w, h, 90)))
    }

    @Test
    fun `three quarters is the other way round`() {
        val (w, h) = TextBoxes.sourceSize(100, 200, 270)
        assertEquals(listOf(200f - 40f, 10f, 200f - 20f, 60f), r(TextBoxes.unrotate(line, w, h, 270)))
    }

    @Test
    fun `half a turn reflects both axes`() {
        assertEquals(
            listOf(100f - 60f, 200f - 40f, 100f - 10f, 200f - 20f),
            r(TextBoxes.unrotate(line, 100, 200, 180)),
        )
    }

    @Test
    fun `a full turn is no turn, and a negative one counts backwards`() {
        assertEquals(r(TextBoxes.unrotate(line, 100, 200, 0)), r(TextBoxes.unrotate(line, 100, 200, 360)))
        assertEquals(r(TextBoxes.unrotate(line, 200, 100, 270)), r(TextBoxes.unrotate(line, 200, 100, -90)))
    }

    @Test
    fun `un-rotating twice around returns the original`() {
        // The property that matters, stated as one: whatever the turn, a box must land inside the
        // source bounds it was mapped into.
        for (turn in listOf(0, 90, 180, 270)) {
            val (w, h) = TextBoxes.sourceSize(100, 200, turn)
            val out = TextBoxes.unrotate(line, w, h, turn)
            assert(out[0] >= 0f && out[2] <= w) { "x out of bounds at $turn: ${out.toList()}" }
            assert(out[1] >= 0f && out[3] <= h) { "y out of bounds at $turn: ${out.toList()}" }
        }
    }

    @Test
    fun `fill scales by the larger ratio and centres the overhang`() {
        // A 100x100 source in a 200x400 view: fill needs 4x and loses 200px of width, half each
        // side, so the offset is negative.
        val p = TextBoxes.fill(100, 100, 200f, 400f)
        assertEquals(4f, p.scale, 0.001f)
        assertEquals(-100f, p.dx, 0.001f)
        assertEquals(0f, p.dy, 0.001f)
    }

    @Test
    fun `fit scales by the smaller ratio and centres the margin`() {
        val p = TextBoxes.fit(100, 100, 200f, 400f)
        assertEquals(2f, p.scale, 0.001f)
        assertEquals(0f, p.dx, 0.001f)
        assertEquals(100f, p.dy, 0.001f)
    }

    @Test
    fun `end to end, upright and unscaled`() {
        val p = TextBoxes.fill(100, 200, 100f, 200f)
        val box = TextBoxes.toView(line, 100, 200, 0, p)
        assertEquals(10f, box.left, 0.001f)
        assertEquals(20f, box.top, 0.001f)
        assertEquals(60f, box.right, 0.001f)
        assertEquals(40f, box.bottom, 0.001f)
    }

    @Test
    fun `end to end, sideways`() {
        // Source is 200x100 shown at 2x in a 400x200 view. The line ran across the top of the
        // upright page, so on the un-rotated frame it runs down one edge.
        val p = TextBoxes.fill(200, 100, 400f, 200f)
        val box = TextBoxes.toView(line, 100, 200, 90, p)
        assertEquals(40f, box.left, 0.001f)
        assertEquals(80f, box.top, 0.001f)
        assertEquals(80f, box.right, 0.001f)
        assertEquals(180f, box.bottom, 0.001f)
    }

    @Test
    fun `the smallest box under the finger wins`() {
        val big = ViewBox(0f, 0f, 100f, 100f)
        val small = ViewBox(10f, 10f, 20f, 20f)
        assertEquals(1, TextBoxes.hit(listOf(big, small), 15f, 15f))
        assertEquals(0, TextBoxes.hit(listOf(big, small), 50f, 50f))
        assertNull(TextBoxes.hit(listOf(big, small), 500f, 500f))
    }
}
