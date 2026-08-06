package com.gios.lightcamera.ocr

import kotlin.math.max
import kotlin.math.min

/** Where a rectangle lands on the screen, as (left, top, right, bottom) in view pixels. */
data class ViewBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
    val area: Float get() = max(0f, right - left) * max(0f, bottom - top)
}

/** How a bitmap of one size is laid into a view of another. */
data class Placement(val scale: Float, val dx: Float, val dy: Float)

/**
 * Getting a recognised line from the page onto the screen.
 *
 * Two transformations, both easy to get subtly wrong and impossible to see in review — which is
 * why this is plain Kotlin with no Android imports and a test file beside it, the same argument
 * as [com.gios.lightcamera.camera.FaceMapper].
 *
 *  1. **Un-rotation.** The recogniser was handed a rotation rather than a rotated bitmap, so it
 *     reports boxes in the upright image's coordinates while the thing on screen is the original.
 *     For a frame read with the phone on its side those two spaces have their axes swapped, so a
 *     box that is merely offset is a bug and a box that is *transposed* is this step missing.
 *  2. **Placement.** The frame is drawn either cropped to fill the panel or fitted inside it, and
 *     the two differ in sign: fill scales by the larger ratio and loses the overhang, fit scales
 *     by the smaller and gains a margin. Using the wrong one puts every box out by the same
 *     proportion, which reads as "the boxes are slightly off" rather than as a wrong formula.
 */
object TextBoxes {

    /** Cropped to fill, losing the overhanging edges. `ContentScale.Crop`. */
    fun fill(srcW: Int, srcH: Int, viewW: Float, viewH: Float): Placement =
        place(srcW, srcH, viewW, viewH, max(viewW / srcW, viewH / srcH))

    /** Fitted inside, with a margin on one axis. `ContentScale.Fit`. */
    fun fit(srcW: Int, srcH: Int, viewW: Float, viewH: Float): Placement =
        place(srcW, srcH, viewW, viewH, min(viewW / srcW, viewH / srcH))

    private fun place(srcW: Int, srcH: Int, viewW: Float, viewH: Float, scale: Float) = Placement(
        scale = scale,
        dx = (viewW - srcW * scale) / 2f,
        dy = (viewH - srcH * scale) / 2f,
    )

    /**
     * The source bitmap's size, given the upright size the recogniser worked in.
     *
     * A quarter turn swaps the axes. Every caller needs this before it can work out a placement,
     * because the thing being drawn is the source and the thing being measured is the upright.
     */
    fun sourceSize(uprightW: Int, uprightH: Int, rotationDegrees: Int): Pair<Int, Int> =
        if (quarter(rotationDegrees) % 2 == 1) uprightH to uprightW else uprightW to uprightH

    /**
     * A line's box, in view pixels.
     *
     * @param rotationDegrees the clockwise turn handed to the recogniser, so this undoes it
     * @param placement from [fill] or [fit], computed against the **source** size
     */
    fun toView(
        line: TextLine,
        uprightW: Int,
        uprightH: Int,
        rotationDegrees: Int,
        placement: Placement,
    ): ViewBox {
        val (srcW, srcH) = sourceSize(uprightW, uprightH, rotationDegrees)
        val r = unrotate(line, srcW, srcH, rotationDegrees)
        return ViewBox(
            left = r[0] * placement.scale + placement.dx,
            top = r[1] * placement.scale + placement.dy,
            right = r[2] * placement.scale + placement.dx,
            bottom = r[3] * placement.scale + placement.dy,
        )
    }

    /**
     * A box in upright coordinates, put back into the source bitmap's.
     *
     * Returned as (left, top, right, bottom) in source pixels. Written out per quarter rather
     * than as a matrix on purpose: four cases with the arithmetic visible is the version anyone
     * can check against a drawing, and a matrix here is the version nobody checks at all.
     */
    fun unrotate(line: TextLine, srcW: Int, srcH: Int, rotationDegrees: Int): FloatArray {
        val w = srcW.toFloat()
        val h = srcH.toFloat()
        return when (quarter(rotationDegrees)) {
            // Upright is the source. Nothing to undo.
            0 -> floatArrayOf(line.left, line.top, line.right, line.bottom)
            // Turned a quarter clockwise to read it: source x came from upright y, and source y
            // is the upright x measured back from the bottom of the source.
            1 -> floatArrayOf(line.top, h - line.right, line.bottom, h - line.left)
            2 -> floatArrayOf(w - line.right, h - line.bottom, w - line.left, h - line.top)
            else -> floatArrayOf(w - line.bottom, line.left, w - line.top, line.right)
        }
    }

    /**
     * Which line was tapped, or null.
     *
     * Smallest hit wins. Lines overlap more often than they look like they do — a recogniser
     * gives a tall box to a line with a descender next to a line with a capital — and on a
     * 3.92" panel the smaller of two overlapping boxes is always the one being aimed at.
     */
    fun hit(boxes: List<ViewBox>, x: Float, y: Float): Int? = boxes
        .withIndex()
        .filter { it.value.contains(x, y) }
        .minByOrNull { it.value.area }
        ?.index

    private fun quarter(degrees: Int): Int = ((degrees / 90) % 4 + 4) % 4
}
