## Roll v2.37 — the QR result reads the way you are holding the phone

**Scan a code with the phone upright and the result is now upright too.**

The scan sheet was wrapped in `HeldSideways`, the helper every other panel in the viewfinder uses.
That helper exists for a good reason: you turn this phone anticlockwise to shoot, which brings the
camera key round to the top edge where a shutter release belongs, so the band and the menus are
pinned sideways to meet you there. It is the right answer for chrome.

It is the wrong answer for a scan result. That is not chrome — it is a paragraph of text you
stopped to read, and you read it holding the phone the way you were already holding it when you
pointed it at the code. For a poster, a menu, a business card or a parking meter, that is upright.
So the sheet was sideways text on an upright phone, which is a thing you notice immediately and
which no amount of good typography fixes.

It now uses `RotatedToDevice` off the accelerometer, the same helper a photograph uses in the
viewer. At 0 it lays out portrait and fills the long edge, which is more room for a long URL than
the sideways version ever had. At 90 it is exactly what it used to be. The 60° of hysteresis
already in `rememberDeviceQuarter` is what keeps it from flipping while you are halfway through
reading a link — a result that reorients itself under your eyes would be worse than one that is
occasionally the wrong way up, which is the same trade the photograph viewer makes.

Nothing else moves. The band, the mode strip, the filter grid and the Purikura menu all stay pinned
sideways, because all of them are controls rather than things to read.
