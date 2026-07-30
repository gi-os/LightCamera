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
        val a = PuriArt.plan(4242L, oneFace, faceStickers = true, marginStickers = true, dateId = PuriArt.RANDOM)
        val b = PuriArt.plan(4242L, oneFace, faceStickers = true, marginStickers = true, dateId = PuriArt.RANDOM)
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
        val dates = (1..100L).map { PuriArt.plan(it, oneFace, true, true, PuriArt.RANDOM).date?.id }.toSet()
        assertTrue("every seed chose the same date", dates.size > 3)
    }

    @Test
    fun `stickers off means no stickers`() {
        val plan = PuriArt.plan(7L, oneFace, faceStickers = false, marginStickers = false, dateId = PuriArt.RANDOM)
        assertTrue(plan.placed.isEmpty())
        assertNotNull(plan.date)
    }

    @Test
    fun `the two kinds of sticker switch independently`() {
        // The reason they are separate: a bad face detection should cost you the ears, not the look.
        val facesOnly = PuriArt.plan(3L, oneFace, faceStickers = true, marginStickers = false, dateId = PuriArt.OFF)
        assertTrue(
            "a margin sticker got through with margins off",
            facesOnly.placed.none { it.sticker.anchor == PuriArt.Anchor.Free },
        )
        val marginsOnly = PuriArt.plan(3L, oneFace, faceStickers = false, marginStickers = true, dateId = PuriArt.OFF)
        assertTrue(
            "a face sticker got through with faces off",
            marginsOnly.placed.all { it.sticker.anchor == PuriArt.Anchor.Free },
        )
        assertTrue("margins alone placed nothing", marginsOnly.placed.isNotEmpty())
    }

    @Test
    fun `date off means no date`() {
        assertNull(PuriArt.plan(7L, oneFace, faceStickers = true, marginStickers = true, dateId = PuriArt.OFF).date)
    }

    @Test
    fun `nothing is anchored to a face when there are no faces`() {
        val plan = PuriArt.plan(11L, emptyList(), faceStickers = true, marginStickers = true, dateId = PuriArt.OFF)
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
            PuriArt.plan(seed, oneFace, faceStickers = true, marginStickers = true, dateId = PuriArt.OFF)
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
            PuriArt.plan(seed, oneFace, faceStickers = true, marginStickers = true, dateId = PuriArt.OFF).placed.forEach {
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
            val blush = PuriArt.plan(seed, oneFace, faceStickers = true, marginStickers = true, dateId = PuriArt.OFF)
                .placed
                .filter { it.sticker.id == "blush" }
            assertTrue("seed $seed placed ${blush.size} blushes", blush.size % 2 == 0)
        }
    }

    @Test
    fun `random resolves the same way for the same seed, and moves with it`() {
        // The whole contract behind Random: the viewfinder and the shutter each ask separately, and they
        // have to agree, or the sample in the menu is showing something the photograph will not have.
        (1..50L).forEach { seed ->
            assertEquals(
                PuriArt.resolveFrame(PuriArt.RANDOM, seed).id,
                PuriArt.resolveFrame(PuriArt.RANDOM, seed).id,
            )
            assertEquals(
                PuriArt.resolveDate(PuriArt.RANDOM, seed)?.id,
                PuriArt.resolveDate(PuriArt.RANDOM, seed)?.id,
            )
        }
        val frames = (1..100L).map { PuriArt.resolveFrame(PuriArt.RANDOM, it).id }.toSet()
        assertTrue("every seed chose the same frame", frames.size > 3)
    }

    @Test
    fun `random never picks None, since you asked for a frame`() {
        (1..200L).forEach { seed ->
            assertTrue(
                "seed $seed resolved Random to None",
                PuriArt.resolveFrame(PuriArt.RANDOM, seed).id != "none",
            )
        }
    }

    @Test
    fun `a named frame or date is returned as asked`() {
        PuriArt.frames.forEach { frame ->
            assertEquals(frame.id, PuriArt.resolveFrame(frame.id, 5L).id)
        }
        PuriArt.dates.forEach { date ->
            assertEquals(date.id, PuriArt.resolveDate(date.id, 5L)?.id)
        }
        assertNull(PuriArt.resolveDate(PuriArt.OFF, 5L))
    }

    @Test
    fun `the frame and the date do not move together`() {
        // Separately salted, so Random is a combination of choices rather than fourteen presets.
        val pairs = (1..100L).map {
            PuriArt.resolveFrame(PuriArt.RANDOM, it).id to PuriArt.resolveDate(PuriArt.RANDOM, it)?.id
        }.toSet()
        assertTrue("frame and date are locked together", pairs.size > PuriArt.frames.size)
    }

    @Test
    fun `margin stickers are big enough to see`() {
        // A tenth of the short edge in the corner of a 4:3 frame reads as a speck of dust.
        (1..80L).forEach { seed ->
            PuriArt.plan(seed, oneFace, faceStickers = false, marginStickers = true, dateId = PuriArt.OFF)
                .placed
                .forEach { assertTrue("${it.sticker.id} is tiny: ${it.size}", it.size >= 0.14f) }
        }
    }

    @Test
    fun `every date has a label for the list`() {
        PuriArt.dates.forEach {
            assertTrue("${it.id} has no label", it.label.isNotBlank())
            assertTrue("${it.id} label is too long", it.label.length <= 8)
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
