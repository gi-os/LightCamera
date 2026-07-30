package com.gios.lightcamera.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.gios.lightcamera.StampStyle
import java.util.Calendar

/**
 * The quartz date back, burned into the photograph.
 *
 * Every compact camera from about 1986 to about 2006 could print the date in the corner of the
 * frame, in amber, and it is the one piece of camera furniture everybody now wants back: the date on
 * a photograph, put there by a camera that had no idea what year it would be looked at in.
 *
 * **Three of them**, because they were three different mechanisms and drawing them the same way is
 * what makes fake ones look fake: the compact camera's dot matrix, the film SLR's seven-segment
 * quartz back, and the camcorder's character generator. Each has its own order, padding, colour and
 * typography. See [StampStyle].
 *
 * What the dot matrix needs, all read off photographs rather than guessed:
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
    fun format(millis: Long, style: StampStyle = StampStyle.Dots): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val year = cal.get(Calendar.YEAR) % 100
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        return when (style) {
            // The compact-camera dot matrix, American firmware: month, day, apostrophe-year.
            StampStyle.Dots -> "%2d %2d '%02d".format(month, day, year)
            // The film SLR quartz back put the year first, and zero-padded everything.
            StampStyle.Quartz -> "'%02d %02d %02d".format(year, month, day)
            // Camcorders wrote a full date with slashes and all four digits of the year.
            StampStyle.Outline -> "%02d/%02d/%d".format(month, day, cal.get(Calendar.YEAR))
        }
    }

    /**
     * Draw the stamp onto [bitmap], returning a bitmap that has it.
     *
     * Takes a copy when handed an immutable bitmap, which a freshly decoded JPEG always is.
     */
    fun apply(bitmap: Bitmap, millis: Long, style: StampStyle = StampStyle.Dots): Bitmap {
        val target = if (bitmap.isMutable) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, true).also { bitmap.recycle() }
        }
        val text = format(millis, style)
        val canvas = Canvas(target)
        when (style) {
            StampStyle.Dots -> drawDots(canvas, target, text)
            StampStyle.Quartz -> drawQuartz(canvas, target, text)
            StampStyle.Outline -> drawOutline(canvas, target, text)
        }
        return target
    }

    /* ---------------- dots: the compact-camera matrix ---------------- */

    private fun drawDots(canvas: Canvas, target: Bitmap, text: String) {

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
                // Halation first, so the lit cells sit on top of their own bloom — and kept small,
                // because a wide bloom bridges the gaps between the lamps and undoes the dots.
                drawGlyph(canvas, rows, x, y, cell, glow, spill = cell * 0.22f)
                drawGlyph(canvas, rows, x, y, cell, lamp, spill = 0f)
            }
            x += glyphW + gap
        }
    }

    /* ---------------- quartz: seven segments ---------------- */

    /**
     * The film SLR's date back: **seven segments**, orange-red, leaning.
     *
     * A different mechanism from the dot matrix and so a different drawing. Segments are bars, not
     * lamps — so a `1` is two bars with nothing between them and a `7` has a hard corner, neither of
     * which a 5x7 dot grid can make convincingly. The bars are drawn as parallelograms by shearing
     * their tops, because the whole display leans and a sheared bar is what a leaning segment is.
     */
    private fun drawQuartz(canvas: Canvas, target: Bitmap, text: String) {
        // Sized off the *long* edge and much smaller than the first attempt, which filled a
        // third of the frame. A date back's display is a couple of centimetres of LCD reflected
        // into the corner of a 35mm frame: it is small, and the digits are tall and narrow rather
        // than square. Bars are thin, and each stops a hair short of the corner so the joints
        // show — a seven-segment digit is seven separate bars and it should look like it.
        val unit = (maxOf(target.width, target.height) / 340f).coerceAtLeast(1f)
        val digitW = unit * 7f
        val digitH = unit * 15f
        val thick = unit * 1.7f
        val gap = unit * 3f
        val lean = digitH * 0.11f
        val width = text.length * (digitW + gap) - gap + lean
        var x = target.width - unit * 14 - width
        val y = target.height - unit * 14 - digitH

        val glow = Paint().apply {
            isAntiAlias = true
            color = Color.argb(52, 255, 132, 60)
        }
        val lamp = Paint().apply {
            isAntiAlias = true
            color = Color.argb(238, 240, 86, 30)
        }

        text.forEach { ch ->
            when (ch) {
                ' ' -> Unit
                '\'' -> {
                    // The apostrophe on these was a single short segment, high and leaning.
                    for (paint in listOf(glow, lamp)) {
                        val spill = if (paint === glow) unit * 0.35f else 0f
                        vbar(canvas, x + digitW * 0.5f, y, thick, digitH * 0.26f, lean, paint, spill)
                    }
                }
                else -> SEGMENTS[ch]?.let { on ->
                    for (paint in listOf(glow, lamp)) {
                        val spill = if (paint === glow) unit * 0.35f else 0f
                        drawSegments(canvas, on, x, y, digitW, digitH, thick, lean, paint, spill)
                    }
                }
            }
            x += digitW + gap
        }
    }

    /** `a` top, then clockwise `b c d e f`, and `g` the middle. */
    private fun drawSegments(
        canvas: Canvas,
        on: String,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        thick: Float,
        lean: Float,
        paint: Paint,
        spill: Float,
    ) {
        val half = h / 2f
        // Horizontal bars inset by a bar's width at each end, vertical ones likewise, so every
        // joint has a visible notch. Without it the digits weld into blocks.
        val nick = thick * 0.85f
        if ('a' in on) hbar(canvas, x + nick, y, w - nick * 2, thick, lean, paint, spill)
        if ('g' in on) {
            hbar(canvas, x + nick, y + half - thick / 2f, w - nick * 2, thick, lean * 0.5f, paint, spill)
        }
        if ('d' in on) hbar(canvas, x + nick, y + h - thick, w - nick * 2, thick, 0f, paint, spill)
        val run = half - nick
        if ('f' in on) vbar(canvas, x, y + nick, thick, run, lean * 0.5f, paint, spill)
        if ('b' in on) vbar(canvas, x + w - thick, y + nick, thick, run, lean * 0.5f, paint, spill)
        if ('e' in on) vbar(canvas, x, y + half + nick * 0.5f, thick, run, 0f, paint, spill)
        if ('c' in on) vbar(canvas, x + w - thick, y + half + nick * 0.5f, thick, run, 0f, paint, spill)
    }

    /** A horizontal segment, sheared so its top edge sits right of its bottom. */
    private fun hbar(
        canvas: Canvas,
        x: Float,
        y: Float,
        w: Float,
        thick: Float,
        lean: Float,
        paint: Paint,
        spill: Float,
    ) {
        val path = Path().apply {
            moveTo(x + lean - spill, y - spill)
            lineTo(x + w + lean + spill, y - spill)
            lineTo(x + w + spill, y + thick + spill)
            lineTo(x - spill, y + thick + spill)
            close()
        }
        canvas.drawPath(path, paint)
    }

    /** A vertical segment, likewise. */
    private fun vbar(
        canvas: Canvas,
        x: Float,
        y: Float,
        thick: Float,
        h: Float,
        lean: Float,
        paint: Paint,
        spill: Float,
    ) {
        val path = Path().apply {
            moveTo(x + lean - spill, y - spill)
            lineTo(x + lean + thick + spill, y - spill)
            lineTo(x + thick + spill, y + h + spill)
            lineTo(x - spill, y + h + spill)
            close()
        }
        canvas.drawPath(path, paint)
    }

    /* ---------------- camcorder: a typeface, outlined ---------------- */

    /**
     * The camcorder stamp, and the one style where **a real font is right**.
     *
     * This one was never a lamp array — it was a character generator drawing an ordinary bold sans
     * into the video signal, with a black keyline so it stayed readable over anything. So it is
     * drawn as text: stroke pass first for the outline, fill pass on top, no lean, slashes between
     * the numbers, and all four digits of the year.
     */
    private fun drawOutline(canvas: Canvas, target: Bitmap, text: String) {
        // A character generator drew this into a video line, so it was small — about a
        // twenty-fifth of the frame, not a fifteenth — and the keyline was one pixel of video,
        // which is a hairline here rather than the heavy slab of the first attempt.
        val size = maxOf(target.width, target.height) / 26f
        val outline = Paint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = size
            style = Paint.Style.STROKE
            strokeWidth = size * 0.07f
            color = Color.argb(235, 0, 0, 0)
        }
        val fill = Paint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = size
            color = Color.argb(255, 247, 160, 42)
        }
        val width = fill.measureText(text)
        val x = target.width - size * 0.7f - width
        val y = target.height - size * 0.7f
        canvas.drawText(text, x, y, outline)
        canvas.drawText(text, x, y, fill)
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
     * Dot radius as a fraction of a cell.
     *
     * The gaps are **tiny** — the lamps nearly touch, and what you see between them is a hairline
     * rather than a grid. At 0.42 the dots leave about a sixth of a cell of picture showing, which
     * is what the photographs show; at a half exactly they meet and every stroke turns solid, and
     * much under 0.4 it stops reading as digits and starts reading as beadwork.
     */
    private const val DOT = 0.42f

    /** Which of the seven segments each digit lights. */
    private val SEGMENTS: Map<Char, String> = mapOf(
        '0' to "abcdef",
        '1' to "bc",
        '2' to "abdeg",
        '3' to "abcdg",
        '4' to "bcfg",
        '5' to "acdfg",
        '6' to "acdefg",
        '7' to "abc",
        '8' to "abcdefg",
        '9' to "abcdfg",
    )

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
