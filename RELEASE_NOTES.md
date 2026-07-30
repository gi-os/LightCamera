## Roll v2.15 — the camera lets go, and the send button admits the truth

**Why sending never worked, and it was never Roll's fault**

LightChat declares no `ACTION_SEND` intent filter. Its only activity filter is MAIN/LAUNCHER, so an
explicit send to `com.gios.lightchat` cannot resolve no matter what this app does — every fix on this side
was addressing the wrong half of the problem.

So the button now asks the system who can actually take an image, and acts on the answer:

- LightChat, if LightChat can take one and you have it preferred.
- Otherwise a chooser, so the photograph goes *somewhere* instead of nowhere. On a Light Phone that is a
  short list, not the wall of icons the original comment was worried about.
- And if nothing on the phone accepts an image, it says so.

The setting is a preference now rather than a gate, and the button is always live. Making the LightChat
route work needs a filter on **LightChat's** side plus something in its UI to do with the photograph — that
is a change to the other repo.

**The camera lets go while you are looking at photographs**

The sensor is the most expensive thing this app can leave running, and it was bound the whole time the
roll, the viewer or the settings were on screen — a full-screen photograph with a live preview stream
behind it. The `active` flag already knew the viewfinder was not visible; it was only being used to stop
*drawing*.

Now it unbinds: sensor, ISP, preview stream and the orientation listener, which exists only to keep the
capture rotation right and has nothing to keep right. Coming back is a rebind rather than a cold start,
and there is a 400 ms grace before letting go, because flicking to the roll and back is a common gesture
and a rebind mid-flick would show a black frame. Recording is never interrupted.
