package com.gios.lightcamera.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import java.util.Calendar
import kotlin.math.roundToInt

/**
 * Four photographs, one print.
 *
 * A booth takes four and hands you a strip, and that is the whole feature: the shutter fires four
 * times with a count-in between, each frame gets the Purikura treatment it would have got on its own,
 * and then they are pasted onto a sheet.
 *
 * **The geometry is separate from the drawing on purpose.** [Layout.measure] and [Layout.cellAt] are
 * arithmetic with no `Bitmap` in sight, so the thing that is easy to get wrong — how big the sheet is
 * and where the third photograph goes on it — is checked off-device. [compose] is then only paste,
 * paste, paste, paste.
 *
 * Every measurement is a fraction of the cell's short edge, so a strip of four panel-sized frames and
 * a strip of four 12-megapixel ones are the same strip at different sizes.
 */
object PuriStrip {

    const val SHOTS = 4

    /**
     * How four frames are arranged, and what is left blank around them.
     *
     * @param columns 1 for a strip, 2 for a sheet
     * @param gutter space between frames, as a fraction of the cell's short edge
     * @param margin blank border around the whole sheet, same units
     * @param footer extra blank at the bottom, for the date, same units
     * @param rails dark film rails with perforations down the long edges
     * @param outerFrame draw the chosen Purikura frame once, around the whole strip
     */
    class Layout(
        val id: String,
        val label: String,
        val columns: Int = 1,
        val gutter: Float = 0f,
        val margin: Float = 0f,
        val footer: Float = 0f,
        val rails: Boolean = false,
        val outerFrame: Boolean = false,
    ) {
        val rows: Int get() = SHOTS / columns

        /** The sheet's size for a given frame size. Integers, because bitmaps are. */
        fun measure(cellW: Int, cellH: Int): Pair<Int, Int> {
            val unit = minOf(cellW, cellH).toFloat()
            val g = gutter * unit
            val m = margin * unit
            val f = footer * unit
            val width = cellW * columns + g * (columns - 1) + m * 2
            val height = cellH * rows + g * (rows - 1) + m * 2 + f
            return width.roundToInt().coerceAtLeast(1) to height.roundToInt().coerceAtLeast(1)
        }

        /** Where frame [index] sits on the sheet. Reading order: across, then down. */
        fun cellAt(index: Int, cellW: Int, cellH: Int): Rect {
            val unit = minOf(cellW, cellH).toFloat()
            val g = gutter * unit
            val m = margin * unit
            val col = index % columns
            val row = index / columns
            val left = m + col * (cellW + g)
            val top = m + row * (cellH + g)
            return Rect(
                left.roundToInt(),
                top.roundToInt(),
                (left + cellW).roundToInt(),
                (top + cellH).roundToInt(),
            )
        }
    }

    /**
     * The layouts, starting with off.
     *
     * Off first for the same reason None is the first frame: the setting has to have somewhere to sit
     * when you do not want the feature, and a photograph is the normal case.
     */
    val layouts: List<Layout> = listOf(
        Layout("off", "Off"),

        /** The booth standard: thin white gutters and a footer with the date in it. */
        Layout("classic", "Classic", gutter = 0.035f, margin = 0.045f, footer = 0.13f),

        /** Four frames touching. No white anywhere, so the strip reads as one tall photograph. */
        Layout("bare", "Bare"),

        /** A wide blank mount, like a contact print left in the tray. */
        Layout("mount", "Mount", gutter = 0.06f, margin = 0.13f, footer = 0.16f),

        /** The chosen Purikura frame, drawn once, around all four. */
        Layout("framed", "Framed", gutter = 0.03f, margin = 0.1f, outerFrame = true),

        /** Roll's own film rails down both long edges. */
        Layout("rails", "Rails", gutter = 0.03f, margin = 0.11f, rails = true),

        /** Two by two, which is the only one of these that fits a phone screen without scrolling. */
        Layout("grid", "Grid", columns = 2, gutter = 0.035f, margin = 0.045f, footer = 0.1f),
    )

    fun layoutById(id: String?): Layout = layouts.firstOrNull { it.id == id } ?: layouts.first()

    private const val PAPER = 0xFFFFFDF9.toInt()
    private const val RAIL = 0xFF2C2A28.toInt()
    private const val PINK = 0xFFE2557F.toInt()

    /**
     * Paste [frames] onto a sheet.
     *
     * The frames are expected to be the same size — they come off the same viewfinder — and the first
     * one sets the cell. A short list is padded with blanks rather than refused: four photographs is
     * the intent, but a sequence interrupted by a phone call should still produce something.
     */
    fun compose(
        frames: List<Bitmap>,
        layout: Layout,
        outerFrame: PuriArt.Frame,
        millis: Long,
    ): Bitmap? {
        val first = frames.firstOrNull() ?: return null
        val cellW = first.width
        val cellH = first.height
        val (sheetW, sheetH) = layout.measure(cellW, cellH)
        val sheet = Bitmap.createBitmap(sheetW, sheetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(PAPER)

        val unit = minOf(cellW, cellH).toFloat()

        if (layout.rails) {
            // Rails first, so the photographs sit on top of them and the perforations stay outside
            // the pictures — which is where they are on real film.
            val railW = layout.margin * unit
            canvas.drawRect(0f, 0f, railW, sheetH.toFloat(), Paint().apply { color = RAIL })
            canvas.drawRect(sheetW - railW, 0f, sheetW.toFloat(), sheetH.toFloat(), Paint().apply { color = RAIL })
            val hole = Paint().apply {
                isAntiAlias = true
                color = PAPER
            }
            val hw = railW * 0.5f
            val hh = railW * 0.38f
            var y = railW * 0.4f
            while (y + hh < sheetH) {
                canvas.drawRoundRect(
                    RectF(railW * 0.25f, y, railW * 0.25f + hw, y + hh),
                    hh * 0.25f,
                    hh * 0.25f,
                    hole,
                )
                canvas.drawRoundRect(
                    RectF(sheetW - railW * 0.25f - hw, y, sheetW - railW * 0.25f, y + hh),
                    hh * 0.25f,
                    hh * 0.25f,
                    hole,
                )
                y += hh * 2.1f
            }
        }

        frames.take(SHOTS).forEachIndexed { index, frame ->
            canvas.drawBitmap(frame, null, layout.cellAt(index, cellW, cellH), null)
        }

        if (layout.outerFrame) {
            // The frame is drawn over the *whole sheet*, which is the point of this layout: one
            // border around four photographs rather than four borders.
            outerFrame.draw(
                canvas,
                sheetW.toFloat(),
                sheetH.toFloat(),
                minOf(sheetW, sheetH) / 100f,
            )
        }

        if (layout.footer > 0f) {
            val cal = Calendar.getInstance().apply { timeInMillis = millis }
            val text = "%d · %d · %02d".format(
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH),
                cal.get(Calendar.YEAR) % 100,
            )
            val paint = Paint().apply {
                isAntiAlias = true
                color = PINK
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                textSize = layout.footer * unit * 0.34f
                letterSpacing = 0.16f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(
                text,
                sheetW / 2f,
                sheetH - layout.footer * unit * 0.32f,
                paint,
            )
        }

        return sheet
    }
}
