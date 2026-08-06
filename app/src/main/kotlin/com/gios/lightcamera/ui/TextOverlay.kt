package com.gios.lightcamera.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.gios.lightcamera.ocr.Found
import com.gios.lightcamera.ocr.Placement
import com.gios.lightcamera.ocr.Reading
import com.gios.lightcamera.ocr.TextBoxes
import com.gios.lightcamera.ocr.ViewBox
import com.gios.lightcamera.ui.theme.LightText
import com.gios.lightcamera.ui.theme.LightTextVariant

/**
 * Where the words were, drawn on the picture they came from.
 *
 * The sheet alone answered "what does this page say" and not "which bit of it said that", and on
 * a menu or a noticeboard the second question is the one you actually have. So the reading is
 * shown over the frame first: every line it found gets a box, and a line carrying something
 * actionable — a number, an address, a link — is marked differently from one that is just words.
 *
 * **Two weights, not a colour.** This panel has no colour to spend and no room for a legend, so
 * the distinction is drawn the way the rest of LightOS draws state: an actionable line is filled
 * and its text will be inverted against it, a plain line gets a hairline. At arm's length the
 * filled ones are the only thing you see, which is the correct summary of the page.
 *
 * Boxes are laid out against the **source** bitmap, then tapped in view coordinates. All of that
 * arithmetic is in [TextBoxes], which has no Android in it and is tested; this file only draws.
 */
@Composable
fun TextOverlay(
    reading: Reading,
    found: List<Found>,
    rotationDegrees: Int,
    /** How the frame underneath is laid into the box — see [TextBoxes.fill] and [fit]. */
    placement: (viewWidth: Float, viewHeight: Float) -> Placement,
    onTapLine: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Which lines carry something worth pressing. A set rather than a search per line: a dense
    // page is a hundred lines and this is read once per frame drawn.
    val actionable = remember(found) { found.mapTo(HashSet()) { it.lineIndex } }

    Box(
        modifier
            .fillMaxSize()
            .pointerInput(reading, rotationDegrees) {
                detectTapGestures { at ->
                    val boxes = layout(reading, rotationDegrees, placement, size.width.toFloat(), size.height.toFloat())
                    TextBoxes.hit(boxes, at.x, at.y)?.let(onTapLine)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val boxes = layout(reading, rotationDegrees, placement, size.width, size.height)
            boxes.forEachIndexed { index, box ->
                val w = box.right - box.left
                val h = box.bottom - box.top
                // A recogniser occasionally reports a degenerate rectangle. Drawing it is a dot
                // on the picture with nothing behind it, which reads as dirt on the lens.
                if (w < 2f || h < 2f) return@forEachIndexed
                val corner = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                if (index in actionable) {
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(box.left, box.top),
                        size = Size(w, h),
                        cornerRadius = corner,
                        // Not solid. A filled box hides the very words it is pointing at, and the
                        // whole promise here is that you can see what was read.
                        alpha = 0.30f,
                    )
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(box.left, box.top),
                        size = Size(w, h),
                        cornerRadius = corner,
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                } else {
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(box.left, box.top),
                        size = Size(w, h),
                        cornerRadius = corner,
                        alpha = 0.55f,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
            }
        }
    }
}

/**
 * Every line's rectangle in view pixels.
 *
 * Recomputed on each draw and on each tap rather than remembered, so the two can never disagree
 * — a stale hit map is a box that highlights the line above the one you pressed, and it would
 * only appear after a rotation.
 */
private fun layout(
    reading: Reading,
    rotationDegrees: Int,
    placement: (Float, Float) -> Placement,
    viewWidth: Float,
    viewHeight: Float,
): List<ViewBox> {
    if (viewWidth <= 0f || viewHeight <= 0f) return emptyList()
    val place = placement(viewWidth, viewHeight)
    return reading.lines.map { line ->
        TextBoxes.toView(line, reading.width, reading.height, rotationDegrees, place)
    }
}

/**
 * The one line of chrome the boxes need: what was found, and the two ways out.
 *
 * Deliberately a strip rather than a sheet. Anything taller starts covering the page it is
 * describing, and the whole reason the boxes exist is that the sheet covered it.
 */
@Composable
fun TextHint(
    found: Int,
    lines: Int,
    onAll: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                ),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightText(
            text = summary(found, lines),
            variant = LightTextVariant.Detail,
            lighten = true,
        )
        Spacer(Modifier.weight(1f))
        ChromeLabel(text = "All text", onClick = onAll)
        Spacer(Modifier.width(14.dp))
        ChromeLabel(text = "Done", onClick = onClose, lighten = true)
    }
}

/**
 * What the strip says.
 *
 * Leads with the count of things worth pressing, because that is the number that decides whether
 * to tap a box or just copy the lot. Falls back to the line count when there is nothing
 * actionable, which is the receipt case — there the honest summary is "this is just words".
 */
private fun summary(found: Int, lines: Int): String = when {
    found == 1 -> "1 thing found · tap it"
    found > 1 -> "$found things found · tap one"
    lines == 1 -> "1 line"
    else -> "$lines lines · nothing to open"
}
