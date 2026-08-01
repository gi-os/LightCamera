# Roll

A camera for the Light Phone III.

The roll sits **above** the viewfinder. Pull down on the camera and your photographs come
into view — every photo on the phone, newest one first, hanging against the top-right corner
of the frame with older ones running up and leftward behind it, the way a contact sheet reads.
Flick up from anywhere and you are back at the shutter. That is the whole navigation model,
and everything else in the app is arranged around it.

It replaces both the stock Camera and the stock Album, and it can be set as the phone's
default camera, so the hardware camera button opens this instead. It is not a fork — a
full rewrite, and the app that has shipped the most releases in this collection.

**Current version:** `versionName` in `app/build.gradle.kts` is `2.36.0`, and the patch is
the CI run number — so the release published from the current `main` is `v2.36.x`
(2026-08-01), the one that turned QR scanning into a camera mode. See
[Version history](#version-history) for the full run from `v1.0.1`.

## Quick start

Sideload a release APK — no build required:

```sh
# grab LightCamera-v<version>.apk from https://github.com/gi-os/LightCamera/releases
adb install -r LightCamera-v1.8.11.apk
```

Every build is signed with the same committed key, fingerprint pinned in
`signing-fingerprint.txt` and checked in CI, so later releases install over this one — or
track the repo in [Obtainium](https://github.com/ImranR98/Obtainium) for automatic updates.

To make it the default camera so the hardware camera button opens it: the first press
after installing shows a chooser with an **"always"** option — pick Roll there. If the
stock camera already claims the default, clear it first in **Settings → Apps → Camera →
Open by default → Clear defaults** (Android has no adb command for setting a default
camera, only the launcher). With [LightControl](https://github.com/gi-os/LightControl)
installed you can skip all of that and bind the camera button straight to
`com.gios.lightcamera`.

To build it yourself:

```sh
git clone https://github.com/gi-os/LightCamera.git
cd LightCamera
./gradlew :app:assembleRelease
```

Requires JDK 17. `minSdk` is 33 because [AGSL](https://developer.android.com/develop/ui/views/graphics/agsl) — every filter is a fragment shader — is API 33.

## Controls

The camera button has two detents and reports them as two separate keys — `FOCUS` at the
half press, `CAMERA` at the bottom — and nothing in stock LightOS uses the first one:

| Control | Does |
|---|---|
| Camera button, half press | Autofocus and lock on the nearest face, or the centre |
| Camera button, pressed through | Shutter — in QR, opens the code on screen |
| Either volume key | Shutter, as a fallback |
| Turn the wheel | The next filter |
| Hold the wheel in and turn | Exposure compensation, in thirds of a stop |
| Click the wheel | Torch |
| Tap the frame | Focus there |
| Double tap the frame | Switch lens |
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
page both move under it. Since v1.6, a bare turn steps through the filters instead of zooming:
the LPIII has no optical zoom and the stock camera offers none, so a dial spent on digital crop
was a dial spent on nothing. None sits three notches wide on the track so a stray turn lands
somewhere harmless. None of it needs a service, a permission or root. Light patched
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
over the 90 ms the lens actually takes. `AF-S`/`AF-C` sits in the corner of the image and
inverts — white on black becomes black on white — the moment focus locks, so autofocus is
visibly on rather than something you infer.

And it beeps. Two short blips and a buzz when the lens lands, one lower note when it gives
up — synthesised PCM rather than shipped audio, on the sonification stream, silent when the
phone is. It fires off the camera's own `CONTROL_AF_STATE`, so it means the lens has it, not
that a request was sent.

## Sideways chrome, upright picture

The stock camera makes a split that isn't obvious until you look at a screengrab of it: the
control band is **written sideways** down the left edge, with `PHOTO ⌄` reading down it, while the
viewfinder image stays **upright in the phone's own frame**. That is the right division. The band
is sideways because you hold the phone like a camera to shoot — turn it anticlockwise and the band
is along the bottom with the camera key up top, where a shutter release belongs. The image is
upright because it is the image.

So only the strips of chrome are rotated (`HeldSideways` in `ui/Common.kt`), and every other
screen — the roll, the viewer, the settings — is an ordinary portrait screen. An earlier version
rotated the whole app: the picture spun with it, and the swipe down to the roll became a sideways
one.

The band is the stock four — **album, the mode slot, flash, brightness** — in the stock order and
spacing, measured off photographs of the real thing. Nothing floats over the picture: the strips
take their own width out of the left-hand side, which is why there is not a gradient anywhere in
this app. As of v1.9, every readout on the band has moved up two steps of the type scale — the
8-design-pixel `Micro` variant worked out to about six points on this panel, too small to read
at arm's length, and nothing uses it any more.

### The mode slot

`PHOTO ⌄` / `VIDEO ⌄` / `SELFIE ⌄`, and the chevron opens the picker, as it does there.

- **Selfie** is the front lens and nothing else, which is what it is on the stock camera too. A
  double tap on the image switches as well.
- **Video** records at HD into `DCIM/Camera` through CameraX's `VideoCapture`, bound *instead of*
  `ImageCapture` rather than alongside it — all three use cases at once is only guaranteed on
  `LEVEL_3` hardware. Audio when the permission is there, asked for on entering the mode rather
  than at the moment you press record. Filters are forced off: a `RenderEffect` belongs to the
  view and never reaches the recorded stream, so a filtered preview would be promising something
  the file wouldn't deliver.
- Filters and settings live on the end of the same strip, so the band stays at four items.

The system bars are hidden, so the picture starts at the panel's edge. On the image itself there
is only the focus mark, the `AF-S`/`AF-C` badge (a record dot and timer while filming), and a
horizon line that appears while the phone is crooked — measured off the **nearest quarter turn**,
so it is square whether you are holding the phone upright or sideways.

## The viewfinder is in colour

The panel is a **full-colour AMOLED**; Light's black and white is Android's accessibility
daltonizer pinned to monochromacy — a secure setting and a SurfaceFlinger colour matrix, so it
lifts instantly with no restart. LightOS does the same thing itself for photos and video.

Roll lifts it while the camera or a photograph is on screen and puts it back the moment the app
is not in front, because the rest of the phone being grey is your setting and not the camera's.
Half the filters here are about colour — Thermal is a false-colour ramp, Dither 16 is a
sixteen-colour palette — and a grey viewfinder was misrepresenting the photograph.

One adb grant, once:

```sh
adb shell pm grant com.gios.lightcamera android.permission.WRITE_SECURE_SETTINGS
```

The permission is `signature|privileged|development`, which is why adb can give it and the
installer cannot. Without it the write is refused, nothing breaks, and everything stays grey.
Settings → Colour chooses between the viewfinder only, the whole app, and off.

## Filters are AGSL, and the photo matches the frame

Seventeen filters, each one a fragment shader. The same shader source runs twice: as a
`RenderEffect` on the live preview, and over a `BitmapShader` when the photograph is
written. So there is one definition of what Halftone looks like, and the file you get is the
frame you saw.

- **Film** — grain modulated by the midtones, halation on the highlights, a vignette. The
  grain moves.
- **Dither 16** — the EGA palette, ordered-dithered with a Bayer matrix. Very dithered, very
  sixteen colours.
- **1-Bit** — pure black and white, dithered. The phone's own idea of a photograph.
- **Halftone** — a rotated dot screen, one read per cell, so the dots stay round.
- **Game Boy / GB Color** — the DMG and GBC palettes through a Bayer threshold, and the
  other half of the look: both quantise the image onto a grid of 128 cells across the
  short edge, the Game Boy Camera's real sensor width, sampling once per cell.
- **Mono, Comic, Thermal, X-Ray, Glow** and the Photo Booth distortions: **Twirl, Bulge,
  Mirror, Kaleido, Tunnel**.

Patterns are sized in design pixels rather than device pixels, which is why the dither in a
photograph looks like the dither in the preview instead of dissolving into noise.

The mode strip's **Filters** entry opens the grid: every filter running live on what the camera is
pointed at, all at once, the way Photo Booth used to do it. Sideways swipes on the image and the
wheel walk through them too — since v1.6 the wheel's default action is stepping filters rather
than zoom — and on the wheel's track **None is three notches wide**, so the most common setting
is the easy one to land on and a deliberate three notches to leave, with a buzz on every notch so
the dial never reads as dead. Landing on None additionally catches for 1.5 seconds: notches inside
that window are felt and discarded, so a fast spin can't skate past it.

### Applying a shader to a still is not three lines

`RuntimeShader` is compiled for the GPU and cannot draw onto a software `Canvas` — paint it
over a bitmap and you silently get nothing. `filter/ShaderRuntime.kt` drives a
`HardwareRenderer` into an `ImageReader` and reads the result back through a
`HardwareBuffer`, which is the supported way to get a hardware-accelerated draw with no view
on screen. The alternative would have been a second, CPU implementation of every filter,
drifting from the shader within a week.

Because it is the GPU, it can decline: a driver has a maximum texture size, a compile can fail,
and a full-resolution still is a much larger ask than a preview. So the still path is written to
fail **soft** — `ShaderRuntime.applyToBitmap` hands back the bitmap it was given rather than
throwing, and `Frames.process` catches everything, out-of-memory included, and writes the
sensor's own frame. A filter that could not run costs you the filter. It must never cost you the
photograph.

## QR is a mode, not an app

Pick **QR** from the mode slot and the viewfinder starts decoding. Point it at a code and what it
says comes up on screen; the camera button, or OPEN, does the obvious thing with it.

**Nothing launches by itself, and that is the whole design.** Every other scanner opens the link
the moment it reads one. On a phone whose camera is also its default camera that is a trap: you
point it at a table to photograph the food, it reads the code on the menu, and a browser is now in
front of the picture you were about to take. So a scan holds — the host is set large, the raw
payload sits under it, and you decide. It is also the only defence anyone has against a sticker
pasted over the QR code on a parking meter.

Seven schemes are ever handed to the system:

```
http  https  tel  mailto  sms  smsto  geo
```

Anything else is text you can copy and nothing more, `intent:` URIs very much included. A QR code
is a string a stranger printed on a wall, and `startActivity` on an arbitrary scheme is how a
poster gets to poke at whatever handlers happen to be installed. The list lives in
`Codes.openable`, which has no Android imports and is tested.

Wi-Fi codes get taken apart rather than shown raw: `WIFI:S:…;T:WPA;P:…;;` arrives as a network
name, a password large enough to read off the screen while you type it into a laptop, and a COPY
PASSWORD row — because the password is the only part of that payload anybody wants. The parser
honours the format's backslash escapes, which a `split(';')` does not; a password with a semicolon
in it is exactly the kind of password people use.

The mode is back-lens only, with no filters, no grid and no film counter. Filters are off for a
reason worth stating: the decoder reads the camera's own frames, which a `RenderEffect` never
touches, so a Game Boy viewfinder would carry on scanning perfectly while showing you something
unreadable — a viewfinder that lies about why it failed. The flash slot becomes the torch, since a
flash mode is a property of a capture and this mode takes none.

Decoding is [ZXing](https://github.com/zxing/zxing), pure Java, inside the APK. ML Kit is the
obvious choice on any other Android phone and is unavailable here: its barcode model is delivered
through Play Services, which LightOS does not ship, so it would install, bind, and never return a
result.

Under it, an `ImageAnalysis` at 1280×720 bound in place of the shutter, and bound **only** in this
mode — an analysis stream is a second full-rate consumer of the ISP, and leaving one attached in
Photo would cost every shot for a feature nobody switched on. Frames are dropped rather than queued
when a decode runs long, or the preview would stutter whenever you pointed the camera at something
busy.

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

## Sending a photo

The viewer's send button is disabled until **Settings → Sending → Use LightChat** is turned on.
Then it hands the photograph over to [LightChat](https://github.com/gi-os/LightChat) by explicit
package, with no chooser — a share sheet with every app that has ever registered for an image is
the one place a Light Phone stops feeling like a Light Phone. It resolves the intent first, so an
absent LightChat is a sentence rather than a crash.

## Setting it as the default camera

The app claims `STILL_IMAGE_CAMERA`, `IMAGE_CAPTURE`, `CAMERA_BUTTON` and the `_SECURE`
variants, and it honours `EXTRA_OUTPUT`, so other apps' "take a photo" works properly. See
[Quick start](#quick-start) above for setting it as the default.

Launched for `IMAGE_CAPTURE` the app shows only the viewfinder — no roll, no settings — takes
one photograph, writes it where the caller asked and finishes.

## Configuration

Everything is in the in-app Settings screen — there is no config file and nothing to edit by
hand:

| Setting | Options | Notes |
|---|---|---|
| Colour | Viewfinder only / whole app / off | Needs the `WRITE_SECURE_SETTINGS` grant above; degrades to grey without it. |
| Aspect | 4:3, 3:2, 16:9, 1:1 | Applied as a centre crop at save time — the live viewfinder always fills the screen. |
| Resolution | Capped at 4000×3000 (12MP) | Down from the sensor's native 50MP, since v1.8 — see [Version history](#version-history). |
| Sending | Off / Use LightChat | Enables the viewer's send button, targeted at `com.gios.lightchat` with no chooser. |
| Film roll | Off / 12 / 24 / 36 | Switches the shutter release from a circle to a square; develops on completion or on demand. |

## Layout

```
camera/     CameraX, hardware face detection, AF, capture, EXIF and cropping
filter/     the AGSL sources and the two ways they get run
hw/         the wheel and the two-stage camera button
media/      MediaStore reads and writes, thumbnails
qr/         QR mode: the ZXing analyser, and the payload rules it is judged by
roll/       film-roll mode
ui/         the two pages, the viewfinder chrome, the filter grid
```

## Contributing

- New filters go in `filter/`: write the AGSL source once, and it must run through both call
  sites in `filter/ShaderRuntime.kt` (live `RenderEffect` and the still-capture `BitmapShader`)
  — a filter that only works on the preview is a bug, not a feature.
- Camera-button and wheel changes touch `hw/ShutterRelease.kt` and `hw/Wheel.kt`; both key
  arrival order and rapid notches are covered by tests specifically because both have shipped
  broken before (see [Version history](#version-history)) — extend those tests rather than
  hand-testing on a device alone.
- CI (`.github/workflows/build.yml`) gates every push to `main` on a signed build, a
  certificate-fingerprint check against `signing-fingerprint.txt`, a launcher-icon check and a
  camera-intent-filter check — a push that doesn't pass all four never reaches Releases.
  `check.yml` runs the same build minus release steps on any other branch, so open a branch
  first if you want CI feedback before a push cuts a release.
- Bump `versionName`'s `major.minor` in `app/build.gradle.kts` when a release deserves one;
  CI stamps the run number on as the patch and tags `vMAJOR.MINOR.RUN` automatically on every
  push to `main` — there is no separate tagging step.

## Version history

| Version | Date | Notes |
|---|---|---|
| `v2.36.x` (pending) | — | **QR scanning is a camera mode.** Pick QR from the mode slot and the viewfinder starts reading codes; point it at one and the payload appears with OPEN and COPY under it. [LightQR](https://github.com/gi-os/LightQR) folded into the camera, because it was one launcher entry and one cold start away from a viewfinder that is already open. **Nothing opens by itself** — most scanners launch the link the instant they read one, which is wrong on a phone whose camera is also its default camera, so the destination is legible before anything is launched. Only seven schemes are ever handed on (`http`, `https`, `tel`, `mailto`, `sms`, `smsto`, `geo`); anything else, including `intent:` URIs, is text you can copy. Wi-Fi codes are parsed into a network name and a password with its own COPY row, honouring the format's backslash escapes — a `split(';')` cuts a password containing a semicolon in half. Two bugs came over from LightQR and are fixed: the analyser ignored the luminance plane's `rowStride`, so at any padded resolution the decoder saw a sheared image and never found a code; and `Patterns.WEB_URL` was deciding what a link was, which takes `1.2` and `v2.0` as web addresses. The camera button is the accept key rather than a scan trigger, the flash slot becomes the torch, and the mode is back-lens only with no filters, no grid and no film counter. `ImageAnalysis` at 1280×720 in place of the shutter, bound only in this mode; ZXing rather than ML Kit, which needs Play Services this phone does not have. |
| `v2.35.x` | ff584c3 | **Shake the phone twice and a SEND ERROR? chip appears in the corner; tap it and Roll files a GitHub issue against the private tracker.** The report carries the symptom, an optional note, the build and firmware, free space and heap, the last crash log if there is one, and — only while the row stays ticked — a screenshot taken at the moment of the shake. Ported unchanged from [LightNotebook](https://github.com/gi-os/LightNotebook), deliberately: this is diagnostic UI, not product surface, so it should be one learned gesture across every app rather than four. **Roll gains the `INTERNET` permission, which it never had before** — worth noticing rather than discovering, in an app that holds the camera, the microphone, your contacts and your whole photo library. It opens a socket in exactly one place, `report/Reports.kt`, after you tap SEND on a report you wrote yourself; never in the background, on launch, or on a timer. The key it posts with can only file issues on one private repo — it cannot read that repo's contents or touch any other, which is why the screenshot travels as base64 in the issue body rather than as a committed file. The gesture counts *reversals* rather than force (four past 0.46g), which is what separates it from a camera being carried, pointed and set down all day; being wrong is cheap, because the chip fades after four seconds and deletes nothing. |
| `v2.35.x` | 2026-07-31 | **A booth strip holds one orientation, and two fingers zoom.** The way up was read per frame inside the countdown loop, so turning the phone between shots gave four photographs of different shapes — and the sheet is measured from the first of them, with `drawBitmap` stretching the rest into cells built for it. The orientation and the aspect ratio are now read once, before the first frame, and held for the whole sequence; turning the phone mid-countdown changes nothing. `PuriStrip.sourceCrop` is the second line of that defence: a frame that differs anyway is centre-cropped to the cell's shape rather than distorted. Separately, the viewfinder only ever followed the first pointer, so a pinch arrived as one finger wandering sideways and **stepped the filter instead of zooming** — two fingers now claim the gesture outright, scaling from the zoom the pinch started at, and nothing else can fire from it. Double tap to swap lenses was already there and is no longer competing with a half-read pinch. |
| `v2.34.50` | 2026-07-31 | **Filtered selfies come out the right way up, and the shutter can no longer fail in silence.** `Frames` mirrored the frame *before* turning it upright, and those two do not commute — a mirror followed by a quarter turn is the same transform as the quarter turn followed by a vertical flip, so every filtered photograph off the front lens was saved upside down and mirrored the wrong way, while an unfiltered one, written as the sensor's own bytes, never came through that code at all. The flipped EXIF orientations (`TRANSVERSE` and friends) are understood now too, so a HAL that has already declared the frame mirrored is not mirrored a second time. Alongside it, four ways the shutter could produce nothing at all: `takePicture` now has a twelve-second deadline, because a capture whose callback never arrived left `_shooting` latched and every later press was dropped without a word; a capture that misses the deadline saves the viewfinder frame rather than nothing; a filter that the GPU refuses on a full-resolution still now writes the unfiltered photograph instead of throwing out of the coroutine and killing the process; and every shooting routine catches everything and names it on the viewfinder. Also: a 50MP filtered capture no longer decodes 200MB of ARGB before scaling it straight back down, and pressing the shutter with the camera unbound says so instead of looking ordinary. |
| `v2.34.x` (pending) | — | Serves the starred list to the rest of the collection, read-only, over `com.gios.lightcamera.stars`. A star is the one fact about a photograph only this app knows — everything else another app wants is already in MediaStore — and `IS_FAVORITE` is effectively writable only by the system gallery, so it has to be offered deliberately. Names, not ids: an id is a row number that changes on a rescan. [LightNotebook](https://github.com/gi-os/LightNotebook) uses it to pick which photograph goes behind a day on its planner. |
| `v2.33.x` (pending) | — | Another app's `IMAGE_CAPTURE` request is now served **plain**, whatever the filter dial says, unless the caller passes `EXTRA_ALLOW_FILTER`. A filter is something the user chose for their own roll, and silently applying it to a photograph another app asked for breaks that app in a way neither of them can see — [LightNotebook](https://github.com/gi-os/LightNotebook) hands a photographed page to Claude to read, and a dithered page is illegible, so "Roll had Game Boy selected three days ago" surfaced there as "the notebook can't read my handwriting". Opt-in rather than opt-out, so a caller written before this gets the safe behaviour. |
| `v1.9.x` (pending) | — | Drops zero shutter lag (it was the cause of "Shutter failed", not a fix for it — CameraX accepted the mode but refused the first captures while its buffer filled); moves every readout up two type-scale steps off the unreadable `Micro` size; fixes the roll grid so the newest photo lands bottom-right, not bottom-left, matching a real contact sheet. |
| `v1.8.11` | 2026-07-30 | Capture resolution capped at 4000×3000 (down from the sensor's native 50MP) — the single biggest fix for the "one to three seconds" shutter lag every review complains about. Adds `CAPTURE_MODE_ZERO_SHUTTER_LAG` where supported (later found to cause its own failures, dropped in v1.9). Filtered stills now downsample with `inSampleSize` instead of decoding the full 50MP JPEG; JPEG quality 92 not 95. |
| `v1.7.9` | 2026-07-30 | Two Game Boy Camera filters (DMG and GBC palettes, both quantised onto a 128-cell grid matching the real Game Boy Camera sensor). Every wheel notch now buzzes; landing on None catches for 1.5s instead of spanning three notches of track. The viewer's send button, gated on Settings → Sending → Use LightChat. |
| `v1.6.8` | 2026-07-30 | The wheel's bare turn now steps through filters instead of zooming — the LPIII has no optical zoom, so a dial spent on digital crop was a dial spent on nothing. |
| `v1.5.7` | 2026-07-30 | Fixed the v1.5.6 instant-crash-on-launch: a `viewModelScope` collector in an `init` block ran synchronously in the constructor and read a field declared below it. Adds an on-device crash log, shown in Settings. |
| `v1.5.6` | 2026-07-29 | Sideways chrome, upright picture: only the control band is rotated, not the whole app, matching what a screengrab of the stock camera actually shows. Adds the Camera/Video/Selfie mode picker. Shipped with the instant crash fixed in v1.5.7 the same day. |
| `v1.4.5` | 2026-07-29 | The whole app briefly drawn a quarter turn (superseded by v1.5.6's more accurate split); the viewfinder lifts LightOS's forced greyscale while a camera or photo is on screen. |
| `v1.2.4` | 2026-07-29 | The roll scrolls the way the wheel is turned. |
| `v1.2.3` | 2026-07-29 | Camera band laid out like LightOS's own: album / lens / mode / flash / brightness, system bars hidden, no on-screen shutter. |
| `v1.1.2` | 2026-07-29 | Unobstructed viewfinder: the bordered-crop preview from v1.0 is gone, replaced by a full-bleed preview with chrome floating in the system-bar insets. Adds the stock focus brackets and the focus beep. |
| `v1.0.1` | 2026-07-29 | First release. Two-stage shutter release, hardware face detection over Camera2 interop, the roll as a reversed grid above the viewfinder. |

Every tag ships a hand-written entry in `RELEASE_NOTES.md`, which is what the table above is
condensed from — read that file at any tag for the full reasoning behind a change.

## Credits

Icons and the design tokens — the 27x31 grid, the type scale, the haptics — are from
[`lightphone/light-sdk`](https://github.com/lightphone/light-sdk), MIT, © The Light Phone.
See `LICENSE-light-sdk`.
