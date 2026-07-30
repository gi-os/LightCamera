## Roll v2.11 — pick from a list, and the sample shows the strip

**The black viewfinder is fixed**

v2.10's overlay was wrapped in the helper that rotates a photograph to the phone — and that helper
paints black behind its content, which is right for a picture and completely wrong for a transparent
layer. Tilt the phone past 45° and the viewfinder went black. It now asks for a transparent box.

**Frame, Date and Four-shot are lists**

Fourteen frames and eight dates are too many to walk one tap at a time. Each row opens a list with the
current choice filled in, and the first item is **Random** — which is the default, not a novelty. Random
resolves from the seed the app holds still between shots, so it is stable while you compose and different
on every photograph, and the sample in the corner is showing you the one you are about to get.

The frame, the date and the layout are salted separately, so Random is a combination of choices rather
than a handful of presets.

**The sample shows the strip**

Turn four-shot on and the example becomes an actual strip: four cells, each decorated separately, run
through the same `PuriStrip.compose` the shutter uses. With Framed selected the cells lose their own
borders, because the point of that layout is one border around all four.

**Margin stickers are bigger**

Half again. At a tenth of the short edge a heart in the corner of a 4:3 frame read as a speck of dust.

**The level lies along the horizon**

Hold the phone sideways and the world's horizon runs *down* the screen, but the level was drawn across
the panel in every pose — at 90° to the thing it was reporting on. It now turns with the same number the
photograph is rotated by, so the level and the file agree by construction.

**The dial catches on Purikura**

Half a second, where None gets a second and a half. Purikura is the other filter you aim *for* rather than
pass through — there is a menu behind it — so the wheel should hesitate there too. Shorter than None's,
because None is the way back to an ordinary photograph and this is somewhere you went on purpose.
