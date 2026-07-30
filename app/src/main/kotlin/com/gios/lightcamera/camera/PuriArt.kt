package com.gios.lightcamera.camera

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.gios.lightcamera.filter.FaceQuad
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The furniture that goes *on top of* a Purikura: a frame, some stickers and a date.
 *
 * **Drawn, not shaded, and drawn exactly once.** The look of the photograph — the eyes, the skin,
 * the wash — is a shader, because a shader is the only way the preview and the file can be the same
 * image. This is the opposite kind of thing: hard-edged vector work with text in it, which a
 * fragment shader is a miserable way to make and a `Canvas` is a good one. So it lives here, and the
 * viewfinder draws it with the same calls the shutter does, into a transparent overlay rather than
 * into the photograph, and both paths get the same picture for the same reason as before: there is
 * only one implementation.
 *
 * Everything is measured in [unit]s — a hundredth of the frame's short edge — so a frame drawn onto
 * a 1080-pixel preview and onto a 4000-pixel photograph is the same frame.
 *
 * What is on a given photograph is decided by [plan] from a seed, which the view model holds still
 * between shots. That is what makes the viewfinder honest: the stickers you are looking at are the
 * stickers you are about to get, and they change when you take the picture rather than ten times a
 * second while you are framing it.
 */
object PuriArt {

    /* ---------------- the palette ---------------- */

    private const val PINK = 0xFFEF7BA8.toInt()
    private const val DEEP = 0xFFE2557F.toInt()
    private const val PALE = 0xFFF9C9DE.toInt()
    private const val CREAM = 0xFFFFFAF2.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val GOLD = 0xFFFFD75E.toInt()
    private const val LILAC = 0xFFC07AE8.toInt()
    private const val MINT = 0xFF7CC6DC.toInt()
    private const val INK = 0xFF3A3A3A.toInt()

    private fun fill(colour: Int) = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        this.color = colour
    }

    private fun stroke(colour: Int, width: Float) = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.color = colour
    }

    private fun face(family: String, bold: Boolean = true): Typeface =
        Typeface.create(family, if (bold) Typeface.BOLD else Typeface.NORMAL)

    /* ---------------- frames ---------------- */

    /**
     * A border. [id] is what gets stored, [label] is what the chrome shows.
     *
     * The first is None, so the wheel — or the chip — always has somewhere to be when you do not
     * want a border at all.
     */
    class Frame(val id: String, val label: String, val draw: (Canvas, Float, Float, Float) -> Unit)

    val frames: List<Frame> = listOf(
        Frame("none", "None") { _, _, _, _ -> },

        // ---- the booth classics ----
        Frame("lace", "Lace") { c, w, h, u ->
            val band = u * 7f
            c.drawRect(0f, 0f, w, band, fill(CREAM))
            c.drawRect(0f, h - band, w, h, fill(CREAM))
            c.drawRect(0f, 0f, band, h, fill(CREAM))
            c.drawRect(w - band, 0f, w, h, fill(CREAM))
            // Scallops eaten out of the inside edge of the band, which is what makes it lace
            // rather than a mount.
            val r = u * 2.2f
            val scallop = fill(CREAM)
            var x = r
            while (x < w) {
                c.drawCircle(x, band, r, scallop)
                c.drawCircle(x, h - band, r, scallop)
                x += r * 1.8f
            }
            var y = r
            while (y < h) {
                c.drawCircle(band, y, r, scallop)
                c.drawCircle(w - band, y, r, scallop)
                y += r * 1.8f
            }
            c.drawRect(band, band, w - band, h - band, stroke(PALE, u * 0.5f))
        },

        Frame("hearts", "Hearts") { c, w, h, u ->
            val inset = u * 2.5f
            c.drawRect(inset, inset, w - inset, h - inset, stroke(PINK, u * 0.9f))
            c.drawRect(inset * 1.9f, inset * 1.9f, w - inset * 1.9f, h - inset * 1.9f, stroke(PALE, u * 0.4f))
            val s = u * 6f
            val p = fill(DEEP)
            heart(c, inset * 2.4f, inset * 2.4f, s, p)
            heart(c, w - inset * 2.4f - s, inset * 2.4f, s, p)
            heart(c, inset * 2.4f, h - inset * 2.4f - s, s, p)
            heart(c, w - inset * 2.4f - s, h - inset * 2.4f - s, s, p)
        },

        Frame("ribbon", "Ribbon") { c, w, h, u ->
            val band = u * 9f
            c.drawRect(0f, 0f, w, band, fill(PINK))
            c.drawRect(0f, band, w, band + u * 1.2f, fill(WHITE))
            c.drawRect(0f, 0f, u * 4f, h, fill(PINK))
            c.drawRect(w - u * 4f, 0f, w, h, fill(PINK))
            // The bow, centred on the top band.
            val cx = w / 2f
            val cy = band * 0.5f
            val loop = u * 5f
            val p = fill(DEEP)
            c.drawPath(
                Path().apply {
                    moveTo(cx - u, cy)
                    lineTo(cx - loop, cy - loop * 0.6f)
                    lineTo(cx - loop, cy + loop * 0.6f)
                    close()
                },
                p,
            )
            c.drawPath(
                Path().apply {
                    moveTo(cx + u, cy)
                    lineTo(cx + loop, cy - loop * 0.6f)
                    lineTo(cx + loop, cy + loop * 0.6f)
                    close()
                },
                p,
            )
            c.drawCircle(cx, cy, u * 1.6f, p)
        },

        Frame("filmstrip", "Film") { c, w, h, u ->
            val band = u * 6f
            c.drawRect(0f, 0f, w, band, fill(0xFF2C2A28.toInt()))
            c.drawRect(0f, h - band, w, h, fill(0xFF2C2A28.toInt()))
            val hole = fill(CREAM)
            val hw = u * 3.4f
            val hh = u * 2.6f
            var x = u * 2f
            while (x + hw < w) {
                c.drawRoundRect(RectF(x, band * 0.2f, x + hw, band * 0.2f + hh), u * 0.5f, u * 0.5f, hole)
                c.drawRoundRect(
                    RectF(x, h - band + band * 0.2f, x + hw, h - band + band * 0.2f + hh),
                    u * 0.5f,
                    u * 0.5f,
                    hole,
                )
                x += hw * 1.9f
            }
        },

        Frame("starburst", "Stars") { c, w, h, u ->
            // Only in the margins: a star over somebody's face is a different feature, and it is
            // called a sticker.
            val p = fill(DEEP)
            val pale = fill(PALE)
            val margin = u * 14f
            val rnd = Random(4)
            repeat(22) {
                val edge = rnd.nextInt(4)
                val along = rnd.nextFloat()
                val depth = rnd.nextFloat() * margin
                val (x, y) = when (edge) {
                    0 -> along * w to depth
                    1 -> along * w to h - depth
                    2 -> depth to along * h
                    else -> w - depth to along * h
                }
                val s = u * (1.4f + rnd.nextFloat() * 2.4f)
                if (rnd.nextFloat() > 0.35f) star4(c, x, y, s, p) else c.drawCircle(x, y, s * 0.4f, pale)
            }
        },

        Frame("neon", "Neon") { c, w, h, u ->
            val a = u * 3f
            val dash = android.graphics.DashPathEffect(floatArrayOf(u * 3f, u * 2f), 0f)
            c.drawRect(a, a, w - a, h - a, stroke(LILAC, u * 0.9f).apply { pathEffect = dash })
            val b = u * 6f
            c.drawRect(
                b,
                b,
                w - b,
                h - b,
                stroke(PINK, u * 0.5f).apply {
                    pathEffect = android.graphics.DashPathEffect(floatArrayOf(u, u * 1.6f), 0f)
                },
            )
            val dot = fill(LILAC)
            c.drawCircle(a, a, u * 1.4f, dot)
            c.drawCircle(w - a, a, u * 1.4f, dot)
            c.drawCircle(a, h - a, u * 1.4f, dot)
            c.drawCircle(w - a, h - a, u * 1.4f, dot)
        },

        Frame("window", "Window") { c, w, h, u ->
            // A hole punched in a cream card. Drawn as four rectangles and a ring rather than as a
            // mask, because a mask means another layer and this is a border.
            val cx = w / 2f
            val cy = h / 2f
            val r = min(w, h) * 0.44f
            val card = fill(CREAM)
            val outside = Path().apply {
                addRect(0f, 0f, w, h, Path.Direction.CW)
                addCircle(cx, cy, r, Path.Direction.CCW)
            }
            c.drawPath(outside, card)
            c.drawCircle(cx, cy, r + u * 0.8f, stroke(WHITE, u * 1.6f))
            c.drawCircle(
                cx,
                cy,
                r + u * 3.4f,
                stroke(PINK, u * 0.5f).apply {
                    pathEffect = android.graphics.DashPathEffect(floatArrayOf(u * 0.6f, u * 2f), 0f)
                },
            )
        },

        Frame("glitterband", "Glitter") { c, w, h, u ->
            val band = u * 10f
            c.drawRect(0f, 0f, w, band, fill(0xCCF7CDE1.toInt()))
            c.drawRect(0f, h - band, w, h, fill(0xCCF7CDE1.toInt()))
            val p = fill(WHITE)
            val rnd = Random(9)
            repeat(26) {
                val x = rnd.nextFloat() * w
                val top = rnd.nextBoolean()
                val y = if (top) rnd.nextFloat() * band else h - rnd.nextFloat() * band
                star4(c, x, y, u * (1f + rnd.nextFloat() * 1.8f), p)
            }
        },

        // ---- the wacky ones ----
        Frame("googly", "Googly") { c, w, h, u ->
            // Eyes all the way round, looking inwards. Absurd on purpose, and the pupils really do
            // point at the middle of the frame.
            val band = u * 8f
            c.drawRect(0f, 0f, w, band, fill(CREAM))
            c.drawRect(0f, h - band, w, h, fill(CREAM))
            c.drawRect(0f, 0f, band, h, fill(CREAM))
            c.drawRect(w - band, 0f, w, h, fill(CREAM))
            val white = fill(WHITE)
            val ring = stroke(INK, u * 0.5f)
            val pupil = fill(INK)
            val cx = w / 2f
            val cy = h / 2f
            fun eye(x: Float, y: Float, r: Float) {
                c.drawCircle(x, y, r, white)
                c.drawCircle(x, y, r, ring)
                val dx = cx - x
                val dy = cy - y
                val len = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
                c.drawCircle(x + dx / len * r * 0.4f, y + dy / len * r * 0.4f, r * 0.45f, pupil)
            }
            val rnd = Random(11)
            var x = band * 0.6f
            while (x < w) {
                val r = u * (1.8f + rnd.nextFloat() * 1.4f)
                eye(x, band * 0.5f, r)
                eye(w - x, h - band * 0.5f, r)
                x += u * 8f
            }
            var y = band * 1.8f
            while (y < h - band) {
                val r = u * (1.8f + rnd.nextFloat() * 1.4f)
                eye(band * 0.5f, y, r)
                eye(w - band * 0.5f, h - y, r)
                y += u * 8f
            }
        },

        Frame("leopard", "Leopard") { c, w, h, u ->
            val band = u * 9f
            c.drawRect(0f, 0f, w, band, fill(0xFFF6D9A8.toInt()))
            c.drawRect(0f, h - band, w, h, fill(0xFFF6D9A8.toInt()))
            c.drawRect(0f, 0f, band, h, fill(0xFFF6D9A8.toInt()))
            c.drawRect(w - band, 0f, w, h, fill(0xFFF6D9A8.toInt()))
            // A rosette is a broken ring, not a blob — three or four arcs around a tan centre.
            val dark = stroke(0xFF4A3218.toInt(), u * 0.9f)
            val tan = fill(0xFFC98A3C.toInt())
            val rnd = Random(21)
            fun rosette(x: Float, y: Float, r: Float) {
                c.drawCircle(x, y, r * 0.55f, tan)
                var a = rnd.nextFloat() * 6.28f
                repeat(3) {
                    val sweep = 60f + rnd.nextFloat() * 40f
                    c.drawArc(
                        RectF(x - r, y - r, x + r, y + r),
                        Math.toDegrees(a.toDouble()).toFloat(),
                        sweep,
                        false,
                        dark,
                    )
                    a += 2.1f + rnd.nextFloat() * 0.6f
                }
            }
            repeat(60) {
                val onSide = rnd.nextBoolean()
                val x = if (onSide) {
                    if (rnd.nextBoolean()) rnd.nextFloat() * band else w - rnd.nextFloat() * band
                } else {
                    rnd.nextFloat() * w
                }
                val y = if (onSide) {
                    rnd.nextFloat() * h
                } else {
                    if (rnd.nextBoolean()) rnd.nextFloat() * band else h - rnd.nextFloat() * band
                }
                rosette(x, y, u * (1.6f + rnd.nextFloat() * 1.6f))
            }
        },

        Frame("checker", "Checker") { c, w, h, u ->
            val cell = u * 5f
            val black = fill(INK)
            val pink = fill(PINK)
            var i = 0
            var x = 0f
            while (x < w) {
                if (i % 2 == 0) {
                    c.drawRect(x, 0f, x + cell, cell, black)
                    c.drawRect(x, h - cell, x + cell, h, pink)
                } else {
                    c.drawRect(x, 0f, x + cell, cell, pink)
                    c.drawRect(x, h - cell, x + cell, h, black)
                }
                x += cell
                i++
            }
            var y = cell
            var j = 1
            while (y < h - cell) {
                if (j % 2 == 0) {
                    c.drawRect(0f, y, cell, y + cell, black)
                    c.drawRect(w - cell, y, w, y + cell, pink)
                } else {
                    c.drawRect(0f, y, cell, y + cell, pink)
                    c.drawRect(w - cell, y, w, y + cell, black)
                }
                y += cell
                j++
            }
        },

        Frame("flames", "Flames") { c, w, h, u ->
            // Licking up from the bottom edge, three colours deep, because one colour is a shape
            // and three is a fire.
            fun tongues(colour: Int, scale: Float, count: Int, seed: Int) {
                val path = Path()
                val rnd = Random(seed)
                path.moveTo(0f, h)
                var x = 0f
                val step = w / count
                while (x < w + step) {
                    val tip = h - u * scale * (0.6f + rnd.nextFloat() * 0.9f)
                    path.lineTo(x + step * 0.25f, h - u * scale * 0.2f)
                    path.quadTo(x + step * 0.5f, tip, x + step * 0.75f, h - u * scale * 0.25f)
                    x += step
                }
                path.lineTo(w, h)
                path.close()
                c.drawPath(path, fill(colour))
            }
            tongues(0xFFE23A2E.toInt(), 16f, 7, 5)
            tongues(0xFFF57C1F.toInt(), 11f, 9, 6)
            tongues(GOLD, 6f, 12, 7)
        },

        Frame("slime", "Slime") { c, w, h, u ->
            // Drips down from the top edge, with a bead on the end of the long ones.
            val path = Path()
            path.moveTo(0f, 0f)
            path.lineTo(0f, u * 4f)
            val rnd = Random(13)
            var x = 0f
            val step = u * 9f
            val beads = ArrayList<FloatArray>()
            while (x < w) {
                val drop = u * (3f + rnd.nextFloat() * 12f)
                path.quadTo(x + step * 0.5f, u * 4f + drop, x + step, u * 4f)
                if (drop > u * 9f) beads += floatArrayOf(x + step * 0.5f, u * 4f + drop, u * 1.6f)
                x += step
            }
            path.lineTo(w, 0f)
            path.close()
            val slime = fill(0xFF7BD46B.toInt())
            c.drawPath(path, slime)
            beads.forEach { c.drawCircle(it[0], it[1] + it[2] * 0.6f, it[2], slime) }
            c.drawRect(0f, 0f, w, u * 2f, fill(0xFF5CB84C.toInt()))
        },
    )

    /**
     * The id that means "surprise me", for a frame, a date or a strip layout.
     *
     * **Resolved from the seed, not from a fresh coin.** The viewfinder and the shutter both ask what
     * the frame is, and they have to get the same answer or the preview is a lie — so Random is a
     * *deterministic* choice made from the same seed the stickers come from, which the view model holds
     * still between shots and rolls once after each. The effect is a different frame every photograph
     * and a stable one while you are composing.
     */
    const val RANDOM = "random"

    fun frameById(id: String?): Frame = frames.firstOrNull { it.id == id } ?: frames.first()

    /** [frameById], except that [RANDOM] picks one — never None, since you asked for a frame. */
    fun resolveFrame(id: String?, seed: Long): Frame =
        if (id == RANDOM) {
            val real = frames.drop(1)
            real[(Random(seed xor 0x5EED_F5A3L).nextInt(real.size))]
        } else {
            frameById(id)
        }

    fun dateById(id: String?): DateStyle? = dates.firstOrNull { it.id == id }

    /** Null for off, one of the eight for [RANDOM] or for a named style. */
    fun resolveDate(id: String?, seed: Long): DateStyle? = when (id) {
        null, OFF -> null
        RANDOM -> dates[Random(seed xor 0x0A7E_5EEDL).nextInt(dates.size)]
        else -> dateById(id)
    }

    /** The id that means no date at all. */
    const val OFF = "off"

    /* ---------------- stickers ---------------- */

    /** Where a sticker wants to be. The face-anchored ones need a face and skip themselves without. */
    enum class Anchor { Head, Cheeks, Eyes, Free }

    class Sticker(
        val id: String,
        val anchor: Anchor,
        /** Drawn into a box [size] across, centred on the origin. */
        val draw: (Canvas, Float) -> Unit,
    )

    val stickers: List<Sticker> = listOf(
        Sticker("catears", Anchor.Head) { c, s ->
            val outer = fill(0xFF8A6A5E.toInt())
            val inner = fill(PALE)
            fun ear(dir: Float) {
                c.drawPath(
                    Path().apply {
                        moveTo(dir * s * 0.42f, s * 0.12f)
                        lineTo(dir * s * 0.52f, -s * 0.34f)
                        lineTo(dir * s * 0.12f, -s * 0.10f)
                        close()
                    },
                    outer,
                )
                c.drawPath(
                    Path().apply {
                        moveTo(dir * s * 0.38f, s * 0.06f)
                        lineTo(dir * s * 0.44f, -s * 0.22f)
                        lineTo(dir * s * 0.20f, -s * 0.07f)
                        close()
                    },
                    inner,
                )
            }
            ear(-1f)
            ear(1f)
        },
        Sticker("crown", Anchor.Head) { c, s ->
            c.drawPath(
                Path().apply {
                    moveTo(-s * 0.4f, s * 0.14f)
                    lineTo(-s * 0.4f, -s * 0.24f)
                    lineTo(-s * 0.16f, -s * 0.02f)
                    lineTo(0f, -s * 0.30f)
                    lineTo(s * 0.16f, -s * 0.02f)
                    lineTo(s * 0.4f, -s * 0.24f)
                    lineTo(s * 0.4f, s * 0.14f)
                    close()
                },
                fill(GOLD),
            )
            c.drawCircle(0f, -s * 0.34f, s * 0.06f, fill(WHITE))
        },
        Sticker("headbow", Anchor.Head) { c, s ->
            val p = fill(PINK)
            c.drawPath(
                Path().apply {
                    moveTo(-s * 0.06f, 0f)
                    lineTo(-s * 0.38f, -s * 0.18f)
                    lineTo(-s * 0.38f, s * 0.18f)
                    close()
                },
                p,
            )
            c.drawPath(
                Path().apply {
                    moveTo(s * 0.06f, 0f)
                    lineTo(s * 0.38f, -s * 0.18f)
                    lineTo(s * 0.38f, s * 0.18f)
                    close()
                },
                p,
            )
            c.drawCircle(0f, 0f, s * 0.1f, fill(DEEP))
        },
        Sticker("blush", Anchor.Cheeks) { c, s ->
            val p = fill(0x99F8A0BD.toInt())
            c.drawOval(RectF(-s * 0.34f, -s * 0.2f, s * 0.34f, s * 0.2f), p)
        },
        Sticker("shades", Anchor.Eyes) { c, s ->
            val lens = fill(INK)
            c.drawRoundRect(RectF(-s * 0.5f, -s * 0.16f, -s * 0.06f, s * 0.18f), s * 0.08f, s * 0.08f, lens)
            c.drawRoundRect(RectF(s * 0.06f, -s * 0.16f, s * 0.5f, s * 0.18f), s * 0.08f, s * 0.08f, lens)
            c.drawRect(-s * 0.08f, -s * 0.06f, s * 0.08f, s * 0.02f, lens)
        },
        Sticker("heart", Anchor.Free) { c, s -> heart(c, -s / 2f, -s / 2f, s, fill(PINK), WHITE) },
        Sticker("sparkle", Anchor.Free) { c, s -> star4(c, 0f, 0f, s * 0.5f, fill(GOLD)) },
        Sticker("cherries", Anchor.Free) { c, s ->
            c.drawPath(
                Path().apply {
                    moveTo(-s * 0.22f, s * 0.1f)
                    quadTo(-s * 0.05f, -s * 0.3f, s * 0.02f, -s * 0.42f)
                    moveTo(s * 0.2f, s * 0.14f)
                    quadTo(s * 0.14f, -s * 0.2f, s * 0.02f, -s * 0.42f)
                },
                stroke(0xFF6AA84F.toInt(), s * 0.06f),
            )
            c.drawCircle(-s * 0.22f, s * 0.22f, s * 0.18f, fill(DEEP))
            c.drawCircle(s * 0.2f, s * 0.26f, s * 0.15f, fill(PINK))
        },
        Sticker("paw", Anchor.Free) { c, s ->
            val p = fill(WHITE)
            val e = stroke(0xFFA5A5C8.toInt(), s * 0.04f)
            c.drawOval(RectF(-s * 0.1f, -s * 0.04f, s * 0.26f, s * 0.34f), p)
            c.drawOval(RectF(-s * 0.1f, -s * 0.04f, s * 0.26f, s * 0.34f), e)
            listOf(-0.3f to -0.28f, -0.06f to -0.4f, 0.18f to -0.34f, 0.34f to -0.1f).forEach { (dx, dy) ->
                c.drawOval(RectF(s * dx, s * dy, s * (dx + 0.18f), s * (dy + 0.24f)), p)
                c.drawOval(RectF(s * dx, s * dy, s * (dx + 0.18f), s * (dy + 0.24f)), e)
            }
        },
        Sticker("daisy", Anchor.Free) { c, s ->
            val petal = fill(WHITE)
            val edge = stroke(PALE, s * 0.04f)
            repeat(5) { i ->
                val a = i * 1.2566f
                val px = cos(a) * s * 0.26f
                val py = sin(a) * s * 0.26f
                val r = RectF(px - s * 0.16f, py - s * 0.2f, px + s * 0.16f, py + s * 0.2f)
                c.drawOval(r, petal)
                c.drawOval(r, edge)
            }
            c.drawCircle(0f, 0f, s * 0.14f, fill(GOLD))
        },
        Sticker("bolt", Anchor.Free) { c, s ->
            c.drawPath(
                Path().apply {
                    moveTo(-s * 0.12f, -s * 0.46f)
                    lineTo(s * 0.2f, -s * 0.08f)
                    lineTo(0f, -s * 0.08f)
                    lineTo(s * 0.14f, s * 0.46f)
                    lineTo(-s * 0.2f, s * 0.02f)
                    lineTo(0f, s * 0.02f)
                    close()
                },
                fill(GOLD),
            )
        },
        Sticker("butterfly", Anchor.Free) { c, s ->
            c.drawPath(
                Path().apply {
                    moveTo(0f, 0f)
                    cubicTo(-s * 0.1f, -s * 0.4f, -s * 0.52f, -s * 0.44f, -s * 0.5f, -s * 0.12f)
                    cubicTo(-s * 0.48f, s * 0.14f, -s * 0.16f, s * 0.2f, 0f, s * 0.34f)
                    cubicTo(s * 0.16f, s * 0.2f, s * 0.48f, s * 0.14f, s * 0.5f, -s * 0.12f)
                    cubicTo(s * 0.52f, -s * 0.44f, s * 0.1f, -s * 0.4f, 0f, 0f)
                    close()
                },
                fill(LILAC),
            )
            c.drawLine(0f, -s * 0.08f, 0f, s * 0.36f, stroke(WHITE, s * 0.05f))
        },
        Sticker("bubble", Anchor.Free) { c, s ->
            c.drawRoundRect(RectF(-s * 0.5f, -s * 0.3f, s * 0.5f, s * 0.22f), s * 0.26f, s * 0.26f, fill(WHITE))
            c.drawPath(
                Path().apply {
                    moveTo(-s * 0.2f, s * 0.2f)
                    lineTo(-s * 0.28f, s * 0.44f)
                    lineTo(-s * 0.04f, s * 0.22f)
                    close()
                },
                fill(WHITE),
            )
            val text = Paint().apply {
                isAntiAlias = true
                color = 0xFF7A5CC4.toInt()
                typeface = face("sans-serif")
                textSize = s * 0.24f
                textAlign = Paint.Align.CENTER
            }
            c.drawText("KAWAII", 0f, s * 0.02f, text)
        },
        Sticker("rainbow", Anchor.Free) { c, s ->
            val r = RectF(-s * 0.46f, -s * 0.3f, s * 0.46f, s * 0.62f)
            c.drawArc(r, 180f, 180f, false, stroke(PINK, s * 0.12f))
            r.inset(s * 0.12f, s * 0.12f)
            c.drawArc(r, 180f, 180f, false, stroke(GOLD, s * 0.12f))
            r.inset(s * 0.12f, s * 0.12f)
            c.drawArc(r, 180f, 180f, false, stroke(MINT, s * 0.12f))
        },
        Sticker("twinkle", Anchor.Free) { c, s ->
            star4(c, 0f, 0f, s * 0.44f, fill(WHITE))
            c.drawCircle(-s * 0.34f, -s * 0.3f, s * 0.06f, fill(GOLD))
            c.drawCircle(s * 0.36f, s * 0.26f, s * 0.05f, fill(GOLD))
        },
    )

    /* ---------------- dates ---------------- */

    class DateStyle(
        val id: String,
        val label: String,
        val draw: (Canvas, Float, Float, Float, Calendar) -> Unit,
    )

    val dates: List<DateStyle> = listOf(
        DateStyle("capsule", "Capsule") { c, w, h, u, cal ->
            val text = "${cal.get(Calendar.MONTH) + 1}·${cal.get(Calendar.DAY_OF_MONTH)}·${yy(cal)}"
            val paint = Paint().apply {
                isAntiAlias = true
                color = WHITE
                typeface = face("sans-serif")
                textSize = u * 4.4f
                textAlign = Paint.Align.CENTER
            }
            val pillW = paint.measureText(text) + u * 5f
            val pillH = u * 7f
            val cx = w - u * 6f - pillW / 2f
            val cy = h - u * 6f - pillH / 2f
            c.drawRoundRect(
                RectF(cx - pillW / 2f, cy - pillH / 2f, cx + pillW / 2f, cy + pillH / 2f),
                pillH / 2f,
                pillH / 2f,
                fill(PINK),
            )
            c.drawText(text, cx, cy + u * 1.6f, paint)
        },

        DateStyle("marker", "Marker") { c, w, h, u, cal ->
            val text = "${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.DAY_OF_MONTH)}/${yy(cal)}"
            val paint = Paint().apply {
                isAntiAlias = true
                color = DEEP
                typeface = Typeface.create("casual", Typeface.BOLD)
                textSize = u * 6f
                textAlign = Paint.Align.RIGHT
            }
            c.save()
            c.rotate(-7f, w - u * 6f, h - u * 6f)
            c.drawText("$text ♥", w - u * 6f, h - u * 6f, paint)
            c.restore()
        },

        DateStyle("ticket", "Ticket") { c, w, h, u, cal ->
            val text = "${month(cal)} ${cal.get(Calendar.DAY_OF_MONTH)} '${yy(cal)}"
            val paint = Paint().apply {
                isAntiAlias = true
                color = 0xFFB4752F.toInt()
                typeface = face("sans-serif-condensed")
                textSize = u * 4f
                letterSpacing = 0.14f
                textAlign = Paint.Align.CENTER
            }
            val tw = paint.measureText(text) + u * 5f
            val th = u * 7f
            val right = w - u * 6f
            val bottom = h - u * 6f
            val r = RectF(right - tw, bottom - th, right, bottom)
            c.drawRect(r, fill(CREAM))
            c.drawRect(
                r,
                stroke(0xFFD9A05B.toInt(), u * 0.4f).apply {
                    pathEffect = android.graphics.DashPathEffect(floatArrayOf(u * 0.8f, u * 0.8f), 0f)
                },
            )
            // The perforation: a bite out of each end, which is the whole reason a stub reads as one.
            c.drawCircle(r.left, r.centerY(), u * 1.1f, fill(0x00000000))
            c.drawText(text, r.centerX(), r.centerY() + u * 1.4f, paint)
        },

        DateStyle("sticker", "Sticker") { c, w, h, u, cal ->
            val text = "%02d.%02d.%02d".format(
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH),
                yy(cal),
            )
            val outline = Paint().apply {
                isAntiAlias = true
                color = 0xFF7A5CC4.toInt()
                typeface = face("sans-serif")
                textSize = u * 6.4f
                style = Paint.Style.STROKE
                strokeWidth = u * 1.6f
                strokeJoin = Paint.Join.ROUND
                textAlign = Paint.Align.RIGHT
            }
            val body = Paint(outline).apply {
                style = Paint.Style.FILL
                color = WHITE
            }
            c.save()
            c.rotate(-6f, w - u * 6f, h - u * 6f)
            c.drawText(text, w - u * 6f, h - u * 6f, outline)
            c.drawText(text, w - u * 6f, h - u * 6f, body)
            c.restore()
        },

        DateStyle("serial", "Serial") { c, w, h, u, cal ->
            val text = "%02d.%02d.%02d · PURI".format(
                yy(cal),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH),
            )
            val paint = Paint().apply {
                isAntiAlias = true
                color = 0xE64A4A48.toInt()
                typeface = face("monospace")
                textSize = u * 3.2f
                letterSpacing = 0.18f
                textAlign = Paint.Align.RIGHT
            }
            c.drawText(text, w - u * 5f, h - u * 8f, paint)
            c.drawText(
                "NO.%04d".format(cal.get(Calendar.DAY_OF_YEAR) * 7 % 9999),
                w - u * 5f,
                h - u * 4.4f,
                Paint(paint).apply {
                    textSize = u * 2.2f
                    color = 0xB39A9A96.toInt()
                },
            )
        },

        DateStyle("cloud", "Cloud") { c, w, h, u, cal ->
            val text = "${cal.get(Calendar.MONTH) + 1}.${cal.get(Calendar.DAY_OF_MONTH)}"
            val cx = w - u * 13f
            val cy = h - u * 12f
            val blob = fill(WHITE)
            c.drawCircle(cx - u * 4f, cy + u * 1f, u * 3.4f, blob)
            c.drawCircle(cx, cy - u * 1.6f, u * 4.4f, blob)
            c.drawCircle(cx + u * 4.4f, cy + u * 0.6f, u * 3.6f, blob)
            c.drawCircle(cx + u * 0.6f, cy + u * 2.4f, u * 3.4f, blob)
            val paint = Paint().apply {
                isAntiAlias = true
                color = 0xFF3F9FBB.toInt()
                typeface = face("sans-serif")
                textSize = u * 4f
                textAlign = Paint.Align.CENTER
            }
            c.drawText(text, cx, cy + u * 1.4f, paint)
        },

        DateStyle("startag", "Star") { c, w, h, u, cal ->
            val cx = w - u * 12f
            val cy = h - u * 12f
            val r = u * 9f
            val path = Path()
            repeat(10) { i ->
                val a = -1.5708 + i * 0.6283
                val rad = if (i % 2 == 0) r else r * 0.46f
                val x = cx + (cos(a) * rad).toFloat()
                val y = cy + (sin(a) * rad).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            c.drawPath(path, fill(GOLD))
            c.drawPath(path, stroke(0xFFE0AA2A.toInt(), u * 0.5f))
            val paint = Paint().apply {
                isAntiAlias = true
                color = 0xFF8A5C00.toInt()
                typeface = face("sans-serif")
                textSize = u * 3.2f
                textAlign = Paint.Align.CENTER
            }
            c.drawText(
                "${cal.get(Calendar.MONTH) + 1}${cal.get(Calendar.DAY_OF_MONTH)}",
                cx,
                cy + u * 1.2f,
                paint,
            )
        },

        DateStyle("diary", "Diary") { c, w, h, u, cal ->
            val top = Paint().apply {
                isAntiAlias = true
                color = 0xFF6B5670.toInt()
                typeface = face("serif", bold = false)
                textSize = u * 4.2f
                letterSpacing = 0.2f
                textAlign = Paint.Align.RIGHT
            }
            val right = w - u * 6f
            val base = h - u * 9f
            c.drawText(
                "${month(cal)} ${cal.get(Calendar.DAY_OF_MONTH)}",
                right,
                base,
                top,
            )
            c.drawLine(right - top.measureText("${month(cal)} 30"), base + u * 1.4f, right, base + u * 1.4f, stroke(0xFFC9B3CE.toInt(), u * 0.3f))
            c.drawText(
                cal.get(Calendar.YEAR).toString(),
                right,
                base + u * 5f,
                Paint(top).apply {
                    textSize = u * 2.8f
                    letterSpacing = 0.3f
                    color = 0xFF9A86A0.toInt()
                },
            )
        },
    )

    private fun yy(cal: Calendar) = cal.get(Calendar.YEAR) % 100

    private fun month(cal: Calendar) = arrayOf(
        "JAN", "FEB", "MAR", "APR", "MAY", "JUN",
        "JUL", "AUG", "SEP", "OCT", "NOV", "DEC",
    )[cal.get(Calendar.MONTH)]

    /* ---------------- what goes on this photograph ---------------- */

    /** One sticker, placed. Normalised, so the same plan draws onto any size of image. */
    class Placed(val sticker: Sticker, val cx: Float, val cy: Float, val size: Float, val turn: Float)

    /**
     * Everything random about a Purikura, decided once.
     *
     * From a seed, so the viewfinder and the file agree: the view model keeps the seed still while
     * you frame and rolls a new one after each shot. Without that the stickers would rearrange
     * themselves ten times a second while you were trying to compose.
     */
    class Plan(val placed: List<Placed>, val date: DateStyle?)

    /**
     * Choose and place the stickers, and choose a date.
     *
     * Face-anchored stickers come first because they are the point — cat ears on a head land where a
     * head is, which needs [faces]. The free ones are then thrown into the margins, and *away* from
     * every face: a booth covers the edges of a print in stars and leaves the faces alone, having
     * just spent all that effort on the eyes.
     */
    fun plan(
        seed: Long,
        faces: List<FaceQuad>,
        faceStickers: Boolean,
        marginStickers: Boolean,
        /** [OFF], [RANDOM] or the id of one of [dates]. */
        dateId: String,
    ): Plan {
        val rnd = Random(seed)
        val out = ArrayList<Placed>()
        // Rolled in a fixed order whether or not each kind is switched on, so that turning the margin
        // stickers off does not silently change which ears you get.
        if (faceStickers) {
            faces.forEach { f ->
                if (rnd.nextFloat() < 0.65f) {
                    val head = stickers.filter { it.anchor == Anchor.Head }.random(rnd)
                    // Sat on the top of the head, which is above the *rectangle* — the detector
                    // reports the face, not the hair.
                    out += Placed(head, f.cx, f.cy - f.hh * 1.15f, f.hw * 2.3f, rnd.nextFloat() * 12f - 6f)
                }
                if (rnd.nextFloat() < 0.7f) {
                    val blush = stickers.first { it.id == "blush" }
                    out += Placed(blush, f.cx - f.hw * 0.52f, f.cy + f.hh * 0.22f, f.hw * 0.9f, 0f)
                    out += Placed(blush, f.cx + f.hw * 0.52f, f.cy + f.hh * 0.22f, f.hw * 0.9f, 0f)
                }
                if (rnd.nextFloat() < 0.22f) {
                    out += Placed(
                        stickers.first { it.id == "shades" },
                        f.cx,
                        f.cy - f.hh * 0.28f,
                        f.hw * 2.1f,
                        0f,
                    )
                }
            }
        }
        if (marginStickers) {
            val free = stickers.filter { it.anchor == Anchor.Free }
            val count = 2 + rnd.nextInt(3)
            var tries = 0
            var made = 0
            while (made < count && tries < 40) {
                tries++
                val edge = rnd.nextInt(4)
                val along = 0.08f + rnd.nextFloat() * 0.84f
                val depth = 0.05f + rnd.nextFloat() * 0.13f
                val cx = when (edge) {
                    0, 1 -> along
                    2 -> depth
                    else -> 1f - depth
                }
                val cy = when (edge) {
                    0 -> depth
                    1 -> 1f - depth
                    else -> along
                }
                // Keep off the faces.
                val clear = faces.none { f ->
                    kotlin.math.abs(cx - f.cx) < f.hw * 1.4f && kotlin.math.abs(cy - f.cy) < f.hh * 1.4f
                }
                if (!clear) continue
                out += Placed(
                    free.random(rnd),
                    cx,
                    cy,
                    // Bigger than they were. At a tenth of the short edge a heart in the corner of a
                    // 4:3 frame reads as a speck of dust; a booth's are the size of a thumbnail.
                    0.15f + rnd.nextFloat() * 0.09f,
                    rnd.nextFloat() * 40f - 20f,
                )
                made++
            }
        }
        // Resolved from the seed rather than from `rnd`, so that switching a sticker off does not
        // silently change which date you get: the two decisions are independent and should read that
        // way.
        return Plan(out, resolveDate(dateId, seed))
    }

    /**
     * Draw a frame, the planned stickers and the date onto [canvas], which is [w] x [h].
     *
     * Used for the photograph and for the viewfinder overlay, in that order of importance and with
     * the same arguments, which is the only reason the viewfinder can be trusted.
     */
    fun draw(
        canvas: Canvas,
        w: Int,
        h: Int,
        frame: Frame,
        plan: Plan,
        millis: Long,
    ) {
        if (w <= 0 || h <= 0) return
        val unit = min(w, h) / 100f
        frame.draw(canvas, w.toFloat(), h.toFloat(), unit)
        val shortEdge = min(w, h).toFloat()
        plan.placed.forEach { placed ->
            canvas.save()
            canvas.translate(placed.cx * w, placed.cy * h)
            canvas.rotate(placed.turn)
            placed.sticker.draw(canvas, placed.size * shortEdge)
            canvas.restore()
        }
        plan.date?.draw(
            canvas,
            w.toFloat(),
            h.toFloat(),
            unit,
            Calendar.getInstance().apply { timeInMillis = millis },
        )
    }

    /* ---------------- shapes ---------------- */

    /** A heart with its top-left at (x, y), [s] across. */
    private fun heart(canvas: Canvas, x: Float, y: Float, s: Float, paint: Paint, outline: Int? = null) {
        val path = Path().apply {
            moveTo(x + s * 0.5f, y + s)
            cubicTo(x - s * 0.28f, y + s * 0.5f, x + s * 0.1f, y - s * 0.18f, x + s * 0.5f, y + s * 0.26f)
            cubicTo(x + s * 0.9f, y - s * 0.18f, x + s * 1.28f, y + s * 0.5f, x + s * 0.5f, y + s)
            close()
        }
        if (outline != null) {
            canvas.drawPath(
                path,
                Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    strokeWidth = s * 0.14f
                    color = outline
                },
            )
        }
        canvas.drawPath(path, paint)
    }

    /** A four-pointed star centred on (x, y), arms [r] long. The purikura sparkle. */
    private fun star4(canvas: Canvas, x: Float, y: Float, r: Float, paint: Paint) {
        val waist = r * 0.22f
        canvas.drawPath(
            Path().apply {
                moveTo(x, y - r)
                quadTo(x + waist * 0.4f, y - waist * 0.4f, x + r, y)
                quadTo(x + waist * 0.4f, y + waist * 0.4f, x, y + r)
                quadTo(x - waist * 0.4f, y + waist * 0.4f, x - r, y)
                quadTo(x - waist * 0.4f, y - waist * 0.4f, x, y - r)
                close()
            },
            paint,
        )
    }
}
