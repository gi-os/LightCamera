## Roll v2.29 — Dither BW

**Dither 16 was already sixteen colours**

Worth saying, since it was the half you asked me to check: it matches each pixel against a fixed sixteen-entry
EGA palette by nearest distance, with an ordered dither applied before the match. Not four levels per channel —
that would be sixty-four colours and a different, softer look.

**So the grayscale one is what was missing**

**Dither BW**, next to it on the wheel: sixteen greys, ordered-dithered, and marked coarse like the rest of the
dithers so it takes the instant panel frame rather than a 1.8 s still.

It is not the colour one desaturated. Quantising *after* a colour match lands on whichever of the sixteen EGA
entries was nearest and then flattens it, throwing away most of the tonal range — so this quantises luminance
directly and all sixteen steps get used. A little contrast goes on first, because sixteen levels across a flat
photograph is mud, and the dither offset is a step and a half wide rather than one: at exactly one step the
pattern is nearly invisible on a photograph, and being able to see it is the entire point.
