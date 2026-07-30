## Roll v2.12 — a Look section, thumbnails in every list, and the send button works

**Look: five switches**

Purikura's effect is now five things you can turn on and off separately, under Look in its menu:

- **Pink wash** — the blow-out, the rose, the glitter. Off, what is left is a beauty filter rather than a
  booth print, which is a reasonable thing to want.
- **Skin** — the twelve-tap edge-preserving smoothing.
- **Bigger eyes** — the magnification.
- **Narrow chin** — new. A horizontal squeeze that ramps from nothing at the cheekbones to full at the
  jaw, which is the difference between a taper and a waist.
- **Smaller face** — new. A gentle radial shrink around the whole head, falling off well outside the
  rectangle so there is no seam at the hairline.

The wash, skin and eyes start on because that is the effect; the chin and the slimming start off, because
they are the two that look uncanny on a face the detector has boxed slightly wrong. They are amounts in
the shader rather than branches, so half strength would work if it were ever offered.

**Every list row shows itself**

Frame, Date and Four-shot rows carry a thumbnail drawn by the same code the photograph uses — the frames
in the list *are* the frames. Pick a strip layout and its row shows four cells arranged that way.

**The example is as big as the panel allows**

A strip is 1:4, so at a fixed size it came out the width of a fingernail. It now takes the full height of
the menu and whatever width that leaves.

**Stickers: bigger, spaced, never touching**

Two or three per photograph instead of two to four, each a fifth of the short edge or more, and a
candidate is rejected if it lands within a sticker's width of one already placed. Tested over three
hundred seeds.

**The date prints once on a strip**

Not into all four panels. A booth prints it in the margin, because the four photographs are one object.

**A frame is paper, not a tint**

The glitter band's pink was translucent, so the wash and the glitter showed through the border and it read
as part of the photograph. Opaque now.

**The send button works**

It never did. From Android 11 an app cannot see another app's activities unless the manifest says which
ones it is looking for, and `resolveActivity` returns null for everything else — so the check in front of
the send always failed and reported that LightChat could not receive photos, on a phone with LightChat
installed. The manifest now declares the query, and the check is gone anyway: it starts the intent and
explains itself if that throws.
