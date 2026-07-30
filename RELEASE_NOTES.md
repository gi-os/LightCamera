## Roll v2.13 — the face warps were wrong sideways

**The 2×2 no longer pushes the menu off the screen**

`ContentScale.Fit` in a column with no width constraint takes the bitmap's intrinsic width, and the grid
sheet is twice as wide as a single frame — so it shoved the rows off the side. The example now has a fixed
width and the full height, and every layout fits inside it.

**No date on a four-shot, anywhere**

Not in the panels, and not in the margin either. The layouts that want a printed date have their own
footer, which the composer fills in.

**The face shaping was measuring the wrong axes**

This is why it looked off, and it is a real bug rather than a matter of taste. The shader runs on the panel
image, and the panel is locked to portrait — so with the phone held sideways a face lies on its side in
that image, eyes one above the other. The eye positions were still being guessed left-and-right of centre,
which put the magnification on a forehead and a chin, and the chin squeeze across the side of the head.

Every offset is now measured along the face's own axes, from a quarter-turn count the shader is told
directly. Held upright nothing changes; held sideways it is correct for the first time.

**And it is all gentler**

The eyes were at nearly twice size with a radius of over half the face's width — on a detector box that
usually takes in hair and forehead, that grabs eyebrows as readily as eyes. Eyes are now up to 1.55×
within a tighter radius, the chin squeeze is 18% rather than 30%, and the head shrink 10% rather than 16%.

**Where this can still go wrong, honestly**

The eyes are *guessed* from the rectangle — a fifth of its width either side of centre, a quarter of its
height above the middle. That is where eyes are on a face, but the hardware's rectangle is loose and
varies between cameras, so on a box that sits high or wide the warp will still land slightly off. The real
fix is the camera's own eye and mouth landmarks, which `STATISTICS_FACE_DETECT_MODE_FULL` publishes on the
hardware that supports it. Roll currently asks for SIMPLE, which is the cheaper mode and carries only the
rectangle. Moving to FULL where available, and falling back to the guess where not, is the next step.
