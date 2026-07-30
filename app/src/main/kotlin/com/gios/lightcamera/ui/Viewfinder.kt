package com.gios.lightcamera.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gios.lightcamera.Chrome
import com.gios.lightcamera.camera.AfState
import com.gios.lightcamera.camera.FaceBox
import com.gios.lightcamera.roll.Roll
import com.gios.lightcamera.ui.theme.LightText
import com.gios.lightcamera.ui.theme.LightTextVariant
import com.gios.lightcamera.ui.theme.LightThemeTokens
import kotlin.math.abs
import kotlin.math.min

/**
 * Everything drawn over the live image.
 *
 * One `Canvas` for the lot, because these marks have to agree with each other pixel for
 * pixel — a focus bracket that lands a device pixel off a face bracket looks broken in a way
 * that is hard to name and impossible to unsee. Composables per mark would each round their
 * own way.
 *
 * Nothing here is a Material component and nothing animates on a spring. A viewfinder mark
 * either is somewhere or it isn't; the only motion is the focus bracket snapping in, which
 * is 90ms because that is roughly how long the lens takes and the two should agree.
 */
@Composable
fun FrameOverlay(
    chrome: Chrome,
    faces: List<FaceBox>,
    priority: FaceBox?,
    afState: AfState,
    focusPoint: Pair<Float, Float>?,
    tilt: Float,
    facesSupported: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LightThemeTokens.colors

    // 1.0 while hunting, 0.78 once locked: the bracket closes on the subject, which is the
    // one piece of animation in the app that carries information.
    val bracket by animateFloatAsState(
        targetValue = if (afState == AfState.Locked) 0.78f else 1f,
        animationSpec = tween(durationMillis = 90),
        label = "focus-bracket",
    )

    Canvas(modifier = modifier) {
        val hair = 1.dp.toPx()
        val heavy = 1.8.dp.toPx()

        if (chrome == Chrome.Thirds) drawThirds(colors.contentSecondary.copy(alpha = 0.35f), hair)
        if (chrome == Chrome.Film) drawRangefinderPatch(colors.content.copy(alpha = 0.55f), hair)

        // Faces the camera can see but is not focusing on: present, unemphatic.
        faces.forEach { face ->
            if (face === priority) return@forEach
            drawCornerBrackets(
                rect = face.toRect(),
                colour = colors.contentSecondary.copy(alpha = 0.55f),
                stroke = hair,
                armFraction = 0.20f,
            )
        }

        // The face the lens is actually working on.
        if (priority != null) {
            drawCornerBrackets(
                rect = priority.toRect().inflate((bracket - 1f) * priority.width * 0.5f),
                colour = when (afState) {
                    AfState.Locked -> colors.content
                    AfState.Failed -> colors.contentSecondary
                    else -> colors.content.copy(alpha = 0.75f)
                },
                stroke = if (afState == AfState.Locked) heavy else hair,
                armFraction = 0.28f,
            )
        }

        // A tapped or centre focus point, when it isn't on a face.
        if (focusPoint != null && priority == null && afState != AfState.Idle) {
            val side = 60.dp.toPx() * bracket
            val rect = Rect(
                Offset(focusPoint.first - side / 2f, focusPoint.second - side / 2f),
                Size(side, side),
            )
            drawCornerBrackets(
                rect = rect,
                colour = if (afState == AfState.Failed) {
                    colors.contentSecondary
                } else {
                    colors.content
                },
                stroke = if (afState == AfState.Locked) heavy else hair,
                armFraction = 0.30f,
            )
            if (afState == AfState.Locked) {
                drawLine(
                    color = colors.content,
                    start = Offset(rect.center.x - 5.dp.toPx(), rect.center.y),
                    end = Offset(rect.center.x + 5.dp.toPx(), rect.center.y),
                    strokeWidth = hair,
                )
            }
        }

        if (chrome != Chrome.Clean) drawLevel(tilt, colors.content, colors.contentSecondary, hair)

        // A camera that can't see faces should say so once, quietly, by drawing nothing —
        // but the centre mark has to be there or a half press looks like it did nothing.
        if (!facesSupported && chrome == Chrome.Clean && afState == AfState.Idle) {
            drawCentreTick(colors.contentSecondary.copy(alpha = 0.4f), hair)
        }
    }
}

private fun FaceBox.toRect(): Rect = Rect(left, top, right, bottom)

private fun Rect.inflate(by: Float): Rect =
    Rect(left - by, top - by, right + by, bottom + by)

/**
 * Four corners, not a rectangle.
 *
 * A closed box over someone's face hides the face, which on a 3.92" panel is most of what
 * you were looking at. Corners mark the same area and leave it visible — the reason every
 * camera that has ever been good at this draws corners.
 */
private fun DrawScope.drawCornerBrackets(
    rect: Rect,
    colour: Color,
    stroke: Float,
    armFraction: Float,
) {
    val arm = min(rect.width, rect.height) * armFraction
    if (arm <= 0f) return
    val corners = listOf(
        Triple(rect.left, rect.top, 1f to 1f),
        Triple(rect.right, rect.top, -1f to 1f),
        Triple(rect.left, rect.bottom, 1f to -1f),
        Triple(rect.right, rect.bottom, -1f to -1f),
    )
    corners.forEach { (x, y, dir) ->
        val (dx, dy) = dir
        drawLine(colour, Offset(x, y), Offset(x + arm * dx, y), stroke, StrokeCap.Square)
        drawLine(colour, Offset(x, y), Offset(x, y + arm * dy), stroke, StrokeCap.Square)
    }
}

private fun DrawScope.drawThirds(colour: Color, stroke: Float) {
    for (i in 1..2) {
        val x = size.width * i / 3f
        val y = size.height * i / 3f
        drawLine(colour, Offset(x, 0f), Offset(x, size.height), stroke)
        drawLine(colour, Offset(0f, y), Offset(size.width, y), stroke)
    }
}

/**
 * The rangefinder patch.
 *
 * On a coupled rangefinder this is the bright window where two images converge when the
 * lens is focused. There is nothing to converge here, so it is doing a plainer job: marking
 * where the camera will focus if you half press without picking a subject, and giving the
 * eye somewhere to put the thing it cares about. Which is most of what the patch on an M3
 * does in practice too.
 */
private fun DrawScope.drawRangefinderPatch(colour: Color, stroke: Float) {
    val w = size.width * 0.30f
    val h = w * 0.72f
    val rect = Rect(
        Offset((size.width - w) / 2f, (size.height - h) / 2f),
        Size(w, h),
    )
    drawRect(
        color = colour,
        topLeft = rect.topLeft,
        size = rect.size,
        style = Stroke(width = stroke),
    )
    val tick = w * 0.06f
    drawLine(
        colour,
        Offset(rect.center.x - tick, rect.center.y),
        Offset(rect.center.x + tick, rect.center.y),
        stroke,
    )
    drawLine(
        colour,
        Offset(rect.center.x, rect.center.y - tick),
        Offset(rect.center.x, rect.center.y + tick),
        stroke,
    )
}

private fun DrawScope.drawCentreTick(colour: Color, stroke: Float) {
    val tick = 7.dp.toPx()
    val c = Offset(size.width / 2f, size.height / 2f)
    drawLine(colour, Offset(c.x - tick, c.y), Offset(c.x + tick, c.y), stroke)
    drawLine(colour, Offset(c.x, c.y - tick), Offset(c.x, c.y + tick), stroke)
}

/**
 * The horizon.
 *
 * Two segments with a gap while the phone is tilted, one continuous line when it isn't.
 * Closing the gap is the whole signal — it can be read out of the corner of your eye, which
 * is the only way a level is any use while you are looking at the picture.
 */
private fun DrawScope.drawLevel(tilt: Float, level: Color, off: Color, stroke: Float) {
    val centre = Offset(size.width / 2f, size.height / 2f)
    val half = size.width * 0.22f
    val gap = if (abs(tilt) < 1f) 0f else size.width * 0.075f
    rotate(degrees = -tilt, pivot = centre) {
        val colour = if (abs(tilt) < 1f) level else off.copy(alpha = 0.75f)
        if (gap == 0f) {
            drawLine(
                colour,
                Offset(centre.x - half, centre.y),
                Offset(centre.x + half, centre.y),
                stroke,
            )
        } else {
            drawLine(
                colour,
                Offset(centre.x - half, centre.y),
                Offset(centre.x - gap, centre.y),
                stroke,
            )
            drawLine(
                colour,
                Offset(centre.x + gap, centre.y),
                Offset(centre.x + half, centre.y),
                stroke,
            )
        }
    }
}

/**
 * A strip of sprocket holes.
 *
 * Two of these, one above the frame and one below, and the viewfinder stops being a
 * rectangle on a phone and starts being a frame on a strip of film. They are drawn in the
 * black margin rather than over the image, because film sprockets are outside the exposed
 * area and because covering the picture with decoration would be a poor trade.
 */
@Composable
fun SprocketStrip(
    modifier: Modifier = Modifier,
    height: Dp = 13.dp,
    /** Advances with the frame count, so the film moves when you take a photograph. */
    offsetFrames: Int = 0,
) {
    val colours = LightThemeTokens.colors
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val holeH = size.height * 0.5f
        val holeW = holeH * 1.25f
        val pitch = holeW * 1.9f
        val y = (size.height - holeH) / 2f
        // A third of a pitch per frame: the strip visibly steps along, without any one shot
        // seeming to drag the whole roll past the gate.
        val shift = (offsetFrames * pitch / 3f) % pitch
        var x = -pitch + shift
        while (x < size.width + pitch) {
            drawRoundRect(
                color = colours.contentSecondary.copy(alpha = 0.55f),
                topLeft = Offset(x, y),
                size = Size(holeW, holeH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(holeH * 0.28f),
            )
            x += pitch
        }
    }
}

/**
 * The frame counter, as a row of ticks and a number.
 *
 * A number alone tells you where you are; the ticks tell you how much is left without
 * arithmetic, which is the thing you actually want to know with a camera at your eye. Both,
 * because the ticks stop being countable somewhere around twenty.
 */
@Composable
fun RollCounter(roll: Roll?, modifier: Modifier = Modifier) {
    val colours = LightThemeTokens.colors
    if (roll == null) return
    Row(
        modifier = modifier.padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightText(
            text = "%02d".format(roll.shot),
            variant = LightTextVariant.Superfine,
        )
        Canvas(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .height(10.dp)
                .weight(1f),
        ) {
            if (roll.length <= 0) return@Canvas
            val pitch = size.width / roll.length
            val tick = 1.2.dp.toPx()
            for (i in 0 until roll.length) {
                val x = pitch * i + pitch / 2f
                val exposed = i < roll.shot
                drawLine(
                    color = if (exposed) colours.content else colours.contentSecondary.copy(alpha = 0.35f),
                    start = Offset(x, if (exposed) 0f else size.height * 0.3f),
                    end = Offset(x, size.height),
                    strokeWidth = tick,
                    cap = StrokeCap.Square,
                )
            }
        }
        LightText(
            text = "%02d".format(roll.length),
            variant = LightTextVariant.Superfine,
            lighten = true,
        )
    }
}

/** The frame blinks when the shutter fires. Nothing else says "that happened" as clearly. */
@Composable
fun ShutterBlink(alpha: Float, modifier: Modifier = Modifier) {
    if (alpha <= 0f) return
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(0.dp),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(color = Color.Black.copy(alpha = alpha))
        }
    }
}
