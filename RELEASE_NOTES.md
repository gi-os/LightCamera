## Roll v2.1 — pick the size, or skip the capture entirely

**Settings → Frame → Size**

`50MP · 12MP · 5MP · 2MP · Screen`, and on this phone that is the same dial as shutter speed.
Reading out and encoding a 50MP frame is most of a second of the ISP's time; each step down is
roughly a halving. 12MP stays the default — four times the largest print anyone makes from a phone.

**Screen doesn't take a photograph at all**

It saves the frame already on the viewfinder. No `takePicture`, no sensor readout, no JPEG from the
ISP — the pixels off the panel, turned upright, cropped to your shape, put through the same shader
as any other photograph, encoded. As instant as this app can be.

And with a filter on it is *better* than the slow path, not just faster: it is **exactly the frame
you were looking at**, rather than a second frame captured afterwards and processed to match. The
shader runs at panel resolution, which is a fraction of the work, and the pattern filters look
identical because they scale themselves to the image either way — a Game Boy shot at Screen size is
the same 128 cells as one at 12MP.

Worth knowing: it is panel resolution, so about 1080px on the long edge. For Dither 16, 1-Bit,
Halftone and the two Game Boy filters that is arguably the right size anyway.

**Also**

The three places a finished photograph can go — another app's `IMAGE_CAPTURE` request, a loaded
roll, the gallery — are now decided in one place instead of two. The fast path was missing the roll
branch when it was first written, which would have quietly dropped frames from a loaded roll.
