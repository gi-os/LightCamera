package com.gios.lightcamera

import com.gios.lightcamera.camera.PuriArt
import com.gios.lightcamera.filter.FaceQuad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The decisions a Purikura makes, checked without drawing anything.
 *
 * `plan` is the part that has to be right: it is called twice for every photograph — once by the
 * viewfinder and once by the shutter — and if those two disagree the preview is a lie. Nothing here
 * touches a `Canvas`, so it all runs on the JVM.
 */
class PuriArtTest {

    private val oneFace = listOf(FaceQuad(cx = 0.5f, cy = 0.4f, hw = 0.16f, hh = 0.12f))

    @Test
    fun `the same seed plans the same photograph`() {
        val a = PuriArt.plan(4242L, oneFace, withStickers = true, withDate = true)
        val b = PuriArt.plan(4242L, oneFace, withStickers = true, withDate = true)
        assertEquals(a.placed.size, b.placed.size)
        assertEquals(a.date?.id, b.date?.id)
        a.placed.zip(b.placed).forEach { (x, y) ->
            assertEquals(x.sticker.id, y.sticker.id)
            assertEquals(x.cx, y.cx, 0f)
            assertEquals(x.cy, y.cy, 0f)
            assertEquals(x.size, y.size, 0f)
        }
    }

    @Test
    fun `different seeds give different prints`() {
        // Not a guarantee for any single pair, so this asks whether the space is being explored at
        // all: a hundred seeds should not all land on the same date.
        val dates = (1..100L).map { PuriArt.plan(it, oneFace, true, true).date?.id }.toSet()
        assertTrue("every seed chose the same date", dates.size > 3)
    }

    @Test
    fun `stickers off means no stickers`() {
        val plan = PuriArt.plan(7L, oneFace, withStickers = false, withDate = true)
        assertTrue(plan.placed.isEmpty())
        assertNotNull(plan.date)
    }

    @Test
    fun `date off means no date`() {
        assertNull(PuriArt.plan(7L, oneFace, withStickers = true, withDate = false).date)
    }

    @Test
    fun `nothing is anchored to a face when there are no faces`() {
        val plan = PuriArt.plan(11L, emptyList(), withStickers = true, withDate = false)
        assertTrue(
            "a sticker needing a face was placed without one",
            plan.placed.none { it.sticker.anchor != PuriArt.Anchor.Free },
        )
        assertTrue("the margins should still get something", plan.placed.isNotEmpty())
    }

    @Test
    fun `free stickers keep off the faces`() {
        // The whole point of the placement loop. A booth decorates the edges of a print, having just
        // spent all that effort on the eyes.
        val face = oneFace.single()
        (1..200L).forEach { seed ->
            PuriArt.plan(seed, oneFace, withStickers = true, withDate = false)
                .placed
                .filter { it.sticker.anchor == PuriArt.Anchor.Free }
                .forEach { placed ->
                    val clear = kotlin.math.abs(placed.cx - face.cx) >= face.hw * 1.4f ||
                        kotlin.math.abs(placed.cy - face.cy) >= face.hh * 1.4f
                    assertTrue("seed $seed put ${placed.sticker.id} on the face", clear)
                }
        }
    }

    @Test
    fun `everything planned is somewhere on the picture`() {
        (1..100L).forEach { seed ->
            PuriArt.plan(seed, oneFace, withStickers = true, withDate = false).placed.forEach {
                assertTrue("${it.sticker.id} is off the frame", it.cx > -0.2f && it.cx < 1.2f)
                assertTrue("${it.sticker.id} is off the frame", it.cy > -0.2f && it.cy < 1.2f)
                assertTrue("${it.sticker.id} has no size", it.size > 0f)
            }
        }
    }

    @Test
    fun `blush comes in pairs`() {
        // One cheek is a bruise.
        (1..60L).forEach { seed ->
            val blush = PuriArt.plan(seed, oneFace, withStickers = true, withDate = false)
                .placed
                .filter { it.sticker.id == "blush" }
            assertTrue("seed $seed placed ${blush.size} blushes", blush.size % 2 == 0)
        }
    }

    @Test
    fun `ids are unique and None comes first`() {
        val ids = PuriArt.frames.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals("none", ids.first())
        val stickerIds = PuriArt.stickers.map { it.id }
        assertEquals(stickerIds.size, stickerIds.toSet().size)
        val dateIds = PuriArt.dates.map { it.id }
        assertEquals(dateIds.size, dateIds.toSet().size)
    }

    @Test
    fun `an unknown frame id falls back to None rather than crashing`() {
        assertEquals("none", PuriArt.frameById("nope").id)
        assertEquals("none", PuriArt.frameById(null).id)
    }

    @Test
    fun `frame labels fit the band`() {
        PuriArt.frames.forEach {
            assertTrue("${it.id} is too long for the chip", it.label.length <= 8)
        }
    }
}
