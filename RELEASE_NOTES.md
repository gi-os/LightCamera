## Roll v2.34 — filtered selfies come out the right way up, and the shutter says something when it fails

**A mirror and a quarter turn do not commute.**

`Frames` mirrored a captured frame first and turned it upright second. A mirror followed by a
quarter turn is the same transform as the quarter turn followed by a *vertical* flip, so every
filtered photograph taken on the front lens was saved upside down and mirrored the wrong way.
An unfiltered selfie was fine, which is what hid it: with no filter, no crop and no date the
sensor's own JPEG is the file and it never reaches that code. The mirror now happens after the
rotation, in the finished frame's own axes.

The flipped EXIF orientations are understood too. A HAL that writes `TRANSVERSE` or
`FLIP_HORIZONTAL` has already declared the frame mirrored, and mirroring it again for the
selfie put it back exactly where it started — so the two now cancel.

**Four ways the shutter could produce nothing at all**

- `takePicture` reports success and failure through a callback, and a capture that delivers
  neither used to leave the shooting flag latched. That flag is the first line of the shutter, so
  every press after it was dropped without a word until the app was force-stopped. There is a
  twelve-second deadline on the capture now — long enough that no real photograph is ever cut
  short.
- A capture that misses the deadline saves the frame on the viewfinder instead of nothing, and
  says so. A camera whose stills unit has stopped answering degrades into a working camera.
- A filter the GPU refuses on a full-resolution still used to throw out of the shutter's
  coroutine, which has no handler, which kills the process — and since the camera key relaunches
  Roll, that arrived as a shutter that had done nothing. Now the unfiltered photograph is written.
  A filter that could not run costs you the filter, never the picture.
- Every shooting routine catches everything and names it on the viewfinder.

**Two things found on the way**

A 50-megapixel filtered capture was decoding all 200MB of it before scaling straight back down
to fit a GPU texture; it samples down in the decoder now, for half a percent of a linear edge and
150MB of peak. And pressing the shutter while the camera is unbound — a lens switch the phone
refused, most likely — says "camera isn't ready" rather than looking ordinary and doing nothing.
