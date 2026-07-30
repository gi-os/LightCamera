## Roll v1.2 — laid out like LightOS's own camera

**The layout**

- Image on top, **one black band beneath it**, nothing drawn over the picture. The band reads
  left to right as **album, lens, mode, flash, brightness** — the stock camera's own order,
  where the mode slot does the job "PHOTO ⌄" does there: it names what the camera is set to and
  opens the picker. Here that is the filter.
- **The system bars are hidden**, so the picture starts at the very top edge of the panel. On a
  3.92" screen a status bar is four percent of the viewfinder spent telling you the time. Swipe
  from an edge to get them back.
- **No shutter button.** The phone has a two-stage shutter release on its side; a circle on the
  glass duplicating it only takes room from the image and teaches the wrong gesture. Tapping the
  frame focuses instead, which is what a touchscreen is better at.
- **Either volume key is also a shutter**, in case something is swallowing the camera key.
- Brightness opens a strip of exposure stops with whole stops marked taller than the thirds
  between them. While it is open the bare wheel drives exposure; tap the strip to reset.
- Settings moved to the roll's header and the filter picker — one tap from the viewfinder,
  nothing on the image.

**The camera button works again**

The camera key was being swallowed by **LightControl**, whose default binding opens a camera —
which it did even with a camera already open and in front, so Roll never saw the key and its
shutter was dead. Fixed in [LightControl
v1.1.6](https://github.com/gi-os/LightControl/releases/latest): any app registered for
`STILL_IMAGE_CAMERA` now gets **both stages of the camera button untouched**, whatever is bound
to it. The test is what the app declares rather than a list of package names, so it holds for
cameras that don't exist yet, and an explicit per-app `OFF` rule still wins.

**Update both.** Roll alone can't fix this — the key never reaches it.

Requires Android 13 or newer — AGSL does.
