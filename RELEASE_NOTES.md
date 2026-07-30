## Roll v2.1 — pick the size, or skip the capture entirely

**Settings → Frame → Size**

`50MP · 12MP · 5MP · 2MP · Screen`, and on this phone that is the same dial as shutter speed.
Reading out and encoding a 50MP frame is most of a second of the ISP's time; each step down is
roughly a halving. 12MP stays the default — four times the largest print anyone makes from a phone.

**Screen doesn't take a photograph at all**

It saves the frame already on the viewfinder. No `takePicture`, no sensor readout, no JPEG from the
ISP — the pixels off the panel, turned upright, cropped to your shape, put through the same shader
as any other photograph, encoded. As instant as this app can be.

And with a filter on it is *better* than the slow path, not just faster: it is **exactly the frame
you were looking at**, rather than a second frame captured afterwards and processed to match. The
shader runs at panel resolution, which is a fraction of the work, and the pattern filters look
identical because they scale themselves to the image either way — a Game Boy shot at Screen size is
the same 128 cells as one at 12MP.

Worth knowing: it is panel resolution, so about 1080px on the long edge. For Dither 16, 1-Bit,
Halftone and the two Game Boy filters that is arguably the right size anyway.

**Also**

The three places a finished photograph can go — another app's `IMAGE_CAPTURE` request, a loaded
roll, the gallery — are now decided in one place instead of two. The fast path was missing the roll
branch when it was first written, which would have quietly dropped frames from a loaded roll.

## Also in v2.2 — the quartz date back

**Settings → Date → Date stamp.** Month, day, apostrophe-year, in leaning amber dots in the corner
of the frame, printed *into* the photograph the way a date back printed onto the negative.

Four things make it, all read off photographs rather than guessed:

- **A dot matrix, not a typeface.** A date back exposed an LED array through the film gate, so close
  up the digits are discrete round lamps with the picture showing between them. Each glyph is a 5×7
  bitmask and each lit cell a circle a little under half a cell across. A real font — even a pixel
  font — gets hinted and kerned and ends up looking like a screenshot of a font.
- **Sized to the frame, not the pixels**, so it's the same size relative to the photograph at 2MP
  and at 50MP.
- **It leans**, about twelve degrees, and because the glyph is a grid the lean comes out as a
  staircase. Shearing a typeface gives clean diagonals and the wrong decade.
- **It glows** — nine tenths opacity so the picture shows through like a light rather than paint,
  with a second larger pass underneath for halation.

Off by default: it writes on the photograph and there's no taking it off afterwards. With it on, a
shot that would have been saved byte-for-byte as the camera made it now costs a decode and a
re-encode.
