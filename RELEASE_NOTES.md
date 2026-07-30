## Roll v1.5.7 — the instant crash

v1.5.6 died on launch. Sorry.

`viewModelScope` runs on `Dispatchers.Main.immediate`, and the view model is built on the main
thread — so every collector launched in `init` starts executing **synchronously inside the
constructor**, and a `StateFlow` hands over its current value the moment you subscribe. The
recording collector therefore wrote to a counter declared thirty lines further down the class,
which was still null, and the app died in the constructor with a null-pointer exception on a
property Kotlin had promised was non-null.

Every field that `init` touches now sits above it, with the reason written beside the block.

**And a crash log**, so this never needs a round trip again. The default uncaught-exception
handler writes the trace into the app's own storage, and Settings shows it at the top with the
first fourteen lines — tap to clear. Nothing leaves the phone: no network call, no identifier,
just a text file the app will read back to you.

Everything in [v1.5](https://github.com/gi-os/LightCamera/releases/tag/v1.5.6) is in this build —
sideways chrome with an upright picture, the Camera / Video / Selfie picker, the three-notch
detent on None, and the level measured off the nearest quarter turn.
