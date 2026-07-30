## Roll v1.5 — sideways chrome, upright picture, and a mode picker

**The layout is now the split the stock camera actually makes**

A screengrab of the real thing settled it: the control band is written **sideways** down the left
edge, and the viewfinder image is **upright** in the phone's own frame. v1.4 rotated both, which
spun the picture the moment you held the phone normally and turned the swipe down to the roll
into a sideways one.

So only the strips of chrome are rotated. The roll, the viewer and the settings are ordinary
portrait screens, and **swipe down for photos is a swipe down again**. Turn the phone
anticlockwise to shoot and the band is along the bottom with the camera key up top, where a
shutter release belongs.

**Camera / Video / Selfie**

The chevron beside the mode now opens a picker with those three, out of the same slot the stock
app puts `PHOTO ⌄` in.

- **Selfie** is the front lens, exactly as it is there. Double-tapping the image still switches.
- **Video records for real** — CameraX `VideoCapture` at HD into `DCIM/Camera`, with audio when
  the permission is granted. It's asked for when you switch into video, never at the moment you
  press record, because a dialog in front of the thing you were filming is worse than silent
  footage. The camera button and the volume keys start and stop it, and a record dot and timer
  replace the AF badge.
- `VideoCapture` is bound *instead of* `ImageCapture`, not alongside it: all three use cases at
  once is only guaranteed on `LEVEL_3` hardware.
- **Filters are off in video.** A `RenderEffect` belongs to the view, so it never reaches the
  recorded stream — a filtered preview would promise something the file wouldn't deliver.
- Filters and settings moved onto the end of the same strip, so the band stays at the stock four.

**None is three notches wide**

On the wheel's filter track, "None" now occupies three positions instead of one. Treating the
most common setting as one of fifteen made it the hardest to find — you spin past, come back, and
spin past the other way. Now it's a detent: easy to land on, a deliberate three notches to leave.

**The level was 90° out**

It was measuring roll against portrait. It now reads off the **nearest quarter turn**, so it's
square in every pose you'd actually shoot from — upright or held sideways like a camera — and
lands in ±45° by construction.

**Colour says why it isn't working**

The viewfinder stays grey until one adb grant, and the grant only became possible in v1.4.5 when
the permission was first declared. The viewfinder now says so instead of looking broken:

```sh
adb shell pm grant com.gios.lightcamera android.permission.WRITE_SECURE_SETTINGS
```

Force-stop the app once afterwards, then Settings → Colour.

Requires Android 13 or newer — AGSL does.
