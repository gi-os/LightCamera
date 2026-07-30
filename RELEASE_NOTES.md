## Roll v2.23 — "nothing on the viewfinder yet"

Three faults, and the first one is why you saw nothing at all.

**The converter threw on every frame, silently**

The YUV→NV21 luma copy indexed with `rowStride` and `remaining()` in a way that walks off the end of a
padded plane — and the call site wrapped it in a `runCatching` that discarded the reason. So a converter
failing on every single frame looked exactly like a camera that had not started yet: no frames, no clue.
Everything is clamped to what the buffer actually holds now, a short plane is refused rather than encoded as
green, and the failure logs three lines to logcat instead of nothing.

**The shutter gave up instantly**

Pressing within a moment of the camera binding — which is what happens when you open straight into a
photograph — can beat the analyser's first delivery. It now waits up to half a second for a frame before
complaining, and complains differently: "the live stream gave nothing — see logcat" rather than blaming the
viewfinder.

**12MP was probably too much to ask of an analysis stream**

That format is uncompressed YUV: 18MB per frame, thirty times a second. A camera that will not do it can
simply decline, which is a strong candidate for why nothing arrived. It now asks for 2560×1920 — five
megapixels, a genuine photograph, and much closer to what analysis streams actually support. The readout
still reports what actually arrived, so you can see it.

**If it still says nothing**

Then this camera will not give a usable second stream and the approach is wrong for this phone. Say so and I
will put Simple back on the stills pipeline — 1.8 s, but reliable — and we can spend the speed budget on
making the wait feel deliberate instead.
