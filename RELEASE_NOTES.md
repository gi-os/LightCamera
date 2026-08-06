## LightCamera v2.42 — Fixing the Text shutter, which could stop working and never say so

**In Text mode the shutter did nothing.** Not sometimes — permanently, once it had happened, with
no message and no way to tell what was wrong. Here is the whole of it, because the shape of this
bug is worth more than the fix.

Both ways of reading text — the TEXT button on a photograph in the roll, and the shutter in Text
mode — share one "busy" flag, because they share one view model. The roll's version set the flag
before starting and cleared it *after finishing*. On the happy path that is the same thing. On any
other path it is not: a reading that threw, or that was cancelled, or that simply never came back,
left the flag set.

The view model is scoped to the activity, so that flag outlives the screen that set it. And the
first line of the Text-mode shutter was `if (busy) return` — no message, no sound, nothing. So the
sequence was: read a photograph on the roll once, have it fail in any way at all, and from then
on, for as long as the app was running, the shutter in Text mode was dead and silent.

Three things changed, and all three were needed.

**The flag is now cleared in a `finally`**, on both paths, so no outcome can leave it set. That is
the actual bug.

**The refusal is no longer silent.** A press that has decided not to do anything says "Still
reading". A button that quietly declines is indistinguishable from a broken phone, and this cost
more time than the bug did.

**The recogniser has a ceiling.** Twelve seconds, after which a reading is treated as never having
answered. Not tuning — a guarantee. The underlying task has no cancel, so without a ceiling one
call that never completes can still wedge everything behind it, and the `finally` would be waiting
on a coroutine that never ends.

Failures are also now caught rather than escaping into the void: a read that throws records itself
the way any other failure in this app does, so it offers to send a report with the reason in it
instead of looking like a photograph with no words on it.

Switching modes clears a stuck reader too. That is belt and braces rather than a fix — but a trip
through the mode strip is the first thing anybody tries when a mode looks broken, and it should
work.
