## LightCamera v2.41 — Text is a mode now, next to QR

Turn the dial past QR and there is **TEXT**. Point it at a menu, a noticeboard, a receipt, the
serial number on the back of a router, and press the shutter. The words come up in the same sheet
a QR code gets, with the same verbs — call the number, open the link, copy the lot.

v2.40 could already read a photograph on the roll. This is that without the photograph, because
standing in front of a sign, taking a picture, swiping up to the roll and finding it again is
three steps more than the job deserves.

### It does not take a photograph

The frame comes off the panel rather than off the sensor — the same `Screen` route the coarse
filters have always used. No `takePicture`, no readout, no encode. A still on this hardware is
most of a second and a 50MP one is nearer two, and a reading that took that long would be slower
than typing the thing out. This is instant, and what gets read is literally the frame you were
looking at when you pressed.

**Nothing lands on the roll.** The frame is held on screen so you can see what was read, and it is
dropped when you close the sheet. A reading is not a photograph, and a roll filling up with
pictures of car park signs would be the wrong outcome. Press the shutter again to dismiss.

### The catch, and what happens about it

The panel has far fewer pixels than the sensor. That is fine for a sign, a menu or a business
card, and marginal for small print. So when the panel frame comes back with nothing — or with the
two or three characters a recogniser returns when the print is too small, which is worse than
nothing because it looks like an answer — it says **Looking closer**, takes one real exposure, and
reads that instead.

The slow path is only paid by the shots that need it, and only after the fast one has already been
tried. Most readings never reach it. The exposure is decoded sampled-down on the way in, because
the recogniser gains nothing above about two thousand pixels and decoding a 12MP frame to read a
street sign is two hundred megabytes to throw away.

### Three ways in, one screen out

A QR code, a photograph on the roll, and now the viewfinder all end at the same sheet. That is not
tidiness — after `TextScan` has shaped a finding into a payload, a phone number photographed off a
card and one inside a QR code are the same value with the same actions, and they should not arrive
in two different screens.

Text mode borrows QR's framing corners for the same reason: you are framing a thing rather than
composing a picture, and the marks are what say so. They come off the moment there is a reading.

### Known

There is no live recogniser, unlike QR. A code is a small target you sweep for and want acted on
within a second; a page is a thing you frame and press. Running text recognition on every preview
frame would cost far more than the viewfinder can spare on this phone.

Nothing is spell-corrected here either — `O` and `0`, `l` and `1` are shown as they were read. On a
printed URL that is the difference between a company's site and a domain someone bought to catch
the typo, so the guess is yours to make.
