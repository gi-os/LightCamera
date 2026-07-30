## Roll v2.20 — 1877 ms was the camera, not the app

The measurement came back **1877 ms in `takePicture`, 87 ms to save**. That settles it: the save was never
the problem, and neither was anything else I changed in the last three releases. The camera is doing nearly
two seconds of work per still.

**What it is doing**

A still on a modern HAL is not one exposure. It is a short burst, stacked for noise, denoised, sharpened,
tone-mapped — and every one of those stages has a HIGH_QUALITY setting and a FAST one. Asking for
`CAPTURE_MODE_MINIMIZE_LATENCY`, which Roll already did, is CameraX *hinting* that the fast ones will do.
Plenty of HALs ignore the hint.

**So Simple now asks in the request itself**

Through Camera2 interop, on the still request only: noise reduction FAST, edge FAST, aberration correction
FAST, tone-map FAST, and capture intent PREVIEW — which is the blunt version of the same statement, telling
the HAL this frame does not need what a photograph normally gets. Pro is untouched: somebody there has asked
for the best file the camera can make, and waiting for it is the right trade.

Take a shot and read the number again. If it drops a lot, that was it. If it barely moves, this camera does
its stacking somewhere an app cannot reach, and the honest options left are both hardware-facing rather than
clever:

- **Shoot the preview stream instead.** A continuous stream means a capture is grabbing the latest frame
  already in flight — genuinely instant, no HAL still pipeline at all. We would JPEG-encode it ourselves.
  At the analysis stream's full resolution that is a real photograph rather than a screenshot, but it is the
  ISP's raw frame without its still-image processing: slightly noisier, and no flash.
- **Ask for less.** 8MP or 5MP, where the burst is shorter and there is less of everything to denoise.

Both are a few hours' work. Neither is worth doing until the number above says which.
