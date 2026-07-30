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
            val y = image.planes[0]
            val yBuffer = y.buffer
            var offset = 0
            if (y.rowStride == width) {
                yBuffer.get(out, 0, width * height)
                offset = width * height
            } else {
                // Padded rows: copy a row at a time and leave the padding behind.
                val row = ByteArray(y.rowStride)
                for (line in 0 until height) {
                    yBuffer.position(line * y.rowStride)
                    yBuffer.get(row, 0, minOf(y.rowStride, yBuffer.remaining() + 0))
                    row.copyInto(out, offset, 0, width)
                    offset += width
                }
            }

            // ---- chroma, V then U ----
            val u = image.planes[1]
            val v = image.planes[2]
            val uBuffer = u.buffer
            val vBuffer = v.buffer
            val chromaHeight = height / 2
            val chromaWidth = width / 2

            if (v.pixelStride == 2 && v.rowStride == width && u.pixelStride == 2) {
                // Already interleaved as VU with the right stride: the whole plane is the answer.
                vBuffer.get(out, offset, minOf(vBuffer.remaining(), out.size - offset))
                return LiveFrame(out, width, height, image.imageInfo.rotationDegrees)
            }

            for (line in 0 until chromaHeight) {
                var vIndex = line * v.rowStride
                var uIndex = line * u.rowStride
                for (col in 0 until chromaWidth) {
                    if (offset + 1 >= out.size) break
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
