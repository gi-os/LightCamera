## Roll v1.1 — an unobstructed viewfinder

This release is almost entirely about what is *not* on the screen.

**The frame box is gone**

- The preview now fills the panel, edge to edge, like the stock Light camera. No letterbox, no
  mattes, no borders, no sprocket strips around the picture.
- Chrome floats in the system-bar insets over a gradient that fades to nothing before it
  reaches the image: invisible against a dark scene, and the only reason the icons are legible
  against a bright one.
- Aspect (4:3, 3:2, 16:9, 1:1) is now purely a crop applied when the photograph is written.
  Because the viewfinder fills the screen and the sensor is 4:3, the file keeps a little more
  than you saw — the settings screen says so.
- The rule-of-thirds grid is still there if you want it; nothing else draws on the picture.
- The horizon level only appears while the phone is crooked, and lingers a beat after you
  straighten up so you actually see it close.

**Focus you can see, and hear**

- The focus mark is now LightOS's own, taken from the drawables the stock camera ships: four
  corner brackets while the lens hunts, closing into a solid box the moment it locks, tweened
  over the 90 ms the lens really takes.
- **Every face the camera detects gets a box.** The one the lens is working on gets the focus
  mark instead, squared off around the same centre.
- `AF-S` / `AF-C` sits in the top bar and inverts — white on black becomes black on white — as
  soon as focus locks. Tap it to switch modes.
- **A digicam beep.** Two short high blips and a buzz when the lens lands, one lower note when
  it gives up, and a tick for the shutter. Synthesised PCM rather than bundled audio, on the
  sonification stream, silent when the ringer is. It fires off the camera's own
  `CONTROL_AF_STATE`, so it means the lens has it — not that a request went out.
- Tapping to focus buzzes immediately for the ask, separately from the confirmation.
- Switch the sounds off under Settings → Shutter.

**The roll shows all your photos**

- Default scope is now every image on the device rather than only `DCIM`, which had been
  hiding screenshots and anything saved by another app. The header still toggles back to just
  the camera roll.

**Other**

- A loaded roll now shows its sprocket strip and counter in the black band under the picture
  rather than around the frame, and the shutter release is still square while film is loaded.
- Zoom, EV and self-timer readouts moved into the top bar next to the flash.

Requires Android 13 or newer — AGSL does.
