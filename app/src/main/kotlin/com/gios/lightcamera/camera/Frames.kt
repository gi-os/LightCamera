package com.gios.lightcamera.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.gios.lightcamera.StampStyle
import com.gios.lightcamera.filter.FaceQuad
import com.gios.lightcamera.filter.FaceQuads
import com.gios.lightcamera.filter.FaceTune
import com.gios.lightcamera.filter.Filters
import com.gios.lightcamera.filter.ShaderRuntime
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** The shapes a photo can be saved in. The sensor shoots 4:3; the rest are crops. */
enum class FrameAspect(val label: String, val ratio: Float?) {
    /** Whatever the sensor gives. */
    Full("4:3", 4f / 3f),
    Wide("3:2", 3f / 2f),
    Cine("16:9", 16f / 9f),
    Square("1:1", 1f),
    ;

    companion object {
        fun byLabel(label: String?): FrameAspect =
            entries.firstOrNull { it.label == label } ?: Full
    }
}

/**
 * Turning a captured frame into the bytes that get saved.
 *
 * Two paths, and the fast one matters. With no filter and no crop the sensor's own JPEG is
 * written **untouched** — no decode, no re-encode, EXIF intact. Any filtered photo has to
 * be decoded anyway, and once decoded there is no reason not to bake in the rotation and
 * drop the orientation tag, so the file is upright for anything that reads it, however
 * badly.
 */
object Frames {

    /**
     * The longest edge a filtered capture is rendered at.
     *
     * The shader runs on a GPU texture, and every driver has a maximum texture size — 4096
     * is the smallest value in common circulation, and being conservative here costs almost
     * nothing: 4096 x 3072 is 12.6 megapixels.
     */
    private const val MAX_FILTERED_EDGE = 4096

    private const val QUALITY = 95

    class Processed(val jpeg: ByteArray, val width: Int, val height: Int)

    fun process(
        frame: CapturedFrame,
        filter: Filters.Filter,
        aspect: FrameAspect,
        seed: Float,
        stampAt: Long? = null,
        stampStyle: StampStyle = StampStyle.Dots,
    ): Processed {
        val needsCrop = aspect != FrameAspect.Full
        // The date back costs a decode and a re-encode on a photograph that would otherwise have
        // been written exactly as the camera produced it. That is the price of printing on the
        // negative, it only applies when the stamp is on, and it is worth saying out loud.
        if (filter.agsl == null && !needsCrop && stampAt == null) {
            val size = readSize(frame.jpeg)
            return Processed(frame.jpeg, size.first, size.second)
        }

        var bitmap = decodeUpright(frame.jpeg, frame.rotationDegrees, frame.mirrored)
            ?: return Processed(frame.jpeg, 0, 0)

        if (needsCrop) bitmap = crop(bitmap, aspect)
        if (filter.agsl != null) {
            bitmap = downscaleIfHuge(bitmap)
            bitmap = ShaderRuntime.applyToBitmap(bitmap, filter, seed)
        }
        // After the filter, always: a date back printed through the film gate, so the date is on
        // the emulsion and not under it. Dithering the stamp along with the picture would turn the
        // digits into confetti.
        if (stampAt != null) bitmap = DateStamp.apply(bitmap, stampAt, stampStyle)

        val out = ByteArrayOutputStream(bitmap.width * bitmap.height / 6)
        bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
        return Processed(out.toByteArray(), bitmap.width, bitmap.height)
    }

    /**
     * A photograph made out of the viewfinder, for `Screen` size.
     *
     * No capture, no sensor readout, no JPEG from the ISP — the frame already on the panel, turned
     * upright, cropped to the chosen shape, put through the same shader as any other photograph
     * and encoded. It is as fast as this app can be, and with a filter on it is *exactly* the
     * frame you were looking at rather than a second frame processed to match.
     *
     * The shader runs at panel resolution here, which is a fraction of the work it does on a
     * capture — and the pattern-based filters look identical, because `unitPx()` scales them to
     * the image either way.
     */
    fun fromPreview(
        preview: Bitmap,
        rotationDegrees: Int,
        filter: Filters.Filter,
        aspect: FrameAspect,
        seed: Float,
        stampAt: Long? = null,
        stampStyle: StampStyle = StampStyle.Dots,
        /** Faces as found in the preview, normalised — carried through the turn and the crop below. */
        faces: List<FaceQuad> = emptyList(),
        /**
         * Drawn on last, over the finished picture, given the faces after the turn and the crop.
         *
         * A lambda rather than the frame-and-stickers themselves, because this file's job is
         * bytes: it should not know what a Purikura is.
         */
        overlay: ((android.graphics.Canvas, Int, Int, List<FaceQuad>) -> Unit)? = null,
        /** Which parts of a face-aware filter to apply. Ignored by every other filter. */
        tune: FaceTune = FaceTune(),
    ): Processed {
        var bitmap = preview
        var quads = faces
        if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            val turned =
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (turned != bitmap) bitmap.recycle()
            bitmap = turned
            quads = quads.map { FaceQuads.rotated(it, rotationDegrees) }
        }
        if (aspect != FrameAspect.Full) {
            // Measured before and after, because the crop is centred and the shift depends on which
            // way round the frame is. A face has to move with the picture or the warp lands beside it.
            val wasW = bitmap.width
            val wasH = bitmap.height
            bitmap = crop(bitmap, aspect)
            quads = quads.map { FaceQuads.cropped(it, wasW, wasH, bitmap.width, bitmap.height) }
        }
        if (filter.agsl != null) {
            bitmap = ShaderRuntime.applyToBitmap(bitmap, filter, seed, quads, tune)
        }
        if (overlay != null) {
            // The shader hands back an immutable bitmap; a frame has to be drawn into a mutable one.
            if (!bitmap.isMutable) {
                val copy = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                if (copy != null) {
                    bitmap.recycle()
                    bitmap = copy
                }
            }
            overlay(android.graphics.Canvas(bitmap), bitmap.width, bitmap.height, quads)
        }
        if (stampAt != null) bitmap = DateStamp.apply(bitmap, stampAt, stampStyle)
        val out = ByteArrayOutputStream(bitmap.width * bitmap.height / 4)
        bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
        return Processed(out.toByteArray(), bitmap.width, bitmap.height)
    }

    /** The dimensions of a JPEG without decoding it. */
    fun sizeOf(jpeg: ByteArray): Pair<Int, Int> = readSize(jpeg)

    private fun readSize(jpeg: ByteArray): Pair<Int, Int> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, opts)
        return opts.outWidth to opts.outHeight
    }

    /**
     * Decode and orient.
     *
     * The rotation can arrive two ways: as CameraX's `rotationDegrees` on the frame, or as
     * an EXIF tag inside the JPEG the HAL produced. Which one is populated varies by device
     * and by capture mode, so both are read and EXIF wins when it says something — it
     * describes the bytes in hand, whereas `rotationDegrees` describes what CameraX intended.
     */
    private fun decodeUpright(jpeg: ByteArray, rotationDegrees: Int, mirrored: Boolean): Bitmap? {
        // Decode *down* rather than decoding everything and throwing most of it away. A
        // full-resolution decode of a 50MP JPEG is 200MB of ARGB — seconds of work and a real
        // chance of an out-of-memory on this phone — and the next thing that happened to it was
        // being scaled to fit a GPU texture anyway. `inSampleSize` does that inside the decoder,
        // in powers of two, for a fraction of the cost.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest > 0 && longest / (sample * 2) >= MAX_FILTERED_EDGE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options) ?: return null
        val exifRotation = runCatching {
            val exif = ExifInterface(ByteArrayInputStream(jpeg))
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        }.getOrDefault(0)
        val turn = if (exifRotation != 0) exifRotation else rotationDegrees
        if (turn == 0 && !mirrored) return bitmap

        val matrix = Matrix()
        // The front camera's preview is mirrored, so a selfie that isn't mirrored on disk
        // does not look like the thing you framed.
        if (mirrored) matrix.postScale(-1f, 1f)
        if (turn != 0) matrix.postRotate(turn.toFloat())
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    /** Centre crop to the chosen frame. The frame lines in the viewfinder mark this exactly. */
    private fun crop(bitmap: Bitmap, aspect: FrameAspect): Bitmap {
        val want = aspect.ratio ?: return bitmap
        // Portrait capture: the frame's long edge is vertical, so the target ratio inverts.
        val target = if (bitmap.height >= bitmap.width) 1f / want else want
        val current = bitmap.width.toFloat() / bitmap.height
        if (kotlin.math.abs(current - target) < 0.001f) return bitmap

        val (w, h) = if (current > target) {
            (bitmap.height * target).toInt() to bitmap.height
        } else {
            bitmap.width to (bitmap.width / target).toInt()
        }
        val x = (bitmap.width - w) / 2
        val y = (bitmap.height - h) / 2
        val cropped = Bitmap.createBitmap(bitmap, x, y, w.coerceAtLeast(1), h.coerceAtLeast(1))
        if (cropped != bitmap) bitmap.recycle()
        return cropped
    }

    private fun downscaleIfHuge(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= MAX_FILTERED_EDGE) return bitmap
        val scale = MAX_FILTERED_EDGE.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt(),
            (bitmap.height * scale).toInt(),
            true,
        )
        if (scaled != bitmap) bitmap.recycle()
        return scaled
    }
}
