## Roll v2.18 — Simple gets its date, and stops waiting

**A date on Simple photographs, for free**

The date back works in Simple now, and costs nothing at the shutter. Printing it means decoding a 12MP JPEG,
drawing, and encoding again — a second of work with no business being between your finger and the
photograph. So the untouched sensor JPEG is saved the instant the shutter returns, and the date is printed
onto that file a moment later, off the main thread, while you are already framing the next one. If it fails,
or the app dies first, what is left is an undated photograph rather than none.

**Three things were making it wait about two seconds**

1. **Auto flash, which is not free even when it decides not to fire.** The HAL runs a precapture metering
   sequence first — usually a preflash, an exposure measurement and a pause — before it will start the frame
   you asked for. Simple now drops Auto to Off when you enter it. Turning the flash on there still works.
2. **Nothing was buffered.** `CAPTURE_MODE_ZERO_SHUTTER_LAG` is asked for again, in Simple only. This failed
   badly in v1.8 and the reason is now understood: ZSL hands back a frame captured *before* the press, and
   for the first second after binding there are none, so every capture failed. It is guarded three ways —
   Simple only, only after 1.5 s of the pipeline running, and if a capture ever fails the mode is abandoned
   for the rest of the process and the next bind goes back to minimise-latency. A dead shutter is not a
   trade worth making.
3. **Focus and exposure were being worked out at the press.** They needn't be: **half-press the camera
   button first.** The first detent locks AF *and* AE — `disableAutoCancel`, which is the load-bearing part
   — so the full press has nothing left to converge. That is what the two detents are for, and with the
   flash off and the buffer warm it is as close to instant as this hardware goes.

There is a note in Settings saying so, because a trick nobody knows about is not a feature.
