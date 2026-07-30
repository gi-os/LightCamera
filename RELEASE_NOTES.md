## Roll v1.0 — first release

A camera and an album in one app for the Light Phone III, arranged vertically: the roll sits
above the viewfinder, and you pull it down.

**Navigation**

- Swipe down on the camera for the roll; the newest photograph hangs against the top edge of
  the frame and older ones run upwards. Flick up from anywhere to get back to the shutter.
- The roll is the device's real camera roll (all of `DCIM`), not a second album. Toggle it to
  every image on the phone from the header.
- Full-screen viewer with the wheel advancing frames, share, and binning through the system
  trash dialog.

**A two-stage shutter release**

- Half press the camera button to autofocus and lock on the nearest face; press through to
  fire. The two keys arrive in an unpredictable order and that is handled.
- Faces come from the camera's own hardware detector over Camera2 interop — no bundled ML
  model — and the focus bracket follows the real `CONTROL_AF_STATE`.
- Single or continuous AF; continuous re-acquires only when the subject actually moves.
- Tap anywhere in the frame to focus there. Press and hold the on-screen shutter for the same
  two-stage behaviour.

**The wheel is a lens ring**

- Turn to zoom, geometrically, so the framing changes by the same proportion at 1x and 8x.
- Hold it in and turn for exposure compensation in thirds of a stop.
- Click for the torch.

**Fifteen filters, as AGSL shaders**

- Film (moving grain, halation, vignette), Mono, Dither 16 (EGA palette, Bayer dither),
  1-Bit, Halftone, Comic, Thermal, X-Ray, Glow, and the Photo Booth distortions: Twirl,
  Bulge, Mirror, Kaleido, Tunnel.
- The same shader runs on the live preview and on the captured photograph, so the file
  matches the frame. Patterns are sized to the image, so a dithered 12MP photo looks like the
  preview did.
- Tap the filter name for a live grid of all fifteen at once.

**Film-roll mode**

- Load 12, 24 or 36 frames. Photographs go to private storage with no preview and no review —
  just a counter — until the roll is developed.
- Developing writes every frame into `DCIM/Camera` keeping the time each was taken, then
  shows a contact sheet.
- The viewfinder dresses for it: sprocket strips, a mechanical frame counter, a rangefinder
  patch, and a square shutter release.

**Frames**

- 4:3, 3:2, 16:9 and 1:1, shown as a box in exactly that ratio with the controls in the
  margins rather than over the picture. What is inside the box is what is saved.
- Horizon level, rule-of-thirds, self timer.

**Installing it as the phone's camera**

- Claims `STILL_IMAGE_CAMERA`, `IMAGE_CAPTURE`, `CAMERA_BUTTON` and the lock-screen `_SECURE`
  variants, and honours `EXTRA_OUTPUT`, so other apps' "take a photo" lands here properly.

Requires Android 13 or newer — AGSL does.
