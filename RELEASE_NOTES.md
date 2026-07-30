## Roll v1.9 — legible chrome, and the shutter fixed

**"Shutter failed" is fixed**

Zero shutter lag, added in v1.8, was the cause. CameraX documents a silent fallback where the
hardware won't do it — but that covers *configuration*, not capture: this camera accepted the
mode, bound without complaint, and then refused the first captures while its ring buffer filled,
which is exactly why it "started working shortly after". It's gone. Minimise-latency is what works
here, and a shutter that fires beats one that is early.

The resolution cap from v1.8 is what actually made the shutter quick, and that stays.

Capture failures now report **what** the camera said instead of "Shutter failed". Guessing at that
message cost a round trip.

**The microtext is gone**

Every readout has moved up two steps of the type scale — the 8-design-pixel `Micro` variant works
out at about six points on this panel, which is not a size anything you need at arm's length
should be set in. Nothing in a screen uses it any more.

And some of it just went: the count of photographs beside each day in the roll was a number the
interface offered because it happened to know it, not because anyone wanted it.

**The newest photograph is bottom right**

`reverseLayout` fills the roll from the bottom, which is what puts the newest frame against the
viewfinder — but rows still filled left to right, so the newest landed bottom-*left* and the corner
nearest your thumb held the third-newest. The grid is laid out right-to-left now: the newest takes
the bottom-right cell and the roll fills leftwards and upwards, the way a contact sheet does.
