package com.gios.lightcamera.filter

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.util.Log

/**
 * Running an AGSL filter, on a live view and on a still.
 *
 * The live case is easy — `View.setRenderEffect` exists for exactly this. The still case is
 * the interesting one, and the reason this file is longer than it looks like it should be:
 *
 * **A `RuntimeShader` cannot draw onto a software `Canvas`.** AGSL is compiled for the GPU,
 * and a `Canvas` wrapping a `Bitmap` is a CPU rasteriser, so the obvious three lines —
 * paint the shader over a bitmap canvas — silently produce nothing. The supported way to
 * get a hardware-accelerated draw without a `View` on screen is to drive the same renderer
 * the view hierarchy uses: a [RenderNode] holding the display list, a [HardwareRenderer]
 * pointed at an [ImageReader]'s surface, and a read-back through the resulting
 * [HardwareBuffer]. That is [Offscreen].
 *
 * The alternative would be a second, CPU implementation of every filter, which would drift
 * from the shader within a week.
 */
object ShaderRuntime {

    private const val TAG = "ShaderRuntime"

    /**
     * Compiled shaders, by filter id. Compilation is not free and the filter grid asks for
     * fifteen of them several times a second.
     *
     * A `RuntimeShader`'s uniforms are mutable state on the object, so callers must set
     * `size` and `seed` before every use rather than assuming what the last caller left.
     */
    private val compiled = HashMap<String, RuntimeShader>()

    private fun shader(filter: Filters.Filter): RuntimeShader? = shader(filter, compiled)

    /**
     * Compile into a caller-owned cache.
     *
     * Uniforms live on the shader object, so a shader shared between the preview (main
     * thread) and the filter grid (a background thread) would have the two of them
     * overwriting each other's `size` mid-draw. Each user keeps its own copies.
     */
    private fun shader(
        filter: Filters.Filter,
        cache: MutableMap<String, RuntimeShader>,
    ): RuntimeShader? {
        val source = filter.source ?: return null
        cache[filter.id]?.let { return it }
        val made = runCatching { RuntimeShader(source) }
            .onFailure { Log.e(TAG, "AGSL failed to compile for ${filter.id}", it) }
            .getOrNull() ?: return null
        cache[filter.id] = made
        return made
    }

    /**
     * Hand the detected faces to a shader that asks for them.
     *
     * **Only when the filter declares them.** Setting a uniform a shader does not have throws, so
     * this is gated on the flag rather than attempted and caught — and every slot is written every
     * time, because a `RuntimeShader` keeps its uniforms between draws and a face left over from the
     * last frame would go on warping an empty room.
     */
    private fun setFaces(
        shader: RuntimeShader,
        filter: Filters.Filter,
        faces: List<FaceQuad>,
        tune: FaceTune,
    ) {
        if (!filter.facesAware) return
        shader.setFloatUniform("warp", tune.eyes, tune.chin, tune.slim, tune.skin)
        shader.setFloatUniform("wash", tune.wash)
        shader.setFloatUniform("faceTurn", (((tune.turns % 4) + 4) % 4).toFloat())
        val used = faces.take(FaceQuads.MAX)
        shader.setFloatUniform("faceCount", used.size.toFloat())
        for (slot in 0 until FaceQuads.MAX) {
            val quad = used.getOrNull(slot)
            shader.setFloatUniform(
                "face$slot",
                quad?.cx ?: 0f,
                quad?.cy ?: 0f,
                quad?.hw ?: 0f,
                quad?.hh ?: 0f,
            )
        }
    }

    /**
     * The effect to hang on the preview.
     *
     * Returns null for [Filters.none], which the caller must read as "clear the effect"
     * rather than as a failure. A compile error also lands here as null — better an
     * unfiltered viewfinder than a black one.
     */
    fun effectFor(
        filter: Filters.Filter,
        width: Int,
        height: Int,
        seed: Float,
        faces: List<FaceQuad> = emptyList(),
        tune: FaceTune = FaceTune(),
    ): RenderEffect? {
        if (width <= 0 || height <= 0) return null
        val shader = shader(filter) ?: return null
        shader.setFloatUniform("size", width.toFloat(), height.toFloat())
        shader.setFloatUniform("seed", seed)
        setFaces(shader, filter, faces, tune)
        return runCatching { RenderEffect.createRuntimeShaderEffect(shader, "src") }
            .onFailure { Log.e(TAG, "effect failed for ${filter.id}", it) }
            .getOrNull()
    }

    /**
     * One-shot filtering of a still. Convenient, but it builds and tears down a renderer
     * each time; the filter grid uses a long-lived [Offscreen] instead.
     */
    fun applyToBitmap(
        source: Bitmap,
        filter: Filters.Filter,
        seed: Float,
        faces: List<FaceQuad> = emptyList(),
        tune: FaceTune = FaceTune(),
    ): Bitmap {
        if (filter.agsl == null) return source
        val renderer = Offscreen(source.width, source.height) ?: return source
        return try {
            renderer.render(source, filter, seed, faces, tune) ?: source
        } finally {
            renderer.close()
        }
    }

    /**
     * A reusable offscreen GPU surface at one fixed size.
     *
     * Keep one per size and call [render] as often as you like. Not thread-safe; each
     * instance belongs to whichever thread built it.
     */
    class Offscreen private constructor(
        private val width: Int,
        private val height: Int,
        private val reader: ImageReader,
        private val renderer: HardwareRenderer,
        private val node: RenderNode,
    ) {

        private val paint = Paint()
        private val owned = HashMap<String, RuntimeShader>()

        fun render(
            source: Bitmap,
            filter: Filters.Filter,
            seed: Float,
            faces: List<FaceQuad> = emptyList(),
            tune: FaceTune = FaceTune(),
        ): Bitmap? {
            val shader = shader(filter, owned) ?: return null
            // The bitmap is sampled in its own pixel space, so `size` here is the image and
            // every pattern in the shader scales to it. Same numbers the preview uses,
            // which is what makes the capture match the viewfinder.
            shader.setFloatUniform("size", width.toFloat(), height.toFloat())
            shader.setFloatUniform("seed", seed)
            setFaces(shader, filter, faces, tune)
            val bitmapShader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            // Scale the source into the node if the caller handed us a different size —
            // used by the filter grid, whose cells are smaller than the preview frame.
            if (source.width != width || source.height != height) {
                val m = android.graphics.Matrix()
                m.setScale(width.toFloat() / source.width, height.toFloat() / source.height)
                bitmapShader.setLocalMatrix(m)
            }
            shader.setInputShader("src", bitmapShader)
            paint.shader = shader

            val canvas = node.beginRecording()
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            node.endRecording()

            return runCatching {
                renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
                val image = reader.acquireLatestImage() ?: return@runCatching null
                try {
                    val buffer = image.hardwareBuffer ?: return@runCatching null
                    try {
                        val wrapped = Bitmap.wrapHardwareBuffer(
                            buffer,
                            ColorSpace.get(ColorSpace.Named.SRGB),
                        ) ?: return@runCatching null
                        // Copy out: the hardware bitmap is a view onto a buffer that is
                        // about to be handed back to the reader, and JPEG encoding needs
                        // pixels it can read on the CPU anyway.
                        wrapped.copy(Bitmap.Config.ARGB_8888, false)
                    } finally {
                        buffer.close()
                    }
                } finally {
                    image.close()
                }
            }.onFailure { Log.e(TAG, "offscreen render failed", it) }.getOrNull()
        }

        fun close() {
            runCatching {
                node.discardDisplayList()
                renderer.destroy()
                reader.close()
            }
        }

        companion object {
            operator fun invoke(width: Int, height: Int): Offscreen? {
                if (width <= 0 || height <= 0) return null
                return runCatching {
                    val reader = ImageReader.newInstance(
                        width,
                        height,
                        PixelFormat.RGBA_8888,
                        2,
                        // Both flags matter: COLOR_OUTPUT so the GPU may render into it,
                        // GPU_SAMPLED so Bitmap.wrapHardwareBuffer will accept it.
                        HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or
                            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE,
                    )
                    val renderer = HardwareRenderer().apply {
                        setSurface(reader.surface)
                        isOpaque = true
                    }
                    val node = RenderNode("lightcamera-filter").apply {
                        setPosition(0, 0, width, height)
                    }
                    renderer.setContentRoot(node)
                    Offscreen(width, height, reader, renderer, node)
                }.onFailure { Log.e(TAG, "offscreen setup failed", it) }.getOrNull()
            }
        }
    }
}
