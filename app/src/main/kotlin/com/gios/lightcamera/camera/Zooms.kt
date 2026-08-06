package com.gios.lightcamera.camera

import kotlin.math.ln
import kotlin.math.pow

/**
 * Where a zoom ratio sits along the zoom strip, and back again.
 *
 * **Logarithmic, because zoom is multiplicative.** Laid out linearly on a strip a hand's width
 * long, the whole of 1x to 2x — the range that contains almost every zoom anybody actually wants —
 * lands in the first eighth of the travel on a lens that reaches 8x, and the last half of the strip
 * is spent between 5x and 8x where consecutive positions are indistinguishable in the frame. On a
 * log scale each doubling gets the same distance, which is the same distance it gets in the
 * picture.
 *
 * Its own file, with no Compose or Android in it, because it is the one part of the strip that can
 * be wrong in a way looking at the screen would not reveal — the marker landing half a tick off the
 * tick it belongs on. See `ZoomsTest`.
 */
object Zooms {

    /**
     * The ratio at [fraction] along a strip that tops out at [max].
     *
     * @param fraction 0 at the left of the strip, 1 at the right. Clamped, because a drag can be
     *   reported a pixel or two outside the bounds of the thing being dragged.
     */
    fun at(fraction: Float, max: Float): Float {
        val top = max.coerceAtLeast(1f)
        if (top <= 1f) return 1f
        return top.pow(fraction.coerceIn(0f, 1f))
    }

    /**
     * Where [zoom] sits, as a fraction of the strip.
     *
     * The inverse of [at], and it has to stay the inverse: the ticks are placed with this and the
     * drag reads with that, so a disagreement between them is a marker that does not land on the
     * tick you dragged it to.
     */
    fun positionOf(zoom: Float, max: Float): Float {
        val top = max.coerceAtLeast(1f)
        if (top <= 1f) return 0f
        val at = zoom.coerceIn(1f, top)
        return (ln(at) / ln(top)).coerceIn(0f, 1f)
    }
}
