## Roll v1.7 — Game Boy, a dial that catches, and a send button with one destination

**Two Game Boy Camera filters**

- **Game Boy** — the DMG palette (`0f380f`, `306230`, `8bac0f`, `9bbc0f`), four shades and
  nothing between them, reached through a Bayer threshold so gradients break into the
  cross-hatch the hardware produced rather than banding.
- **GB Color** — the same sensor on a Game Boy Color: five levels a channel, dithered, slightly
  sour.

The green is only half of that look. The other half is the resolution, so both quantise the
image onto a grid of **128 cells across the short edge** — the GB Camera's actual sensor width —
sampling once per cell, because that is what a 128-pixel sensor does. Without the grid they'd
just be palette filters.

**The dial catches on None**

Every notch of the wheel now **vibrates**, whether or not it moves the dial — a physical control
that gives nothing back reads as a broken one.

The three-notch-wide None is gone. Landing on None now **stops the dial dead for 1.5 seconds**:
notches inside that window are felt and discarded, so a fast spin can't skate over it, and it
costs nothing to leave once the moment has passed. Widening it worked but took three deliberate
clicks to escape, which felt broken rather than detented. A film advance that catches at the
frame line does exactly this.

**The send button has one destination**

The viewer's send button is **disabled** until you turn on **Settings → Sending → Use
LightChat**. Then it hands the photograph straight to LightChat by name — no share sheet, no
chooser. A grid of every app that ever registered for an image is the one place a Light Phone
stops feeling like a Light Phone.

If LightChat isn't installed or can't take images, it says so rather than throwing.
