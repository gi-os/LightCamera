package com.gios.lightcamera.filter

import com.gios.lightcamera.camera.FaceBox

/**
 * A face as a shader sees it: centre and half-extent, **normalised to the image**, 0..1.
 *
 * Normalised rather than in pixels because the same face has to be described to two different
 * images — a 1080-pixel-tall preview and whatever the photograph turns out to be — and a fraction
 * of the frame is the one description that survives both.
 */
data class FaceQuad(val cx: Float, val cy: Float, val hw: Float, val hh: Float)

/**
 * Getting a detected face from the preview into a shader's coordinates.
 *
 * [FaceBox]es arrive in the preview view's own pixels. A shader that warps a face has to know
 * where the face is in *its* image, and between the two there may be a quarter turn and a centred
 * crop. Both are simple maps and both are easy to get subtly wrong in a way no one notices until
 * somebody's eye is enlarged next to their ear — so, like [com.gios.lightcamera.camera.FaceMapper],
 * this is plain Kotlin with no Android imports and it is checked off-device.
 */
object FaceQuads {

    /** How many faces a shader is given. Three is a photo booth's worth. */
    const val MAX = 3

    /** Preview-view pixels to normalised quads, biggest face first, at most [MAX] of them. */
    fun of(faces: List<FaceBox>, viewWidth: Int, viewHeight: Int, limit: Int = MAX): List<FaceQuad> {
        if (viewWidth <= 0 || viewHeight <= 0) return emptyList()
        return faces
            .sortedByDescending { it.area }
            .take(limit)
            .map { face ->
                FaceQuad(
                    cx = face.centreX / viewWidth,
                    cy = face.centreY / viewHeight,
                    hw = face.width * 0.5f / viewWidth,
                    hh = face.height * 0.5f / viewHeight,
                )
            }
    }

    /**
     * The same face after the image has been turned [degrees] clockwise.
     *
     * Clockwise, because that is the direction everything else in this app rotates by: a point at
     * the top-left goes to the top-right on a quarter turn. The half-extents swap with the axes.
     */
    fun rotated(quad: FaceQuad, degrees: Int): FaceQuad = when ((degrees % 360 + 360) % 360) {
        90 -> FaceQuad(1f - quad.cy, quad.cx, quad.hh, quad.hw)
        180 -> FaceQuad(1f - quad.cx, 1f - quad.cy, quad.hw, quad.hh)
        270 -> FaceQuad(quad.cy, 1f - quad.cx, quad.hh, quad.hw)
        else -> quad
    }

    /**
     * The same face after a **centred** crop from [srcW] x [srcH] down to [dstW] x [dstH].
     *
     * Centred is the only kind this app does — [com.gios.lightcamera.camera.Frames] takes the
     * middle of the frame for every aspect ratio — so the offset is half the difference and there
     * is nothing to pass in. A face can end up outside the crop, which is fine: the shader gets a
     * centre beyond 0..1 and warps nothing, because nothing of it is on screen to warp.
     */
    fun cropped(quad: FaceQuad, srcW: Int, srcH: Int, dstW: Int, dstH: Int): FaceQuad {
        if (srcW <= 0 || srcH <= 0 || dstW <= 0 || dstH <= 0) return quad
        if (srcW == dstW && srcH == dstH) return quad
        val offX = (srcW - dstW) * 0.5f
        val offY = (srcH - dstH) * 0.5f
        return FaceQuad(
            cx = (quad.cx * srcW - offX) / dstW,
            cy = (quad.cy * srcH - offY) / dstH,
            hw = quad.hw * srcW / dstW,
            hh = quad.hh * srcH / dstH,
        )
    }
}
