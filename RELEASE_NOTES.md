## Roll v2.5 — the fixes batch

**Swiping between photographs works again, and that one was mine**

`detectTransformGestures` consumes every drag it is given, single-finger ones included, so adding
pinch-to-zoom quietly took the swipe away and left the wheel as the only way through the roll. The
gesture is arbitrated by hand now: two fingers is always a pinch, one finger is only claimed once
you're zoomed in — where panning has to win — and one finger at 1x is claimed by nobody, so the pager
gets it back.

**Rotation was 180 degrees out**

`atan2(x, y)` grows as the phone turns *anticlockwise*, and `rotationZ` is clockwise-positive, so
cancelling the phone's turn means adding it, not negating it. Negating it put the picture upside down
in both sideways poses.

**The roll grid turns too**

Open the photos with the phone already on its side and the thumbnails now come round with it, the
same way the viewer does.

**The header bar takes its own taps**

A background paints but does not claim touches, so the roll's top bar was transparent to the finger
and reaching for settings opened whichever photograph was tiled underneath it.

**The wheel in the viewer, the other way**

Reported backwards twice. I reasoned my way to the current direction both times on the grounds that
it matched the roll grid; the thumb is the authority on which way a dial turns, so it is flipped.

**Coarse filters always take the viewfinder frame**

Dither 16, 1-Bit, Halftone, Game Boy and GB Color quantise onto a grid of their own, so a sensor
capture has nothing to give them — a 12MP frame and a panel-sized one come out of a 160-cell dither
as the same picture. They now use the instant path whatever the photo size is set to. The size
setting governs the photographs where resolution is a real quantity.

**Quartz, rebuilt rather than resized again**

There is no seven-segment typeface on Android to switch to, and a seven-segment font wouldn't be a
typeface anyway — every glyph in DSEG and its relatives is the same seven chamfered bars with a
different subset filled in. So the bars are drawn the way the real ones are built:

- **ends mitred at 45 degrees**, which is the detail that was missing. A segment on a real LCD is a
  hexagon tapered at both ends; square-ended bars read as a bar chart.
- **one shear per digit**, applied to the canvas about the baseline, instead of a lean fudged onto
  each bar separately. The display leans; the segments stand up inside it.
- classic LCD proportions — near twice as tall as wide, bars a fifth of the width — and about a
  fiftieth of the long edge, smaller again.

**Camcorder**

Condensed, tighter digits, and a heavy black keyline rather than a hairline: on a video line the
border around each glyph was as thick as the strokes, and that is what kept the date readable over
grass or sky.
