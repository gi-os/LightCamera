package com.gios.lightcamera.camera

import android.graphics.ImageFormat
import androidx.camera.core.ImageProxy

/**
 * A frame off the live stream, already in NV21 and ready to encode.
 *
 * **This exists because 1.8 seconds was measured inside `takePicture`.** The still pipeline on this camera
 * is a burst — stacked, denoised, sharpened — and asking it for fast post-processing changed the number by
 * three percent. So Simple stops asking it for anything: the analysis stream delivers frames continuously,
 * this holds the newest one, and pressing the shutter copies what is already in memory.
 *
 * The conversion happens on the camera's own executor as each frame arrives, not at the press. Y'UV out of
 * three planes into one interleaved buffer is a few milliseconds; doing it in advance is what makes the
 * shutter a reference read.
 */
class LiveFrame(
    val nv21: ByteArray,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
) {
    companion object {

        /**
         * `YUV_420_888` to NV21.
         *
         * NV21 is Y followed by **interleaved V and U** — the order is the trap, since the format's name
         * lists them the other way round. `YuvImage` wants NV21 and nothing else, which is why this exists
         * rather than handing the planes straight over.
         *
         * The chroma planes arrive with a row stride and a pixel stride that vary by device: some hand back
         * V and U already interleaved with a pixel stride of two, which the fast path below copies whole,
         * and some hand back two tightly packed planes that have to be woven a byte at a time. Both are
         * common enough that assuming either one is a bug on half the phones in circulation.
         */
        fun from(image: ImageProxy): LiveFrame? {
            if (image.format != ImageFormat.YUV_420_888) return null
            val width = image.width
            val height = image.height
            val out = ByteArray(width * height * 3 / 2)

            // ---- luma ----
            // **Bounds first, cleverness never.** The previous version indexed with `rowStride` and
            // `remaining()` in a way that threw on the last row of a padded plane — and the exception was
            // swallowed by a `runCatching`, so every frame was silently dropped and the shutter reported an
            // empty viewfinder. Everything here is clamped to what the buffer actually holds.
            val y = image.planes[0]
            val yBuffer = y.buffer
            var offset = 0
            for (line in 0 until height) {
                val start = line * y.rowStride
                if (start >= yBuffer.limit()) break
                val take = minOf(width, yBuffer.limit() - start)
                if (take <= 0) break
                yBuffer.position(start)
                yBuffer.get(out, offset, take)
                offset += take
            }
            // A short luma plane is a frame we cannot use; better to say so than to encode green.
            if (offset < width * height) return null

            // ---- chroma, V then U ----
            val u = image.planes[1]
            val v = image.planes[2]
            val uBuffer = u.buffer
            val vBuffer = v.buffer
            val chromaHeight = height / 2
            val chromaWidth = width / 2

            if (v.pixelStride == 2 && v.rowStride == width && u.pixelStride == 2) {
                // Already interleaved as VU with the right stride: the whole plane is the answer.
                vBuffer.position(0)
                vBuffer.get(out, offset, minOf(vBuffer.limit(), out.size - offset))
                return LiveFrame(out, width, height, image.imageInfo.rotationDegrees)
            }

            for (line in 0 until chromaHeight) {
                var vIndex = line * v.rowStride
                var uIndex = line * u.rowStride
                for (col in 0 until chromaWidth) {
                    if (offset + 1 >= out.size) break
                    if (vIndex >= vBuffer.limit() || uIndex >= uBuffer.limit()) break
                    out[offset++] = vBuffer.get(vIndex)
                    out[offset++] = uBuffer.get(uIndex)
                    vIndex += v.pixelStride
                    uIndex += u.pixelStride
                }
            }
            return LiveFrame(out, width, height, image.imageInfo.rotationDegrees)
        }
    }
}
