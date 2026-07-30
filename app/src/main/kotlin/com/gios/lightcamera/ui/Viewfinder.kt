package com.gios.lightcamera.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
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
 * The marks on the live image, and nothing else.
 *
 * The language is LightOS's own, taken from the drawables its camera ships in
 * `lightphone/light-sdk`: `ic_camera_focus_locking` is four corner brackets and
 * `ic_camera_focus_locked` is a closed square. So a hunting lens draws brackets, and the
 * moment it locks they close into a box. Those two frames are the entire focus vocabulary of
 * the stock camera and they are worth matching exactly — it is the one animation on the
 * screen that carries information, and it is the one a Light Phone owner already knows.
 *
 * Faces are closed rectangles, heavier on the one the lens is working on. Everything is
 * stroked at the same two weights and there is no fill anywhere, because the point of a
 * viewfinder is the photograph and not the marks.
 *
 * One `Canvas` for all of it: these marks have to agree with each other pixel for pixel, and
 * a composable per mark would each round its own way.
 */
@Composable
fun FrameOverlay(
    chrome: Chrome,
    faces: List<FaceBox>,
    priority: FaceBox?,
    afState: AfState,
    focusPoint: Pair<Float, Float>?,
    tilt: Float,
    /** True while the phone has been crooked recently enough for the level to be useful. */
    levelVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    val colours = LightThemeTokens.colors

    // The brackets close as the lens locks. 90 ms because that is roughly how long the lens
    // takes, and the two should agree.
    val closing by animateFloatAsState(
        targetValue = if (afState == AfState.Locked) 1f else 0f,
        animationSpec = tween(durationMillis = 90),
        label = "focus-close",
    )

    Canvas(modifier = modifier) {
        val hair = 1.4.dp.toPx()
        val heavy = 2.4.dp.toPx()

        if (chrome == Chrome.Thirds) {
            drawThirds(colours.contentSecondary.copy(alpha = 0.30f), hair)
        }

        // Every face the camera can see gets a box. The one it is focusing on is drawn by the
        // focus indicator below instead, so it isn't boxed twice.
        faces.forEach { face ->
            if (face === priority) return@forEach
            drawRect(
                color = colours.content.copy(alpha = 0.62f),
                topLeft = face.topLeft(),
                size = face.boxSize(),
                style = Stroke(width = hair),
            )
        }

        // The subject: a face if there is one, otherwise wherever focus was asked for, and
        // failing that the centre of the frame while a focus run is in flight.
        val target = when {
            priority != null -> priority.squared()
            focusPoint != null -> squareAt(focusPoint.first, focusPoint.second, 62.dp.toPx())
            afState != AfState.Idle -> squareAt(size.width / 2f, size.height / 2f, 62.dp.toPx())
            else -> null
        }
        if (target != null && (afState != AfState.Idle || priority != null)) {
            drawFocusIndicator(
                rect = target,
                progress = closing,
                colour = when (afState) {
                    AfState.Failed -> colours.contentSecondary
                    AfState.Idle -> colours.content.copy(alpha = 0.62f)
                    else -> colours.content
                },
                stroke = if (afState == AfState.Locked) heavy else hair,
            )
        }

        if (levelVisible) {
            drawLevel(tilt, colours.content, colours.contentSecondary, hair)
        }
    }
}

private fun FaceBox.topLeft() = Offset(left, top)

private fun FaceBox.boxSize() = Size(width, height)

/**
 * Faces come back as tall or wide rectangles depending on the detector's mood. The focus box
 * is square, so the subject is squared off around the same centre — it reads as the camera's
 * focus box landing on the face rather than as the face's outline changing weight.
 */
private fun FaceBox.squared(): Rect {
    val side = maxOf(width, height)
    return squareAt(centreX, centreY, side)
}

private fun squareAt(x: Float, y: Float, side: Float) =
    Rect(Offset(x - side / 2f, y - side / 2f), Size(side, side))

/**
 * The stock camera's focus mark, tweened between its two states.
 *
 * At `progress` 0 it is `ic_camera_focus_locking`: four corner brackets, each arm 18% of the
 * side, which is the ratio in the drawable (53 of 300). At 1 it is
 * `ic_camera_focus_locked`: one closed square, drawn 6% tighter so the lock reads as the box
 * snapping shut on the subject. In between the arms simply grow, which is the cheapest
 * possible interpolation between those two drawings and happens to look exactly right.
 */
private fun DrawScope.drawFocusIndicator(
    rect: Rect,
    progress: Float,
    colour: Color,
    stroke: Float,
) {
    val inset = rect.width * 0.06f * progress
    val box = Rect(
        rect.left + inset,
        rect.top + inset,
        rect.right - inset,
        rect.bottom - inset,
    )
    if (progress >= 0.999f) {
        drawRect(colour, box.topLeft, box.size, style = Stroke(width = stroke))
        return
    }
    // Arms run from 18% of the side to half of it, at which point the box is closed.
    val arm = min(box.width, box.height) * (0.18f + 0.32f * progress)
    listOf(
        Triple(box.left, box.top, 1f to 1f),
        Triple(box.right, box.top, -1f to 1f),
        Triple(box.left, box.bottom, 1f to -1f),
        Triple(box.right, box.bottom, -1f to -1f),
    ).forEach { (x, y, dir) ->
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
 * The horizon.
 *
 * Two segments with a gap while the phone is tilted, one continuous line the moment it isn't.
 * Closing the gap is the whole signal, and it can be read out of the corner of your eye — the
 * only way a level is any use while you are looking at the picture. It is drawn only when it
 * has something to say; see [rememberLevelVisible].
 */
private fun DrawScope.drawLevel(tilt: Float, level: Color, off: Color, stroke: Float) {
    val centre = Offset(size.width / 2f, size.height / 2f)
    val half = size.width * 0.20f
    val square = abs(tilt) < 1f
    val gap = if (square) 0f else size.width * 0.07f
    rotate(degrees = -tilt, pivot = centre) {
        val colour = if (square) level else off.copy(alpha = 0.7f)
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
 * A strip of sprocket holes, shown only while a roll is loaded.
 *
 * It sits in the black band under the viewfinder with the frame counter, never over the
 * image. Film sprockets are outside the exposed area anyway, and a viewfinder is no place for
 * decoration — but a loaded roll changes what the shutter does, and this is how the app says
 * so at a glance.
 */
@Composable
fun SprocketStrip(
    modifier: Modifier = Modifier,
    height: Dp = 11.dp,
    /** Advances with the frame count, so the film moves when you take a photograph. */
    offsetFrames: Int = 0,
) {
    val colours = LightThemeTokens.colors
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val holeH = size.height * 0.5f
        val holeW = holeH * 1.25f
        val pitch = holeW * 1.9f
        val y = (size.height - holeH) / 2f
        // A third of a pitch per frame: the strip visibly steps along without any one shot
        // seeming to drag the whole roll past the gate.
        val shift = (offsetFrames * pitch / 3f) % pitch
        var x = -pitch + shift
        while (x < size.width + pitch) {
            drawRoundRect(
                color = colours.contentSecondary.copy(alpha = 0.5f),
                topLeft = Offset(x, y),
                size = Size(holeW, holeH),
                cornerRadius = CornerRadius(holeH * 0.28f),
            )
            x += pitch
        }
    }
}

/**
 * The frame counter, as a row of ticks and a number.
 *
 * The number tells you where you are; the ticks tell you how much is left without arithmetic,
 * which is the thing you actually want to know with a camera at your eye. Both, because ticks
 * stop being countable somewhere around twenty.
 */
@Composable
fun RollCounter(roll: Roll?, modifier: Modifier = Modifier) {
    val colours = LightThemeTokens.colors
    if (roll == null) return
    Row(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightText(text = "%02d".format(roll.shot), variant = LightTextVariant.Detail)
        Canvas(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .height(9.dp)
                .weight(1f),
        ) {
            if (roll.length <= 0) return@Canvas
            val pitch = size.width / roll.length
            val tick = 1.2.dp.toPx()
            for (i in 0 until roll.length) {
                val x = pitch * i + pitch / 2f
                val exposed = i < roll.shot
                drawLine(
                    color = if (exposed) {
                        colours.content
                    } else {
                        colours.contentSecondary.copy(alpha = 0.35f)
                    },
                    start = Offset(x, if (exposed) 0f else size.height * 0.35f),
                    end = Offset(x, size.height),
                    strokeWidth = tick,
                    cap = StrokeCap.Square,
                )
            }
        }
        LightText(
            text = "%02d".format(roll.length),
            variant = LightTextVariant.Detail,
            lighten = true,
        )
    }
}
