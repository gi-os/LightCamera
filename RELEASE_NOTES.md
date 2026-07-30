## Roll v2.9 — a menu, and the shutter takes four

**Tap PURI in the band**

The frame chip is a menu now, because there was too much behind it for a chip that cycled one thing:
frame, face stickers, margin stickers, date, four-shot strip. Rows you tap to cycle, in the same shape
LightOS settings use, opening out of the band the way the mode picker does.

Face stickers and margin stickers are separate switches on purpose. The face-anchored ones — ears,
blush, shades — depend on the detector finding a face and can land wrong when it drifts; the margin
ones cannot. One switch would mean a bad detection cost you the whole look instead of the ears.

**Everything is rolled at random when the app starts**

A booth does not remember what you chose last week. Each launch picks a frame and flips the stickers
and the date for you, and the menu is there to overrule it for the session. None of it is written to
disk. Four-shot always starts off — a strip is something you decide to do, not something that happens
to you on the first photograph of the day.

**Four shots, three seconds apart, one print**

Press the shutter once. It counts you in before every frame, including the first, and you cannot stop
halfway. Seven layouts: Off, Classic, Bare, Mount, Framed, Rails, Grid — a proper booth strip with a
footer, four frames touching with no gutters at all, a wide blank mount, your chosen Purikura frame
drawn once around all four, Roll's own film rails with perforations down both long edges, and a 2×2
sheet.

Stickers are reshuffled between the four, deliberately: a booth's panels are four different
decorations of the same three seconds, and identical cat ears in every panel looks like a mistake
rather than a set.

**The roll shows one print, not five photographs**

The strip goes into the camera roll. Its four frames are saved too, but into their own folder that
every roll query excludes — four near-identical photographs of the same three seconds filling a whole
screen of the grid is not what you were looking for. Open the strip and there is a **Frames** button on
it; that is the way to them, rather than a folder to go and find. They are linked by the timestamp in
their names, which is the only relationship MediaStore has anywhere to store.

**Under it**

Strip geometry is arithmetic with no bitmap in it — how big the sheet is and where the third
photograph goes — so it is unit tested, including that no two cells ever overlap in any layout and
that a footer always leaves paper to print on.
