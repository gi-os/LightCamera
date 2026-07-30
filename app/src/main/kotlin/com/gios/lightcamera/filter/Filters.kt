package com.gios.lightcamera.filter

/**
 * The filters, as AGSL.
 *
 * Every one of them is a single fragment shader, and that is the design decision the whole
 * app hangs off. A shader can be handed to the platform twice:
 *
 *  - wrapped in a `RenderEffect` on the preview's `TextureView`, where it filters the live
 *    image on the GPU with no per-frame work on our side, and
 *  - wrapped in a `Paint` over a `BitmapShader`, where it filters the captured photo.
 *
 * So there is exactly one implementation of "what Halftone looks like", and the photo you
 * get is the photo you framed. The alternative — a preview approximation plus a separate
 * CPU pass at capture — is how filter apps end up lying to you.
 *
 * Two rules keep the two paths honest:
 *
 *  - **Patterns are sized in design pixels, not device pixels.** [PRELUDE]'s `unitPx()`
 *    divides by the image height, so a halftone dot covers the same fraction of the frame
 *    in a 340px preview and a 4000px capture. Without this, every dithered filter
 *    dissolves into invisible noise the moment it is applied at full resolution.
 *  - **Nothing samples outside the frame** without clamping, because the two paths differ
 *    on what lies outside: a view returns transparent, a clamped `BitmapShader` returns
 *    the edge pixel.
 *
 * AGSL requires API 33, which is why this app does.
 */
object Filters {

    /**
     * Prepended to every shader. `src` is the image, `size` its dimensions in pixels, and
     * `seed` moves the grain between frames.
     *
     * `bayer2/4/8` build an ordered-dither threshold matrix by recursion rather than from a
     * lookup table — SkSL has no arrays in a runtime shader worth relying on, and the
     * recursion is exact: `bayer2` alone yields {0, .5, .75, .25}, the 2x2 Bayer matrix
     * over four, and each level adds a quarter-weighted finer copy of it.
     */
    private const val PRELUDE = """
uniform shader src;
uniform float2 size;
uniform float seed;

float lum(float3 c) { return dot(c, float3(0.2126, 0.7152, 0.0722)); }

float3 tap(float2 p) {
    float2 q = clamp(p, float2(0.0, 0.0), size - float2(1.0, 1.0));
    return float3(src.eval(q).rgb);
}

float unitPx() { return max(1.0, size.y / 640.0); }

float bayer2(float2 a) { a = floor(a); return fract(a.x * 0.5 + a.y * a.y * 0.75); }
float bayer4(float2 a) { return bayer2(a * 0.5) * 0.25 + bayer2(a); }
float bayer8(float2 a) { return bayer4(a * 0.5) * 0.25 + bayer2(a); }

float hash(float2 p) {
    return fract(sin(dot(p, float2(12.9898, 78.233)) + seed) * 43758.5453);
}

half4 grey(float g) {
    float v = clamp(g, 0.0, 1.0);
    return half4(float4(v, v, v, 1.0));
}
"""

    /**
     * Grain, halation and a vignette. The default, and the reason the app is called Roll.
     *
     * The grain is modulated by the midtones because that is where silver halide actually
     * clumps — flat grain over the whole frame reads as digital noise, not as film. The
     * halation is a four-tap blur of the highlights added back on top, which is the same
     * trick as a real bloom, done cheaply enough to run on a live preview.
     */
    private const val FILM = """
half4 main(float2 xy) {
    float2 uv = xy / size;
    float3 c = tap(xy);
    float u = unitPx() * 3.0;
    float3 blur = (tap(xy + float2(u, 0.0)) + tap(xy - float2(u, 0.0)) +
                   tap(xy + float2(0.0, u)) + tap(xy - float2(0.0, u))) * 0.25;
    float halation = max(0.0, lum(blur) - 0.70);
    c = c * 1.06 + halation * 0.45;
    c = (c - 0.5) * 1.10 + 0.5;
    float g = lum(c);
    float n = hash(floor(xy / unitPx())) - 0.5;
    c += n * 0.09 * (1.0 - abs(g - 0.5) * 1.4);
    float2 d = uv - 0.5;
    c *= 1.0 - dot(d, d) * 0.60;
    c = clamp(c, 0.0, 1.0);
    return half4(float4(c, 1.0));
}
"""

    /** Black and white with a print-like S-curve. What the panel does best. */
    private const val MONO = """
half4 main(float2 xy) {
    float g = lum(tap(xy));
    g = clamp((g - 0.5) * 1.28 + 0.5, 0.0, 1.0);
    g = pow(g, 0.92);
    return grey(g);
}
"""

    /**
     * Sixteen colours, ordered-dithered. The EGA palette, which is the one your eye reads
     * as "computer" rather than as "low quality".
     *
     * Dithering happens *before* quantisation: the threshold matrix nudges each pixel's
     * colour, then the nearest palette entry is picked, so flat gradients break into the
     * cross-hatch instead of banding.
     */
    private const val DITHER16 = """
void nearer(float3 c, float3 cand, inout float3 best, inout float bd) {
    float3 e = c - cand;
    float d = dot(e, e);
    if (d < bd) { bd = d; best = cand; }
}

half4 main(float2 xy) {
    float3 c = tap(xy);
    c = clamp((c - 0.5) * 1.15 + 0.5, 0.0, 1.0);
    float t = bayer8(xy / (unitPx() * 2.0)) - 0.5;
    c = clamp(c + t * 0.26, 0.0, 1.0);

    float3 best = float3(0.0, 0.0, 0.0);
    float bd = 1000.0;
    nearer(c, float3(0.00, 0.00, 0.00), best, bd);
    nearer(c, float3(0.50, 0.00, 0.00), best, bd);
    nearer(c, float3(0.00, 0.50, 0.00), best, bd);
    nearer(c, float3(0.50, 0.50, 0.00), best, bd);
    nearer(c, float3(0.00, 0.00, 0.50), best, bd);
    nearer(c, float3(0.50, 0.00, 0.50), best, bd);
    nearer(c, float3(0.00, 0.50, 0.50), best, bd);
    nearer(c, float3(0.66, 0.66, 0.66), best, bd);
    nearer(c, float3(0.33, 0.33, 0.33), best, bd);
    nearer(c, float3(1.00, 0.00, 0.00), best, bd);
    nearer(c, float3(0.00, 1.00, 0.00), best, bd);
    nearer(c, float3(1.00, 1.00, 0.00), best, bd);
    nearer(c, float3(0.00, 0.00, 1.00), best, bd);
    nearer(c, float3(1.00, 0.00, 1.00), best, bd);
    nearer(c, float3(0.00, 1.00, 1.00), best, bd);
    nearer(c, float3(1.00, 1.00, 1.00), best, bd);
    return half4(float4(best, 1.0));
}
"""

    /** One bit. Newsprint, or the phone's own idea of an image. */
    private const val ONE_BIT = """
half4 main(float2 xy) {
    float g = lum(tap(xy));
    g = clamp((g - 0.48) * 1.35 + 0.5, 0.0, 1.0);
    float t = bayer8(xy / (unitPx() * 1.6));
    return grey(step(t, g));
}
"""

    /**
     * A rotated dot screen. Each cell reads the image once at its own centre and grows a
     * dot to match, so this is a genuine halftone rather than a thresholded texture — the
     * dots stay round and evenly spaced no matter what the image does.
     */
    private const val HALFTONE = """
half4 main(float2 xy) {
    float cell = unitPx() * 6.0;
    float a = 0.5236;
    float ca = cos(a);
    float sa = sin(a);
    float2 p = float2(xy.x * ca - xy.y * sa, xy.x * sa + xy.y * ca);
    float2 centre = (floor(p / cell) + 0.5) * cell;
    float2 back = float2(centre.x * ca + centre.y * sa, -centre.x * sa + centre.y * ca);
    float g = lum(tap(back));
    g = clamp((g - 0.5) * 1.2 + 0.5, 0.0, 1.0);
    float r = sqrt(1.0 - g) * cell * 0.70;
    float ink = 1.0 - smoothstep(r - 1.0, r + 1.0, distance(p, centre));
    return grey(1.0 - ink);
}
"""

    /** Posterised, with the edges inked in. Sobel, four levels, no outline shader tricks. */
    private const val COMIC = """
half4 main(float2 xy) {
    float u = unitPx();
    float l00 = lum(tap(xy + float2(-u, -u)));
    float l10 = lum(tap(xy + float2(0.0, -u)));
    float l20 = lum(tap(xy + float2(u, -u)));
    float l01 = lum(tap(xy + float2(-u, 0.0)));
    float l21 = lum(tap(xy + float2(u, 0.0)));
    float l02 = lum(tap(xy + float2(-u, u)));
    float l12 = lum(tap(xy + float2(0.0, u)));
    float l22 = lum(tap(xy + float2(u, u)));
    float gx = -l00 - 2.0 * l01 - l02 + l20 + 2.0 * l21 + l22;
    float gy = -l00 - 2.0 * l10 - l20 + l02 + 2.0 * l12 + l22;
    float e = sqrt(gx * gx + gy * gy);
    float3 c = tap(xy);
    c = clamp((c - 0.5) * 1.35 + 0.5, 0.0, 1.0);
    float3 q = floor(c * 4.0 + 0.5) / 4.0;
    q *= 1.0 - smoothstep(0.30, 0.62, e);
    return half4(float4(q, 1.0));
}
"""

    /** False colour up a five-stop ramp. Photo Booth's thermal camera, more or less. */
    private const val THERMAL = """
half4 main(float2 xy) {
    float g = lum(tap(xy));
    float3 c = mix(float3(0.0, 0.0, 0.10), float3(0.15, 0.0, 0.55), smoothstep(0.00, 0.25, g));
    c = mix(c, float3(0.72, 0.0, 0.45), smoothstep(0.25, 0.50, g));
    c = mix(c, float3(1.0, 0.25, 0.0), smoothstep(0.50, 0.72, g));
    c = mix(c, float3(1.0, 0.90, 0.10), smoothstep(0.72, 0.90, g));
    c = mix(c, float3(1.0, 1.0, 1.0), smoothstep(0.90, 1.00, g));
    return half4(float4(c, 1.0));
}
"""

    /**
     * The Game Boy Camera.
     *
     * Two things make that look, and only one of them is the green. The other is the
     * resolution: the GB Camera's sensor was **128 x 112**, so the image is quantised onto a
     * grid of that many cells across the short edge before anything else happens — sampling
     * once per cell rather than averaging, because that is what a 128-pixel sensor does.
     *
     * Then four shades and nothing between them, the DMG palette from the real thing
     * (`0f380f`, `306230`, `8bac0f`, `9bbc0f`), reached through a Bayer threshold so gradients
     * break into the cross-hatch the hardware produced instead of banding. The contrast is
     * pushed first: four levels of a flat exposure is mud.
     */
    private const val GAMEBOY = """
half4 main(float2 xy) {
    float cell = max(1.0, min(size.x, size.y) / 128.0);
    float2 grid = (floor(xy / cell) + 0.5) * cell;
    float g = lum(tap(grid));
    g = clamp((g - 0.5) * 1.45 + 0.5, 0.0, 1.0);
    // Three thresholds for four shades, nudged by the dither so the steps break up.
    float t = (bayer4(xy / cell) - 0.5) * 0.30;
    float level = floor(clamp(g + t, 0.0, 0.999) * 4.0);
    float3 c = float3(0.059, 0.220, 0.059);
    if (level > 0.5) c = float3(0.188, 0.384, 0.188);
    if (level > 1.5) c = float3(0.545, 0.675, 0.059);
    if (level > 2.5) c = float3(0.608, 0.737, 0.059);
    return half4(float4(c, 1.0));
}
"""

    /**
     * The same sensor, on a Game Boy Color.
     *
     * The GBC kept the low resolution and gained fifteen bits of colour — five per channel — so
     * this is the same 128-cell grid with each channel dithered to five levels rather than
     * everything crushed to four greens. What you get is not "colour": it is the specific,
     * slightly sour palette of a 1998 handheld, which is the point of asking for it.
     */
    private const val GB_COLOR = """
half4 main(float2 xy) {
    float cell = max(1.0, min(size.x, size.y) / 128.0);
    float2 grid = (floor(xy / cell) + 0.5) * cell;
    float3 c = tap(grid);
    c = clamp((c - 0.5) * 1.30 + 0.5, 0.0, 1.0);
    // Five steps a channel, which is 125 colours — close enough to the GBC's usable palette,
    // and the dither is what stops it looking like a posterise filter.
    float t = (bayer4(xy / cell) - 0.5) * 0.26;
    float3 q = floor(clamp(c + t, 0.0, 0.999) * 5.0) / 4.0;
    // A touch warm and green, the way that screen was.
    q *= float3(0.98, 1.02, 0.90);
    return half4(float4(clamp(q, 0.0, 1.0), 1.0));
}
"""

    /** Inverted, gamma-lifted, cooled. Bones. */
    private const val X_RAY = """
half4 main(float2 xy) {
    float g = 1.0 - lum(tap(xy));
    g = pow(clamp(g, 0.0, 1.0), 0.78);
    return half4(float4(g * 0.80, g * 0.94, g, 1.0));
}
"""

    /** A soft ring blur added back over the highlights. Everything looks kinder. */
    private const val GLOW = """
half4 main(float2 xy) {
    float u = unitPx() * 5.0;
    float3 b = tap(xy + float2(u, 0.0)) + tap(xy - float2(u, 0.0)) +
               tap(xy + float2(0.0, u)) + tap(xy - float2(0.0, u)) +
               tap(xy + float2(u, u)) + tap(xy - float2(u, u)) +
               tap(xy + float2(u, -u)) + tap(xy - float2(u, -u));
    b *= 0.125;
    float3 c = tap(xy);
    c = mix(c, b, 0.35);
    c += max(float3(0.0, 0.0, 0.0), b - 0.55) * 1.15;
    return half4(float4(clamp(c, 0.0, 1.0), 1.0));
}
"""

    /** Polar warp, strongest at the centre and easing out to nothing at the edge. */
    private const val TWIRL = """
half4 main(float2 xy) {
    float2 ctr = size * 0.5;
    float2 d = xy - ctr;
    float r = length(d);
    float R = min(size.x, size.y) * 0.58;
    float t = clamp(1.0 - r / R, 0.0, 1.0);
    float a = t * t * 2.8;
    float ca = cos(a);
    float sa = sin(a);
    float2 p = ctr + float2(d.x * ca - d.y * sa, d.x * sa + d.y * ca);
    return half4(float4(tap(p), 1.0));
}
"""

    /** A lens on the middle of the frame. */
    private const val BULGE = """
half4 main(float2 xy) {
    float2 ctr = size * 0.5;
    float2 d = xy - ctr;
    float R = min(size.x, size.y) * 0.62;
    float t = clamp(length(d) / R, 0.0, 1.0);
    float k = mix(0.48, 1.06, t * t);
    return half4(float4(tap(ctr + d * k), 1.0));
}
"""

    /** The left half of the frame, and the left half of the frame again. */
    private const val MIRROR = """
half4 main(float2 xy) {
    float half_w = size.x * 0.5;
    float x = half_w - abs(xy.x - half_w);
    return half4(float4(tap(float2(x, xy.y)), 1.0));
}
"""

    /** Six segments folded around the centre. */
    private const val KALEIDO = """
half4 main(float2 xy) {
    float2 ctr = size * 0.5;
    float2 d = xy - ctr;
    float r = length(d);
    float a = atan(d.y, d.x);
    float seg = 1.0471976;
    a = abs(mod(a, seg) - seg * 0.5);
    float2 p = ctr + float2(cos(a), sin(a)) * r;
    return half4(float4(tap(p), 1.0));
}
"""

    /** Everything past a small central disc is smeared out from its rim. */
    private const val TUNNEL = """
half4 main(float2 xy) {
    float2 ctr = size * 0.5;
    float2 d = xy - ctr;
    float r = max(length(d), 0.0001);
    float R = min(size.x, size.y) * 0.30;
    float2 p = r > R ? ctr + d * (R / r) : xy;
    return half4(float4(tap(p), 1.0));
}
"""

    /**
     * A filter, as the rest of the app sees it.
     *
     * [agsl] is null for [none] only. [animated] marks the ones whose look depends on
     * `seed`, so the preview re-applies them a few times a second and the grain crawls the
     * way it does on a projector; everything else is applied once and left alone.
     *
     * [lowRes] marks the ones that quantise the image onto a coarse grid of their own — the dithers,
     * the halftone, the two Game Boys. **There is nothing for a sensor capture to give these.** A
     * 12MP frame and a panel-sized one both come out of a 160-cell dither as the same picture, so
     * these always take the viewfinder frame instead: instant, silent, and exactly what you were
     * looking at when you pressed.
     */
    data class Filter(
        val id: String,
        val label: String,
        val agsl: String?,
        val animated: Boolean = false,
        val lowRes: Boolean = false,
    ) {
        /** The whole shader, prelude included. */
        val source: String? get() = agsl?.let { PRELUDE + it }
    }

    val none = Filter("none", "None", null)

    /**
     * Order matters: this is the order the wheel and a sideways swipe walk through, so it
     * runs from the ones you would actually shoot with to the ones you would not.
     */
    val all: List<Filter> = listOf(
        none,
        Filter("film", "Film", FILM, animated = true),
        Filter("mono", "Mono", MONO),
        Filter("dither16", "Dither 16", DITHER16, lowRes = true),
        Filter("onebit", "1-Bit", ONE_BIT, lowRes = true),
        Filter("halftone", "Halftone", HALFTONE, lowRes = true),
        Filter("gameboy", "Game Boy", GAMEBOY, lowRes = true),
        Filter("gbcolor", "GB Color", GB_COLOR, lowRes = true),
        Filter("comic", "Comic", COMIC),
        Filter("thermal", "Thermal", THERMAL),
        Filter("xray", "X-Ray", X_RAY),
        Filter("glow", "Glow", GLOW),
        Filter("twirl", "Twirl", TWIRL),
        Filter("bulge", "Bulge", BULGE),
        Filter("mirror", "Mirror", MIRROR),
        Filter("kaleido", "Kaleido", KALEIDO),
        Filter("tunnel", "Tunnel", TUNNEL),
    )

    fun byId(id: String?): Filter = all.firstOrNull { it.id == id } ?: none

    fun indexOf(filter: Filter): Int = all.indexOfFirst { it.id == filter.id }.coerceAtLeast(0)

    /** Stepping wraps, because a physical dial should never dead-end. */
    fun step(from: Filter, by: Int): Filter {
        val size = all.size
        val next = ((indexOf(from) + by) % size + size) % size
        return all[next]
    }

    /**
     * How long the wheel rests on [none] before it will move again.
     *
     * A dial that treats "no filter" as one position among seventeen makes the most common
     * setting the hardest to find: you spin past it, come back, and spin past it the other way.
     * The first attempt at fixing that gave None three notches of its own, which worked but
     * meant three deliberate clicks to leave — the wheel felt broken rather than detented.
     *
     * This is the better answer: landing on None **stops the dial dead** for a moment. Every
     * notch inside that window is swallowed, so a fast spin cannot skate over it, and it costs
     * nothing to leave once the moment has passed. A film advance that catches at the frame line
     * does exactly this.
     */
    const val NONE_DWELL_MS = 1_500L
}
