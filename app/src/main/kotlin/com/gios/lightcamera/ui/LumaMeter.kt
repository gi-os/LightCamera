package com.gios.lightcamera.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.gios.lightcamera.camera.CameraEngine
import com.gios.lightcamera.camera.Luma
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The exposure reading, refreshed while it is being looked at.
 *
 * **Off the panel rather than off an analysis stream, and that is the whole design decision.** A
 * second `ImageAnalysis` bound alongside the preview would hand over YUV frames continuously, with
 * the luma plane already separate and free to read — which is the textbook way to do this and the
 * reason `CameraEngine` binds one in QR mode. It is also a second full-rate consumer of the ISP,
 * which costs power on every frame whether or not anything is reading it, and it changes the
 * camera's bound configuration — the thing this app has spent most of its releases being careful
 * about. So the meter takes the frame that is already on the screen, a few times a second, and
 * nothing about the camera's configuration changes when it is switched on.
 *
 * The cost is a panel readback per sample, which is why [RATE_MS] is a third of a second rather
 * than every frame. A histogram is a thing you glance at while composing; at 3 Hz it responds
 * faster than you can move the phone, and at 30 Hz it would be the same picture drawn ten times
 * as often.
 */
@Composable
fun rememberLuma(engine: CameraEngine, active: Boolean): Luma.Reading? {
    var reading by remember { mutableStateOf<Luma.Reading?>(null) }
    // Reused across samples: a fresh 128x96 bitmap three times a second is a hundred allocations a
    // minute for no reason, and the pixel buffer is the same size every time too.
    val pixels = remember { IntArray(SAMPLE_W * SAMPLE_H) }

    LaunchedEffect(active, engine) {
        if (!active) {
            reading = null
            return@LaunchedEffect
        }
        while (true) {
            val panel = engine.previewFrame()
            if (panel != null) {
                reading = withContext(Dispatchers.Default) {
                    runCatching {
                        // Downscale first. `getPixels` on a full panel frame is 2.5 million ints —
                        // 10MB copied out three times a second — and every one of them would be
                        // averaged away into a 64-bin histogram anyway.
                        val small = Bitmap.createScaledBitmap(panel, SAMPLE_W, SAMPLE_H, true)
                        small.getPixels(pixels, 0, SAMPLE_W, 0, 0, SAMPLE_W, SAMPLE_H)
                        if (small != panel) small.recycle()
                        Luma.read(pixels, SAMPLE_W, SAMPLE_H)
                    }.getOrNull()
                } ?: reading
                // The panel grab is ours to release. Left to the collector this is a 10MB bitmap
                // per sample and the first thing to notice would be the camera stuttering.
                runCatching { panel.recycle() }
            }
            delay(RATE_MS)
        }
    }
    return reading
}

/**
 * Small enough that the whole pass is cheap, large enough that a clipping cell — the grid is
 * 24 x 32 — still has a few pixels of the picture in it to decide with.
 */
private const val SAMPLE_W = 96
private const val SAMPLE_H = 128

/** Three times a second. See the note above. */
private const val RATE_MS = 330L
