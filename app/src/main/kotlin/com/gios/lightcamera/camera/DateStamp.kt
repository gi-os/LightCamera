package com.gios.lightcamera.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.util.Calendar

/**
 * The quartz date back, burned into the photograph.
 *
 * Every compact camera from about 1986 to about 2006 could print the date in the corner of the
 * frame, in amber, and it is the one piece of camera furniture everybody now wants back: the date on
 * a photograph, put there by a camera that had no idea what year it would be looked at in.
 *
 * Four things make it, and they were all read off photographs rather than guessed:
 *
 *  - **A dot matrix, not a typeface.** A date back exposed a small LED array through the film gate,
 *    so close up the digits are plainly discrete round lamps with the picture showing between them.
 *    Each glyph here is a 5x7 bitmask and each lit cell is a circle a little under half a cell
 *    across. A real font — even a pixel font — gets hinted and kerned and comes out looking like a
 *    screenshot of a font rather than like lamps behind a mask.
 *  - **Sized to the frame, not to the pixels.** The cell is a fraction of the image, so the stamp is
 *    the same size relative to the photograph at 2MP and at 50MP.
 *  - **It leans**, about twelve degrees, and because the glyph is a grid of cells the lean comes out
 *    as a staircase. Shearing a typeface would give clean diagonals and the wrong decade.
 *  - **It glows.** Amber-green at nine tenths opacity so the picture shows through the way a light
 *    does rather than sitting on top like paint, with a second pass underneath, larger and barely
 *    there, for the halation of a bright lamp against emulsion. Without it the stamp reads as a
 *    watermark.
 */
object DateStamp {

    /**
     * `11  5 '21` — month, day, then the two-digit year behind an apostrophe.
     *
     * Month-day-year with the year last and apostrophised is what the Japanese compacts printed on
     * their American firmware, and it is the order in the photographs this was built from. Days and
     * months are **space**-padded rather than zero-padded, which is why the fifth of a month sits
     * with a gap in front of it.
     */
    fun format(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val year = cal.get(Calendar.YEAR) % 100
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        return "%2d %2d '%02d".format(month, day, year)
    }

    /**
     * Draw the stamp onto [bitmap], returning a bitmap that has it.
     *
     * Takes a copy when handed an immutable bitmap, which a freshly decoded JPEG always is.
     */
    fun apply(bitmap: Bitmap, millis: Long): Bitmap {
        val target = if (bitmap.isMutable) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, true).also { bitmap.recycle() }
        }
        val text = format(millis)
        val canvas = Canvas(target)

        // One glyph is seven cells tall, and the whole stamp about a twenty-fifth of the frame —
        // measured off photographs from a Canon Sure Shot, which is as principled as this gets.
        val cell = (minOf(target.width, target.height) / 175f).coerceAtLeast(1.5f)
        val glyphW = 5 * cell
        val glyphH = 7 * cell
        val gap = cell
        // The lean pushes the top row right, so the run is that much wider than the glyph boxes.
        val overhang = 6 * cell * SLANT
        val width = text.length * (glyphW + gap) - gap + overhang
        val right = target.width - cell * 8
        val bottom = target.height - cell * 8
        var x = right - width
        val y = bottom - glyphH

        val glow = Paint().apply {
            // Circles, so antialiasing goes back on — a hard-edged circle at this size is a
            // polygon. The squares it replaced wanted it off for exactly the opposite reason.
            isAntiAlias = true
            color = Color.argb(58, 214, 232, 96)
        }
        val lamp = Paint().apply {
            isAntiAlias = true
            color = Color.argb(230, 205, 222, 74)
        }

        text.forEach { ch ->
            val rows = GLYPHS[ch]
            if (rows != null) {
                // Halation first, so the lit cells sit on top of their own bloom.
                drawGlyph(canvas, rows, x, y, cell, glow, spill = cell * 0.55f)
                drawGlyph(canvas, rows, x, y, cell, lamp, spill = 0f)
            }
            x += glyphW + gap
        }
        return target
    }

    private fun drawGlyph(
        canvas: Canvas,
        rows: Array<String>,
        left: Float,
        top: Float,
        cell: Float,
        paint: Paint,
        spill: Float,
    ) {
        for (row in rows.indices) {
            val bits = rows[row]
            // Sheared right, and by whole rows rather than smoothly: a date back's digits lean,
            // and because the glyph is made of square cells the lean comes out as a staircase.
            // That staircase is a large part of why the originals look the way they do — slanting
            // a real typeface instead gives you clean diagonal edges and the wrong thing entirely.
            val lean = (rows.size - 1 - row) * cell * SLANT
            for (col in bits.indices) {
                if (bits[col] != '1') continue
                val cx = left + col * cell + lean + cell / 2f
                val cy = top + row * cell + cell / 2f
                // **Round dots with gaps between them, not a solid grid.** Close up, a date back
                // is plainly a dot matrix: each lamp prints a small circle and the paper shows
                // between them. Filling whole cells gives you blocky digits that read as a pixel
                // font, which is a different era entirely.
                canvas.drawCircle(cx, cy, cell * DOT + spill, paint)
            }
        }
    }

    /**
     * How far each row leans right, as a fraction of a cell. About twelve degrees over seven
     * rows, which is where the date backs sat.
     */
    private const val SLANT = 0.26f

    /**
     * Dot radius as a fraction of a cell. Under a half leaves the gap between lamps visible, which
     * is the whole character of the thing; at a half exactly the dots touch and it turns into a
     * solid stroke.
     */
    private const val DOT = 0.38f

    /**
     * 5x7 masks. Only the characters a date needs, because a date back could only make those —
     * the originals had ten digits, an apostrophe and a space, and that was the whole ROM.
     */
    private val GLYPHS: Map<Char, Array<String>> = mapOf(
        '0' to arrayOf("01110", "10001", "10001", "10001", "10001", "10001", "01110"),
        '1' to arrayOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
        '2' to arrayOf("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
        '3' to arrayOf("01110", "10001", "00001", "00110", "00001", "10001", "01110"),
        '4' to arrayOf("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
        '5' to arrayOf("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
        '6' to arrayOf("00110", "01000", "10000", "11110", "10001", "10001", "01110"),
        '7' to arrayOf("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
        '8' to arrayOf("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
        '9' to arrayOf("01110", "10001", "10001", "01111", "00001", "00010", "01100"),
        '\'' to arrayOf("00100", "00100", "01000", "00000", "00000", "00000", "00000"),
    )
}
