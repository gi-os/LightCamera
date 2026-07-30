## Roll v2.26 — the viewfinder holds what you framed

The camera still takes 1.8 s to make a still and nothing here changes that. What changes is that the second
and a half stops feeling like no response.

**The frame is held at the press**

The panel frame is grabbed the moment you press — a couple of milliseconds — and shown over the preview, so
the viewfinder stops on your composition instead of carrying on live while the camera works. The moment reads
as taken, and the wait becomes "the file is being written" rather than "nothing happened". The real photograph
replaces it the instant it exists.

**A bar timed to this phone, not to a guess**

Underneath, a two-pixel bar that fills over however long stills have actually been taking — a rolling average
of the last few shots, seeded at the 1.8 s measured here. Determinate on purpose: a bar arriving at about the
right moment feels far shorter than a spinner, and nothing feels longer than one that stalls near the end. It
stops at nine tenths, because the last tenth belongs to the photograph actually arriving.

**What it deliberately does not do**

No shutter flash at the press. In Pro the light does not land when you press — `takePicture` meters, then
bursts, then stacks, so the exposure happens somewhere inside that second and a half. A flash animation at t=0
would assert otherwise. The held frame is honest about being a stand-in: same framing, from a moment slightly
earlier, replaced by the real thing in front of you rather than a difference you discover later in the roll.

Simple is unaffected — the frame it saves *is* the frame that was on the panel, so there is nothing to stand
in for.
