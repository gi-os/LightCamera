## Roll v2.31 — the camera key opens the camera

Press the shutter key with Roll already running and you arrived wherever you left it — the roll, a photograph,
the settings. A camera button should mean the viewfinder.

**Why it happened**

The activity is `singleTop`, so the key does not start a new instance: it resumes the existing one, and the
pager's `initialPage` only applies to the first composition. Everything about the app was working as written;
what was missing was anybody telling it a launch had happened.

**The fix**

`onNewIntent` now signals the view model, and the shell goes back to the viewfinder — closing the viewer and
the settings on the way, since a photograph covering the picture is the same problem as being on the wrong
page.

The signal is a `SharedFlow` rather than a flag, deliberately. A boolean already true would not re-fire on the
second press, and a state-keyed effect would run at startup — which is precisely the bug that made tapping a
photograph open the newest one back in v2.11. A shared flow fires only on emission and never replays, so both
faults are impossible rather than merely avoided.

Mode is untouched: Pro is already the default and Simple is opt-in, so a key press lands on the normal camera.
