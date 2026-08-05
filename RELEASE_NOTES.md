## Roll v2.39 — backed up, and a smaller app

**What's new:** Roll now offers its settings to [LightSync](https://github.com/gi-os/LightSync), so
a new phone comes up with your stars, your stamp style and your roll numbering already in place.
And the release APK is minified for the first time, which is worth about a third of its size and
a noticeably shorter wait for the viewfinder.

### The backup, and what is deliberately not in it

Your photographs are not backed up by Roll, and they should not be. They live in `DCIM/Camera` —
the phone's real camera roll, shared with every other app — and that is exactly the storage a
per-app backup has no business speaking for. Pushing gigabytes of JPEG across the house so that
one app can hand you back files it does not own would be slow, enormous, and a duplicate of
whatever already backs up your pictures.

So what Roll offers is the small set of facts about your photographs that exist nowhere else:

**The stars.** A favourite is the one thing about a photograph that only Roll knows. Android's own
`IS_FAVORITE` flag is effectively writable only by the system gallery, so the list lives here. It
is keyed by file name rather than by id, which is why it survives your photos being restored onto
a different phone.

**The settings**, and **the film-roll counter** — which roll is loaded, how long it is, and how
many you have developed, so the next one is roll 12 and not roll 1 again.

An undeveloped roll's frames stay behind. They are full-size JPEGs, same size argument as above,
and a half-shot roll is not a thing you restore onto another phone — the counters come back and
the frames do not.

### The wheel now comes from somewhere else

The wheel handling — the two-notch guard against a stray brush of your thumb, the glide, the
separate meaning of a turn with the wheel held in — moved out of this app and into the shared
`light-common` library that all the Light apps use. The code is identical, notch for notch and
millisecond for millisecond; it was written here first and the library is where it went. Nothing
about how the wheel behaves changes. Zoom is still a bare turn, exposure compensation is still a
turn with the wheel pressed in, and the roll still scrolls the right way round.

Shake-to-report stayed put. It is the one part of the shared library Roll does not use yet,
because Roll's version attaches a screenshot to the issue and the library's does not. Moving it
would quietly make every future bug report harder to read.

### Minification, and the honest risk

Release builds now run through R8 in full mode: dead code removed, classes merged, names
shortened. It is the single cheapest thing available for cold-start time on this phone.

It is also the change most likely to break something, because the shrinker only keeps what it can
see being used, and anything reached by name rather than by a call is invisible to it. The rules
that protect those paths are written down with a note each saying what would break without it.
The two that took thought:

- **Every setting is stored by name.** `Large`, `Continuous`, `Viewfinder` — the shrinker renames
  those to single letters and rewrites the stored string with them, and every setting silently
  reverts to its default. If your preferences come back blank after this update, that is what
  happened.
- **The camera backend is found by name, not by a call.** Nothing in the code refers to it, so
  full mode is entitled to delete it and leave a black viewfinder.

Face detection needed nothing, for a reason worth stating: Roll uses the phone's *hardware* face
detector through the camera driver, not ML Kit. There is no model loaded by name and no ML Kit in
the app at all. If face detection does stop working after this update, it is a missing keep rule
and not a broken detector — file it with a shake and it will be a one-line fix.

Video mode is the other thing to try. It binds a different camera path that only starts when you
switch to it, so a working viewfinder is not evidence that recording still works.

**If anything in this release misbehaves, shake the phone.** That is what it is for, and the
stack traces stay readable through minification on purpose.
