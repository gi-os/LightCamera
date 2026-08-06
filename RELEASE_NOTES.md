## LightCamera v2.44 — Point the controls where you want them

The wheel walks the filters, holding it in and turning is exposure, clicking it is the torch, and
either volume key is a shutter. That is a good mapping and it is still exactly what a fresh install
does. It was also the *only* mapping, which is a problem on this phone in particular: the wheel is a
system-wide control, and anyone who has remapped it elsewhere — with LightControl, most likely —
found this app quietly assuming the wheel was free.

So the mapping is a setting now. Five controls, each pointed wherever you want it:

| Control | Can be | Defaults to |
| --- | --- | --- |
| Volume up | shutter, torch, front/rear, next mode, timer, exposure, zoom, nothing | shutter |
| Volume down | the same list | shutter |
| Click the wheel | the same list | torch |
| Turn the wheel | filter, exposure, zoom, nothing | filter |
| Press and turn | the same four | exposure |

The camera button is not on the list and never will be: half press to focus, press through to shoot.

**One shutter always survives.** There is no shutter button on the screen, so unbinding the last one
on a phone whose camera key is being swallowed by an accessibility service would leave a camera that
cannot take a photograph and no obvious way back. An option that would do that is skipped rather
than offered. A key set to Nothing is handed back to the phone rather than eaten, so a volume key
with no job here still changes the volume.

A filter dial stays unarmed — each notch counts once, and None is three notches wide so a flick
lands somewhere harmless. Exposure and zoom are armed, so a fast turn racks through them. Whichever
way the wheel is pointed, an open strip takes it for as long as it is open.

### Drag the exposure strip

Twelve notches is the whole exposure range on this camera, so getting from −2 to +2 by tapping `+`
was twelve taps. Now: drag to set it, tap to jump to a stop, long-press to go back to zero. The `+`
and `−` are still there for a single stop, and the wheel is still the better control — this is the
one you reach for when the phone is already in your hand.

### Zoom, at last, as a control

The lens is fixed and the crop is digital, which is why the wheel was spent on filters instead. But
a digital crop is still the difference between a photograph of a sign and a photograph of the wall
it is on, and until now the only way to get one was a pinch on a 3.92" panel held sideways in one
hand. There is a zoom strip now, and it is **logarithmic**: each doubling gets the same travel, so
the low end is not crushed into the first few millimetres.

### Two slots in the band are yours

The album, the mode-and-filter chip and the flash are the stock camera's own bar in the stock
camera's own order, and they stay put. The two at the end — where the brightness icon was — can be
exposure, zoom, front/rear, self timer, shape, grid, or nothing. Exposure then nothing is what the
app has always shown.

**Front/rear is the one worth pointing at.** Turning the camera round was a double tap on the
viewfinder and nothing else, which is not a thing anybody finds.

### Exposure aids, for a greyscale screen

Judging exposure in black and white is the hard case: a face and a window can read as the same grey,
and nothing warns you that a highlight has gone to 255 and taken the detail with it. Two aids, both
off by default, both drawn from the frame already on the panel so neither changes what the camera is
bound to and neither costs the shutter anything:

- a **histogram**, bottom left, 64 bins on a square-root scale so the shadows are visible rather
  than a flat line;
- **clipping marks**, a 45° hairline hatch over the cells that have gone to white. Hatching rather
  than crawling zebras: a static diagonal on a monochrome panel is already unmistakably not part of
  the world, and it does not put an animation over the viewfinder permanently.

A cell has to be a third blown before it is marked, so one hot pixel does not hatch a correctly
exposed night frame.

### Sharpest of eight

Optional, and off by default. Where the photograph comes off the panel — Simple, and every coarse
filter — the shutter can take eight frames over about a quarter of a second, score each by the
variance of its Laplacian in the middle of the frame, and keep the sharpest. That is hand shake
being chosen against instead of frozen into the file.

It is off by default because it changes *which* frame you get: what you want for a face is not what
you want for timing a jump. Simple without it is exactly as quick as it ever was.

### The filter can no longer change mid-shot

In Pro the filter is applied to the bytes *after* the sensor answers, about 1.8 seconds after your
finger. A notch turned inside that window baked a look you were not framing into the file, while the
held frame on the panel went on showing you the old one. The dial is closed from the press until the
file is written, and says so rather than buzzing.

### Settings has tabs

Every setting was worth having and the notes beside them were worth reading, and together they were
one column about eleven screens long — so finding the film roll meant scrolling past the date back.
Same rows, same order, cut where the subject changes: **Frame, Look, Keys, Film, About**. The prose
is folded behind a `?` on each section, unchanged, for the once you wonder rather than every time
you come in to change the timer.

### Faster filtered captures

An offscreen GPU renderer — an ImageReader, a HardwareRenderer bound to its surface and a RenderNode
— was built and torn down again on **every single photograph**, to draw one rectangle through it. The
draw was a fraction of a frame; the setup and teardown around it were most of the time the filtered
path spent on the GPU. Two of them are kept and reused now, one for the panel and one for the
capture size, and released when the camera screen goes away.
