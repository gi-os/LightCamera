## Roll v2.0 — the type scale, fixed at the source

**Everything is about a quarter larger**

Moving individual readouts up the scale was never going to fix this, and it took two goes to see
why. The SDK's type scale divides each design pixel by a 600px baseline — but the LPIII's panel is
about 477dp tall, so every size came out at **0.79 of what the scale intends**: `Detail`, drawn at
20 design pixels, rendered as 16sp. The scale was describing a 600dp screen and being applied to a
much shorter one.

It now divides by the panel's own height, so a design pixel is a point and the scale means what it
says. One number, every screen at once, and the proportions between the variants are untouched.

**Three labels gone**

- The **filter name** no longer flashes at the bottom when you turn the wheel. The viewfinder is
  already showing you the filter; a label naming what you can plainly see is a label in the way of
  it. The buzz says the dial moved, the picture says where to.
- The **`AF-S` badge** is gone from the corner. The focus mark already says what focus is doing —
  brackets while it hunts, a closed box when it has it. Switch modes in Settings → Focus.
- The **filter name** is gone from the corner too, for the same reason as the first.

What stays in that corner is only what you couldn't otherwise know: that it's recording, that the
torch is on, that the lens is zoomed, that exposure is pushed, that a timer is armed. Each
disappears the moment it goes back to normal.
