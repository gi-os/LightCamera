package com.gios.lightcamera.camera

/**
 * How sharp a frame is, as one number.
 *
 * The variance of the Laplacian: run a second-derivative kernel over the luma and measure how much
 * the result varies. A sharp edge produces a large positive and a large negative response side by
 * side, so an image full of crisp edges has a high variance and the same image out of focus or
 * smeared by a shaking hand has a low one. It is the oldest trick in autofocus and it is the right
 * one here because it needs no reference frame — the frames being compared are all of the same
 * scene, and the only thing that differs between them is how still the phone was.
 *
 * **Comparable within a burst and meaningless outside one.** The number depends on how much detail
 * the scene contains, so a sharp photograph of a blank wall scores lower than a blurred one of a
 * bookshelf. Never compare two scenes with it; only ever pick the largest of a handful of frames
 * taken a few milliseconds apart.
 *
 * No Android in here, so the arithmetic can be tested. See `SharpnessTest`.
 */
object Sharpness {

    /**
     * Score [pixels] — `Bitmap.getPixels` output, ARGB ints, row-major, [width] wide.
     *
     * **The middle of the frame only, by default.** Camera shake blurs everything equally, but a
     * subject moving through an otherwise still frame does not, and the edges of a viewfinder are
     * usually floor and ceiling. Scoring the central portion picks the frame where the thing you
     * pointed at is sharp rather than the one where the pavement is.
     *
     * @param inset how much of each edge to ignore, as a fraction. 0.25 leaves the middle half.
     */
    fun of(pixels: IntArray, width: Int, height: Int, inset: Float = 0.25f): Float {
        if (width < 3 || height < 3) return 0f
        val x0 = (width * inset).toInt().coerceIn(1, width - 2)
        val x1 = (width * (1f - inset)).toInt().coerceIn(x0 + 1, width - 1)
        val y0 = (height * inset).toInt().coerceIn(1, height - 2)
        val y1 = (height * (1f - inset)).toInt().coerceIn(y0 + 1, height - 1)

        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                // The four-neighbour Laplacian. Four taps rather than eight: it responds to the same
                // edges, and this runs eight times per shutter press.
                val v = 4 * luma(pixels[y * width + x]) -
                    luma(pixels[y * width + (x - 1)]) -
                    luma(pixels[y * width + (x + 1)]) -
                    luma(pixels[(y - 1) * width + x]) -
                    luma(pixels[(y + 1) * width + x])
                sum += v
                sumSq += v.toDouble() * v
                n++
            }
        }
        if (n == 0) return 0f
        val mean = sum / n
        return ((sumSq / n) - mean * mean).toFloat()
    }

    private fun luma(argb: Int): Int = (
        ((argb shr 16 and 0xFF) * 299) +
            ((argb shr 8 and 0xFF) * 587) +
            ((argb and 0xFF) * 114)
        ) / 1000
}
