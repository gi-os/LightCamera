package com.gios.lightcamera.camera

/** A face, in the preview view's own pixels. */
data class FaceBox(
    val id: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    /** 1..100 from the hardware, or 0 when it doesn't say. */
    val score: Int,
) {
    val centreX: Float get() = (left + right) * 0.5f
    val centreY: Float get() = (top + bottom) * 0.5f
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val area: Float get() = width * height
}

/**
 * Getting a face rectangle from the camera onto the screen.
 *
 * The hardware face detector reports rectangles in **sensor active-array coordinates**,
 * which is three transformations away from where the face appears on screen. Every one of
 * them is easy to get subtly wrong and impossible to notice in code review, so this is
 * plain Kotlin with no Android imports — it can be, and is, checked off-device.
 *
 * The three:
 *
 *  1. **Zoom.** The rectangle stays in active-array coordinates even when zoomed, and only
 *     the part of the array inside `SCALER_CROP_REGION` is being read out. So the crop
 *     region, not the array, is the frame to normalise against — normalise against the
 *     array and every box drifts towards the centre as you zoom in.
 *  2. **Rotation.** The buffer comes out in sensor orientation. CameraX reports how far it
 *     has to be turned clockwise to look upright, and the same turn applies to the points.
 *     A front camera is additionally mirrored, because the preview mirrors it.
 *  3. **Crop to fill.** The preview fills the view and loses the overhanging edges, so the
 *     mapping is scale-by-max with a negative offset, not a stretch.
 */
object FaceMapper {

    /**
     * @param sensorRect face bounds as (left, top, right, bottom) in active-array pixels
     * @param cropRect the active read-out region — `SCALER_CROP_REGION`, or the full active
     *   array when the camera doesn't report one
     * @param rotationDegrees clockwise rotation to make the buffer upright, from
     *   `ResolutionInfo.rotationDegrees`
     * @param bufferWidth /  @param bufferHeight preview buffer size, *unrotated*
     * @param mirrored true for a front camera, whose preview is flipped
     */
    fun toView(
        id: Int,
        score: Int,
        sensorRect: IntArray,
        cropRect: IntArray,
        rotationDegrees: Int,
        bufferWidth: Int,
        bufferHeight: Int,
        mirrored: Boolean,
        viewWidth: Int,
        viewHeight: Int,
    ): FaceBox? {
        if (viewWidth <= 0 || viewHeight <= 0) return null
        if (bufferWidth <= 0 || bufferHeight <= 0) return null

        val cropW = (cropRect[2] - cropRect[0]).toFloat()
        val cropH = (cropRect[3] - cropRect[1]).toFloat()
        if (cropW <= 0f || cropH <= 0f) return null

        // 1. Into the crop region, as fractions.
        var x0 = (sensorRect[0] - cropRect[0]) / cropW
        var y0 = (sensorRect[1] - cropRect[1]) / cropH
        var x1 = (sensorRect[2] - cropRect[0]) / cropW
        var y1 = (sensorRect[3] - cropRect[1]) / cropH

        // 2. Turn both corners, then mirror.
        val a = rotate(x0, y0, rotationDegrees)
        val b = rotate(x1, y1, rotationDegrees)
        x0 = minOf(a[0], b[0]); x1 = maxOf(a[0], b[0])
        y0 = minOf(a[1], b[1]); y1 = maxOf(a[1], b[1])
        if (mirrored) {
            val l = 1f - x1
            val r = 1f - x0
            x0 = l; x1 = r
        }

        // 3. Fill the view with the upright buffer and crop the overhang.
        val quarter = ((rotationDegrees % 360) + 360) % 360
        val uprightW = if (quarter == 90 || quarter == 270) bufferHeight else bufferWidth
        val uprightH = if (quarter == 90 || quarter == 270) bufferWidth else bufferHeight
        val scale = maxOf(viewWidth / uprightW.toFloat(), viewHeight / uprightH.toFloat())
        val drawW = uprightW * scale
        val drawH = uprightH * scale
        val offX = (viewWidth - drawW) * 0.5f
        val offY = (viewHeight - drawH) * 0.5f

        return FaceBox(
            id = id,
            left = x0 * drawW + offX,
            top = y0 * drawH + offY,
            right = x1 * drawW + offX,
            bottom = y1 * drawH + offY,
            score = score,
        )
    }

    /** Clockwise, in the unit square. */
    private fun rotate(x: Float, y: Float, degrees: Int): FloatArray =
        when (((degrees % 360) + 360) % 360) {
            90 -> floatArrayOf(1f - y, x)
            180 -> floatArrayOf(1f - x, 1f - y)
            270 -> floatArrayOf(y, 1f - x)
            else -> floatArrayOf(x, y)
        }

    /**
     * Which face the camera should focus on.
     *
     * Biggest wins, which in practice means nearest, and near enough to what a person means
     * by "the subject". Ties on area go to whichever is closest to the centre of the frame,
     * so two people at the same distance don't make the lens flick between them every time
     * one of them moves a fraction closer.
     */
    fun priority(faces: List<FaceBox>, viewWidth: Int, viewHeight: Int): FaceBox? {
        if (faces.isEmpty()) return null
        val cx = viewWidth * 0.5f
        val cy = viewHeight * 0.5f
        return faces.maxByOrNull { face ->
            val dx = (face.centreX - cx) / viewWidth.coerceAtLeast(1)
            val dy = (face.centreY - cy) / viewHeight.coerceAtLeast(1)
            face.area - (dx * dx + dy * dy) * face.area * 0.35f
        }
    }

    /**
     * Whether a continuously-focusing camera should re-acquire.
     *
     * Refocusing on every frame would hunt audibly and never settle; refocusing only on a
     * new face would leave someone walking towards you soft. The threshold is a fraction of
     * the frame, so it scales with the panel rather than being a dp guess.
     */
    fun movedEnoughToRefocus(
        from: FaceBox?,
        to: FaceBox?,
        viewWidth: Int,
        viewHeight: Int,
    ): Boolean {
        if (to == null) return false
        if (from == null) return true
        if (from.id != to.id && to.id != 0) return true
        val span = maxOf(viewWidth, viewHeight).coerceAtLeast(1)
        val dx = (to.centreX - from.centreX) / span
        val dy = (to.centreY - from.centreY) / span
        val moved = dx * dx + dy * dy > REFOCUS_FRACTION * REFOCUS_FRACTION
        val resized = to.width > from.width * 1.3f || to.width < from.width * 0.77f
        return moved || resized
    }

    private const val REFOCUS_FRACTION = 0.07f
}
