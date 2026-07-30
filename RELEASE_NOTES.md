## Roll v2.21 — Simple stops using the still pipeline

1877 ms, then 1815 ms after asking the HAL for fast post-processing. A three percent difference: this
camera's still pipeline is a fixed cost, and no capture request an app can send will move it.

**So Simple doesn't take a still any more**

It binds a high-resolution analysis stream in place of the stills unit, keeps the newest frame in memory as
NV21, and a shutter press *copies what is already there*. There is no capture to wait for — the frame
existed before you pressed. The only work left is our own JPEG encode, which is a couple of hundred
milliseconds of CPU rather than the camera's second and a half.

Bound **instead of** `ImageCapture`, not alongside: this camera is LEVEL_3 and will not give three streams
at once. Simple never called `takePicture` anyway. Pro is completely unchanged and still uses the stills
pipeline, because that is where somebody asked for the best file the camera can make.

**What you give up, plainly**

- **No flash in Simple.** There is no still request to fire it with. Pro has it.
- **Slightly noisier.** This is the ISP's frame without the still pipeline's stacking and denoising. In
  daylight it is hard to tell; in a dim room it will be visible.
- **The resolution is whatever the camera will give an analysis stream.** It asks for 12MP and takes the
  closest available. That might be 12MP, it might be 1080p — this varies by device and there is no way to
  know without asking the hardware. **So the readout now tells you**: `120ms shot · 60ms save · 12.2MP`.

If that last number comes back low enough to bother you, the answer is a middle path — the stills pipeline
at 5MP, which is slower than this but faster than 12MP and keeps the flash. The number will say.

**Under it**

The YUV→NV21 conversion happens on the camera's executor as each frame arrives, not at the press, so the
shutter is a reference read. It handles both chroma layouts phones actually produce — already-interleaved VU
with a pixel stride of two, and two tightly packed planes that have to be woven a byte at a time. Assuming
either one is a bug on half the phones in circulation.
