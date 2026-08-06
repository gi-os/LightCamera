## LightCamera v2.43 — The words are boxed where they were found

A reading used to go straight to a sheet, and the sheet covers the picture. That answers "what
does this page say" and not "which part of it said that" — and standing in front of a menu or a
noticeboard, the second question is the one you actually have.

So a reading now shows its rectangles on the frozen frame first. Every line the recogniser found
gets a box, and the boxes are the thing you press: tap one and the sheet opens on that line alone,
close it and you are back on the picture with the boxes still up, so reading a second line is one
press rather than four. **ALL TEXT** in the strip along the bottom opens the whole page as before.

### Two weights, not two colours

A line carrying something worth pressing — a number, an address, a link — is filled and outlined.
A line that is just words gets a hairline. There is no colour on this panel to spend and no room
for a legend, so the distinction is drawn the way LightOS draws every other piece of state.

The fill is deliberately not solid. A filled box hides the very words it is pointing at, and being
able to see what was read is the whole promise.

At arm's length the marked boxes are the only thing you see, which is the right summary of a page:
here are the four things on this poster you might want, and here is where each of them is.

### The part that is easy to get wrong

The recogniser is handed a rotation rather than a rotated picture, so it reports boxes in the
upright page's coordinates while the thing on screen is the frame as taken. For a sign
photographed with the phone on its side those two have their axes swapped — a box that is merely
offset is a bug, and a box that is *transposed* is that step missing altogether.

That arithmetic lives in its own file with no Android in it and eleven tests beside it, the same
arrangement as the face mapper for the same reason: it is impossible to check by reading, and
trivial to check off the phone. The turn is captured at the moment of the press rather than read
at draw time, so tilting the phone with the sheet up cannot slide every box off its words.

**When the closer look wins, the boxes are dropped.** If the panel frame was too coarse and a real
exposure was taken instead, those rectangles belong to the exposure and the picture still on
screen is the panel grab. Drawing one on the other would put every box confidently in the wrong
place, so that reading goes straight to the sheet, as it did before. Wrong boxes are worse than
none.

### Elsewhere

The TEXT button on a photograph in the roll still goes straight to the sheet. The photograph there
is zoomable and pannable, so the boxes would need to follow the zoom, and half-following it is the
version that looks broken. Worth doing next, deliberately not done blind.
