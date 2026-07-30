## Roll v2.3 — three date backs

**Settings → Date → Date stamp, then Style.** Three of them, because they were three different
mechanisms and drawing them the same way is what makes fake ones look fake.

- **Dots** — the compact camera's LED matrix. `11  5 '21`, month-day-apostrophe-year, space-padded,
  amber-green, leaning. Each glyph is a 5×7 bitmask and each lit cell a circle a little under half a
  cell across, so the picture shows through the hairline gaps between the lamps.
- **Quartz** — the film SLR's date back. `'99 12 29`, year first, zero-padded, orange-red, **seven
  segments** rather than dots: a `1` is two bars with nothing between them and a `7` has a hard
  corner, neither of which a dot grid makes convincingly. The bars are parallelograms because the
  whole display leans.
- **Camcorder** — `08/31/2015`, slashes, all four digits of the year, upright, solid orange with a
  black keyline. This is the one style where a **real typeface is correct**: it was never a lamp
  array, it was a character generator drawing bold sans into the video signal.

All three are sized as a fraction of the frame, so the stamp is the same size relative to the
photograph at 2MP and at 50MP, and printed *into* the file. Off by default — there's no taking it
off afterwards, and with it on a shot that would have been saved byte-for-byte as the camera made it
costs a decode and a re-encode.

**Also:** the wheel was scrolling the viewer backwards — turning it sent you back through
photographs you'd just passed. Fixed.
