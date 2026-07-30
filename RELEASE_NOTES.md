## Roll v2.28 — send a photograph to a person, not to an app

Tapping send used to open Android's share chooser: a colour Material sheet listing every app that ever
registered for an image, on a phone whose whole argument is that there aren't any — and then you still had to
pick the person once you were inside one. The chooser answers "which app?". The question you actually have is
"who?".

**So the send button now opens your contacts.**

A plain list of people in the app's own type and grid, the ones you sent to most recently at the top, and a
search box that takes either letters or digits — type `alex`, or type `5550148` and never mind how the number
is punctuated. Tap somebody and the photograph goes out addressed to them.

Android will not let an app ask this question the easy way. The row of faces at the top of the stock chooser is
built from *sharing shortcuts*, which each app publishes for the system's own UI, and there is no API to read
another app's. So Roll reads the address book itself — a new contacts permission, asked for the first time you
open the picker, read on the phone and sent nowhere.

**Hold a photograph in the roll to send several at once**

Long-press any frame to start choosing; unpicked ones dim, tap to add and remove, and the header counts them.
The tick stays upright when you turn the phone, because it's a control rather than part of the picture.
Sending clears the selection — left standing, the next send would have quietly included everything already
sent, to somebody else.

**Where the photograph actually goes**

LightChat, if LightChat can take it. On this build it cannot: it registers no share filter at all, which is
why the old send button reported "LightChat can't receive photos" on a phone with LightChat installed. Until
that changes, a send goes to whatever else on the phone handles addressed messages — and only to something
that plausibly understands what a recipient *is*. Handing the photograph to the first app that resolved was
the previous behaviour and it was wrong in the worst way: a wallpaper cropper or a cloud drive accepts an
image send, ignores the recipient, and the picker closed as though the photograph had reached Alex.

**Smaller things in here**

- Notices are drawn once, above everything, instead of on a pager page that overlays covered up. Every failure
  message from the viewer, from settings and from the picker was previously invisible — a send that failed
  looked exactly like a tap that hadn't registered.
- The recipient is the contact's own preferred number, then a mobile, rather than whichever row the address
  book happened to return first. A photograph sent to a landline is a photograph nobody sees.
- An email-only contact is sent to as an email address, not by putting one in a field meant for a phone number.
- A selection of screenshots is described as what it is. The type was hard-coded to JPEG, which contradicts
  the attachment's real type and invites the receiver to re-encode it.
- Refusing contacts twice no longer leaves a dead button: Android stops showing the dialog, and Roll now says
  so and offers the settings page instead.
- Back leaves selection before it leaves the roll — and no longer swallows the back press on the viewfinder,
  where the roll page is still composed but not on screen.
