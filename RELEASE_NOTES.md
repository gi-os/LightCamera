## Roll v1.4 — held like a camera, and in colour

**The whole app is drawn a quarter turn round**

This is how LightOS's own camera works, and it takes a moment to see why. The window stays
locked to portrait — nothing reflows, there is no landscape layout — and the interface is
*drawn* rotated inside it. Turn the phone anticlockwise, the way you'd pick up a compact
camera, and everything is upright: the picture fills the width, the control band runs along the
bottom, and the phone's camera key has come round to the top edge exactly where a shutter
release belongs. Held in portrait, that band runs down the left-hand side.

Rotating rather than supporting landscape is deliberate:

- **Nothing moves while you shoot.** A layout that reflowed would swap the band from one edge
  to another as you turned the camera to frame something.
- **Every spatial idea survives.** The roll is still above the viewfinder and still arrives
  with a downward pull, because "down" in there is down in your hand.
- **The framing comes out right for free.** A 4:3 sensor nearly fills a screen turned on its
  side, so almost nothing is cropped — portrait was throwing away a third of it.

**The band is the stock four**

Album, the mode slot, flash, brightness — the stock camera's own order and spacing, measured
off a photograph of the real thing rather than guessed. The mode slot does the job "PHOTO ⌄"
does there: it names what the camera is set to and opens the picker. Here that is the filter.

The lens switch moved to a **double tap on the image**, which is where every other phone camera
keeps it, so the band stays at four items.

**The viewfinder is in colour**

The panel is a full-colour AMOLED; Light's black and white is the accessibility daltonizer
pinned to monochromacy. Roll lifts it while the camera or a photograph is on screen and puts it
back the moment you leave the app — the rest of LightOS stays grey, because that is your
setting and not the camera's. Half the filters are about colour, and a viewfinder showing a grey
version of the photograph it is about to save was misrepresenting the picture.

One adb grant, once:

```sh
adb shell pm grant com.gios.lightcamera android.permission.WRITE_SECURE_SETTINGS
```

Without it the write is simply refused and everything stays grey. Settings → Colour switches
between the viewfinder only, the whole app, and off.

**Photographs come out the way you held the phone**

With the window locked to portrait, CameraX was baking upright into every file, so a photograph
taken with the phone held horizontally arrived on its side. Only the capture's target rotation
now follows the accelerometer; the preview's is deliberately left alone, because the face mapper
reads it.

**Also**

- Roll now names LightControl when it is the reason the camera key is doing nothing, rather than
  looking broken — and the version that fixed it.
- The wheel scrolls the roll the way it's turned; `reverseLayout` had been reversing the scroll
  axis with it.

Requires Android 13 or newer — AGSL does.
