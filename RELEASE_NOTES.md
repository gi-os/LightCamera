## Roll v2.7 — Purikura

**A new filter that knows where your face is**

Purikura, the Japanese photo-booth look, and the only shader in the app that gets told where the
faces are. It does the four things a booth does, all of them too much on purpose:

- **Eyes about twice the size.** A radial magnification centred on each eye, sampled towards the
  centre so what is there grows, and smoothed all the way to the rim — a hard edge would read as a
  disc of face sitting on a face, which is the tell of a bad beauty filter. The eyes are worked out
  from the face rectangle rather than detected: not every camera publishes eye landmarks, but every
  camera publishes the rectangle.
- **Skin blown out.** Luminance lifted hard and the top end crushed flat, so 0.8 and 1.0 come out
  nearly the same white. Poreless and papery, which is the part people actually go for.
- **Pink.** Cool rose in the shadows, warm rose in the highlights, saturation up, and nothing allowed
  to be properly black — booth prints wash out in the shadows and that missing black is half of why
  they look like booth prints.
- **Glitter.** Four-pointed stars on a jittered hash grid, denser near a face, drifting with the seed
  so they twinkle in the viewfinder.

With nobody in frame it is still the wash, the soft focus and the glitter. A booth with no one in it
is a pink room.

**Why the photograph always comes off the viewfinder for this one**

Faces are detected in the preview. Making the file out of a second, differently-cropped sensor frame
would mean mapping those rectangles across — and that arithmetic is exactly how an eye ends up
enlarged next to somebody's ear. Filtering the frame the faces were found in cannot be misaligned, so
Purikura takes the panel frame the same way the coarse filters do, whatever the photo size is set to.

The face-to-shader arithmetic — normalising, quarter turns, centred crops — is plain Kotlin with no
Android imports and is checked off-device, because a sign error there is silent and specific.
