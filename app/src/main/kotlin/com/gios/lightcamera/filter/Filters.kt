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
    /**
     * Sixteen greys, ordered-dithered — the grayscale half of Dither 16.
     *
     * Not the colour one desaturated: quantising *after* a colour match would land on whichever of the
     * sixteen EGA entries happened to be nearest and then flatten it, which throws away most of the tonal
     * range. This quantises luminance directly, so all sixteen steps are used and the gradients stay smooth
     * in the way a 4-bit greyscale image does.
     *
     * The dither offset is a step and a half wide rather than a step: at exactly one step the pattern is
     * almost invisible on a photograph, and the point of dithering is that you can see it.
     */
    private const val DITHER_GREY = """
half4 main(float2 xy) {
    float3 c = tap(xy);
    // A touch of contrast first. Sixteen levels across a flat photograph is mud; across a slightly punchy
    // one it reads as an old greyscale scan.
    float l = clamp((lum(c) - 0.5) * 1.18 + 0.5, 0.0, 1.0);
    float steps = 15.0;
    float t = bayer8(xy / (unitPx() * 2.0)) - 0.5;
    l = clamp(l + t * (1.5 / steps), 0.0, 1.0);
    float q = floor(l * steps + 0.5) / steps;
    return half4(float4(q, q, q, 1.0));
}
"""

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
     * **Purikura.** The Japanese photo-booth look, and the only shader here that knows where a face
     * is.
     *
     * A booth does four things to you, all of them too much on purpose, and this does the same four:
     *
     *  1. **Eyes twice the size.** A radial magnification centred on each eye — sampled *towards*
     *     the eye's centre, which is what makes it grow. The eyes are guessed from the face
     *     rectangle rather than detected: a quarter of the face's width either side of centre, a
     *     fifth of its height above the middle. That is where eyes are on a face, and the hardware
     *     detector's landmarks are not available on every camera, whereas its rectangle always is.
     *  2. **Skin blown out.** Luminance lifted hard and the top end crushed flat, so faces come out
     *     poreless and papery. This is the part people actually go for.
     *  3. **Pink.** A wash pulled towards a cool rose in the shadows and a warm one in the
     *     highlights, saturation up, contrast down. Booth prints have almost no black in them.
     *  4. **Glitter.** Four-pointed stars scattered on a hash grid, brighter near a face, drifting
     *     with `seed` so they twinkle in the viewfinder.
     *
     * The face uniforms are `face0..face2` as (centre x, centre y, half width, half height) in
     * fractions of the image, and `faceCount` says how many are real. Three separate `float4`s
     * rather than an array because an unset uniform is a compile-time promise a `RuntimeShader`
     * will not let you break, and three is a photo booth's worth of people.
     *
     * With no face in frame it is still the wash, the bloom and the glitter — a booth with nobody
     * in it is a pink room.
     */
    private const val PURIKURA = """
uniform float4 face0;
uniform float4 face1;
uniform float4 face2;
uniform float faceCount;

// **Every part of the look, switchable.** (eyes, chin, slim, skin) and the wash on its own — all 0..1,
// all multiplying an amount rather than gating a branch, so a half-strength setting would work if one
// were ever offered and turning something off costs a multiply rather than a different shader.
uniform float4 warp;
uniform float wash;

/**
 * Which way up the face is, in quarter turns: 0, 1, 2, 3.
 *
 * **Without this every warp was wrong the moment you turned the phone.** The shader runs on the panel,
 * and the panel is portrait-locked — so held sideways a face lies on its side in the image, with the eyes
 * one above the other. The eye positions were being guessed left-and-right of centre regardless, which
 * put the magnification on a forehead and a chin. Everything below is measured along the face's own axes
 * instead of the image's.
 */
uniform float faceTurn;

float4 faceAt(int i) {
    if (i == 0) return face0;
    if (i == 1) return face1;
    return face2;
}

// How much this colour looks like skin: warm, red above green above blue, not too saturated.
// Deliberately generous — it decides how hard to smooth, not whether to, so being wrong about a
// wooden floor costs a slightly soft floor.
float skinness(float3 c) {
    float mx = max(max(c.r, c.g), c.b);
    float mn = min(min(c.r, c.g), c.b);
    float sat = mx - mn;
    float warm = clamp((c.r - c.b) * 3.0, 0.0, 1.0);
    float order = (c.r >= c.g && c.g >= c.b) ? 1.0 : 0.35;
    float bright = smoothstep(0.15, 0.45, mx);
    float notNeon = 1.0 - smoothstep(0.45, 0.8, sat);
    return clamp(warm * order * bright * notNeon, 0.0, 1.0);
}

// Magnify around a point: sample nearer its centre, so what is there grows.
float2 magnify(float2 p, float2 centre, float radius, float amount) {
    float2 d = p - centre;
    float dist = length(d);
    if (dist >= radius || radius <= 0.0) return p;
    float t = dist / radius;
    // Smooth all the way to the rim, or the enlargement has a visible edge — a disc of face
    // sitting on a face, which is the tell of a bad beauty filter.
    float k = mix(1.0 / amount, 1.0, smoothstep(0.0, 1.0, t));
    return centre + d * k;
}

half4 main(float2 xy) {
    float2 p = xy;
    int n = int(faceCount);

    // ---- the shape of the face ----
    // No `break` and no `continue`: SkSL wants a loop it can unroll, and a face that is not there is
    // handled by a zero half-extent, which every warp below refuses, rather than by leaving early.
    //
    // Three warps, in the order a booth applies them: the eyes grow, the jaw comes in, the whole head
    // shrinks a little. All three read the *original* rectangle rather than each other's output, which
    // compounds slightly and is invisible at these strengths — and is far easier to reason about than
    // three warps chasing a moving centre.
    // The face's own axes: `ax` runs along the eye line, `ay` down from the brow to the chin. At a
    // quarter turn they are the image's y and -x, which is what makes every offset below correct in
    // every pose instead of only in portrait.
    float turns = mod(faceTurn, 4.0);
    float sideways = (turns == 1.0 || turns == 3.0) ? 1.0 : 0.0;
    float ca = (turns == 0.0) ? 1.0 : ((turns == 2.0) ? -1.0 : 0.0);
    float sa = (turns == 1.0) ? 1.0 : ((turns == 3.0) ? -1.0 : 0.0);
    float2 ax = float2(ca, sa);
    // **Negated, and determined on the device rather than from first principles.** `ay` is "down the
    // face", from brow to jaw. Built the obvious way round it pointed the other way: the chin squeeze
    // appeared on the forehead, which also means the eye magnification — placed at minus ay — had been
    // landing near the mouth this whole time. One sign, both faults. If a future change makes the warps
    // look upside down again, this is the line.
    float2 ay = -float2(-sa, ca);

    for (int i = 0; i < 3; ++i) {
        float4 f = faceAt(i);
        float2 mid = float2(f.x, f.y) * size;
        // Half extents, in pixels, in the *image's* axes. `ext` rather than `half`, which is a type here.
        float2 ext = float2(f.z, f.w) * size * (i < n ? 1.0 : 0.0);
        // The same two numbers along the face's axes: sideways, the face's width is the box's height.
        float across = mix(ext.x, ext.y, sideways);
        float down = mix(ext.y, ext.x, sideways);

        // Eyes. Guessed from the rectangle — a fifth of the face's width either side of centre, a
        // quarter of its height above the middle — because the hardware's landmarks are not available on
        // every camera and its rectangle always is. Kept gentle: the box is loose, often taking in hair
        // and forehead, so a strong magnification lands on an eyebrow as readily as an eye.
        float2 eyeL = mid - ax * (across * 0.40) - ay * (down * 0.26);
        float2 eyeR = mid + ax * (across * 0.40) - ay * (down * 0.26);
        float radius = across * 0.38;
        p = magnify(p, eyeL, radius, 1.0 + 0.55 * warp.x);
        p = magnify(p, eyeR, radius, 1.0 + 0.55 * warp.x);

        // Chin. Squeezed along the eye line, and only below the middle of the face: the amount ramps from
        // nothing at the cheekbones to full at the jaw, which is the difference between a taper and a
        // waist. Sampling *further out* pulls the picture in, so the multiplier is above one.
        if (down > 0.0 && warp.y > 0.0) {
            float2 d = p - mid;
            float lower = clamp((dot(d, ay) - down * 0.15) / (down * 1.0), 0.0, 1.0);
            float near = 1.0 - smoothstep(across * 0.9, across * 1.9, abs(dot(d, ax)));
            float k = 0.18 * warp.y * smoothstep(0.0, 1.0, lower) * near;
            p = p + ax * (dot(d, ax) * k);
        }

        // The whole head, in a little. A radial version of the same trick, falling off to nothing well
        // outside the rectangle so there is no seam at the hairline.
        float reach = max(max(ext.x, ext.y) * 1.8, 0.0001);
        float away = length(p - mid) / reach;
        if (away < 1.0 && ext.x > 0.0 && warp.z > 0.0) {
            float k = 1.0 + 0.10 * warp.z * (1.0 - smoothstep(0.0, 1.0, away));
            p = mid + (p - mid) * k;
        }
    }

    // ---- skin smoothing ----
    // **Edge-preserving, and only on skin.** A plain blur is what makes a beauty filter look like
    // a smear: it takes the eyelashes and the hairline with it. This weights each tap by how far
    // its colour is from the centre pixel — a cross-bilateral filter — so a tap that has fallen
    // off the face onto hair or background contributes almost nothing, and the edge survives while
    // the pores inside it average away.
    //
    // Two rings of six taps at different radii rather than one dense ring: the wide ring does the
    // smoothing and the tight one keeps it from banding, for twelve samples instead of the
    // twenty-five a 5x5 kernel would need.
    float3 here = tap(p);
    float smoothing = mix(0.35, 1.0, skinness(here)) * warp.w;
    float rad = unitPx() * 3.4 * smoothing;
    float3 sum = here;
    float weight = 1.0;
    for (int i = 0; i < 6; ++i) {
        float a = float(i) * 1.0471976;
        float2 dir = float2(cos(a), sin(a));
        for (int ring = 1; ring <= 2; ++ring) {
            float2 q = p + dir * rad * float(ring);
            float3 t = tap(q);
            // Colour distance decides everything. 9.0 is tuned so that skin-to-skin variation
            // passes and skin-to-anything-else does not.
            float w = exp(-dot(t - here, t - here) * 9.0) / float(ring);
            sum += t * w;
            weight += w;
        }
    }
    float3 smoothed = sum / weight;

    // Put a little of the real detail back, or the face reads as plastic rather than as a booth
    // print. Booth prints are soft, not featureless.
    float3 col = mix(smoothed, here, 0.12);
    // And keep the eyes sharp — they are the one thing a purikura wants crisp, having just doubled
    // them in size.
    float sharpness = 0.0;
    for (int i = 0; i < 3; ++i) {
        float4 f = faceAt(i);
        float2 mid = float2(f.x, f.y) * size;
        float2 ext = float2(f.z, f.w) * size;
        float2 eyeL = mid - ax * (mix(ext.x, ext.y, sideways) * 0.40) - ay * (mix(ext.y, ext.x, sideways) * 0.26);
        float2 eyeR = mid + ax * (mix(ext.x, ext.y, sideways) * 0.40) - ay * (mix(ext.y, ext.x, sideways) * 0.26);
        float reach = max(mix(ext.x, ext.y, sideways) * 0.40, 1.0);
        float near = max(
            1.0 - clamp(length(p - eyeL) / reach, 0.0, 1.0),
            1.0 - clamp(length(p - eyeR) / reach, 0.0, 1.0)
        );
        sharpness = max(sharpness, near * (i < n ? 1.0 : 0.0));
    }
    col = mix(col, here, sharpness * 0.75);

    // ---- skin blown out ----
    // From here down is the wash: the blow-out, the pink and the glitter. Switched off, what is left is
    // the smoothing and the warps — a beauty filter without the booth, which is a reasonable thing to
    // want and is why it is one switch rather than part of the filter's identity.
    float l = lum(col);
    // Lift, then crush the top: 0.55 arrives at 0.82, and 0.8 and 1.0 are nearly the same white.
    float lifted = pow(clamp(l * 1.22 + 0.16, 0.0, 1.0), 0.62);
    col = mix(col, col + (lifted - l), wash);

    // ---- pink ----
    float3 shadow = float3(1.02, 0.94, 1.02);
    float3 light = float3(1.06, 0.93, 0.95);
    col *= mix(float3(1.0), mix(shadow, light, clamp(lifted, 0.0, 1.0)), wash);
    float grey = lum(col);
    col = mix(float3(grey), col, 1.0 + 0.35 * wash);
    // Nothing is allowed to be properly black. Booth prints wash out in the shadows and that
    // missing black is half of why they look like booth prints.
    col = mix(col, float3(1.0, 0.97, 0.98), 0.10 * wash);

    // ---- glitter ----
    // On a grid so the stars keep still between frames, jittered inside their cells so the grid
    // cannot be seen, and only about one cell in twelve lights up.
    float cell = unitPx() * 26.0;
    float2 g = floor(p / cell);
    float pick = hash(g);
    float bright = 0.0;
    if (pick > 0.90) {
        float2 jitter = float2(hash(g + 3.1), hash(g + 7.7)) - 0.5;
        float2 star = (g + 0.5 + jitter) * cell;
        float2 d = abs(p - star);
        float arm = cell * 0.34 * (0.55 + 0.45 * sin(seed * 0.11 + pick * 40.0));
        // Two thin bars crossed, plus a hot centre: a four-pointed star, which is the shape a
        // booth's sparkle overlay uses and also the cheapest one to draw.
        float horiz = max(0.0, 1.0 - d.x / arm) * max(0.0, 1.0 - d.y / (arm * 0.13));
        float vert = max(0.0, 1.0 - d.y / arm) * max(0.0, 1.0 - d.x / (arm * 0.13));
        float core = max(0.0, 1.0 - length(d) / (arm * 0.22));
        bright = clamp(horiz + vert + core * 0.9, 0.0, 1.0);
    }
    // Denser where the people are, because that is where a booth puts them.
    float nearFace = 0.0;
    for (int i = 0; i < 3; ++i) {
        float4 f = faceAt(i);
        float2 mid = float2(f.x, f.y) * size;
        float2 ext = float2(f.z, f.w) * size;
        float reach = max(max(ext.x, ext.y) * 2.4, 1.0);
        float near = 1.0 - clamp(length(p - mid) / reach, 0.0, 1.0);
        nearFace = max(nearFace, near * (i < n ? 1.0 : 0.0));
    }
    col += bright * (0.55 + 0.45 * nearFace) * wash;

    // ---- a white glow in from the corners ----
    float2 q = (p / size - 0.5) * 2.0;
    float edge = clamp(length(q) - 0.72, 0.0, 1.0);
    col = mix(col, float3(1.0, 0.98, 0.99), edge * 0.55 * wash);

    return half4(float4(clamp(col, 0.0, 1.0), 1.0));
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
     *
     * [facesAware] marks the one that is handed the detected faces as uniforms. It also takes the
     * viewfinder frame, for a different and stricter reason: the faces are detected **in the
     * preview**, and a photograph made from a second, differently-cropped frame would have to have
     * those rectangles mapped across — which is exactly the arithmetic that puts an enlarged eye
     * next to somebody's ear. Filtering the frame the faces were found in cannot be misaligned.
     */
    data class Filter(
        val id: String,
        val label: String,
        val agsl: String?,
        val animated: Boolean = false,
        val lowRes: Boolean = false,
        val facesAware: Boolean = false,
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
        Filter("dithergrey", "Dither BW", DITHER_GREY, lowRes = true),
        Filter("onebit", "1-Bit", ONE_BIT, lowRes = true),
        Filter("halftone", "Halftone", HALFTONE, lowRes = true),
        Filter("gameboy", "Game Boy", GAMEBOY, lowRes = true),
        Filter("gbcolor", "GB Color", GB_COLOR, lowRes = true),
        Filter("comic", "Comic", COMIC),
        Filter("purikura", "Purikura", PURIKURA, animated = true, facesAware = true),
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

    /**
     * The same catch on Purikura, half as long.
     *
     * Purikura is the other filter you are aiming *for* rather than passing through — it has a menu
     * behind it and four-shot strips behind that — so the dial should hesitate there as well. Shorter
     * than None's, because None is the way back to an ordinary photograph and this is a place you went
     * looking for on purpose.
     */
    const val PURIKURA_DWELL_MS = 500L

    /** How long the dial should stop dead on [filter], or zero for the ones you scroll past. */
    fun dwellMs(filter: Filter): Long = when {
        filter.id == none.id -> NONE_DWELL_MS
        filter.facesAware -> PURIKURA_DWELL_MS
        else -> 0L
    }
}
