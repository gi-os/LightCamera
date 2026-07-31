## Roll v2.35 — shake the phone to report a glitch

**Shake Roll twice and a small SEND ERROR? chip appears in the corner; tap it and the phone files
a GitHub issue against the private tracker.**

The report carries what went wrong, the build and firmware, how much space and heap were left, the
last crash log if there is one, and — only while the row stays ticked — a screenshot of the moment
you started shaking. The same sheet is on the settings screen under SEND A REPORT.

This is the same reporting that landed in gi-os/LightNotebook, ported deliberately unchanged. It is
diagnostic UI rather than product surface, so it should look and behave identically in every app
that has it: one learned gesture, not four.

**Roll now has the INTERNET permission, which it never had before.**

Worth saying plainly, because a camera app that can reach the network is a thing you should notice
rather than discover. Roll holds the camera, the microphone, your contacts and your whole photo
library, and until now it had no way to send any of it anywhere. It opens a socket in exactly one
place — `report/Reports.kt`, after you have tapped SEND on a report you wrote yourself. Nothing is
uploaded in the background, on launch, or on a timer.

The key it posts with can do one thing: file issues on one private repository. It cannot read that
repository's contents, and it cannot touch any other repo. That constraint is why the screenshot
travels as base64 inside the issue body rather than as a committed file.

**The gesture is four reversals past 0.46g** — two quick shakes. It counts reversals rather than
force, which is what separates it from a camera being carried, pointed and set down all day. Being
wrong is meant to be cheap: the chip fades after four seconds and deletes nothing.
