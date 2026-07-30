# Roll

A camera for the Light Phone III.

The roll sits **above** the viewfinder. Pull down on the camera and your photographs come
into view — every photo on the phone, newest one first, hanging against the top edge of the
frame with older ones running up behind it. Flick up from anywhere and you are back at the
shutter. That is the whole navigation model, and everything else in the app is arranged
around it.

It replaces both the stock Camera and the stock Album, and it can be set as the phone's
default camera, so the hardware camera button opens this instead.

## The camera button is a real two-stage release

The LPIII's camera button has two detents and reports them as two separate keys — `FOCUS`
at the half press, `CAMERA` at the bottom. Nothing in LightOS uses the first one. Here:

| Control | Does |
|---|---|
| Camera button, half press | Autofocus and lock on the nearest face, or the centre |
| Camera button, pressed through | Shutter |
| Either volume key | Shutter, as a fallback |
| Turn the wheel | Zoom, geometrically — the wheel is a lens ring |
| Hold the wheel in and turn | Exposure compensation, in thirds of a stop |
| Click the wheel | Torch |
| Tap the frame | Focus there |
| Swipe the frame sideways | Next filter |
| Swipe down | The roll |

There is **no shutter button on screen**, on purpose: the phone has one on its side, and a
circle on the glass duplicating it only costs image area and teaches the wrong gesture. If the
camera button does nothing, an accessibility service is swallowing it — most likely an old
[LightControl](https://github.com/gi-os/LightControl), which used to keep that key for itself.
From v1.1.6 it hands both stages to whatever camera is in front; update it.

The two keys arrive in an unpredictable order, so the release is a state machine
(`hw/ShutterRelease.kt`) rather than a pair of key handlers — see the tests for the cases
that matter.

Faces come from the **camera's own hardware detector**, read out of each capture result over
Camera2 interop, not from a bundled ML model. Every face gets a box; the one the lens is
working on gets the focus mark.

## The wheel needs nothing else installed

Zoom, exposure and the torch are the wheel's, and so is scrolling — the roll and the settings
page both move under it. None of it needs a service, a permission or root. Light patched
`/system/usr/keylayout/Generic.kl`, so a notch arrives as an ordinary key event delivered to
whichever app has focus, and this app reads those keys itself. Install the APK and the wheel is
a lens ring.

[LightControl](https://github.com/gi-os/LightControl) is optional, and what it adds is the rest
of the wheel — everywhere else on the phone. Hold the wheel in and turn for brightness, tap it
for the flashlight, the camera button to open a camera; each of those is rebindable, tap and
hold separately, to any installed app. It also gives brightness or a synthetic-swipe scroll to
apps that carry no wheel code of their own.

Installing it does not take scrolling away here. Bare turns are passed through to `com.gios.*`,
`com.lightfastread` and `com.lightrss.reader` on purpose. The camera button is the part worth
stating plainly, because a two-stage release is exactly the thing a global key service would
eat: LightControl hands that button to whichever camera is in front — anything registered for
`STILL_IMAGE_CAMERA` — rather than to a package it remembers. So Roll keeps its own half press
and its own shutter, and LightControl never sees either. That is a deliberate carve-out, not
luck.

```bash
# Optional: LightControl, for brightness, the flashlight and the camera button
adb install -r LightControl-v1.0.x.apk

# The key service. NOTE: this setting is a list, and this command REPLACES it —
# if you also run LightVoice's push-to-talk, colon-join both components instead.
adb shell settings put secure enabled_accessibility_services \
  com.gios.lightcontrol/com.gios.lightcontrol.keys.ControlService
adb shell settings put secure accessibility_enabled 1

# Brightness, and the level readout + opening apps from the service
adb shell appops set com.gios.lightcontrol WRITE_SETTINGS allow
adb shell appops set com.gios.lightcontrol SYSTEM_ALERT_WINDOW allow
```

The latest build is at <https://github.com/gi-os/LightControl/releases/latest>.

## The focus marks are LightOS's own

The stock camera ships two drawables — `ic_camera_focus_locking`, four corner brackets, and
`ic_camera_focus_locked`, a closed square. That is its entire focus vocabulary, so it is this
app's too: **brackets while the lens hunts, closing into a box the moment it locks**, tweened
over the 90 ms the lens actually takes. `AF-S`/`AF-C` sits in the top bar and inverts on lock,
so autofocus is visibly on rather than something you infer.

And it beeps. Two short blips and a buzz when the lens lands, one lower note when it gives
up — synthesised PCM rather than shipped audio, on the sonification stream, silent when the
phone is. It fires off the camera's own `CONTROL_AF_STATE`, so it means the lens has it, not
that a request was sent.

The viewfinder is otherwise as empty as the stock camera's: the image on top with only the
focus marks and an `AF-S`/`AF-C` badge on it, one black band underneath holding album, lens,
mode, flash and brightness, and a horizon line that appears only while the phone is crooked.
The system bars are hidden, so the picture starts at the panel's top edge.

## Filters are AGSL, and the photo matches the frame

Fifteen filters, each one a fragment shader. The same shader source runs twice: as a
`RenderEffect` on the live preview, and over a `BitmapShader` when the photograph is
written. So there is one definition of what Halftone looks like, and the file you get is the
frame you saw.

- **Film** — grain modulated by the midtones, halation on the highlights, a vignette. The
  grain moves.
- **Dither 16** — the EGA palette, ordered-dithered with a Bayer matrix. Very dithered, very
  sixteen colours.
- **1-Bit** — pure black and white, dithered. The phone's own idea of a photograph.
- **Halftone** — a rotated dot screen, one read per cell, so the dots stay round.
- **Mono, Comic, Thermal, X-Ray, Glow** and the Photo Booth distortions: **Twirl, Bulge,
  Mirror, Kaleido, Tunnel**.

Patterns are sized in design pixels rather than device pixels, which is why the dither in a
4000px photograph looks like the dither in the 340px preview instead of dissolving into
noise.

Tap the filter name for the **grid**: every filter running live on what the camera is
pointed at, all at once, the way Photo Booth used to do it.

### Applying a shader to a still is not three lines

`RuntimeShader` is compiled for the GPU and cannot draw onto a software `Canvas` — paint it
over a bitmap and you silently get nothing. `filter/ShaderRuntime.kt` drives a
`HardwareRenderer` into an `ImageReader` and reads the result back through a
`HardwareBuffer`, which is the supported way to get a hardware-accelerated draw with no view
on screen. The alternative would have been a second, CPU implementation of every filter,
drifting from the shader within a week.

## Film-roll mode

Load a roll of 12, 24 or 36. Photographs go into app-private storage instead of the gallery,
there is no preview and no review, and the only feedback is a counter and a click. When the
roll is finished — or when you decide — it develops: every frame is written into
`DCIM/Camera` at once, each keeping the time it was actually taken, and you get a contact
sheet of twenty-four photographs you have not seen yet.

The point isn't nostalgia. Checking the screen after every shot changes what you photograph.

A loaded roll shows itself in the black band under the picture — a strip of sprocket holes
that steps along with each frame, and the counter — and the shutter release turns from a
circle into a square, so a glance tells you the photograph is going onto film.

## The frame

4:3, 3:2, 16:9 or 1:1, applied as a centre crop when the photograph is written. The viewfinder
does **not** letterbox itself to match: it fills the screen, so the file keeps a little more
than you saw at the top and bottom of the frame. An earlier version did draw the exact save
aspect as a bordered box with the controls in the margins, which was honest about cropping and
horrible to look through.

## Setting it as the default camera

The app claims `STILL_IMAGE_CAMERA`, `IMAGE_CAPTURE`, `CAMERA_BUTTON` and the `_SECURE`
variants, and it honours `EXTRA_OUTPUT`, so other apps' "take a photo" works properly.

With both cameras installed, the first press of the camera key shows a chooser with an
"always" option — pick Roll there and the key belongs to it from then on. If the stock camera
already holds the default, clear it first in **Settings → Apps → Camera → Open by default →
Clear defaults**; Android has no supported adb command for setting a default camera, only for
the launcher.

If you have [LightControl](https://github.com/gi-os/LightControl) installed, you can skip all
of that and bind the camera button straight to `com.gios.lightcamera`.

Launched for `IMAGE_CAPTURE` the app shows only the viewfinder — no roll, no settings — takes
one photograph, writes it where the caller asked and finishes.

## Install

Grab the APK from [Releases](https://github.com/gi-os/LightCamera/releases), or point
[Obtainium](https://github.com/ImranR98/Obtainium) at this repo. Every build is signed with
the same committed key and the certificate fingerprint is pinned in `signing-fingerprint.txt`
and checked in CI, so updates install over each other.

```sh
adb install -r LightCamera-v1.0.x.apk
```

## Build

```sh
./gradlew :app:assembleRelease
```

Requires JDK 17. `minSdk` is 33 because AGSL is.

## Layout

```
camera/     CameraX, hardware face detection, AF, capture, EXIF and cropping
filter/     the AGSL sources and the two ways they get run
hw/         the wheel and the two-stage camera button
media/      MediaStore reads and writes, thumbnails
roll/       film-roll mode
ui/         the two pages, the viewfinder chrome, the filter grid
```

## Credits

Icons and the design tokens — the 27x31 grid, the type scale, the haptics — are from
[`lightphone/light-sdk`](https://github.com/lightphone/light-sdk), MIT, © The Light Phone.
See `LICENSE-light-sdk`.
