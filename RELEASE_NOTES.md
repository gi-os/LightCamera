## Roll v2.14 — favourites, three sign errors and a switch

**Tapping a photograph opens that photograph**

My regression from v2.11. A `LaunchedEffect` fires once on composition as well as on every change, and the
one that resets the pager when you swap to a strip's frames was therefore firing the moment the viewer
opened — throwing away the page computed from the photo you tapped and jumping to the end of the roll. It
now skips its first run.

**The chin shrinks the chin**

And the eyes are on the eyes. These were the same fault: the axis meaning "down the face" pointed up, so
the jaw squeeze landed on the forehead — and the eye magnification, which is placed on the opposite side
of the same axis, had been landing near the mouth all along. That is most of why the shaping looked wrong
rather than merely strong. One sign, both faults.

**The level leans the right way**

Also a sign. The tilt reading comes from `atan2(x, y)`, which grows as the phone turns anticlockwise,
while a rotation is clockwise-positive — so cancelling the tilt means adding it, not subtracting. It was
leaning the wrong way by twice the angle. The same mistake as the viewer's rotation two releases ago, in a
different file; three of those now, which is enough to be a pattern worth remembering.

**The level has a switch**

Settings → Camera. On by default, since it only appears when you are crooked and goes away a beat after
you straighten up — but it is still a line through the middle of the frame.

**Favourites**

Open a photograph and tap the star. The roll's scope label walks three positions now — Camera roll, All
photos, Starred — so the starred ones are a collection you can browse rather than a flag you can only see
one photo at a time.

Stored **by file name, not by MediaStore id**. An id is a row number: rescan the volume, move a file or
restore a backup and the same photograph comes back with a different one, and a favourites list that
quietly empties itself is worse than none. Unlike the Purikura settings this list is written to disk — a
star is a statement about a photograph, and it should still be there next week.

The grid narrows in memory rather than re-querying, so starring something updates it immediately instead
of flickering through a round trip to MediaStore. `IS_FAVORITE` exists in MediaStore and would have been
the tidy answer, but only the system gallery is allowed to write it.
