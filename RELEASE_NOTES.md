## Roll v2.8 — Purikura gets a booth

**Skin smoothing**

The old soft focus was a five-tap cross that blurred everything equally, eyelashes and hairline
included, which is what makes a beauty filter look like a smear. This is a twelve-tap cross-bilateral
pass: two rings of six, each tap weighted by how far its colour is from the centre pixel, so a tap
that has fallen off the face onto hair or background contributes almost nothing. Pores average away,
edges survive. The radius follows a skin-tone test, a little real detail goes back on top so the face
is not plastic, and the eyes stay sharp — having just been doubled in size, they are the one thing
that should be crisp.

**Fourteen frames, on a chip you tap**

Lace, Hearts, Ribbon, Film, Stars, Neon, Window, Glitter — plus five that are not trying to be
tasteful: Googly (eyes all round the border, pupils genuinely pointing at the middle of the frame),
Leopard, Checker, Flames and Slime. Plus None. While Purikura is on, the frame's name appears in the
viewfinder band; tap it to walk them.

**Stickers, chosen for you**

Cat ears sit on a head, blush on the cheeks, sunglasses across the eyes — the face rectangles are
already tracked, so they land where they should. Hearts, sparkles, cherries, a paw print, a daisy and
the rest scatter into the margins, and deliberately away from every face: a booth decorates the edges
of a print, having just spent all that effort on the eyes. Reshuffled after each shot.

**Its own date, one of eight, at random**

A bubble capsule, marker pen, ticket stub, sticker text, booth serial number, a cloud, a star tag or
a diary serif. It replaces the date back rather than printing beside it, and it follows the same "on
filters" switch the other filtered photographs do.

**Why the viewfinder can be trusted**

The frame, stickers and date are drawn with `Canvas`, not AGSL — hard-edged vector work with text in
it, which a fragment shader is a miserable way to make. So the viewfinder draws them by calling the
same function the shutter calls, with the same seed, into a half-resolution overlay laid over the
preview. There is one implementation, and the stickers you are looking at are the stickers you are
about to get.

That seed is held still between shots on purpose. The shader's own seed moves ten times a second so
the glitter twinkles; if the stickers came off that they would rearrange themselves while you were
composing.
