## Roll v2.19 — stop guessing, measure

**Every Simple shot now tells you where its time went**

After each photograph: `1420ms shot · 90ms save`. The first number is the camera hardware answering
`takePicture`; the second is this app writing the file. It goes to logcat as well, tagged `CameraViewModel`.

Three releases have gone into making Simple quick on the strength of my reasoning about where the time
goes, and you have told me three times that it is still slow. That is enough of that — the phone knows and
I do not, so this asks it. It is on by default; there is a switch in Settings to turn it off once the
answer is boring.

**How to read it**

- **A large first number** means the time is inside the camera HAL, and there is nothing left for the app
  to shave. The remaining levers are all hardware-facing: dropping to a smaller capture, or giving up on
  the sensor's JPEG and taking the viewfinder frame instead — which is instant but panel resolution.
- **A large second number** means it is the save, and that is squarely mine to fix.

**Two real changes while we find out**

- **The save is off the critical path.** It used to sit in the same coroutine as the capture, so the shot
  was not "finished" — and the next press not accepted — until five megabytes were on disk and a MediaStore
  row inserted. The camera is ready again the moment the bytes are in hand.
- **JPEG quality 88 in Simple**, 92 in Pro. Encode is a real slice of the shutter on a 12MP frame and cost
  is not linear in quality: 88 to 92 is a few percent of file size and nothing visible on a 3.92" screen,
  while the encoder does measurably less work. Pro keeps 92, where somebody asked for the best file.
