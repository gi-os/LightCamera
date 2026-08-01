## Roll v2.36 — QR is a camera mode now

**Pick QR from the mode slot and the viewfinder starts reading codes. Point it at one, and what
it says appears on screen with OPEN and COPY under it.**

This is [LightQR](https://github.com/gi-os/LightQR) folded into the camera. It was a good app and
it was one launcher entry, one cold start and one camera bind away from the viewfinder that is
already open — for a job that takes two seconds and is then over. Scanning a code is the same
sentence as every other mode here: point the camera at a thing, press the button, get the thing.

**Nothing opens by itself.** Most scanners launch the link the instant they read one, and that is
the wrong behaviour on a phone whose camera is also its default camera — you point it at a table,
it reads a code on a menu you never meant to scan, and a browser is in front of the picture you
were about to take. So a scan puts up a sheet with the destination on it and waits. The host is set
large at the top and the raw payload underneath, which is the only defence anyone has against a
sticker stuck over the QR code on a parking meter.

Only seven URI schemes are ever handed to the system: `http`, `https`, `tel`, `mailto`, `sms`,
`smsto` and `geo`. Everything else — including anything shaped like an Android `intent:` URI — is
treated as text you can copy and nothing more. A QR code is a string a stranger printed on a wall.

**Wi-Fi codes are parsed rather than shown raw.** `WIFI:S:…;T:WPA;P:…;;` comes up as a network
name, a password you can read off the screen while typing it into a laptop, and a COPY PASSWORD
row — because the password is the only part of that payload anybody wants on their clipboard. The
parser honours the backslash escapes in the format, which a `split(';')` does not: a password with
a semicolon in it is exactly the kind of password people use, and LightQR cut those in half.

**Two bugs came over from LightQR and are fixed here.** The analyser copied the camera's luminance
plane whole, ignoring `rowStride` — camera planes are padded to a hardware-friendly row length, so
on any resolution where the padding is non-zero the decoder was handed a sheared image and simply
never found a code. And "is this a link?" was `android.util.Patterns.WEB_URL`, which accepts `1.2`
and `v2.0` as web addresses, so a code carrying a version number offered to browse to it. The rule
here is stricter and lives in `qr/Codes.kt` with no Android imports and thirty tests on it.

Smaller things that follow from QR being a mode rather than an app:

- **The camera button is the accept key.** It does not start a scan — the camera is already
  scanning, continuously, so a button that started it would be a button that did nothing visible.
  It commits to the result on screen, which is what a hardware key is best at when your eyes are on
  a poster rather than on the panel. A scan buzzes and blips the same way focus landing does.
- **The flash slot becomes the torch.** A flash mode is a property of a capture and QR takes none,
  so the control would be dead there — while the thing you actually want in a dim restaurant has no
  home in the chrome at all.
- **Back lens only, no filters, no grid, no film counter.** Half the filters would make a code
  unreadable to a person while the decoder carried on reading it perfectly, which is a viewfinder
  that lies about why it failed. And a code stays in frame for as long as you hold it there, so the
  same code never fires twice — it has to leave the frame for two seconds before it counts again.

QR mode binds an `ImageAnalysis` at 1280×720 in place of the shutter, and only in that mode: an
analysis stream is a second full-rate consumer of the ISP, and leaving one attached in Photo would
cost every shot for a feature nobody had switched on. Decoding is ZXing, not ML Kit — ML Kit's
barcode reader is delivered through Play Services, which LightOS does not have, so it would bind
and never answer.
