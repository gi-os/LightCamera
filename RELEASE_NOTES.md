## Roll v1.8 — a shutter that answers

Three changes, all latency. Nothing about the interface moved.

**Twelve megapixels instead of fifty**

This is the whole thing. The LPIII's sensor is 50MP and, left alone, CameraX asks the camera for
the biggest JPEG it will give — 8160 x 6144. Reading that out and encoding it costs the ISP the
better part of a second or two, which is exactly the "one to three seconds" every review of this
phone complains about, and then this app had to decode it again to apply a filter.

It now asks for 4000 x 3000. Nothing is lost that anyone can see: still four times the pixels of
the largest print you'd make from a phone, and about thirty times the panel you'll look at it on.
What's gained is a shutter that answers.

**Zero shutter lag**

Where the hardware supports it and the flash is off, the capture now uses
`CAPTURE_MODE_ZERO_SHUTTER_LAG`: the camera keeps a ring of recent frames and hands back the one
from the instant the button went down. The photograph is the moment you pressed rather than the
moment the camera got round to it. CameraX falls back on its own where the phone won't do it, so
it costs nothing to ask.

**Filtered shots decode down, not across**

A filter meant decoding the full JPEG — 200MB of ARGB at 50MP, seconds of work and a genuine
out-of-memory risk — and then scaling it to fit a GPU texture. The decoder now does that
reduction itself with `inSampleSize`, in powers of two, for a fraction of the cost.

JPEG quality is 92 rather than 95, which is invisible and shortens the encode.

If it still feels slow after this, the next lever is a shutter that grabs the preview frame
instead of taking a real capture — instant, at panel resolution. Say the word.
