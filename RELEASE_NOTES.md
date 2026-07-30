## Roll v2.22 — "camera is closed" when changing megapixels in Simple

That error was mine, and it was a leftover. `shootSimple` used to set the photo size to 12MP before each
shot and put it back afterwards — and changing the photo size **rebinds the camera**. Rebind while a capture
is in flight and the HAL answers "camera is closed", which is precisely what happened when you touched the
megapixels in Simple.

The whole dance is gone. Since v2.21 Simple's resolution comes from the live stream rather than from a still
request, so the size setting has nothing to do with it: **Size is a Pro setting, and Simple ignores it.**
