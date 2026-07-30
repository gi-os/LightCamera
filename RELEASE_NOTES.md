## Roll v2.27 — filters hold honestly, the wait has two ends, and Simple is opt-in

**Filtered photographs hold a filtered frame**

The hold worked for filters already, but it was holding the wrong picture. `previewFrame` returns the
*unfiltered* surface — the live filter is a `RenderEffect` on the view and never reaches the bitmap — so a
Game Boy shot froze on a plain one and then saved a dithered one. That is the same dishonesty as the Purikura
preview showing one thing and saving another, which you caught me on twice.

The same shader now runs over the held frame at panel size, where it costs a few milliseconds. What you hold is
what you get.

**The click moved to the press**

It was firing when the capture *returned* — a second and a half after your finger, which is the wrong end of
the event entirely. A shutter sound is feedback for the press.

**And the file landing has its own sound**

Two notes rising, quiet, when the photograph is actually on disk. Deliberately unlike the focus confirmation,
which is two of the *same* note: rising says finished rather than ready. So the ear brackets the wait — click
when you press, chime when it lands — and the second and a half becomes a process with two ends rather than a
delay with one.

It plays for every photograph that reaches disk, Simple and Pro alike, and follows the existing Sounds switch.

**Simple is a switch now, and it starts off**

Settings → Simple mode. Off, Simple is not in the mode picker and the wheel does not walk into it — as far as
the camera is concerned it does not exist, and Pro is what opens. On, it joins the picker as before.

That is the right shape for it: Simple buys an instant shutter with resolution, panel-sized instead of 12MP,
and a trade is something to opt into rather than find yourself in.

**The band names the filter, not the mode**

In Pro the mode slot now reads MONO, GAME BOY, PURIKURA — whatever is on — instead of "PRO". "Pro" labels
something you can already see from the chrome; which filter is loaded is the one piece of state you cannot
read off the picture with certainty, and it is exactly what the wheel changes. Video, Selfie and Simple keep
their own names, because in those the mode is the news.

**And the Purikura chip says OPTIONS**

Which is what is behind it: a frame, two kinds of sticker, a date, a four-shot strip and five parts of the
look.
