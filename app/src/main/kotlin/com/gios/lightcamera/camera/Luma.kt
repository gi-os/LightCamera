package com.gios.lightcamera.camera

/**
 * Brightness, read off a frame.
 *
 * **Why this exists at all: greyscale is the hard case for judging exposure.** On a colour screen a
 * blown highlight announces itself — the colour drains out of it before the luminance does, and a
 * face going orange under tungsten is visible long before it is a problem. On the Light Phone the
 * panel is monochrome by default, a face and a window can read as the same grey, and the thing you
 * cannot see at all is the difference between a highlight that is nearly white and one that has
 * gone to 255 and taken the detail with it. A photographer with a film camera had a meter for
 * this. This is the meter.
 *
 * Two readings, from one pass:
 *
 *  - a **histogram**, 64 bins, which says whether the whole frame is piled up at one end;
 *  - a **clipping grid**, coarse cells flagged where the pixels have gone to pure white, which
 *    says *where*.
 *
 * No Compose and no Android in here, and the arithmetic is deliberately dull, because this is the
 * part that can be wrong in a way that looking at the screen would not reveal — a histogram that is
 * subtly the wrong shape still looks like a histogram. See `LumaTest`.
 */
object Luma {

    /** Enough bins to show a shape, few enough that each one is a readable column at 72dp. */
    const val BINS = 64

    /** Cells across and down the clipping grid. A cell is roughly a fingertip on this panel. */
    const val CELLS_X = 24
    const val CELLS_Y = 32

    /**
     * Where "blown" starts.
     *
     * 250 rather than 255: an 8-bit JPEG from this ISP rarely reports a clean 255 even on a
     * specular highlight — there is noise and a tone curve between the sensor and the byte — so a
     * threshold at the ceiling flags almost nothing and reads as a broken feature. Everything from
     * here up is recoverable in no editor.
     */
    const val BLOWN = 250

    /**
     * A reading, both halves.
     *
     * @param counts [BINS] buckets, darkest first.
     * @param blown one flag per cell, row-major, [CELLS_X] wide. True where most of the cell is at
     *   or above [BLOWN].
     * @param peak the largest bucket, kept so the drawing can scale without walking the array again.
     */
    class Reading(val counts: IntArray, val blown: BooleanArray, val peak: Int)

    /**
     * Read [pixels], which is `Bitmap.getPixels` output — ARGB ints, row-major, [width] wide.
     *
     * **Every pixel, because the bitmap handed in is already small.** The caller downscales to
     * something like 128x96 first, which is where the saving is: sampling every fourth pixel of a
     * full panel frame still costs the readback of the full panel frame, and the readback is the
     * expensive half. At this size the whole pass is a few hundred microseconds.
     */
    fun read(pixels: IntArray, width: Int, height: Int): Reading {
        val counts = IntArray(BINS)
        val bright = IntArray(CELLS_X * CELLS_Y)
        val total = IntArray(CELLS_X * CELLS_Y)
        if (width <= 0 || height <= 0) return Reading(counts, BooleanArray(CELLS_X * CELLS_Y), 0)

        for (y in 0 until height) {
            val cellY = (y * CELLS_Y / height).coerceIn(0, CELLS_Y - 1)
            val row = y * width
            for (x in 0 until width) {
                val p = pixels[row + x]
                // Rec. 601 luma, in integers. The same weighting the eye uses and the same one
                // every histogram in every camera has used since the first one.
                val v = (
                    ((p shr 16 and 0xFF) * 299) +
                        ((p shr 8 and 0xFF) * 587) +
                        ((p and 0xFF) * 114)
                    ) / 1000
                counts[(v * BINS / 256).coerceIn(0, BINS - 1)]++
                val cell = cellY * CELLS_X + (x * CELLS_X / width).coerceIn(0, CELLS_X - 1)
                total[cell]++
                if (v >= BLOWN) bright[cell]++
            }
        }

        // **A third of the cell, not one pixel of it.** A single hot pixel in a cell is noise, and
        // hatching a cell for it would put marks all over a correctly exposed night frame. A third
        // is enough that the mark means the area is gone rather than speckled.
        val blown = BooleanArray(CELLS_X * CELLS_Y) { i ->
            total[i] > 0 && bright[i] * 3 >= total[i]
        }
        return Reading(counts, blown, counts.max())
    }
}
