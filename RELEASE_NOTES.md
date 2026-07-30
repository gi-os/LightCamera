## Roll v2.27 — the held frame carries the filter, and the wait has two ends

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
