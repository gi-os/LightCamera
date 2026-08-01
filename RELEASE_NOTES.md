## Roll v2.37 — the camera opens plain, and the QR result reads the way you are holding the phone

**Two fixes to what Roll hands you when you arrive at it.**

**The filter no longer survives to the next time you open the camera.** It was persisted, seeded
from Film, so whatever you last chose was still on when you next reached for the shutter. A filter
is not a setting — it is a decision you made about one photograph. The failure mode is the whole
argument: shoot a roll through Game Boy on a Tuesday, and on Thursday somebody does something worth
photographing and you get a 160-cell dither of it. There is no undo on that, and the reverse mistake
costs one turn of the wheel.

Roll now opens on Pro with no filter, the way it already opened on Pro rather than in Video for the
same reason. **"Opens" means the app starting, not every glance at it** — the dial stays where you
left it for as long as Roll is alive, so pulling down to the roll, opening the shot you just took,
going to settings and coming back changes nothing. Resetting on every resume would take the filter
away at exactly the moment you are most likely to want another frame of the same thing.

**The QR result now turns with the phone.** The scan sheet was wrapped in `HeldSideways`, the helper
every other panel in the viewfinder uses. That helper exists for a good reason: you turn this phone
anticlockwise to shoot, which brings the camera key round to the top edge where a shutter release
belongs, so the band and the menus are pinned sideways to meet you there. It is the right answer for
chrome.

It is the wrong answer for a scan result. That is not chrome — it is a paragraph of text you stopped
to read, and you read it holding the phone the way you were already holding it when you pointed it
at the code. For a poster, a menu, a business card or a parking meter, that is upright. So the sheet
was sideways text on an upright phone, which is a thing you notice immediately and which no amount
of good typography fixes.

It uses `RotatedToDevice` off the accelerometer now, the same helper a photograph uses in the viewer.
At 0 it lays out portrait and fills the long edge, which is more room for a long URL than the
sideways version ever had. At 90 it is exactly what it used to be. The 60° of hysteresis already in
`rememberDeviceQuarter` is what keeps it from flipping while you are halfway through reading a
link — a result that reoriented itself under your eyes would be worse than one occasionally the
wrong way up, which is the same trade the photograph viewer makes.

Nothing else moves. The band, the mode strip, the filter grid and the Purikura menu all stay pinned
sideways, because all of them are controls rather than things to read.
